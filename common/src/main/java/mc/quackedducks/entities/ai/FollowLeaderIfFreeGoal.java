package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.List;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
/**
 * Makes a duck follow the nearest eligible duck to form a single-file chain.
 * 
 * Rules:
 * 
 *   - Only starts if this duck currently has no leader.
 *   - Picks the closest duck that has no follower (i.e., attaches to a chain's tail).
 *   - Prevents cycles (won't follow an upstream duck in its own chain).
 *   - Uses a faster speed when far, slower “waddle” near the leader, and stops when close.
 *   - Re-paths on a short cooldown to avoid constant path recomputation.
 * 
 * Intended use: register after breeding goals and before idle wander/swim.
 */
public class FollowLeaderIfFreeGoal extends Goal {

   //private static final double FAR_MULTIPLIER = 2.25; // “really far” threshold = (startDist * FAR_MULTIPLIER)^2
    private final DuckEntity duck;
    private DuckEntity leader;          // set when goal starts; cleared on stop
    private final double speed;         // base speed (used when far)
    private final float searchRadius;   // how far to look for a leader candidate
    private final float startDist;      // must be farther than this to start following
    private final float stopDist;       // stop moving when within this distance
    private final double farSpeed;      // speed when very far
    private final double nearSpeed;     // slower speed when already near
    private int repathCooldown = 0;  
    /**
     * @param duck         the follower
     * @param speed        base move speed (used for farSpeed; nearSpeed is 50%)
     * @param searchRadius how far to search for a leader candidate
     * @param startDist    must be farther than this to begin following
     * @param stopDist     stop moving when within this distance
     */
    public FollowLeaderIfFreeGoal(DuckEntity duck, double speed, float searchRadius, float startDist, float stopDist){
        this.duck = duck;
        this.speed = speed;
        this.searchRadius = searchRadius;
        this.startDist = startDist;
        this.stopDist = stopDist;
        this.farSpeed = speed;
        this.nearSpeed = speed*0.5; // 50% slower when closer to its leader
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

    }
/**
     * Returns true if choosing {@code candidate} as leader would create a follow cycle.
     */    private static boolean wouldCycle(DuckEntity follower, DuckEntity candidate) {
    DuckEntity cur = candidate;
    int guard = 64; // safety against bad states
    while (cur != null && guard-- > 0) {
        if (cur == follower) return true; // follower is already upstream of candidate
        cur = cur.getLeader();
    }
    return false;
}

/**
    * Start if:
    * - we have no current leader; and
    * - there is a nearby duck with no follower; and
    *
*/
@Override
public boolean canUse() {
    // only start if NOT already following someone
        if (duck.getLeader() != null) return false;

        // find nearest duck that doesn't already have a follower (attach to the tail)
        AABB box = duck.getBoundingBox().inflate(searchRadius);
        List<DuckEntity> candidates = duck.level().getEntitiesOfClass(DuckEntity.class, box, c ->
                c != duck && c.isAlive() 
                    && !c.isRemoved() 
                    && !c.hasFollower() 
                    && c.getLeader() != duck 
                    && !wouldCycle(duck, c)
        );
        if (candidates.isEmpty()) return false;
        // Candidate selection
        DuckEntity nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (DuckEntity c : candidates) {
            double distance2Square = duck.distanceToSqr(c);
            if (distance2Square < bestDistance) { bestDistance = distance2Square; nearest = c; }
        }
        // don’t start if we’re already close enough
        if (nearest == null || bestDistance <= (double)(startDist * startDist)) return false;

        this.leader = nearest;
        return true;
    }
  /**
     * Claim the chosen leader’s tail slot and begin moving toward it.
     * Aborts if the leader is invalid, would create a cycle, or already has a follower.
     */
    @Override
    public void start() {
        if (leader == null || !leader.isAlive() || leader.isRemoved()) return;
        //prevent duck from following its own follower
        if (leader == duck.getLeader() || wouldCycle(duck, leader)) {
            leader = null;
            return;
        }
        // race-guard: ensure exclusivity
        if (!leader.claimFollower(duck)) {
            leader = null;
            return;
        }

        duck.setLeader(leader);
        duck.getNavigation().moveTo(leader, speed);
    }
     /**
     * Continue while:
     * - the leader is valid;
     * - we are still following that leader; and
     * - the leader isn't far beyond the search bounds (+small buffer).
     */
    @Override
    public boolean canContinueToUse() {
        if (leader == null || !leader.isAlive() || leader.isRemoved()) return false;
        // must still be following THIS leader
        if (duck.getLeader() != leader) return false;
        // distance sanity: don’t chase forever if they get way too far
        double d2 = duck.distanceToSqr(leader);
        double max = (double)((searchRadius + 4.0F) * (searchRadius + 4.0F));
        return d2 < max;
    }

    /**
     * Release the tail slot, clear leader, and stop navigating.
     */
    @Override
    public void stop() {
        if (leader != null) {
            leader.releaseFollower(duck);
        }
        duck.setLeader(null);
        leader = null;
        duck.getNavigation().stop();
    }
    /**
     * Periodically re-path toward the leader:
     * - If very far: move at {@code speedFar}.
     * - If outside {@code stopDistance}: move at {@code speedNear}.
     * - Otherwise: stop.
     *
     * Re-path cooldown reduces nav spam for smoother movement.
     */
    @Override
    public void tick() {
    if (leader == null) return;

    duck.getLookControl().setLookAt(leader, 30.0F, 30.0F);

    double distanceToSquare = duck.distanceToSqr(leader);
    double startDistanceSquared = (double)(startDist * startDist);  //Start Distancesquared because of startDist*StartDist, same for stop.
    double stopDistanceSquared = (double)(stopDist * stopDist);

    if (repathCooldown > 0) repathCooldown--;

    // "really far" threshold = (startDist * 2.25)^2 (i.e., startDistanceSquared * 2.25^2)
    double far2 = startDistanceSquared * (2.25 * 2.25);

    if (distanceToSquare > far2) {
        if (repathCooldown == 0) {
            duck.getNavigation().moveTo(leader, farSpeed); // catch up at full speed
            repathCooldown = 8; // ~0.4s @ 20 TPS
        }
        return;
    }

    // waddle zone: close enough to go slower
    if (distanceToSquare > stopDistanceSquared) {
        if (repathCooldown == 0) {
            duck.getNavigation().moveTo(leader, nearSpeed); // slower, more obvious follow
            repathCooldown = 10;
        }
        return;
    }

    // close enough — stop
    duck.getNavigation().stop();
}
   
}
