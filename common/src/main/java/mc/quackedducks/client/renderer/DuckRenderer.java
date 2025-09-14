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
     * If you also want a smaller hitbox, change the EntityType size in QuackEntityTypes.
     */
    @Override
    public void scaleModelForRender(
        final R renderState,
        final float widthScale,
        final float heightScale,
        final com.mojang.blaze3d.vertex.PoseStack poseStack,
        final BakedGeoModel model,
        final boolean isReRender
        ) {

    // keep babies at 0.5x; shrink adults a bit (0.9x)
    final float scale = renderState.isBaby ? 0.5f : 0.9f;

    super.scaleModelForRender(
            renderState,
            widthScale * scale,
            heightScale * scale,
            poseStack, 
            model, 
            isReRender
            );
        }
        
    }
