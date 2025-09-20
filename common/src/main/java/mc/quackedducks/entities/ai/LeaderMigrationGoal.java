package mc.quackedducks.entities.ai;

import java.util.EnumSet;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.Random;
/**
 * Causes the **head duck** (a duck with no leader) to occasionally choose a distant point
 * and start migrating toward it. Followers (ducks with a leader) never run this goal.
 *
 * Behavior (unchanged):
 * - Only runs if {@link DuckEntity#getLeader()} is {@code null} (i.e., we are the head).
 * - Uses an internal cooldown; when it reaches 0 there is a small random chance (1/30) to pick a target.
 * - Target is ~20–32 blocks away in a random horizontal direction with a tiny vertical wobble.
 * - Starts pathing toward that target at the provided speed; continues while the path exists.
 *
 * Intended use: register after core survival/breeding goals, before idle wander/swim.
 */
public class LeaderMigrationGoal extends Goal {
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
     * Only the **head** duck can start migration.
     * When cooldown hits 0, there’s a 1-in-30 chance to pick a new far-ish destination (20–32 blocks).
     */
    @Override
    public boolean canUse() {
        // Only the head-of-line (no predecessor) and never babies
        if (duck.isBaby()) return false;
        if (!duck.isLeader()) return false;

        if (cooldown > 0) { cooldown--; return false; }
        if (rng.nextInt(30) != 0) return false;

        // choose a far-ish target (20–32 blocks) with slight Y jitter
        double distance = 20.0 + rng.nextDouble() * 12.0;
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;
        double dy = rng.nextGaussian() * 1.0;

        Vec3 here = duck.position();
        this.target = new Vec3(here.x + dx, here.y + dy, here.z + dz);

        // next time we *consider* migrating (e.g., 2400–3600 ticks)
        this.cooldown = rng.nextInt(maxDelayTicks - minDelayTicks + 1) + minDelayTicks;
        return true;
    }


    /**
     * Begin pathing toward the chosen {@link #target}, if any.
     */
    @Override
    public void start() {
        if (target != null) {
            duck.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    /**
     * Keep going while we still have no leader (still the head) and the navigator has a path.
     */
    @Override
    public boolean canContinueToUse() {
        // stop if we’re no longer head, became a baby (edge bug), or navigation finished
        if (duck.isBaby()) return false;
        if (!duck.isLeader()) return false;
        return !duck.getNavigation().isDone();
    }


    /**
     * Stop navigating and clear the current target.
     */
    @Override
    public void stop() {
        duck.getNavigation().stop();
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
