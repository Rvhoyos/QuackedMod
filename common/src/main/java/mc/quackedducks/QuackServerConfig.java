package mc.quackedducks;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.server.MinecraftServer;

/**
 * Loader-agnostic server-side handling of config changes.
 *
 * <p>The loader entrypoints own transport and thread dispatch (Fabric
 * {@code context.server().execute(...)}, NeoForge {@code context.enqueueWork(...)});
 * they then delegate here. Methods below assume they run on the server thread.
 *
 * <p>Both ways the config can change at runtime - a GUI save and {@code /quack reload} -
 * converge on {@link #refreshAndBroadcast}, so live entities and connected clients are
 * updated identically no matter which path fired.
 */
public final class QuackServerConfig {
    private QuackServerConfig() {
    }

    /**
     * Pushes the live config out to everything already in the world: every connected
     * client gets a {@link QuackNetwork.SyncConfigPayload}, and every loaded duck
     * re-reads its attributes and hitbox.
     */
    public static void refreshAndBroadcast(final MinecraftServer server) {
        QuackMod.broadcastConfigSync(server);

        for (var level : server.getAllLevels()) {
            for (var duck : level.getEntities(QuackEntityTypes.DUCK, e -> true)) {
                duck.updateFromConfig();
            }
        }
    }

    /**
     * Applies a client's {@link QuackNetwork.UpdateConfigPayload} and persists it, then
     * converges live state via {@link #refreshAndBroadcast}.
     */
    public static void applyUpdateSaveAndBroadcast(final QuackNetwork.UpdateConfigPayload payload,
            final MinecraftServer server) {
        QuackConfig.get().apply(payload);
        QuackConfig.save();

        refreshAndBroadcast(server);
    }

    /**
     * Handles {@code /quack reload}: re-reads {@code config/quack.json} and converges live
     * state. Deliberately does not save - on this path the file is the source of truth.
     *
     * <p>Spawn weights and biome lists are read when the world loads, so they are not
     * affected until a restart.
     */
    public static void reloadAndBroadcast(final MinecraftServer server) {
        QuackConfig.load();
        refreshAndBroadcast(server);
    }
}
