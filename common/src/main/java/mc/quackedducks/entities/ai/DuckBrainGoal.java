package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.List;

import com.mojang.logging.LogUtils;
import mc.quackedducks.QuackDebug;
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
 * Everything a duck does under its own steam, as one state machine.
 *
 * Panicking, migrating, following and wandering used to be four goals that fought each
 * other over startFlying and stopFlying. They are one goal now, and this class is the
 * only caller of either.
 *
 * Each tick it picks a state: a higher-priority one takes over if it can, otherwise the
 * current one keeps going while it still can, otherwise the first lower one that can
 * start does. The states are listed in the enum below, best first.
 *
 * Before changing anything in here, read the AI State Machine Change Protocol in
 * CLAUDE.md. Update the diagram there first.
 */
public class DuckBrainGoal extends Goal {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * The states, best first. The order is the priority: anything above the current
     * state can cut in the moment its guard says yes.
     */
    public enum BrainState {
        /** Hurt and old enough to fly: up and away to somewhere else. */
        PANIC_FLY,
        /** Hurt and too young to fly: run for it. */
        PANIC_GROUND,
        /** An untamed leader taking its flock 80 to 140 blocks to new ground. */
        MIGRATE,
        /** Following an airborne leader. */
        FOLLOW_LEADER_AIR,
        /**
         * Coming down. Every airborne state ends here, and only doTransition can put a
         * duck in it.
         *
         * The duck is still in flying mode: the flying navigator is told to descend to
         * just above the ground, and stopFlying() waits until that finishes. Calling
         * stopFlying() up front instead hands the duck back to gravity while it still
         * carries downward speed, which is what used to sink ducks into the water.
         */
        LANDING,
        /** A tamed leader walking back to its owner. */
        FOLLOW_OWNER,
        /** Following a leader that is on the ground. */
        FOLLOW_LEADER_GROUND,
        /** A duckling keeping up with the nearest adult. */
        FOLLOW_PARENT,
        /** A stroll, on a roughly one in 120 chance per tick. */
        WANDER,
        /** Standing there. Always available, so the machine can never get stuck. */
        IDLE
    }

    // Speeds
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

    // Distances a follower cares about
    private static final float  SEARCH_RADIUS  = 18.0F;
    private static final float  START_DIST     = 4.2F;
    private static final float  STOP_DIST      = 2.0F;
    private static final double FAR_MULTIPLIER = 2.25;  // far² = (startDist * FAR_MULT)²
    private static final float  OWNER_STOP_DIST    = 4.0F;
    /** Past this the duck gives up on its owner until they come back within range. */
    private static final float  OWNER_FOLLOW_MAX_DIST = 20.0F;

    // Nothing but panic may interrupt a state this soon after it started. Without it,
    // ducks flip between two states on consecutive ticks and lose their formation place.
    private static final int MIN_STATE_TICKS = 15;
    /** If a landing has not finished in three seconds, stop flying anyway. */
    private static final int MAX_LANDING_TICKS = 60;
    /** A follower ignores its leader moving less than a block. Squared, to skip a sqrt. */
    private static final double FOLLOW_REPATH_THRESHOLD_SQ = 1.0;

    private final DuckEntity duck;
    private BrainState currentState = BrainState.IDLE;

    private int  migrateCooldown = 0;
    private Vec3 migrateTarget   = null;

    // pendingLeader is a duck we found while checking whether we could start following.
    // claimedLeader is one we have actually taken a place behind.
    private DuckEntity              pendingLeader  = null;
    private DuckEntity              claimedLeader  = null;
    private DuckEntity.FollowerSlot mySlot         = null;
    private int                     repathCooldown = 0;
    /** Where we last sent the duck, so we can tell whether a new path is worth it. */
    private Vec3                    lastIssuedFollowTarget = null;

    private Vec3 wanderTarget = null;

    /** Set when we wanted to take off but the duck's head was still under water. */
    private boolean pendingTakeoff = false;

    private int     minStateTicksLeft = 0;
    /** True for the single tick between deciding to land and entering LANDING. */
    private boolean pendingLanding    = false;
    private int     landingTicks      = 0;

    public DuckBrainGoal(DuckEntity duck) {
        this.duck = duck;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // Always on. The state machine inside decides whether the duck actually does anything,
    // and the MOVE flag keeps the goals above this one able to take over.
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
        // A takeoff we owed from an earlier tick. FloatGoal will have brought the duck
        // up by now, and taking off from the sea floor does not work.
        if (pendingTakeoff && !duck.isInWater()) {
            duck.startFlying();
            pendingTakeoff = false;
        }

        // Underwater and not flying: leave it to FloatGoal to surface us. Note this is
        // isUnderWater, eyes below the surface, not isInWater. A duck bobbing on a pond
        // is in water all day and should still be able to walk out of it.
        if (duck.isUnderWater() && !duck.inFlyingMode()) {
            duck.getNavigation().stop();
            return;
        }

        if (migrateCooldown > 0) migrateCooldown--;
        if (minStateTicksLeft > 0) minStateTicksLeft--;

        BrainState desired = findBestState();
        if (desired != currentState) doTransition(currentState, desired);

        tickCurrentState();
    }

    /** What the duck is doing, for the animation hint. */
    public BrainState getBrainState() { return currentState; }

    /**
     * Picks the state for this tick: something better if it can start, otherwise what we
     * are already doing, otherwise the first lesser thing that can start.
     *
     * The settling window only applies while the current state is still going. Once it
     * has finished there is nothing to protect, so everything competes for the empty
     * slot at once. That matters for a follower whose leader just took off: it can move
     * straight from following on the ground to following in the air without dropping to
     * IDLE in between and losing its place in the formation.
     */
    private BrainState findBestState() {
        int  cur         = currentState.ordinal();
        boolean curDone  = !canContinue(currentState);

        for (BrainState s : BrainState.values()) {
            int ord = s.ordinal();
            if (ord < cur) {
                // Panic always gets through. Everything else waits out the settling window.
                boolean isPanic = ord <= BrainState.PANIC_GROUND.ordinal();
                boolean hysteresisBlocks = !isPanic && !curDone && minStateTicksLeft > 0;
                if (!hysteresisBlocks && canEnter(s)) return s;
            } else if (s == currentState) {
                if (!curDone) return s;
            } else {
                if (curDone && canEnter(s)) return s;
            }
        }
        return BrainState.IDLE; // unreachable: IDLE can always start
    }

    /**
     * Can the duck start this state right now?
     *
     * These are asked speculatively during the scan above, so they must not change
     * anything. The two exceptions are deliberate: the follow helpers stash the leader
     * they found in pendingLeader, and canStartWander stashes the position it rolled,
     * so entering the state does not have to search for either a second time.
     */
    private boolean canEnter(BrainState s) {
        return switch (s) {
            case PANIC_FLY            -> !duck.isBaby() && shouldPanic() && !duck.isInWater();
            case PANIC_GROUND         -> duck.isBaby() && shouldPanic();
            case MIGRATE              -> canStartMigrate();
            case FOLLOW_LEADER_AIR    -> canStartFollowLeaderAir();
            // Never picked by the scan. doTransition sets the flag and enters LANDING itself.
            case LANDING              -> pendingLanding;
            case FOLLOW_OWNER         -> canStartFollowOwner();
            case FOLLOW_LEADER_GROUND -> canStartFollowLeaderGround();
            case FOLLOW_PARENT        -> duck.isBaby() && findParent() != null;
            case WANDER               -> canStartWander();
            case IDLE                 -> true;
        };
    }

    /** Can the duck keep doing this? A no here frees the slot for anything else. */
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
            // Until the feet are down, or we run out of patience.
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

    private boolean shouldPanic() {
        var src = duck.getLastDamageSource();
        return src != null && src.is(DamageTypeTags.PANIC_CAUSES);
    }

    private boolean canStartMigrate() {
        if (!duck.isLeader() || duck.isTame() || duck.isBaby()) return false;
        if (duck.isUnderWater()) return false; // no taking off from the sea floor
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
        // Already following a flying leader - keep it
        if (existing != null && existing.isAlive() && !existing.isRemoved()
                && existing.inFlyingMode() && !duck.isUnderWater()) return true;
        // Already following a non-flying leader - use GROUND state instead
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
        // Already following a ground leader - keep it
        if (existing != null && existing.isAlive() && !existing.isRemoved()
                && !existing.inFlyingMode()) return true;
        // Already following a flying leader - use AIR state instead
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

    private static boolean isFollowLeaderState(BrainState s) {
        return s == BrainState.FOLLOW_LEADER_AIR || s == BrainState.FOLLOW_LEADER_GROUND;
    }

    private static boolean isAirborne(BrainState s) {
        return s == BrainState.PANIC_FLY
            || s == BrainState.MIGRATE
            || s == BrainState.FOLLOW_LEADER_AIR;
    }

    /**
     * Changes state, and is the only thing in the mod that starts or stops flying.
     *
     * Coming down from the air is never direct: it is rewritten to go through LANDING,
     * which keeps flying mode on and glides the duck down before handing it back to
     * gravity. Going up is direct, unless the duck's head is under water, in which case
     * it waits for the next tick that finds it at the surface.
     */
    private void doTransition(BrainState from, BrainState to) {
        // Worked out before the rewrite below, so a follower whose leader lands keeps its
        // place in the formation instead of releasing it on the way through LANDING.
        boolean keepSlot = isFollowLeaderState(from) && isFollowLeaderState(to);

        if (isAirborne(from) && !isAirborne(to) && to != BrainState.LANDING) {
            pendingLanding = true;
            to = BrainState.LANDING;
        }

        if (QuackDebug.ENABLED) {
            LOG.info("[BRAIN id={}] {} → {} y={} inWater={} flying={}",
                    duck.getId(), from, to,
                    String.format("%.2f", duck.getY()),
                    duck.isInWater(), duck.inFlyingMode());
        }

        exitState(from, keepSlot);

        // A LANDING duck that has not finished coming down still counts as airborne, so
        // panicking mid-descent keeps its wings and finishing the descent stops them.
        boolean fromAir = isAirborne(from) || (from == BrainState.LANDING && duck.inFlyingMode());
        boolean toAir   = isAirborne(to);
        if (fromAir && !toAir) {
            pendingTakeoff = false;
            // LANDING needs its wings a little longer.
            if (to != BrainState.LANDING) {
                duck.stopFlying();
            }
        } else if (!fromAir && toAir) {
            if (!duck.isUnderWater()) duck.startFlying();
            else pendingTakeoff = true;   // wait for FloatGoal to bring us up
        }

        enterState(to, keepSlot);

        // Give the new state a moment to settle before anything short of panic can take it.
        if (to.ordinal() > BrainState.PANIC_GROUND.ordinal() && to != BrainState.IDLE) {
            minStateTicksLeft = MIN_STATE_TICKS;
        }

        currentState = to;
    }

    /**
     * Tidies up on the way out of a state.
     *
     * Anything that was moving stops moving, so the pathfinder is not left chasing a
     * path that belonged to the old state. keepSlot means the duck is only switching
     * between following in the air and following on the ground, and should hold onto
     * its place behind the leader.
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
                // Normally the descent finishes on its own and stops flying. This catches
                // the case where something interrupted it partway down.
                if (duck.inFlyingMode()) duck.stopFlying();
            }
            case IDLE    -> {}
        }

        // The cooldown runs from the end of a migration, not the start of one.
        if (s == BrainState.MIGRATE) {
            migrateCooldown = QuackConfig.get().genericDucks.migrationCooldownTicks;
            migrateTarget   = null;
        }
    }

    /**
     * Sets a state up.
     *
     * Panicking, migrating and wandering each get one moveTo here and then run on the
     * navigator alone until it says it is done. The follow states take a place behind
     * their leader instead, and steer every tick.
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
                    // The default path region is about 24 blocks across, nowhere near a
                    // migration. Ask for the whole distance before pathing, or the duck
                    // gets a path to nowhere and gives up.
                    double dist = duck.position().distanceTo(migrateTarget);
                    duck.getNavigation().setRequiredPathLength((float) dist + 16.0f);
                    duck.getNavigation().moveTo(
                            migrateTarget.x, migrateTarget.y, migrateTarget.z, MIGRATE_SPEED);
                    if (QuackDebug.ENABLED) {
                        LOG.info("[BRAIN id={}] MIGRATE nav to ({},{},{}) dist={} navDone={}",
                                duck.getId(),
                                String.format("%.1f", migrateTarget.x),
                                String.format("%.1f", migrateTarget.y),
                                String.format("%.1f", migrateTarget.z),
                                String.format("%.1f", dist),
                                duck.getNavigation().isDone());
                    }
                }
            }
            case LANDING -> {
                pendingLanding = false;
                landingTicks   = MAX_LANDING_TICKS;
                // Still flying, and stays that way: glide down to just above the ground
                // and let the tick below finish the job.
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
                        // Someone else took the place in between. Next tick canContinue
                        // says no and the duck goes and finds something else to do.
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
            case IDLE -> {}
        }
    }

    private void tickCurrentState() {
        switch (currentState) {
            case PANIC_FLY, PANIC_GROUND, WANDER, IDLE -> {}   // the navigator has it
            case LANDING -> {
                if (landingTicks > 0) landingTicks--;
                // Down far enough. Kill the vertical speed before handing the duck back
                // to gravity, so it drops the last block or so from a standstill instead
                // of driving itself into the ground.
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
     * Following a leader, in the air or on the ground. Same code either way; the flag
     * picks the speeds and turns on the water guard.
     *
     * Two things keep this from hammering the pathfinder: a short cooldown after each
     * moveTo, and a check that the leader has actually moved a block since the last one.
     * Without them a follower repaths every tick and the whole flock stutters.
     *
     * On the ground, a leader with its head under water parks the follower until it
     * surfaces. A leader merely floating is fine to follow, which is why this checks
     * isUnderWater and not isInWater.
     */
    private void tickFollowLeader(boolean airborne) {
        DuckEntity leader = duck.getLeader();
        if (leader == null) return;

        // Clearing the cooldown here drops the path we already committed to underwater,
        // instead of letting it run for another ten ticks.
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

        // The leader has barely shifted and we are already on our way there.
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

    /**
     * Where a follower heads for: the leader itself, so the flock walks single file.
     * The V formation the slots were built for is not wired up yet.
     */
    private Vec3 getFollowerTargetPos(DuckEntity leader, boolean airborne) {
        return leader.position();
    }

    /**
     * Same damping as following a leader. No water guard here: if the owner swims out,
     * their duck goes with them.
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
     * Same again for ducklings. The cooldown check comes first on purpose: while it is
     * running there is no need to search the area for a parent at all.
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

    /**
     * The nearest duck nearby with room behind it that we would not end up in a loop with.
     *
     * Ducklings are not filtered out here. claimFollower turns them down anyway, and the
     * follow state gives up on the next tick.
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
     * True if following this candidate would put the duck in a ring, following itself
     * back around the chain. Walks at most 64 links, in case a ring already exists.
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

    /** Any adult duck within 8 blocks. First one found, not the closest; a duckling is not fussy. */
    private DuckEntity findParent() {
        AABB box = duck.getBoundingBox().inflate(8.0);
        return duck.level().getEntitiesOfClass(DuckEntity.class, box,
                c -> !c.isBaby() && c != duck && c.isAlive()
        ).stream().findFirst().orElse(null);
    }

    /** Somewhere to flee to: 8 to 16 blocks off in any direction, and well above the ground. */
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
