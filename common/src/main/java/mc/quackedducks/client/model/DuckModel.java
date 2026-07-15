package mc.quackedducks.client.model;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model binding for {@link DuckEntity}.
 * Supplies the model, texture, and animation resource locations used by the
 * renderer.
 */

public class DuckModel extends GeoModel<DuckEntity> {
    private static final String MODID = QuackMod.MOD_ID;

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(MODID, "geo/entity/duck.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MODID, "textures/entity/duck.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(MODID, "animations/entity/duck.animation.json");

    /** @return the baked model resource for this entity */
    @Override
    public ResourceLocation getModelResource(DuckEntity animatable) {
        return MODEL;
    }

    /** @return the texture used for this entity */
    @Override
    public ResourceLocation getTextureResource(DuckEntity animatable) {
        return TEXTURE;
    }

    /** @return the animation file used by GeckoLib for this entity */
    @Override
    public ResourceLocation getAnimationResource(DuckEntity animatable) {
        return ANIMATIONS;
    }
}
