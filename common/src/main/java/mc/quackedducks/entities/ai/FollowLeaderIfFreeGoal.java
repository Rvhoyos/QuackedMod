package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.List;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
/**
 * Makes a duck follow the nearest eligible duck, forming single-file chains on the ground
 * and a V-formation in the air during migration.
 *
 * <p>Rules:
 * <ul>
 *   <li>Only starts if this duck currently has no leader.</li>
 *   <li>Picks the closest duck that has no open follower slot (attaches to a chain's tail).</li>
 *   <li>Prevents cycles (won't follow an upstream duck in its own chain).</li>
 *   <li>Syncs flying mode with the leader every tick — starts/stops flying as the leader does.</li>
 *   <li>When airborne and directly behind the chain head, targets a heading-relative
 *       V-offset (LEFT = −3 blocks lateral, RIGHT = +3 blocks lateral, 4 back, 1.5 below).</li>
 *   <li>Uses {@code farSpeed} when very far away and {@code nearSpeed} (50%) when closer.</li>
 *   <li>Re-paths on a short cooldown to avoid constant path recomputation.</li>
 * </ul>
 *
 * <p>Intended use: register after breeding goals and before idle wander/swim.
 */
public class FollowLeaderIfFreeGoal extends Goal {

    private static final Logger LOG = LogUtils.getLogger();
    private static final double FAR_MULTIPLIER = 2.25; // “really far” threshold = (startDist * FAR_MULTIPLIER)^2
    private final DuckEntity duck;
    private DuckEntity leader;          // set when goal starts; cleared on stop
    private final double speed;         // base speed (used when far)
    private final float searchRadius;   // how far to look for a leader candidate
    private final float startDist;      // must be farther than this to start following
    private final float stopDist;       // stop moving when within this distance
    private final double farSpeed;      // speed when very far
    private final double nearSpeed;     // slower speed when already near
    private int repathCooldown = 0;
    /** Which slot (LEFT/RIGHT) this duck occupies on its leader; null when not following. */
    private DuckEntity.FollowerSlot mySlot = null;
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
     * Walks the candidate's leader chain upward looking for {@code follower}.
     */
    private static boolean wouldCycle(DuckEntity follower, DuckEntity candidate) {
    DuckEntity cur = candidate;
    int guard = 64; // safety against bad states
    while (cur != null && guard-- > 0) {
        if (cur == follower) return true; // follower is already upstream of candidate
        cur = cur.getLeader();
    }
    return false;
}

    /**
     * Returns true if:
     * <ul>
     *   <li>this duck has no current leader;</li>
     *   <li>a nearby duck has at least one open follower slot; and</li>
     *   <li>this duck is farther than {@code startDist} from that candidate.</li>
     * </ul>
     * Sets {@link #leader} to the closest qualifying candidate before returning true.
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
        DuckEntity.FollowerSlot slot = leader.claimFollower(duck);
        if (slot == null) {
            leader = null;
            return;
        }
        mySlot = slot;

        duck.setLeader(leader);
        duck.getNavigation().moveTo(leader, speed);
        LOG.info("[follow id={}] start — leader={} slot={}", duck.getId(), leader.getId(), mySlot);
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
        // larger range when airborne — leader may be well above/ahead during migration
        double d2 = duck.distanceToSqr(leader);
        float range = duck.inFlyingMode() ? searchRadius + 16.0F : searchRadius + 4.0F;
        return d2 < (double)(range * range);
    }

    /**
     * Release the tail slot, clear leader, and stop navigating.
     */
    @Override
    public void stop() {
        if (leader != null) {
            leader.releaseFollower(duck);
        }
        LOG.info("[follow id={}] stop — was following={}", duck.getId(), leader != null ? leader.getId() : "null");
        duck.setLeader(null);
        duck.stopFlying();
        mySlot = null;
        leader = null;
        duck.getNavigation().stop();
    }
    /**
     * Returns the position this duck should target:
     * - If leader is flying AND leader is the chain head AND we have a slot,
     *   returns a heading-relative V-formation offset behind the leader.
     * - Otherwise, returns the leader's current position (single-file ground follow).
     */
    private Vec3 getTargetPos() {
        if (duck.inFlyingMode() && leader.isLeader() && mySlot != null) {
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

    /**
     * Periodically re-path toward the leader (or V-formation offset when airborne):
     * - Syncs flying mode with the leader each tick.
     * - If very far: move at {@code farSpeed}.
     * - If outside {@code stopDist}: move at {@code nearSpeed}.
     * - Otherwise: stop.
     *
     * Re-path cooldown reduces nav spam for smoother movement.
     */
    @Override
    public void tick() {
        if (leader == null) return;

        // Sync flying mode with leader — use inFlyingMode (nav-swap state), NOT onGround().
        // onGround() flickers during hops/terrain which would cause constant nav thrashing.
        if (leader.inFlyingMode() && !duck.inFlyingMode()) {
            LOG.info("[follow id={}] sync → startFlying (leader={} leaderFlyMode={} tick={})",
                    duck.getId(), leader.getId(), leader.inFlyingMode(), duck.tickCount);
            duck.startFlying();
            repathCooldown = 0;
        } else if (!leader.inFlyingMode() && duck.inFlyingMode()) {
            LOG.info("[follow id={}] sync → stopFlying (leader={} tick={})",
                    duck.getId(), leader.getId(), duck.tickCount);
            duck.stopFlying();
            repathCooldown = 0;
        }

        duck.getLookControl().setLookAt(leader, 30.0F, 30.0F);

        Vec3 targetPos = getTargetPos();
        double distSq       = duck.position().distanceToSqr(targetPos);
        double startDistSq  = (double)(startDist * startDist);
        double stopDistSq   = (double)(stopDist * stopDist);
        double far2         = startDistSq * (FAR_MULTIPLIER * FAR_MULTIPLIER);

        if (repathCooldown > 0) repathCooldown--;

        if (distSq > far2) {
            if (repathCooldown == 0) {
                duck.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, farSpeed);
                repathCooldown = 8;
            }
            return;
        }

        if (distSq > stopDistSq) {
            if (repathCooldown == 0) {
                duck.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, nearSpeed);
                repathCooldown = 10;
            }
            return;
        }

        duck.getNavigation().stop();
    }

}
