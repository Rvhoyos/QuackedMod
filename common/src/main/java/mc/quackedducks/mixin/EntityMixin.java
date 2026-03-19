package mc.quackedducks.mixin;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into {@link net.minecraft.world.entity.LivingEntity} to redirect
 * {@code getDimensions} for {@link mc.quackedducks.entities.DuckEntity}.
 *
 * <p>This lets ducks return config-driven hitbox sizes without overriding
 * the vanilla method directly, which would break on both loaders.
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class EntityMixin {
    /**
     * Intercepts {@code getDimensions} at HEAD and substitutes the duck's
     * config-driven dimensions when the target entity is a {@link mc.quackedducks.entities.DuckEntity}.
     */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof DuckEntity duck) {
            EntityDimensions dims = duck.getDuckDimensions(pose);
            cir.setReturnValue(dims);
        }
    }
}
