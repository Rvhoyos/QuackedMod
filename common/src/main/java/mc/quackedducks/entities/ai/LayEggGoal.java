
package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.Random;
import java.util.function.Supplier;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ItemLike;
/**
 * Makes an adult duck drop an egg every so often.
 *
 * Server-side only, never for babies. Fires the tick the cooldown runs out, then
 * rolls a fresh cooldown somewhere between the min and max interval.
 *
 * It holds no goal flags, so it can lay an egg while the duck is off doing something
 * else. Nothing here moves the duck.
 */
public class LayEggGoal extends Goal{
    private final Supplier<? extends ItemLike> eggItemSupplier;

    private final DuckEntity self;
    /** How many ticks between eggs, at the low and high end. */
    private final int minIntervalTicks, maxIntervalTicks;
    private final Random rng = new Random();
    private int cooldown;
    /** The first egg comes sooner than the normal interval, and at a different offset per duck. */
    public LayEggGoal(DuckEntity self, int minIntervalTicks, int maxIntervalTicks, Supplier<? extends ItemLike> eggItemSupplier){
        this.self = self;
        this.eggItemSupplier = eggItemSupplier;
        this.minIntervalTicks = minIntervalTicks;
        this.maxIntervalTicks = maxIntervalTicks;
        this.setFlags(EnumSet.noneOf(Flag.class));
        this.cooldown = 1 + rng.nextInt(Math.max(1, this.minIntervalTicks));    }
    /** True on the one tick the cooldown hits zero. */
    @Override
    public boolean canUse() {
        if (self.isBaby() || self.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        return true;
    }

    /** Sound, one egg on the ground, new cooldown. */
    @Override
    public void start() {
        self.playSound(SoundEvents.CHICKEN_EGG, 1.0F, 1.0F);
        if (!self.level().isClientSide()) {
            net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) self.level();
            self.spawnAtLocation(sl, eggItemSupplier.get());
        }
        // Both ends inclusive.
        cooldown = rng.nextInt((maxIntervalTicks - minIntervalTicks) + 1) + minIntervalTicks;
    }
    /** One and done. start() lays the egg, there is nothing to keep running. */
    @Override
    public boolean canContinueToUse() { return false; }
}

