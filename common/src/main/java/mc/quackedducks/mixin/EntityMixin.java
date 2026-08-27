package mc.quackedducks.mixin;

import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirects LivingEntity.getDimensions for ducks.
 *
 * A mixin rather than an override because the duck's hitbox comes from the config
 * and has to be able to change at runtime.
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class EntityMixin {
    /** Hands back the duck's configured dimensions and skips the vanilla lookup. */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void onGetDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof DuckEntity duck) {
            EntityDimensions dims = duck.getDuckDimensions(pose);
            cir.setReturnValue(dims);
        }
    }
}
