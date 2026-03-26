package mc.quackedducks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.sound.QuackedSounds;

/**
 * Common entrypoint and shared bootstrap.
 *
 * Responsibilities:
 * - Defines the mod id and logger.
 * - Initializes registries for entities, sounds, and items via their
 * {@code init()} methods.
 *
 * Biome spawns and creative tabs are handled per-platform in the loader
 * entrypoints.
 *
 * Lifecycle:
 * - This method is invoked from loader-specific entrypoints:
 * - Fabric: {@code mc.quackedducks.fabric.QuackModFabric#onInitialize()}
 * - NeoForge: {@code mc.quackedducks.neoforge.QuackModNeoForge} constructor
 */
public final class QuackMod {
    public static final String MOD_ID = "quack";
    public static final String BUILD = "2.0.1";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * Common initialization.
     *
     * Steps:
     * 1) Initialize entity, sound, and item registries (vanilla Registry.register).
     *
     * Biome spawns, attribute binding, and creative tabs are handled per-platform.
     */
    public static void init() {
        mc.quackedducks.config.QuackConfig.load();
        LOGGER.info("QuackMod initialized!");

        QuackEntityTypes.init();
        QuackedSounds.init();
        QuackyModItems.init();
    }

    /**
     * Loader-injected handler that sends an {@link mc.quackedducks.network.QuackNetwork.OpenConfigGuiPayload}
     * to the given player, causing the client to open {@link mc.quackedducks.client.gui.QuackConfigScreen}.
     * Back-filled by each loader entrypoint before any command can invoke it.
     */
    public static java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> CONFIG_OPENER = (p) -> {
        LOGGER.warn("Config opener not initialized!");
    };

    /** Sends an {@link mc.quackedducks.network.QuackNetwork.OpenConfigGuiPayload} to {@code player}. */
    public static void openConfigGui(net.minecraft.server.level.ServerPlayer player) {
        CONFIG_OPENER.accept(player);
    }

    /**
     * Loader-injected handler that forwards an {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload}
     * from the client GUI to the server. Back-filled by each loader entrypoint before
     * the config screen can be opened.
     */
    public static java.util.function.Consumer<mc.quackedducks.network.QuackNetwork.UpdateConfigPayload> PACKET_SENDER = (
            p) -> {
        LOGGER.warn("Packet sender not initialized!");
    };

    /** Sends an {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload} to the server. */
    public static void sendConfigUpdate(mc.quackedducks.network.QuackNetwork.UpdateConfigPayload payload) {
        PACKET_SENDER.accept(payload);
    }
}
