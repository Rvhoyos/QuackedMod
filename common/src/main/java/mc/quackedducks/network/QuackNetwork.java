package mc.quackedducks.network;

import mc.quackedducks.QuackMod;
import mc.quackedducks.config.ConfigFields;
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
 *   <li>{@link SyncConfigPayload} - server → client on join and after config saves</li>
 *   <li>{@link OpenConfigGuiPayload} - server → client to open the config screen</li>
 *   <li>{@link UpdateConfigPayload} - client → server when the player saves GUI changes</li>
 * </ul>
 *
 * Each loader registers these types independently via its own networking API.
 *
 * <p>The two config payloads carry an identical field list - they stay distinct record
 * types only to preserve wire identity and direction. To keep that list written once,
 * both are built through {@link #configCodec} and {@link #from}, driven by
 * {@link ConfigPayloadFactory}.
 */
public class QuackNetwork {
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "sync_config"));
    public static final CustomPacketPayload.Type<OpenConfigGuiPayload> OPEN_CONFIG_GUI = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "open_config_gui"));
    public static final CustomPacketPayload.Type<UpdateConfigPayload> UPDATE_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "update_config"));

    /**
     * Canonical constructor of a config payload record, in {@link ConfigFields} order.
     * Both payload records' generated constructors match it.
     */
    @FunctionalInterface
    public interface ConfigPayloadFactory<T> {
        T create(float duckWidth, float duckHeight, float babyScale, double movementSpeed,
                int ambientSoundInterval, int migrationCooldownTicks, int dabChance);
    }

    /**
     * Builds a config payload from any {@link ConfigFields} source - the live config or the
     * config screen's unsaved draft.
     */
    public static <T> T from(ConfigFields src, ConfigPayloadFactory<T> ctor) {
        return ctor.create(src.duckWidth(), src.duckHeight(), src.babyScale(), src.movementSpeed(),
                src.ambientSoundInterval(), src.migrationCooldownTicks(), src.dabChance());
    }

    /** The wire format shared by both config payloads. */
    private static <T extends CustomPacketPayload & ConfigFields> StreamCodec<FriendlyByteBuf, T> configCodec(
            ConfigPayloadFactory<T> ctor) {
        return StreamCodec.composite(
                ByteBufCodecs.FLOAT, ConfigFields::duckWidth,
                ByteBufCodecs.FLOAT, ConfigFields::duckHeight,
                ByteBufCodecs.FLOAT, ConfigFields::babyScale,
                ByteBufCodecs.DOUBLE, ConfigFields::movementSpeed,
                ByteBufCodecs.VAR_INT, ConfigFields::ambientSoundInterval,
                ByteBufCodecs.VAR_INT, ConfigFields::migrationCooldownTicks,
                ByteBufCodecs.VAR_INT, ConfigFields::dabChance,
                ctor::create);
    }

    /**
     * Carries the subset of {@link mc.quackedducks.config.QuackConfig.GenericDucks} fields
     * that need to be mirrored on the client for rendering and hitbox sizing.
     */
    public record SyncConfigPayload(float duckWidth, float duckHeight, float babyScale, double movementSpeed,
                                    int ambientSoundInterval, int migrationCooldownTicks, int dabChance)
            implements CustomPacketPayload, ConfigFields {
        public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> STREAM_CODEC = configCodec(
                SyncConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_CONFIG;
        }

        /** Snapshots the live server config for broadcast. */
        public static SyncConfigPayload fromCurrent() {
            return from(QuackConfig.get().genericDucks, SyncConfigPayload::new);
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
    public record UpdateConfigPayload(float duckWidth, float duckHeight, float babyScale, double movementSpeed,
                                      int ambientSoundInterval, int migrationCooldownTicks, int dabChance)
            implements CustomPacketPayload, ConfigFields {
        public static final StreamCodec<FriendlyByteBuf, UpdateConfigPayload> STREAM_CODEC = configCodec(
                UpdateConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return UPDATE_CONFIG;
        }
    }
}
