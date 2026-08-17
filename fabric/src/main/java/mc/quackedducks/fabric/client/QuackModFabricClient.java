package mc.quackedducks.fabric.client;

import mc.quackedducks.client.QuackClientConfig;
import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.network.QuackNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import mc.quackedducks.entities.projectile.DuckEggEntity;
import mc.quackedducks.QuackMod;

/**
 * Fabric client entrypoint.
 * Registers client-only renderers (entity and projectile).
 */
public final class QuackModFabricClient implements ClientModInitializer {
    /**
     * Registers entity renderers, client-side network receivers, and the
     * {@link mc.quackedducks.QuackMod#PACKET_SENDER} hook used by common code
     * to send payloads without loader-specific imports.
     */
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(QuackEntityTypes.DUCK, DuckRenderer::new);
        EntityRendererRegistry.register(
                QuackEntityTypes.DUCK_EGG_PROJECTILE,
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<DuckEggEntity>(ctx, 1.0f, false));

        // Networking receivers — transport + thread dispatch only; logic lives in common.
        ClientPlayNetworking.registerGlobalReceiver(QuackNetwork.SYNC_CONFIG, (payload, context) -> {
            context.client().execute(() -> QuackClientConfig.applySyncAndRefresh(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(QuackNetwork.OPEN_CONFIG_GUI, (payload, context) -> {
            context.client().execute(QuackClientConfig::openConfigScreen);
        });

        // Hooks
        QuackMod.PACKET_SENDER = (payload) -> {
            ClientPlayNetworking.send(payload);
        };
    }
}
