package mc.quackedducks.neoforge.client;

import mc.quackedducks.client.QuackClientConfig;
import mc.quackedducks.network.QuackNetwork;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side network payload handlers for NeoForge.
 *
 * <p>Called from the play-to-client registrations in {@link mc.quackedducks.neoforge.QuackModNeoForge#registerNetworking}.
 * Transport + thread dispatch only ({@code context.enqueueWork()}); the logic lives in the
 * loader-agnostic {@link QuackClientConfig}.
 */
public class QuackNeoForgeClientNetworking {

    /** Applies the incoming config and refreshes loaded ducks on the client thread. */
    public static void handleSyncConfig(final QuackNetwork.SyncConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> QuackClientConfig.applySyncAndRefresh(payload));
    }

    /** Opens {@link mc.quackedducks.client.gui.QuackConfigScreen} on the main client thread. */
    public static void handleOpenConfigGui(final QuackNetwork.OpenConfigGuiPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(QuackClientConfig::openConfigScreen);
    }
}
