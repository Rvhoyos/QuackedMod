package mc.quackedducks;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.server.MinecraftServer;

/**
 * What the server does when the config changes, shared by both loaders.
 *
 * The loaders get us onto the server thread and then call in here. Both ways the
 * config can change at runtime, a GUI save and /quack reload, end up in
 * refreshAndBroadcast, so ducks and clients are updated the same either way.
 */
public final class QuackServerConfig {
    private QuackServerConfig() {
    }

    /**
     * Pushes the live config at everything already in the world: every client gets a
     * sync payload, and every loaded duck re-reads its attributes and hitbox.
     */
    public static void refreshAndBroadcast(final MinecraftServer server) {
        QuackMod.broadcastConfigSync(server);

        for (var level : server.getAllLevels()) {
            for (var duck : level.getEntities(QuackEntityTypes.DUCK, e -> true)) {
                duck.updateFromConfig();
            }
        }
    }

    /** Takes a config change from a client's GUI, saves it, then pushes it back out. */
    public static void applyUpdateSaveAndBroadcast(final QuackNetwork.UpdateConfigPayload payload,
            final MinecraftServer server) {
        QuackConfig.get().apply(payload.config());
        QuackConfig.save();

        refreshAndBroadcast(server);
    }

    /**
     * /quack reload: re-reads config/quack.json and pushes it out. Does not save, because
     * on this path the file is what we are reading from.
     *
     * Spawn weights and biome lists are read at world load, so those need a restart.
     */
    public static void reloadAndBroadcast(final MinecraftServer server) {
        QuackConfig.load();
        refreshAndBroadcast(server);
    }
}
