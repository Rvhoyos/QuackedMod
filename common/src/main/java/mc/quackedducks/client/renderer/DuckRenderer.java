package mc.quackedducks.client.renderer;

import mc.quackedducks.client.model.DuckModel;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

/** GeckoLib renderer for the duck. Handles model scale and shadow size. */
public class DuckRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<DuckEntity, R> {

    /** Built from the client bootstrap's render context. */
    public DuckRenderer(EntityRendererProvider.Context context) {
        super(context, new DuckModel());
        this.shadowRadius = 0.3f;
    }

    /**
     * Scales the model off the configured hitbox so the duck fills it instead of
     * rattling around inside one. Babies render at half the adult scale.
     *
     * Visual only. The hitbox itself comes from the EntityType in QuackEntityTypes.
     */
    @Override
    public void scaleModelForRender(
            final RenderPassInfo<R> renderPassInfo,
            final float widthScale,
            final float heightScale) {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;
        var renderState = renderPassInfo.renderState();

        // 0.75 x 0.95 is the stock hitbox, so a default-sized duck renders at 1.0.
        final float baseScaleX = (config.duckWidth / 0.75f) * 1.0f;
        final float baseScaleY = (config.duckHeight / 0.95f) * 1.0f;

        // Babies are half of whatever the adult scale worked out to.
        final float scaleX = renderState.isBaby ? baseScaleX * 0.5f : baseScaleX;
        final float scaleY = renderState.isBaby ? baseScaleY * 0.5f : baseScaleY;

        super.scaleModelForRender(
                renderPassInfo,
                widthScale * scaleX,
                heightScale * scaleY);
    }

}
