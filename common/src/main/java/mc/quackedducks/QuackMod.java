package mc.quackedducks;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.network.QuackNetwork;
import mc.quackedducks.sound.QuackedSounds;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
     * Loader-injected strategy for sending any clientbound payload to one player.
     * Sending is the only genuinely loader-specific part of the server → client path
     * (Fabric {@code ServerPlayNetworking.send} vs NeoForge {@code player.connection.send}),
     * so it is the only thing the loaders back-fill; which payload to send and to whom is
     * decided here in common.
     */
    public static BiConsumer<ServerPlayer, CustomPacketPayload> CLIENTBOUND_SENDER = (player, payload) -> {
        LOGGER.warn("Clientbound sender not initialized!");
    };

    /**
     * Loader-injected handler that forwards an {@link QuackNetwork.UpdateConfigPayload}
     * from the client GUI to the server. Back-filled by each loader's client entrypoint
     * before the config screen can be opened.
     */
    public static Consumer<QuackNetwork.UpdateConfigPayload> PACKET_SENDER = (payload) -> {
        LOGGER.warn("Packet sender not initialized!");
    };

    /**
     * Sends an {@link QuackNetwork.OpenConfigGuiPayload} to {@code player}, causing the client
     * to open {@link mc.quackedducks.client.gui.QuackConfigScreen}.
     */
    public static void openConfigGui(ServerPlayer player) {
        CLIENTBOUND_SENDER.accept(player, new QuackNetwork.OpenConfigGuiPayload());
    }

    /** Sends the live config to a single player - used on join. */
    public static void sendConfigSync(ServerPlayer player) {
        CLIENTBOUND_SENDER.accept(player, QuackNetwork.SyncConfigPayload.fromCurrent());
    }

    /** Sends the live config to every player on {@code server}. */
    public static void broadcastConfigSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendConfigSync(player);
        }
    }

    /** Sends an {@link QuackNetwork.UpdateConfigPayload} to the server. */
    public static void sendConfigUpdate(QuackNetwork.UpdateConfigPayload payload) {
        PACKET_SENDER.accept(payload);
    }
}
