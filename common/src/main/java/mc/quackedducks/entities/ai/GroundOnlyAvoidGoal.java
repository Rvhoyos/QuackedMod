package mc.quackedducks.entities.ai;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/**
 * {@link AvoidEntityGoal} that is suppressed while the duck is airborne.
 *
 * <p>Ground predators (wolves, monsters, polar bears, bees) cannot reach a flying duck,
 * so avoidance while {@link DuckEntity#inFlyingMode()} is true would only disrupt
 * migration or aerial following. This goal simply returns false from {@link #canUse()}
 * in that state, letting the duck continue uninterrupted.
 */
public class GroundOnlyAvoidGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

    private final DuckEntity duck;

    public GroundOnlyAvoidGoal(DuckEntity duck, Class<T> entityClass,
                               float maxDist, double walkSpeed, double sprintSpeed) {
        super(duck, entityClass, maxDist, walkSpeed, sprintSpeed);
        this.duck = duck;
    }

    /**
     * Returns false when airborne (predators can't reach flying ducks) or when
     * the duck is tamed (domesticated ducks don't flee from predators).
     */
    @Override
    public boolean canUse() {
        if (duck.inFlyingMode()) return false;
        if (duck.isTame()) return false;
        return super.canUse();
    }
}
