package mc.quackedducks.client.model;

import mc.quackedducks.QuackMod; 
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib model binding for {@link DuckEntity}.
 * Supplies the model, texture, and animation resource locations used by the renderer.
 */

public class DuckModel extends GeoModel<DuckEntity> {
    private static final String MODID = QuackMod.MOD_ID; 

    private static final ResourceLocation MODEL      = ResourceLocation.fromNamespaceAndPath(MODID, "geckolib/models/entity/duck.geo.json");
    private static final ResourceLocation TEXTURE    = ResourceLocation.fromNamespaceAndPath(MODID, "textures/entity/duck.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(MODID, "geckolib/animations/entity/duck.animation.json");
    
    /** @return the baked model resource for the current render state */
    @Override
    public ResourceLocation getModelResource(GeoRenderState renderState) {
        return MODEL;
    }
    /** @return the texture used for this entity */
    @Override
    public ResourceLocation getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }
    /** @return the animation file used by GeckoLib for this entity */
    @Override
    public ResourceLocation getAnimationResource(DuckEntity animatable) {
        return ANIMATIONS;
    }
}
