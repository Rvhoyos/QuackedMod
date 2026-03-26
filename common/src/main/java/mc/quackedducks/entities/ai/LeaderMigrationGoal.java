package mc.quackedducks.entities.ai;

import java.util.EnumSet;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.Random;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
/**
 * Causes the <b>head duck</b> (untamed adult with no leader) to occasionally migrate to a
 * distant point at high altitude, dragging any followers behind it in V-formation.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Only runs if {@link DuckEntity#isLeader()} is {@code true} and the duck is not tamed.</li>
 *   <li>Uses an internal cooldown; when it reaches 0 there is a 1-in-30 chance per tick to
 *       pick a migration target.</li>
 *   <li>Target is 80–140 blocks away horizontally in a random direction, at
 *       {@code terrainHeight + 12–20} blocks altitude (sampled via
 *       {@link net.minecraft.world.level.levelgen.Heightmap.Types#MOTION_BLOCKING_NO_LEAVES}).</li>
 *   <li>Calls {@link DuckEntity#startFlying()} in {@link #start()} to swap to
 *       {@link net.minecraft.world.entity.ai.navigation.FlyingPathNavigation}; calls
 *       {@link DuckEntity#stopFlying()} in {@link #stop()} to restore ground navigation.</li>
 *   <li>Followers detect the leader leaving the ground and mirror the flying-mode switch
 *       automatically via {@link FollowLeaderIfFreeGoal#tick()}.</li>
 * </ul>
 *
 * <p>Intended use: register after core survival/breeding goals, before idle wander/swim.
 */
public class LeaderMigrationGoal extends Goal {
    private static final Logger LOG = LogUtils.getLogger();
    private final Random rng = new Random();
    private final DuckEntity duck;
    private final double speed;
    private final int minDelayTicks; // the min time before considering to migrate
    private final int maxDelayTicks; // and max time before considering to migrate
    private int cooldown; // ticks until next possible migration attempt
    private Vec3 target; // where to migrate to
    /**
     * @param duck           the head duck candidate
     * @param speed          navigation speed toward the target
     * @param minDelayTicks  minimum ticks between attempts (inclusive)
     * @param maxDelayTicks  maximum ticks between attempts (inclusive)
     */
    public LeaderMigrationGoal(DuckEntity duck, double speed, int minDelayTicks, int maxDelayTicks) {
        this.duck = duck;
        this.speed = speed;
        this.minDelayTicks = minDelayTicks;
        this.maxDelayTicks = maxDelayTicks;
        this.cooldown = 0;
        this.target = null;
        // Movement + look only; this goal doesn’t grab jump/target flags.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }
    /**
     * Only untamed head-of-line adult ducks can start migration.
     * When cooldown hits 0, there is a 1-in-30 chance per tick to pick a target
     * 80–140 blocks away at {@code terrainHeight + 12–20} blocks altitude.
     * Resets the cooldown on success so migration isn’t attempted again too soon.
     */
    @Override
    public boolean canUse() {
        // Only untamed head-of-line adults can migrate
        if (duck.isBaby()) return false;
        if (!duck.isLeader()) return false;
        if (duck.isTame()) return false;

        if (cooldown > 0) { cooldown--; return false; }
        if (rng.nextInt(30) != 0) return false;

        // Choose a target 80-140 blocks away; altitude = terrain height + 12-20 blocks
        double distance = 80.0 + rng.nextDouble() * 60.0;
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;

        Vec3 here = duck.position();
        int terrainY = duck.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int)(here.x + dx), (int)(here.z + dz));
        double flightY = terrainY + 12.0 + rng.nextDouble() * 8.0;
        this.target = new Vec3(here.x + dx, flightY, here.z + dz);

        // next time we *consider* migrating (e.g., 2400–3600 ticks)
        this.cooldown = rng.nextInt(maxDelayTicks - minDelayTicks + 1) + minDelayTicks;
        LOG.info("[migrate id={}] canUse TRUE — target=({:.1f},{:.1f},{:.1f}) cooldownNext={}",
                duck.getId(), target.x, target.y, target.z, cooldown);
        return true;
    }


    /**
     * Switches the duck to {@link net.minecraft.world.entity.ai.navigation.FlyingPathNavigation}
     * via {@link DuckEntity#startFlying()}, then begins pathing toward the chosen {@link #target}.
     */
    @Override
    public void start() {
        if (target != null) {
            duck.startFlying();
            duck.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    /**
     * Keep going while we still have no leader (still the head) and the navigator has a path.
     */
    @Override
    public boolean canContinueToUse() {
        if (duck.isBaby()) { LOG.info("[migrate id={}] canContinue FALSE — isBaby", duck.getId()); return false; }
        if (!duck.isLeader()) { LOG.info("[migrate id={}] canContinue FALSE — not leader", duck.getId()); return false; }
        boolean done = duck.getNavigation().isDone();
        if (done) LOG.info("[migrate id={}] canContinue FALSE — nav isDone (path={})",
                duck.getId(), duck.getNavigation().getPath());
        return !done;
    }


    /**
     * Stops navigation, restores ground nav via {@link DuckEntity#stopFlying()},
     * and clears the current target. Followers detect the leader landing via
     * {@link FollowLeaderIfFreeGoal#tick()} and call {@code stopFlying()} themselves.
     */
    @Override
    public void stop() {
        duck.getNavigation().stop();
        duck.stopFlying();
        target = null;
    }

    /**
     * Face the current target each tick for more natural movement.
     */
    @Override
    public void tick() {
        if (target != null) {
            duck.getLookControl().setLookAt(target.x, target.y, target.z);
        }
    }

    
}
