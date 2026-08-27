package mc.quackedducks.neoforge.client;

import mc.quackedducks.client.QuackClientConfig;
import mc.quackedducks.network.QuackNetwork;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The clientbound payload handlers on NeoForge.
 *
 * Thread hop only. Everything they actually do lives in QuackClientConfig, which
 * both loaders share.
 */
public class QuackNeoForgeClientNetworking {

    /** Applies the incoming config and refreshes loaded ducks on the client thread. */
    public static void handleSyncConfig(final QuackNetwork.SyncConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> QuackClientConfig.applySyncAndRefresh(payload));
    }

    /** Opens the config screen on the main client thread. */
    public static void handleOpenConfigGui(final QuackNetwork.OpenConfigGuiPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(QuackClientConfig::openConfigScreen);
    }
}
