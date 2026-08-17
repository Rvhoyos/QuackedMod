package mc.quackedducks;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-agnostic server-side handling of config network payloads.
 *
 * <p>The loader entrypoints own transport and thread dispatch (Fabric
 * {@code context.server().execute(...)}, NeoForge {@code context.enqueueWork(...)});
 * they then delegate here. Methods below assume they run on the server thread.
 */
public final class QuackServerConfig {
    private QuackServerConfig() {
    }

    /**
     * Applies a client's {@link QuackNetwork.UpdateConfigPayload}, persists it, re-broadcasts the
     * new config to every player, and refreshes all loaded ducks so size/attribute changes apply
     * server-side immediately.
     */
    public static void applyUpdateSaveAndBroadcast(final QuackNetwork.UpdateConfigPayload payload,
            final MinecraftServer server) {
        QuackConfig.get().apply(payload);
        QuackConfig.save();

        QuackMod.broadcastConfigSync(server);

        for (var level : server.getAllLevels()) {
            for (var duck : level.getEntities(QuackEntityTypes.DUCK, e -> true)) {
                duck.updateFromConfig();
            }
        }
    }
}
