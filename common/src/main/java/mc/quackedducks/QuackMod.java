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
 * The mod id, the logger, and the shared bootstrap both loaders call into.
 *
 * Also holds the two hooks the loaders back-fill, because sending a packet is the
 * only part of networking that cannot be written once for both.
 */
public final class QuackMod {
    public static final String MOD_ID = "quack";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * Config, then the entity, sound and item registries.
     *
     * Biome spawns, attributes and creative tabs are the loaders' job, not this one's.
     */
    public static void init() {
        mc.quackedducks.config.QuackConfig.load();
        LOGGER.info("QuackMod initialized!");

        QuackEntityTypes.init();
        QuackedSounds.init();
        QuackyModItems.init();
    }

    /**
     * How to send a payload to one player. Back-filled by whichever loader is running,
     * since that call is genuinely different on Fabric and NeoForge.
     *
     * What to send and who to send it to is decided here in common, so neither loader
     * ends up carrying a broadcast loop of its own.
     */
    public static BiConsumer<ServerPlayer, CustomPacketPayload> CLIENTBOUND_SENDER = (player, payload) -> {
        LOGGER.warn("Clientbound sender not initialized!");
    };

    /**
     * The same thing in the other direction: how the config screen reaches the server.
     * Back-filled by each loader's client entrypoint before the screen can open.
     */
    public static Consumer<QuackNetwork.UpdateConfigPayload> PACKET_SENDER = (payload) -> {
        LOGGER.warn("Packet sender not initialized!");
    };

    /** Tells one player's client to open the config screen. */
    public static void openConfigGui(ServerPlayer player) {
        CLIENTBOUND_SENDER.accept(player, new QuackNetwork.OpenConfigGuiPayload());
    }

    /** Sends the live config to one player. Used on join. */
    public static void sendConfigSync(ServerPlayer player) {
        CLIENTBOUND_SENDER.accept(player, QuackNetwork.SyncConfigPayload.fromCurrent());
    }

    /** Sends the live config to everyone on the server. */
    public static void broadcastConfigSync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendConfigSync(player);
        }
    }

    /** Sends a config change from the screen up to the server. */
    public static void sendConfigUpdate(QuackNetwork.UpdateConfigPayload payload) {
        PACKET_SENDER.accept(payload);
    }
}
