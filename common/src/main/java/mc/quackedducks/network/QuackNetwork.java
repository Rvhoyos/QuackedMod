package mc.quackedducks.network;

import mc.quackedducks.QuackMod;
import mc.quackedducks.config.QuackConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Defines the three custom network payloads used by QuackedMod.
 *
 * <ul>
 *   <li>{@link SyncConfigPayload} — server → client on join and after config saves</li>
 *   <li>{@link OpenConfigGuiPayload} — server → client to open the config screen</li>
 *   <li>{@link UpdateConfigPayload} — client → server when the player saves GUI changes</li>
 * </ul>
 *
 * Each loader registers these types independently via its own networking API.
 */
public class QuackNetwork {
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "sync_config"));
    public static final CustomPacketPayload.Type<OpenConfigGuiPayload> OPEN_CONFIG_GUI = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "open_config_gui"));
    public static final CustomPacketPayload.Type<UpdateConfigPayload> UPDATE_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "update_config"));

    /**
     * Carries the subset of {@link mc.quackedducks.config.QuackConfig.GenericDucks} fields
     * that need to be mirrored on the client for rendering and hitbox sizing.
     */
    public record SyncConfigPayload(float duckWidth, float duckHeight, double movementSpeed,
                                    int ambientSoundInterval, int migrationCooldownTicks, int dabChance)
            implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> STREAM_CODEC = StreamCodec
                .composite(
                        ByteBufCodecs.FLOAT, SyncConfigPayload::duckWidth,
                        ByteBufCodecs.FLOAT, SyncConfigPayload::duckHeight,
                        ByteBufCodecs.DOUBLE, SyncConfigPayload::movementSpeed,
                        ByteBufCodecs.VAR_INT, SyncConfigPayload::ambientSoundInterval,
                        ByteBufCodecs.VAR_INT, SyncConfigPayload::migrationCooldownTicks,
                        ByteBufCodecs.VAR_INT, SyncConfigPayload::dabChance,
                        SyncConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_CONFIG;
        }

        public static SyncConfigPayload fromCurrent() {
            var c = QuackConfig.get().genericDucks;
            return new SyncConfigPayload(c.duckWidth, c.duckHeight, c.movementSpeed,
                    c.ambientSoundInterval, c.migrationCooldownTicks, c.dabChance);
        }
    }

    /** Empty payload; receipt on the client triggers opening {@link mc.quackedducks.client.gui.QuackConfigScreen}. */
    public record OpenConfigGuiPayload() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, OpenConfigGuiPayload> STREAM_CODEC = StreamCodec
                .unit(new OpenConfigGuiPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return OPEN_CONFIG_GUI;
        }
    }

    /**
     * Sent by the client when the player confirms changes in the config GUI.
     * The server validates, saves, and re-broadcasts via {@link SyncConfigPayload}.
     */
    public record UpdateConfigPayload(float duckWidth, float duckHeight, double movementSpeed,
                                      int ambientSoundInterval, int migrationCooldownTicks, int dabChance)
            implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, UpdateConfigPayload> STREAM_CODEC = StreamCodec
                .composite(
                        ByteBufCodecs.FLOAT, UpdateConfigPayload::duckWidth,
                        ByteBufCodecs.FLOAT, UpdateConfigPayload::duckHeight,
                        ByteBufCodecs.DOUBLE, UpdateConfigPayload::movementSpeed,
                        ByteBufCodecs.VAR_INT, UpdateConfigPayload::ambientSoundInterval,
                        ByteBufCodecs.VAR_INT, UpdateConfigPayload::migrationCooldownTicks,
                        ByteBufCodecs.VAR_INT, UpdateConfigPayload::dabChance,
                        UpdateConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return UPDATE_CONFIG;
        }
    }
}
