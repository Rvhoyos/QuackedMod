package mc.quackedducks.fabric.client;

import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.entities.QuackEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import mc.quackedducks.entities.projectile.DuckEggEntity;
/**
 * Fabric **client** entrypoint.
 *
 * Responsibilities:
 * - Registers client-only renderers (entity and projectile).
 *
 * Lifecycle:
 * - Invoked by Fabric Loader via {@code fabric.mod.json} → {@code "entrypoints": {"client": ...}}.
 * - Runs after Minecraft client bootstrap is ready for client registrations.
 */
public final class QuackModFabricClient implements ClientModInitializer {

    /**
     * Client-only initialization.
     * Registers:
     * - {@link DuckRenderer} for the duck entity.
     * - {@link net.minecraft.client.renderer.entity.ThrownItemRenderer} for the duck-egg projectile.
     */
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        EntityRendererRegistry.register(QuackEntityTypes.DUCK.get(), DuckRenderer::new);
        EntityRendererRegistry.register(
        QuackEntityTypes.DUCK_EGG_PROJECTILE.get(),
        ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<
            DuckEggEntity
        >(ctx, 1.0f, false)
    );
    }
}
