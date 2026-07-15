package mc.quackedducks.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mc.quackedducks.client.model.DuckModel;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer for {@link DuckEntity}.
 * Handles simple visual scaling (baby vs. adult) and shadow size.
 */

public class DuckRenderer extends GeoEntityRenderer<DuckEntity> {

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
     * - Adults render relative to the configured hitbox size.
     *
     * If you also want a smaller hitbox, change the EntityType size in
     * QuackEntityTypes.
     */
    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack,
            DuckEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick,
            int packedLight, int packedOverlay) {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;

        // Ratio logic: Baseline adult model is now 1.0x of the 0.75x0.95 hitbox.
        // This makes the model larger relative to the hitbox to reduce "empty space".
        final float baseScaleX = (config.duckWidth / 0.75f) * 1.0f;
        final float baseScaleY = (config.duckHeight / 0.95f) * 1.0f;

        // Baby ducks scale relative to the calculated adult scale.
        final float scaleX = animatable.isBaby() ? baseScaleX * 0.5f : baseScaleX;
        final float scaleY = animatable.isBaby() ? baseScaleY * 0.5f : baseScaleY;

        super.scaleModelForRender(widthScale * scaleX, heightScale * scaleY, poseStack,
                animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

}
