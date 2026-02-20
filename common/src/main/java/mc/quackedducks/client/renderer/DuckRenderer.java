package mc.quackedducks.client.renderer;

import mc.quackedducks.client.model.DuckModel;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.cache.object.BakedGeoModel;

/**
 * GeckoLib renderer for {@link DuckEntity}.
 * Handles simple visual scaling (baby vs. adult) and shadow size.
 *
 * @param <R> the render state combining vanilla and GeckoLib state
 */

public class DuckRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DuckEntity, R> {

    /**
     * @param context render provider from the client bootstrap
     */

    public DuckRenderer(EntityRendererProvider.Context context) {
        super(context, new DuckModel());
        this.shadowRadius = 0.3f;
    }

    /**
     * Applies a simple visual scale:
     * - Babies render at 0.5×.
     * - Adults render at 0.9× to look a touch smaller without changing hitbox.
     *
     * If you also want a smaller hitbox, change the EntityType size in
     * QuackEntityTypes.
     */
    @Override
    public void scaleModelForRender(
            final R renderState,
            final float widthScale,
            final float heightScale,
            final com.mojang.blaze3d.vertex.PoseStack poseStack,
            final BakedGeoModel model,
            final boolean isReRender) {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;

        // Ratio logic: Baseline adult model is now 1.0x of the 0.75x0.95 hitbox.
        // This makes the model larger relative to the hitbox to reduce "empty space".
        final float baseScaleX = (config.duckWidth / 0.75f) * 1.0f;
        final float baseScaleY = (config.duckHeight / 0.95f) * 1.0f;

        // Baby ducks scale relative to the calculated adult scale.
        final float scaleX = renderState.isBaby ? baseScaleX * 0.5f : baseScaleX;
        final float scaleY = renderState.isBaby ? baseScaleY * 0.5f : baseScaleY;

        if (renderState.ageInTicks % 40 == 0) { // Log window removed
        }

        super.scaleModelForRender(
                renderState,
                widthScale * scaleX,
                heightScale * scaleY,
                poseStack,
                model,
                isReRender);
    }

}
