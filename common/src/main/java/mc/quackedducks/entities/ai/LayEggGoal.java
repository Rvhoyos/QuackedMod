
package mc.quackedducks.entities.ai;

import java.util.EnumSet;
import java.util.Random;
import java.util.function.Supplier;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ItemLike;
/**
 * One-shot goal that periodically makes an adult duck lay a duck egg item.
 *
 * Behavior:
 * - Runs server-side only and never for babies.
 * - Triggers once when the cooldown hits 0, then resets the cooldown to a random
 *   value in [`minIntervalTicks`, `maxIntervalTicks`].
 * - Plays a short egg sound and drops the egg item at the duck's position.
 *
 * Intended use: register in {@link DuckEntity#registerGoals()} after survival/breeding goals.
 */
public class LayEggGoal extends Goal{
    private final Supplier<? extends ItemLike> eggItemSupplier;  //explore suppliers and use of ? generics ? 

    private final DuckEntity self;
    /** Minimum and maximum interval (in ticks) between egg lays. */
    private final int minIntervalTicks, maxIntervalTicks;
    private final Random rng = new Random();
    private int cooldown=0;
/**
     * @param self              the duck entity
     * @param minIntervalTicks  minimum ticks between egg lays (inclusive)
     * @param maxIntervalTicks  maximum ticks between egg lays (inclusive)
     * @param eggItemSupplier   supplier for the egg item to spawn
     */
    public LayEggGoal(DuckEntity self, int minIntervalTicks, int maxIntervalTicks, Supplier<? extends ItemLike> eggItemSupplier){
        this.self = self;
        this.eggItemSupplier = eggItemSupplier;
        this.minIntervalTicks = minIntervalTicks;
        this.maxIntervalTicks = maxIntervalTicks;
        this.setFlags(EnumSet.noneOf(Flag.class)); 
    }
    /**
     * Server-only, adult-only; returns true exactly on the tick where the
     * internal cooldown reaches zero.
     */
    @Override
    public boolean canUse() {
        if (self.isBaby() || self.level().isClientSide()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        return true; // ready to lay this tick
    }

    /**
     * Plays the egg sound, drops one egg item, then arms a new cooldown in
     * [`minIntervalTicks`, `maxIntervalTicks`] (inclusive).
     */
    @Override
    public void start() {
        // lay the egg
        self.playSound(SoundEvents.CHICKEN_EGG, 1.0F, 1.0F);
        if (!self.level().isClientSide()) {
            net.minecraft.server.level.ServerLevel sl = (net.minecraft.server.level.ServerLevel) self.level();
            self.spawnAtLocation(sl, eggItemSupplier.get()); // 1 item;
        }
        // Re-arm cooldown (inclusive range).
        cooldown = rng.nextInt((maxIntervalTicks - minIntervalTicks) + 1) + minIntervalTicks;
    }
    /** One-shot: never continues after {@link #start()}. */
    @Override
    public boolean canContinueToUse() { return false; } // one-shot
}

