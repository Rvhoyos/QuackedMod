package mc.quackedducks.fabric.client;

import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.entities.QuackEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import mc.quackedducks.entities.projectile.DuckEggEntity;

/**
 * Fabric client entrypoint.
 * Registers client-only renderers (entity and projectile).
 */
public final class QuackModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(QuackEntityTypes.DUCK, DuckRenderer::new);
        EntityRendererRegistry.register(
                QuackEntityTypes.DUCK_EGG_PROJECTILE,
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<DuckEggEntity>(ctx, 1.0f, false));
    }
}
