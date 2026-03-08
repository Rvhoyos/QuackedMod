package mc.quackedducks.client.model;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib model binding for {@link DuckEntity}.
 * Supplies the model, texture, and animation resource locations used by the
 * renderer.
 */

public class DuckModel extends GeoModel<DuckEntity> {
    private static final String MODID = QuackMod.MOD_ID;

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(MODID, "entity/duck");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/duck.png");
    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(MODID, "entity/duck");

    /** @return the baked model resource for the current render state */
    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    /** @return the texture used for this entity */
    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    /** @return the animation file used by GeckoLib for this entity */
    @Override
    public Identifier getAnimationResource(DuckEntity animatable) {
        return ANIMATIONS;
    }
}
