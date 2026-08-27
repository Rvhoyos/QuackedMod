package mc.quackedducks.entities.ai;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/**
 * An AvoidEntityGoal that switches itself off while the duck is flying.
 *
 * Wolves, monsters, bears and bees cannot reach a duck in the air, so fleeing up
 * there would do nothing but wreck a migration or break formation.
 */
public class GroundOnlyAvoidGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

    private final DuckEntity duck;

    public GroundOnlyAvoidGoal(DuckEntity duck, Class<T> entityClass,
                               float maxDist, double walkSpeed, double sprintSpeed) {
        super(duck, entityClass, maxDist, walkSpeed, sprintSpeed);
        this.duck = duck;
    }

    /** Off while flying, and off once tamed - a pet duck stands its ground. */
    @Override
    public boolean canUse() {
        if (duck.inFlyingMode()) return false;
        if (duck.isTame()) return false;
        return super.canUse();
    }
}
