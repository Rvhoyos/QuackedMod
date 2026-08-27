package mc.quackedducks.client.model;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

/** Points GeckoLib at the duck's model, texture and animation files. */
public class DuckModel extends GeoModel<DuckEntity> {
    private static final String MODID = QuackMod.MOD_ID;

    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(MODID, "entity/duck");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/duck.png");
    private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(MODID, "entity/duck");

    /** The duck's model. */
    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    /** The duck's texture. */
    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    /** The duck's animation file. */
    @Override
    public Identifier getAnimationResource(DuckEntity animatable) {
        return ANIMATIONS;
    }
}
