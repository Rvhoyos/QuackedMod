package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.List;

import com.mojang.logging.LogUtils;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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

    // ── Follow-leader geometry ────────────────────────────────────────────────
    private static final float  SEARCH_RADIUS  = 18.0F;
    private static final float  START_DIST     = 4.2F;
    private static final float  STOP_DIST      = 2.0F;
    private static final double FAR_MULTIPLIER = 2.25;  // far² = (startDist * FAR_MULT)²
    private static final float  OWNER_STOP_DIST = 4.0F;

    // ── Core ─────────────────────────────────────────────────────────────────
    private final DuckEntity duck;
    private BrainState currentState = BrainState.IDLE;
    /** Tracks whether water guard fired last tick to avoid log spam. */
    private boolean wasWaterGuardActive = false;

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

    // ── WANDER ────────────────────────────────────────────────────────────────
    private Vec3 wanderTarget = null;

    // ── Locomotion ────────────────────────────────────────────────────────────
    /** True when an airborne transition was requested while duck was underwater. */
    private boolean pendingTakeoff = false;

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
            if (!wasWaterGuardActive) {
                LOG.info("[BRAIN id={}] water guard ON  state={} y={} inWater={} isUnderWater={}",
                        duck.getId(), currentState,
                        String.format("%.2f", duck.getY()),
                        duck.isInWater(), duck.isUnderWater());
                wasWaterGuardActive = true;
            }
            return;
        }
        if (wasWaterGuardActive) {
            LOG.info("[BRAIN id={}] water guard OFF state={} y={} inWater={}",
                    duck.getId(), currentState,
                    String.format("%.2f", duck.getY()),
                    duck.isInWater());
            wasWaterGuardActive = false;
        }

        // 3. Advance migrate cooldown every tick.
        if (migrateCooldown > 0) migrateCooldown--;

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
     *       — any match immediately preempts.</li>
     *   <li>Current state: kept if {@link #canContinue} is true.</li>
     *   <li>States with higher ordinal: only considered once the current state
     *       is done; first {@link #canEnter} wins.</li>
     * </ul>
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
                if (canEnter(s)) return s;
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
     * Called speculatively during priority scanning — must be side-effect-free
     * except for {@code pendingLeader} / {@code wanderTarget} assignments, which
     * are written here and consumed in {@link #enterState}.
     */
    private boolean canEnter(BrainState s) {
        return switch (s) {
            case PANIC_FLY            -> !duck.isBaby() && shouldPanic() && !duck.isInWater();
            case PANIC_GROUND         -> duck.isBaby() && shouldPanic();
            case MIGRATE              -> canStartMigrate();
            case FOLLOW_LEADER_AIR    -> canStartFollowLeaderAir();
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
     * When this returns false for the current state, {@link #findBestState()} falls
     * through to lower-priority states.
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
            case FOLLOW_OWNER ->
                    duck.isTame() && duck.isLeader() && ownerAlive();
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
     * <p>Also handles the {@code pendingTakeoff} deferral: if a transition to an
     * airborne state is requested while the duck is underwater, the actual
     * {@code startFlying()} call is deferred until the duck surfaces (checked at
     * the top of {@link #tick()}).
     */
    private void doTransition(BrainState from, BrainState to) {
        LOG.info("[BRAIN id={}] {} → {} y={} inWater={} flying={}",
                duck.getId(), from, to,
                String.format("%.2f", duck.getY()),
                duck.isInWater(), duck.inFlyingMode());
        // GROUND↔AIR transitions between the two follow-leader states preserve
        // the claimed slot so we don't momentarily release and risk losing it.
        boolean keepSlot = isFollowLeaderState(from) && isFollowLeaderState(to);

        exitState(from, keepSlot);

        // Locomotion mode change — single point of truth for startFlying/stopFlying.
        boolean fromAir = isAirborne(from);
        boolean toAir   = isAirborne(to);
        if (fromAir && !toAir) {
            duck.stopFlying();
            pendingTakeoff = false;
        } else if (!fromAir && toAir) {
            if (!duck.isUnderWater()) duck.startFlying();
            else pendingTakeoff = true;   // FloatGoal will surface the duck first
        }

        enterState(to, keepSlot);
        currentState = to;
    }

    /**
     * Cleans up state-specific resources when leaving {@code s}.
     *
     * @param keepSlot when true, the follow-leader slot is preserved across a
     *                 ground↔air transition so the duck doesn't momentarily
     *                 release and risk losing its position in the formation
     */
    private void exitState(BrainState s, boolean keepSlot) {
        switch (s) {
            case PANIC_FLY, PANIC_GROUND, MIGRATE,
                 FOLLOW_OWNER, FOLLOW_PARENT, WANDER ->
                    duck.getNavigation().stop();

            case FOLLOW_LEADER_AIR, FOLLOW_LEADER_GROUND -> {
                duck.getNavigation().stop();
                repathCooldown = 0;
                if (!keepSlot) {
                    DuckEntity current = duck.getLeader();
                    if (current != null) current.releaseFollower(duck);
                    duck.setLeader(null);
                    claimedLeader = null;
                    pendingLeader = null;
                    mySlot        = null;
                }
            }

            case IDLE -> {} // nothing to clean up
        }

        // Cooldown starts when migration ends, not when it begins.
        if (s == BrainState.MIGRATE) {
            migrateCooldown = QuackConfig.get().genericDucks.migrationCooldownTicks;
            migrateTarget   = null;
        }
    }

    /**
     * Initialises state-specific resources when entering {@code s}.
     * For follow-leader states, claims a slot from {@code pendingLeader} unless
     * {@code keepSlot} is true (ground↔air transition — slot already held).
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
                if (migrateTarget != null)
                    duck.getNavigation().moveTo(
                            migrateTarget.x, migrateTarget.y, migrateTarget.z, MIGRATE_SPEED);
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
                    LOG.info("[BRAIN id={}] WANDER start duck=({},{},{}) target=({},{},{}) inWater={}",
                            duck.getId(),
                            String.format("%.1f", duck.getX()), String.format("%.1f", duck.getY()), String.format("%.1f", duck.getZ()),
                            String.format("%.1f", wanderTarget.x), String.format("%.1f", wanderTarget.y), String.format("%.1f", wanderTarget.z),
                            duck.isInWater());
                    wanderTarget = null;
                }
            }
            case FOLLOW_OWNER, FOLLOW_PARENT, IDLE -> {} // nav driven by tick
        }
    }

    // ── Per-state tick logic ──────────────────────────────────────────────────

    private void tickCurrentState() {
        switch (currentState) {
            case PANIC_FLY, PANIC_GROUND, WANDER, IDLE -> {}   // nav handles movement
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
     * <p>Ground mode water guard: if the leader is anywhere in water,
     * nav is stopped and the follower waits on land. The follower resumes
     * automatically once the leader exits the water.
     *
     * <p>Repath cooldown: after issuing a {@code moveTo}, the duck skips
     * re-pathing for several ticks to avoid thrashing the pathfinder.
     *
     * @param airborne true → {@link BrainState#FOLLOW_LEADER_AIR} (flying nav,
     *                 V-formation target); false → {@link BrainState#FOLLOW_LEADER_GROUND}
     *                 (ground nav, direct-position target)
     */
    private void tickFollowLeader(boolean airborne) {
        DuckEntity leader = duck.getLeader();
        if (leader == null) return;

        // Ground only: if leader is anywhere in water, park and wait.
        // Never navigate to a position inside water — that causes the follower to walk in and drown.
        // Follower resumes automatically once leader exits water.
        if (!airborne && leader.isInWater()) {
            duck.getNavigation().stop();
            return;
        }

        duck.getLookControl().setLookAt(leader, 30.0F, 30.0F);

        Vec3   target     = getFollowerTargetPos(leader, airborne);
        double distSq     = duck.position().distanceToSqr(target);
        double stopDistSq = STOP_DIST * STOP_DIST;
        double far2       = START_DIST * START_DIST * (FAR_MULTIPLIER * FAR_MULTIPLIER);

        if (repathCooldown > 0) { repathCooldown--; return; }

        if (distSq > far2) {
            duck.getNavigation().moveTo(target.x, target.y, target.z,
                    airborne ? FOLLOW_AIR_FAR_SPEED : FOLLOW_GND_FAR_SPEED);
            LOG.info("[BRAIN id={}] follow {} repath FAR  duck=({},{},{}) target=({},{},{}) inWater={}",
                    duck.getId(), airborne ? "AIR" : "GND",
                    String.format("%.1f", duck.getX()), String.format("%.1f", duck.getY()), String.format("%.1f", duck.getZ()),
                    String.format("%.1f", target.x), String.format("%.1f", target.y), String.format("%.1f", target.z),
                    duck.isInWater());
            repathCooldown = 8;
        } else if (distSq > stopDistSq) {
            duck.getNavigation().moveTo(target.x, target.y, target.z,
                    airborne ? FOLLOW_AIR_NEAR_SPEED : FOLLOW_GND_NEAR_SPEED);
            LOG.info("[BRAIN id={}] follow {} repath NEAR duck=({},{},{}) target=({},{},{}) inWater={}",
                    duck.getId(), airborne ? "AIR" : "GND",
                    String.format("%.1f", duck.getX()), String.format("%.1f", duck.getY()), String.format("%.1f", duck.getZ()),
                    String.format("%.1f", target.x), String.format("%.1f", target.y), String.format("%.1f", target.z),
                    duck.isInWater());
            repathCooldown = 10;
        } else {
            duck.getNavigation().stop();
        }
    }

    /**
     * Computes the navigation target position for a following duck.
     *
     * <ul>
     *   <li>Airborne + directly behind a chain-head leader: heading-relative
     *       V-formation offset (4 back, ±3 lateral, 1.5 below).</li>
     *   <li>All other cases (ground follow, or mid-chain aerial): leader's
     *       current position (single-file).</li>
     * </ul>
     */
    private Vec3 getFollowerTargetPos(DuckEntity leader, boolean airborne) {
        if (airborne && leader.isLeader() && mySlot != null) {
            float yawRad = leader.getYRot() * (float)(Math.PI / 180.0);
            Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
            Vec3 right   = new Vec3(forward.z, 0, -forward.x);
            double side  = (mySlot == DuckEntity.FollowerSlot.LEFT) ? -1.0 : 1.0;
            return leader.position()
                    .subtract(forward.scale(4.0))
                    .add(right.scale(side * 3.0))
                    .add(0, -1.5, 0);
        }
        return leader.position();
    }

    private void tickFollowOwner() {
        var owner = duck.getOwner();
        if (owner == null) return;
        duck.getLookControl().setLookAt(owner, 10.0F, duck.getMaxHeadXRot());
        if (duck.distanceTo(owner) > OWNER_STOP_DIST)
            duck.getNavigation().moveTo(owner.getX(), owner.getY(), owner.getZ(), OWNER_SPEED);
        else
            duck.getNavigation().stop();
    }

    private void tickFollowParent() {
        DuckEntity parent = findParent();
        if (parent == null) return;
        duck.getLookControl().setLookAt(parent, 10.0F, duck.getMaxHeadXRot());
        if (duck.distanceTo(parent) > 3.0F)
            duck.getNavigation().moveTo(parent.getX(), parent.getY(), parent.getZ(),
                    FOLLOW_PARENT_SPEED);
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
