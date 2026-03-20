package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.List;

import com.mojang.logging.LogUtils;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Single-goal hierarchical state machine (HSM) that drives all duck locomotion.
 *
 * <p>Replaces the four separate goals that previously handled panicking, migration,
 * leader-following, and wandering. Consolidating them here eliminates the class of
 * bugs where two goals race to call {@link DuckEntity#startFlying()} /
 * {@link DuckEntity#stopFlying()} in the same tick.
 *
 * <h3>Architectural invariants</h3>
 * <ul>
 *   <li>This is the <strong>only</strong> caller of {@code startFlying()} /
 *       {@code stopFlying()}, always from {@link #doTransition}.</li>
 *   <li>The {@link Goal.Flag#MOVE} mutex ensures exactly one MOVE-holding goal
 *       runs per tick, so no external goal can interfere with locomotion state.</li>
 * </ul>
 *
 * <h3>State selection (per tick)</h3>
 * <ol>
 *   <li>For every state with <em>higher</em> priority than current: if
 *       {@link #canEnter} → preempt immediately.</li>
 *   <li>If no higher-priority state preempts, keep current if
 *       {@link #canContinue} is true.</li>
 *   <li>Otherwise scan lower-priority states for the first {@link #canEnter}.</li>
 * </ol>
 *
 * <h3>Tick flow</h3>
 * <ol>
 *   <li>Deferred takeoff check (clears {@code pendingTakeoff} if duck surfaced).</li>
 *   <li>Water guard: stops nav and returns early when duck is fully submerged
 *       and not in flying mode — lets {@code FloatGoal} surface the duck
 *       without interference.</li>
 *   <li>Advance migrate cooldown.</li>
 *   <li>Find best state via priority scan.</li>
 *   <li>Transition if state changed.</li>
 *   <li>Tick current state.</li>
 * </ol>
 *
 * <h3>BEFORE CHANGING THIS CLASS</h3>
 * See the AI State Machine Change Protocol in {@code CLAUDE.md}. Update the
 * state diagram there first, reason through every guard, then implement.
 */
public class DuckBrainGoal extends Goal {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * All brain states in priority order — lower ordinal = higher priority.
     *
     * <p>The ordering determines preemption: a higher-priority state that
     * satisfies {@link #canEnter} will interrupt any lower-priority state
     * on the very next tick.
     */
    public enum BrainState {
        /** Adult duck was hurt recently → takes off and flies to a random aerial escape point. */
        PANIC_FLY,
        /** Baby duck was hurt recently → sprints on the ground (babies cannot fly). */
        PANIC_GROUND,
        /** Untamed adult leader → flies 80–140 blocks to a random land target. */
        MIGRATE,
        /** Follower duck → flies in V-formation behind its leader while leader is airborne. */
        FOLLOW_LEADER_AIR,
        /**
         * Transitional state entered whenever an airborne state ends.
         * {@code stopFlying()} is called on entry (gravity re-engages); no nav is issued
         * until the duck physically lands ({@code onGround()} or 60-tick timeout).
         * Never selected by the priority scan — only entered via the intercept in
         * {@link #doTransition}.
         */
        LANDING,
        /** Tamed adult leader → walks toward owner when nearby and head-stable. */
        FOLLOW_OWNER,
        /** Follower duck → walks toward its leader while leader is on the ground. */
        FOLLOW_LEADER_GROUND,
        /** Baby duck → walks toward the nearest adult duck. */
        FOLLOW_PARENT,
        /** Random ground stroll — fires on a ~1/120-tick chance. */
        WANDER,
        /** Fallback — no nav, duck stands idle. Always {@link #canEnter} returns true. */
        IDLE
    }

    // ── Speed constants ──────────────────────────────────────────────────────
    private static final double PANIC_SPEED           = 1.25;
    private static final double MIGRATE_SPEED         = 1.12;
    private static final double FOLLOW_AIR_FAR_SPEED  = 1.10;
    private static final double FOLLOW_AIR_NEAR_SPEED = 0.55;
    private static final double OWNER_SPEED           = 1.05;
    private static final double FOLLOW_GND_FAR_SPEED  = 1.10;
    private static final double FOLLOW_GND_NEAR_SPEED = 0.55;
    private static final double STROLL_SPEED          = 1.08;
    private static final double FOLLOW_PARENT_SPEED   = 1.10;
    private static final double LANDING_DESCEND_SPEED = 0.7;

    // ── Follow-leader geometry ────────────────────────────────────────────────
    private static final float  SEARCH_RADIUS  = 18.0F;
    private static final float  START_DIST     = 4.2F;
    private static final float  STOP_DIST      = 2.0F;
    private static final double FAR_MULTIPLIER = 2.25;  // far² = (startDist * FAR_MULT)²
    private static final float  OWNER_STOP_DIST    = 4.0F;
    /** Beyond this distance FOLLOW_OWNER stops; re-engages when owner re-enters START_FOLLOW_OWNER_DIST. */
    private static final float  OWNER_FOLLOW_MAX_DIST = 20.0F;

    // ── Stability / hysteresis ────────────────────────────────────────────────
    /** Ticks after a non-panic state is entered before non-panic preemptions are allowed. */
    private static final int MIN_STATE_TICKS = 15;
    /** Max ticks LANDING stays active waiting for the duck to touch down. */
    private static final int MAX_LANDING_TICKS = 60;
    /** Skip moveTo in follow-leader if target moved less than this distance² since last issue. */
    private static final double FOLLOW_REPATH_THRESHOLD_SQ = 1.0;

    // ── Core ─────────────────────────────────────────────────────────────────
    private final DuckEntity duck;
    private BrainState currentState = BrainState.IDLE;

    // ── MIGRATE ───────────────────────────────────────────────────────────────
    private int  migrateCooldown = 0;
    private Vec3 migrateTarget   = null;

    // ── FOLLOW_LEADER (shared by AIR and GROUND states) ───────────────────────
    // pendingLeader: candidate found in canEnter(), awaiting formal slot claim in enterState()
    // claimedLeader: formally claimed leader matching duck.getLeader()
    private DuckEntity              pendingLeader  = null;
    private DuckEntity              claimedLeader  = null;
    private DuckEntity.FollowerSlot mySlot         = null;
    private int                     repathCooldown = 0;
    /** Last target position passed to moveTo; used to skip repath when target barely moved. */
    private Vec3                    lastIssuedFollowTarget = null;

    // ── WANDER ────────────────────────────────────────────────────────────────
    private Vec3 wanderTarget = null;

    // ── Locomotion ────────────────────────────────────────────────────────────
    /** True when an airborne transition was requested while duck was underwater. */
    private boolean pendingTakeoff = false;

    // ── Hysteresis / LANDING ──────────────────────────────────────────────────
    /** Countdown after state entry; non-panic preemptions by higher-priority states are blocked while > 0. */
    private int     minStateTicksLeft = 0;
    /** Set in doTransition's airborne→ground intercept; cleared in enterState(LANDING). */
    private boolean pendingLanding    = false;
    /** Countdown during LANDING; forces exit after MAX_LANDING_TICKS even if duck is still airborne. */
    private int     landingTicks      = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public DuckBrainGoal(DuckEntity duck) {
        this.duck = duck;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // ── Goal lifecycle ────────────────────────────────────────────────────────

    @Override public boolean canUse()           { return true; }
    @Override public boolean canContinueToUse() { return true; }

    @Override
    public void start() {
        currentState = BrainState.IDLE;
    }

    @Override
    public void stop() {
        doTransition(currentState, BrainState.IDLE);
    }

    @Override
    public void tick() {
        // 1. Deferred takeoff — duck was underwater when an airborne state was entered.
        //    FloatGoal (JUMP only) will have surfaced the duck by now.
        if (pendingTakeoff && !duck.isInWater()) {
            duck.startFlying();
            pendingTakeoff = false;
        }

        // 2. Water guard — stop nav only when fully submerged (eyes below surface).
        // A duck floating at the surface can still navigate to land normally.
        // isUnderWater() = eyes below surface, NOT just touching water.
        if (duck.isUnderWater() && !duck.inFlyingMode()) {
            duck.getNavigation().stop();
            return;
        }

        // 3. Advance cooldowns every tick.
        if (migrateCooldown > 0) migrateCooldown--;
        if (minStateTicksLeft > 0) minStateTicksLeft--;

        // 4. Find the highest-priority state that should run.
        BrainState desired = findBestState();

        // 5. Transition if the desired state changed.
        if (desired != currentState) doTransition(currentState, desired);

        // 6. Tick the current state.
        tickCurrentState();
    }

    /**
     * Exposes the current brain state so {@code DuckEntity.updateDuckState()}
     * can compute the correct {@code DATA_ANIM_HINT} for the GeckoLib controller.
     */
    public BrainState getBrainState() { return currentState; }

    // ── State selection ───────────────────────────────────────────────────────

    /**
     * Scans all {@link BrainState} values in priority order and returns the
     * state that should run this tick.
     *
     * <ul>
     *   <li>States with lower ordinal than current: checked for {@link #canEnter}
     *       — panic states (ordinals 0–1) always preempt; other higher-priority
     *       states are blocked by {@link #minStateTicksLeft} hysteresis
     *       <em>only while the current state is still running</em>.</li>
     *   <li>Current state: kept if {@link #canContinue} is true.</li>
     *   <li>States with higher ordinal: only considered once the current state
     *       is done; first {@link #canEnter} wins.</li>
     * </ul>
     *
     * <p>Hysteresis bypass: when {@code canContinue(current)=false} (i.e. the
     * current state has ended), the hysteresis window is ignored so that
     * higher-priority states can immediately take over without the duck
     * temporarily falling to {@link BrainState#IDLE} and losing formation slots.
     *
     * @return the state that should be active after this tick
     */
    private BrainState findBestState() {
        int  cur         = currentState.ordinal();
        boolean curDone  = !canContinue(currentState);

        for (BrainState s : BrainState.values()) {
            int ord = s.ordinal();
            if (ord < cur) {
                // Higher-priority state — can it preempt?
                // Panic states always preempt.
                // Other higher-priority states are blocked by hysteresis ONLY while the
                // current state is still running (curDone=false). When the current state
                // has already ended (curDone=true), all states compete freely so that
                // e.g. FOLLOW_LEADER_AIR can immediately replace a finished
                // FOLLOW_LEADER_GROUND without the slot being momentarily released.
                boolean isPanic = ord <= BrainState.PANIC_GROUND.ordinal();
                boolean hysteresisBlocks = !isPanic && !curDone && minStateTicksLeft > 0;
                if (!hysteresisBlocks && canEnter(s)) return s;
            } else if (s == currentState) {
                if (!curDone) return s;   // keep running
                // current is done; fall through to lower-priority states
            } else {
                // Lower-priority state — only considered when current is done
                if (curDone && canEnter(s)) return s;
            }
        }
        return BrainState.IDLE; // IDLE.canEnter() is always true, so unreachable normally
    }

    // ── canEnter guards ───────────────────────────────────────────────────────

    /**
     * Returns true if the duck may <em>begin</em> the given state this tick.
     *
     * <p>Called speculatively during the priority scan in {@link #findBestState}.
     * Must be side-effect-free except for the following intentional writes:
     * <ul>
     *   <li>{@code pendingLeader} — set by the follow-leader helpers to avoid
     *       a redundant AABB search in {@link #enterState}.</li>
     *   <li>{@code wanderTarget} — set by {@link #canStartWander} so the
     *       position computed here is reused verbatim in {@link #enterState}.</li>
     * </ul>
     * Neither write has observable side effects before {@link #enterState} consumes them.
     */
    private boolean canEnter(BrainState s) {
        return switch (s) {
            case PANIC_FLY            -> !duck.isBaby() && shouldPanic() && !duck.isInWater();
            case PANIC_GROUND         -> duck.isBaby() && shouldPanic();
            case MIGRATE              -> canStartMigrate();
            case FOLLOW_LEADER_AIR    -> canStartFollowLeaderAir();
            // LANDING is never entered via the priority scan — only via doTransition intercept.
            // pendingLanding is true only for the single tick of the intercepted transition.
            case LANDING              -> pendingLanding;
            case FOLLOW_OWNER         -> canStartFollowOwner();
            case FOLLOW_LEADER_GROUND -> canStartFollowLeaderGround();
            case FOLLOW_PARENT        -> duck.isBaby() && findParent() != null;
            case WANDER               -> canStartWander();
            case IDLE                 -> true;
        };
    }

    // ── canContinue guards ────────────────────────────────────────────────────

    /**
     * Returns true if the duck may <em>keep running</em> the given state this tick.
     *
     * <p>When this returns false for the current state, {@link #findBestState()}
     * sets {@code curDone=true} and falls through: higher-priority states are checked
     * without hysteresis, then lower-priority states. The state machine never gets
     * "stuck" in a finished state.
     */
    private boolean canContinue(BrainState s) {
        return switch (s) {
            case PANIC_FLY, PANIC_GROUND ->
                    shouldPanic() && !duck.getNavigation().isDone();
            case MIGRATE ->
                    duck.isLeader() && !duck.isBaby() && !duck.getNavigation().isDone();
            case FOLLOW_LEADER_AIR -> {
                DuckEntity l = duck.getLeader();
                yield l != null && l.isAlive() && !l.isRemoved() && l.inFlyingMode();
            }
            // Stay in LANDING until the duck physically touches down, or the timeout expires.
            case LANDING -> !duck.onGround() && landingTicks > 0;
            case FOLLOW_OWNER -> {
                var owner = duck.getOwner();
                yield duck.isTame() && duck.isLeader()
                        && owner != null && owner.isAlive()
                        && duck.distanceTo(owner) < OWNER_FOLLOW_MAX_DIST;
            }
            case FOLLOW_LEADER_GROUND -> {
                DuckEntity l = duck.getLeader();
                yield l != null && l.isAlive() && !l.isRemoved() && !l.inFlyingMode();
            }
            case FOLLOW_PARENT ->
                    duck.isBaby() && findParent() != null;
            case WANDER ->
                    !duck.getNavigation().isDone();
            case IDLE ->
                    true;
        };
    }

    // ── Individual canEnter helpers ───────────────────────────────────────────

    private boolean shouldPanic() {
        var src = duck.getLastDamageSource();
        return src != null && src.is(DamageTypeTags.PANIC_CAUSES);
    }

    private boolean canStartMigrate() {
        if (!duck.isLeader() || duck.isTame() || duck.isBaby()) return false;
        if (duck.isUnderWater()) return false; // can't take off from ocean floor; surface is fine
        if (migrateCooldown > 0) return false;
        if (duck.getRandom().nextInt(30) != 0) return false;
        return findMigrateTarget();
    }

    private boolean findMigrateTarget() {
        Vec3 here = duck.position();
        var  rng  = duck.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            double distance = 80.0 + rng.nextDouble() * 60.0;
            double angle    = rng.nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            int tx = (int)(here.x + dx);
            int tz = (int)(here.z + dz);
            int surfY  = duck.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
            int floorY = duck.level().getHeight(Heightmap.Types.OCEAN_FLOOR, tx, tz);
            if (surfY - floorY <= 3) {
                double flightY = surfY + 12.0 + rng.nextDouble() * 8.0;
                migrateTarget = new Vec3(here.x + dx, flightY, here.z + dz);
                return true;
            }
        }
        return false;
    }

    private boolean canStartFollowLeaderAir() {
        DuckEntity existing = duck.getLeader();
        // Already following a flying leader — keep it
        if (existing != null && existing.isAlive() && !existing.isRemoved()
                && existing.inFlyingMode() && !duck.isUnderWater()) return true;
        // Already following a non-flying leader — use GROUND state instead
        if (existing != null) return false;
        // No leader yet: find a flying candidate nearby
        DuckEntity candidate = findLeaderCandidate();
        if (candidate == null || !candidate.inFlyingMode()) return false;
        if (duck.isUnderWater()) return false;
        pendingLeader = candidate;
        return true;
    }

    private boolean canStartFollowOwner() {
        if (!duck.isTame() || !duck.isLeader() || duck.isBaby()) return false;
        if (!duck.isHeadStable()) return false;
        var owner = duck.getOwner();
        return owner != null && owner.isAlive() && duck.distanceTo(owner) < 12.0F;
    }

    private boolean canStartFollowLeaderGround() {
        DuckEntity existing = duck.getLeader();
        // Already following a ground leader — keep it
        if (existing != null && existing.isAlive() && !existing.isRemoved()
                && !existing.inFlyingMode()) return true;
        // Already following a flying leader — use AIR state instead
        if (existing != null) return false;
        // No leader yet: find a ground candidate nearby that has an open slot
        DuckEntity candidate = findLeaderCandidate();
        if (candidate == null || candidate.inFlyingMode()) return false;
        if (duck.distanceToSqr(candidate) <= (double)(START_DIST * START_DIST)) return false;
        pendingLeader = candidate;
        return true;
    }

    private boolean canStartWander() {
        if (duck.isUnderWater()) return false;
        if (duck.getRandom().nextInt(120) != 0) return false;
        Vec3 pos = DefaultRandomPos.getPos(duck, 10, 7);
        if (pos == null) return false;
        wanderTarget = pos;
        return true;
    }

    private boolean ownerAlive() {
        var owner = duck.getOwner();
        return owner != null && owner.isAlive();
    }

    // ── Transition logic ──────────────────────────────────────────────────────

    private static boolean isFollowLeaderState(BrainState s) {
        return s == BrainState.FOLLOW_LEADER_AIR || s == BrainState.FOLLOW_LEADER_GROUND;
    }

    private static boolean isAirborne(BrainState s) {
        return s == BrainState.PANIC_FLY
            || s == BrainState.MIGRATE
            || s == BrainState.FOLLOW_LEADER_AIR;
    }

    /**
     * The <strong>only</strong> method that calls {@link DuckEntity#startFlying()} /
     * {@link DuckEntity#stopFlying()}. All locomotion-mode changes are funnelled
     * through here so that startFlying/stopFlying can never be called from two
     * different code paths in the same tick.
     *
     * <h4>LANDING intercept</h4>
     * <p>Every airborne→ground transition is intercepted: {@code to} is replaced with
     * {@link BrainState#LANDING} and {@code pendingLanding} is set. Flying mode is
     * <strong>kept active</strong> so that {@code flyingNav} can issue a graceful descent
     * command in {@link #enterState}. {@code stopFlying()} is deferred: it is called in
     * {@link #tickCurrentState} when nav completes or the 60-tick timeout fires, and
     * {@link #exitState} provides a safety-net call in case LANDING is preempted early.
     *
     * <h4>keepSlot preservation across LANDING</h4>
     * <p>{@code keepSlot} is computed from the <em>original</em> {@code from/to} pair
     * before the intercept rewrites {@code to}. This ensures that a
     * {@code FOLLOW_LEADER_AIR → FOLLOW_LEADER_GROUND} transition preserves the
     * formation slot through the LANDING bridge without momentarily releasing it.
     *
     * <h4>Hysteresis</h4>
     * <p>After entering any non-panic, non-IDLE state, {@link #minStateTicksLeft} is set
     * to {@value #MIN_STATE_TICKS}. While positive, non-panic higher-priority states
     * cannot preempt — but only when the current state is still running; see
     * {@link #findBestState}.
     *
     * <h4>pendingTakeoff deferral</h4>
     * <p>If a transition to an airborne state fires while {@code isUnderWater()},
     * {@code startFlying()} is deferred until the duck surfaces (checked at the top
     * of {@link #tick()}). This prevents takeoff from the ocean floor.
     */
    private void doTransition(BrainState from, BrainState to) {
        // keepSlot is computed from the ORIGINAL intent (before any LANDING intercept)
        // so that FOLLOW_LEADER_AIR → FOLLOW_LEADER_GROUND preserves the formation slot
        // even though the actual transition goes through LANDING.
        boolean keepSlot = isFollowLeaderState(from) && isFollowLeaderState(to);

        // Intercept: every airborne→ground transition routes through LANDING first.
        // stopFlying() is called on LANDING entry; no new nav until the duck lands.
        if (isAirborne(from) && !isAirborne(to) && to != BrainState.LANDING) {
            pendingLanding = true;
            to = BrainState.LANDING;
        }

        LOG.info("[BRAIN id={}] {} → {} y={} inWater={} flying={}",
                duck.getId(), from, to,
                String.format("%.2f", duck.getY()),
                duck.isInWater(), duck.inFlyingMode());

        exitState(from, keepSlot);

        // Locomotion mode change — single point of truth for startFlying/stopFlying.
        // fromAir includes LANDING-while-still-flying so that LANDING→panic keeps flying mode,
        // and LANDING→ground calls stopFlying() correctly in the safety-net path.
        boolean fromAir = isAirborne(from) || (from == BrainState.LANDING && duck.inFlyingMode());
        boolean toAir   = isAirborne(to);
        if (fromAir && !toAir) {
            pendingTakeoff = false;
            // LANDING keeps flying mode so flyingNav can descend gracefully;
            // stopFlying() is deferred to tickCurrentState(LANDING).
            if (to != BrainState.LANDING) {
                duck.stopFlying();
            }
        } else if (!fromAir && toAir) {
            if (!duck.isUnderWater()) duck.startFlying();
            else pendingTakeoff = true;   // FloatGoal will surface the duck first
        }

        enterState(to, keepSlot);

        // Hysteresis: after entering any non-panic, non-IDLE state, block non-panic
        // higher-priority preemptions for MIN_STATE_TICKS to prevent rapid oscillation.
        if (to.ordinal() > BrainState.PANIC_GROUND.ordinal() && to != BrainState.IDLE) {
            minStateTicksLeft = MIN_STATE_TICKS;
        }

        currentState = to;
    }

    /**
     * Cleans up state-specific resources when leaving {@code s}.
     *
     * <p>Called unconditionally before every state transition. Stops the navigator
     * for all motion-generating states so the pathfinder is not left running with
     * a stale path after the locomotion mode changes.
     *
     * @param keepSlot when true (only for FOLLOW_LEADER_AIR↔FOLLOW_LEADER_GROUND
     *                 transitions), the follow-leader slot on the leader is NOT
     *                 released, preserving formation position across the mode switch.
     *                 {@code keepSlot} is computed from the original {@code from/to}
     *                 pair in {@link #doTransition} before any LANDING intercept.
     */
    private void exitState(BrainState s, boolean keepSlot) {
        switch (s) {
            case PANIC_FLY, PANIC_GROUND, MIGRATE, WANDER ->
                    duck.getNavigation().stop();

            case FOLLOW_OWNER, FOLLOW_PARENT -> {
                duck.getNavigation().stop();
                repathCooldown         = 0;
                lastIssuedFollowTarget = null;
            }

            case FOLLOW_LEADER_AIR, FOLLOW_LEADER_GROUND -> {
                duck.getNavigation().stop();
                repathCooldown = 0;
                lastIssuedFollowTarget = null;
                if (!keepSlot) {
                    DuckEntity current = duck.getLeader();
                    if (current != null) current.releaseFollower(duck);
                    duck.setLeader(null);
                    claimedLeader = null;
                    pendingLeader = null;
                    mySlot        = null;
                }
            }

            case LANDING -> {
                // Safety net: stopFlying() is normally called by tickCurrentState when
                // the descent nav completes. If LANDING is preempted early (e.g. panic)
                // before that happens, ensure flying mode is cleaned up here.
                if (duck.inFlyingMode()) duck.stopFlying();
            }
            case IDLE    -> {} // nothing to clean up
        }

        // Cooldown starts when migration ends, not when it begins.
        if (s == BrainState.MIGRATE) {
            migrateCooldown = QuackConfig.get().genericDucks.migrationCooldownTicks;
            migrateTarget   = null;
        }
    }

    /**
     * Initialises state-specific resources when entering {@code s}.
     *
     * <p>For PANIC_FLY/GROUND, MIGRATE, and WANDER: issues a single {@code moveTo}
     * call that drives the entire state's navigation. The navigator handles movement
     * until {@code isDone()} triggers {@link #canContinue} to return false.
     *
     * <p>For FOLLOW_LEADER_AIR/GROUND: claims a slot from {@code pendingLeader} (set
     * by {@link #canEnter}) unless {@code keepSlot=true}, in which case the slot was
     * already claimed and is merely preserved.
     *
     * <p>For LANDING: consumes {@link #pendingLanding} and arms the {@link #landingTicks}
     * timeout. Issues a {@code flyingNav.moveTo} to {@code groundY + 1.5} so the duck
     * glides down under flying control. {@code stopFlying()} is deferred to
     * {@link #tickCurrentState} when that nav completes or the timeout fires.
     *
     * @param s       the state being entered
     * @param keepSlot true when the follow-leader slot survives from the previous state
     */
    private void enterState(BrainState s, boolean keepSlot) {
        switch (s) {
            case PANIC_FLY -> {
                Vec3 target = findPanicAerialTarget();
                if (target != null)
                    duck.getNavigation().moveTo(target.x, target.y, target.z, PANIC_SPEED);
            }
            case PANIC_GROUND -> {
                Vec3 pos = DefaultRandomPos.getPos(duck, 5, 4);
                if (pos != null)
                    duck.getNavigation().moveTo(pos.x, pos.y, pos.z, PANIC_SPEED);
            }
            case MIGRATE -> {
                if (migrateTarget != null) {
                    // Default FOLLOW_RANGE (~16 blocks) gives a ±24-block PathNavigationRegion —
                    // far too small for 80–140 block migration targets. Expand the required path
                    // length to cover the full distance before calling moveTo.
                    double dist = duck.position().distanceTo(migrateTarget);
                    duck.getNavigation().setRequiredPathLength((float) dist + 16.0f);
                    duck.getNavigation().moveTo(
                            migrateTarget.x, migrateTarget.y, migrateTarget.z, MIGRATE_SPEED);
                    LOG.info("[BRAIN id={}] MIGRATE nav to ({},{},{}) dist={} navDone={}",
                            duck.getId(),
                            String.format("%.1f", migrateTarget.x),
                            String.format("%.1f", migrateTarget.y),
                            String.format("%.1f", migrateTarget.z),
                            String.format("%.1f", dist),
                            duck.getNavigation().isDone());
                }
            }
            case LANDING -> {
                pendingLanding = false;
                landingTicks   = MAX_LANDING_TICKS;
                // Duck is still in flying mode. Issue a descent nav command so
                // flyingNav glides it down to just above the terrain surface.
                // stopFlying() + velocity zeroing are deferred to tickCurrentState.
                if (duck.inFlyingMode()) {
                    int groundY = duck.level().getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            (int) duck.getX(), (int) duck.getZ());
                    duck.getNavigation().moveTo(
                            duck.getX(), groundY + 1.5, duck.getZ(), LANDING_DESCEND_SPEED);
                }
            }
            case FOLLOW_LEADER_AIR, FOLLOW_LEADER_GROUND -> {
                if (!keepSlot && pendingLeader != null) {
                    DuckEntity.FollowerSlot slot = pendingLeader.claimFollower(duck);
                    if (slot != null) {
                        mySlot        = slot;
                        claimedLeader = pendingLeader;
                        duck.setLeader(pendingLeader);
                    } else {
                        // Slot was taken between canEnter and enterState — self-heals
                        // on next tick when canContinue returns false.
                        claimedLeader = null;
                        mySlot        = null;
                    }
                    pendingLeader = null;
                }
                repathCooldown = 0;
            }
            case WANDER -> {
                if (wanderTarget != null) {
                    duck.getNavigation().moveTo(
                            wanderTarget.x, wanderTarget.y, wanderTarget.z, STROLL_SPEED);
                    wanderTarget = null;
                }
            }
            case FOLLOW_OWNER, FOLLOW_PARENT -> {
                repathCooldown         = 0;
                lastIssuedFollowTarget = null;
            }
            case IDLE -> {} // nav driven by tick
        }
    }

    // ── Per-state tick logic ──────────────────────────────────────────────────

    private void tickCurrentState() {
        switch (currentState) {
            case PANIC_FLY, PANIC_GROUND, WANDER, IDLE -> {}   // nav handles movement
            case LANDING -> {
                if (landingTicks > 0) landingTicks--;
                // Finish the landing once flyingNav reaches the target (or timeout).
                // Zero vertical velocity before handing off to gravity so the duck
                // falls the remaining ~1.5 blocks from rest rather than diving.
                if (duck.inFlyingMode() && (duck.getNavigation().isDone() || landingTicks == 0)) {
                    Vec3 mv = duck.getDeltaMovement();
                    duck.setDeltaMovement(new Vec3(mv.x, 0, mv.z));
                    duck.stopFlying();
                }
            }
            case MIGRATE -> {
                if (migrateTarget != null)
                    duck.getLookControl().setLookAt(
                            migrateTarget.x, migrateTarget.y, migrateTarget.z);
            }
            case FOLLOW_LEADER_AIR    -> tickFollowLeader(true);
            case FOLLOW_LEADER_GROUND -> tickFollowLeader(false);
            case FOLLOW_OWNER         -> tickFollowOwner();
            case FOLLOW_PARENT        -> tickFollowParent();
        }
    }

    /**
     * Per-tick logic shared by {@link BrainState#FOLLOW_LEADER_AIR} and
     * {@link BrainState#FOLLOW_LEADER_GROUND}.
     *
     * <h4>Ground water guard</h4>
     * <p>If the leader is anywhere in water, nav is stopped, {@link #repathCooldown}
     * and {@link #lastIssuedFollowTarget} are cleared, and the follower parks until
     * the leader exits the water. Clearing the cooldown ensures any previously-committed
     * underwater path is abandoned immediately rather than surviving 8–10 more ticks.
     *
     * <h4>Repath threshold</h4>
     * <p>After the repath cooldown expires, a new {@code moveTo} is skipped if the
     * new target is within {@value #FOLLOW_REPATH_THRESHOLD_SQ} blocks² of the last
     * issued target AND the navigator is still running. This prevents constant
     * pathfinder calls when the leader is barely moving.
     *
     * @param airborne true → {@link BrainState#FOLLOW_LEADER_AIR} (flying nav,
     *                 V-formation target); false → {@link BrainState#FOLLOW_LEADER_GROUND}
     *                 (ground nav, direct-position target)
     */
    private void tickFollowLeader(boolean airborne) {
        DuckEntity leader = duck.getLeader();
        if (leader == null) return;

        // Ground only: park and wait only when leader is actually submerged (eyes below surface).
        // Using isUnderWater() NOT isInWater() — a surface-floating leader has isInWater=true
        // but isUnderWater=false; using isInWater would freeze followers any time the leader
        // touches water. groundNav.canFloat=true handles navigation to water-surface positions.
        // Clears repathCooldown so any previously-committed underwater path is abandoned
        // immediately rather than surviving another 8–10 ticks.
        if (!airborne && leader.isUnderWater()) {
            duck.getNavigation().stop();
            repathCooldown         = 0;
            lastIssuedFollowTarget = null;
            return;
        }

        duck.getLookControl().setLookAt(leader, 30.0F, 30.0F);

        Vec3   target     = getFollowerTargetPos(leader, airborne);
        double distSq     = duck.position().distanceToSqr(target);
        double stopDistSq = STOP_DIST * STOP_DIST;
        double far2       = START_DIST * START_DIST * (FAR_MULTIPLIER * FAR_MULTIPLIER);

        if (distSq <= stopDistSq) {
            duck.getNavigation().stop();
            return;
        }

        if (repathCooldown > 0) { repathCooldown--; return; }

        // Skip moveTo if the target hasn't moved enough AND nav is still running.
        // Prevents constant path thrashing when the leader is barely moving.
        if (lastIssuedFollowTarget != null
                && lastIssuedFollowTarget.distanceToSqr(target) < FOLLOW_REPATH_THRESHOLD_SQ
                && !duck.getNavigation().isDone()) {
            return;
        }

        double speed;
        int    cooldown;
        if (distSq > far2) {
            speed    = airborne ? FOLLOW_AIR_FAR_SPEED  : FOLLOW_GND_FAR_SPEED;
            cooldown = 8;
        } else {
            speed    = airborne ? FOLLOW_AIR_NEAR_SPEED : FOLLOW_GND_NEAR_SPEED;
            cooldown = 10;
        }

        duck.getNavigation().moveTo(target.x, target.y, target.z, speed);
        lastIssuedFollowTarget = target;
        repathCooldown = cooldown;
    }

    /** Returns the navigation target for a following duck — always the leader's position (single-file). */
    private Vec3 getFollowerTargetPos(DuckEntity leader, boolean airborne) {
        return leader.position();
    }

    /**
     * Per-tick logic for {@link BrainState#FOLLOW_OWNER}.
     *
     * <p>Applies the same repath cooldown and target-change threshold as
     * {@link #tickFollowLeader} to avoid issuing a new path to the pathfinder
     * every tick. No water guard — the owner may intentionally be swimming and
     * the tamed duck should follow.
     */
    private void tickFollowOwner() {
        var owner = duck.getOwner();
        if (owner == null) return;
        duck.getLookControl().setLookAt(owner, 10.0F, duck.getMaxHeadXRot());
        if (duck.distanceTo(owner) <= OWNER_STOP_DIST) {
            duck.getNavigation().stop();
            return;
        }
        if (repathCooldown > 0) { repathCooldown--; return; }
        Vec3 target = owner.position();
        if (lastIssuedFollowTarget != null
                && lastIssuedFollowTarget.distanceToSqr(target) < FOLLOW_REPATH_THRESHOLD_SQ
                && !duck.getNavigation().isDone()) {
            return;
        }
        duck.getNavigation().moveTo(target.x, target.y, target.z, OWNER_SPEED);
        lastIssuedFollowTarget = target;
        repathCooldown = 8;
    }

    /**
     * Per-tick logic for {@link BrainState#FOLLOW_PARENT}.
     *
     * <p>Applies the same repath cooldown and target-change threshold as
     * {@link #tickFollowLeader}. When the repath cooldown is active the
     * {@code findParent()} AABB search is skipped entirely — the navigator
     * is already running to the previously issued target and the baby duck
     * just looks at the last-known parent direction via the look control.
     */
    private void tickFollowParent() {
        if (repathCooldown > 0) { repathCooldown--; return; }
        DuckEntity parent = findParent();
        if (parent == null) return;
        duck.getLookControl().setLookAt(parent, 10.0F, duck.getMaxHeadXRot());
        if (duck.distanceTo(parent) <= 3.0F) {
            duck.getNavigation().stop();
            return;
        }
        Vec3 target = parent.position();
        if (lastIssuedFollowTarget != null
                && lastIssuedFollowTarget.distanceToSqr(target) < FOLLOW_REPATH_THRESHOLD_SQ
                && !duck.getNavigation().isDone()) {
            return;
        }
        duck.getNavigation().moveTo(target.x, target.y, target.z, FOLLOW_PARENT_SPEED);
        lastIssuedFollowTarget = target;
        repathCooldown = 8;
    }

    // ── Entity search helpers ─────────────────────────────────────────────────

    /**
     * Finds the nearest adult duck within {@link #SEARCH_RADIUS} that:
     * <ul>
     *   <li>is alive and not removed,</li>
     *   <li>has at least one open follower slot ({@code !hasFollower()}),</li>
     *   <li>is not already following this duck (avoids trivial cycles), and</li>
     *   <li>would not create a longer cycle ({@link #wouldCycle}).</li>
     * </ul>
     *
     * @return the nearest qualifying duck, or {@code null} if none found
     */
    private DuckEntity findLeaderCandidate() {
        AABB box = duck.getBoundingBox().inflate(SEARCH_RADIUS);
        List<DuckEntity> candidates = duck.level().getEntitiesOfClass(DuckEntity.class, box,
                c -> c != duck
                        && c.isAlive()
                        && !c.isRemoved()
                        && !c.hasFollower()
                        && c.getLeader() != duck
                        && !wouldCycle(duck, c));
        if (candidates.isEmpty()) return null;
        DuckEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (DuckEntity c : candidates) {
            double d2 = duck.distanceToSqr(c);
            if (d2 < best) { best = d2; nearest = c; }
        }
        return nearest;
    }

    /**
     * Returns true if choosing {@code candidate} as leader would create a cycle
     * in the follow chain (i.e., {@code follower} already appears somewhere in
     * {@code candidate}'s own leader chain). Guards against infinite loops with a
     * 64-step walk limit.
     */
    private static boolean wouldCycle(DuckEntity follower, DuckEntity candidate) {
        DuckEntity cur = candidate;
        int guard = 64;
        while (cur != null && guard-- > 0) {
            if (cur == follower) return true;
            cur = cur.getLeader();
        }
        return false;
    }

    /** Finds the nearest adult (non-baby) {@link DuckEntity} within 8 blocks of this duck. */
    private DuckEntity findParent() {
        AABB box = duck.getBoundingBox().inflate(8.0);
        return duck.level().getEntitiesOfClass(DuckEntity.class, box,
                c -> !c.isBaby() && c != duck && c.isAlive()
        ).stream().findFirst().orElse(null);
    }

    /**
     * Picks a random aerial escape point for {@link BrainState#PANIC_FLY}:
     * 8–16 blocks horizontally at a random angle, terrain height + 12–18 blocks.
     *
     * @return the target Vec3, or {@code null} if not in panic mode (callers handle null)
     */
    private Vec3 findPanicAerialTarget() {
        var    rng   = duck.getRandom();
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist  = 8.0 + rng.nextDouble() * 8.0;
        double dx = Math.cos(angle) * dist;
        double dz = Math.sin(angle) * dist;
        int terrainY = duck.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int)(duck.getX() + dx), (int)(duck.getZ() + dz));
        return new Vec3(duck.getX() + dx,
                terrainY + 12.0 + rng.nextDouble() * 6.0,
                duck.getZ() + dz);
    }
}
