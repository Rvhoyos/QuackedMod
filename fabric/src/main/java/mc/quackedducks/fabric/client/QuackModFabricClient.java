package mc.quackedducks.fabric.client;

import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
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
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(QuackEntityTypes.DUCK, DuckRenderer::new);
        EntityRendererRegistry.register(
                QuackEntityTypes.DUCK_EGG_PROJECTILE,
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<DuckEggEntity>(ctx, 1.0f, false));

        // Networking receivers
        ClientPlayNetworking.registerGlobalReceiver(QuackNetwork.SYNC_CONFIG, (payload, context) -> {
            context.client().execute(() -> {
                var c = QuackConfig.get().genericDucks;
                c.duckWidth = payload.duckWidth();
                c.duckHeight = payload.duckHeight();
                c.movementSpeed = payload.movementSpeed();
                c.ambientSoundInterval = payload.ambientSoundInterval();
                QuackConfig.get().validate();

                // Update all existing ducks in the client world
                if (context.client().level != null) {
                    for (var entity : context.client().level.entitiesForRendering()) {
                        if (entity instanceof DuckEntity duck) {
                            duck.updateFromConfig();
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(QuackNetwork.OPEN_CONFIG_GUI, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new mc.quackedducks.client.gui.QuackConfigScreen());
            });
        });

        // Hooks
        QuackMod.PACKET_SENDER = (payload) -> {
            ClientPlayNetworking.send(payload);
        };
    }
}
