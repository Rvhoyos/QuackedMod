package mc.quackedducks.neoforge.client;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side network payload handlers for NeoForge.
 *
 * <p>Called from the play-to-client registrations in {@link mc.quackedducks.neoforge.QuackModNeoForge#registerNetworking}.
 * All work is enqueued on the main client thread via {@code context.enqueueWork()}.
 */
public class QuackNeoForgeClientNetworking {

    /**
     * Applies incoming config values to the local {@link mc.quackedducks.config.QuackConfig}
     * and calls {@link mc.quackedducks.entities.DuckEntity#updateFromConfig()} on all
     * currently loaded duck entities.
     */
    public static void handleSyncConfig(final QuackNetwork.SyncConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var c = QuackConfig.get().genericDucks;
            c.duckWidth = payload.duckWidth();
            c.duckHeight = payload.duckHeight();
            c.movementSpeed = payload.movementSpeed();
            c.ambientSoundInterval = payload.ambientSoundInterval();
            c.migrationCooldownTicks = payload.migrationCooldownTicks();
            c.dabChance = payload.dabChance();
            QuackConfig.get().validate();

            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (var entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof DuckEntity duck) {
                        duck.updateFromConfig();
                    }
                }
            }
        });
    }

    /** Opens {@link mc.quackedducks.client.gui.QuackConfigScreen} on the main client thread. */
    public static void handleOpenConfigGui(final QuackNetwork.OpenConfigGuiPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new mc.quackedducks.client.gui.QuackConfigScreen());
        });
    }
}
