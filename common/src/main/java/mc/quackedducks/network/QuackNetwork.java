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
 * The mod's three packets. Sync sends the config down on join and after a save, open
 * tells a client to show the config screen, and update carries the player's edits back up.
 *
 * Sync and update hold exactly the same fields. They stay separate record types so the
 * direction is unambiguous on the wire, but the field list itself is only written once,
 * in ConfigFields; configCodec and from() do the rest.
 */
public class QuackNetwork {
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "sync_config"));
    public static final CustomPacketPayload.Type<OpenConfigGuiPayload> OPEN_CONFIG_GUI = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "open_config_gui"));
    public static final CustomPacketPayload.Type<UpdateConfigPayload> UPDATE_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "update_config"));

    /** The shape of both payload constructors, in ConfigFields order. */
    @FunctionalInterface
    public interface ConfigPayloadFactory<T> {
        T create(float duckWidth, float duckHeight, float babyScale, double movementSpeed,
                int ambientSoundInterval, int migrationCooldownTicks, int dabChance);
    }

    /** Builds either payload from anything that has the fields: the live config, or a draft. */
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

    /** Server to client: the config values the client needs to draw and size a duck. */
    public record SyncConfigPayload(float duckWidth, float duckHeight, float babyScale, double movementSpeed,
                                    int ambientSoundInterval, int migrationCooldownTicks, int dabChance)
            implements CustomPacketPayload, ConfigFields {
        public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> STREAM_CODEC = configCodec(
                SyncConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_CONFIG;
        }

        /** A snapshot of the live config, ready to send. */
        public static SyncConfigPayload fromCurrent() {
            return from(QuackConfig.get().genericDucks, SyncConfigPayload::new);
        }
    }

    /** Server to client, and empty. Arriving at all is the whole message: open the screen. */
    public record OpenConfigGuiPayload() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, OpenConfigGuiPayload> STREAM_CODEC = StreamCodec
                .unit(new OpenConfigGuiPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return OPEN_CONFIG_GUI;
        }
    }

    /**
     * Client to server, on Done in the config screen. The server clamps it, saves it,
     * and sends it back out to everyone as a sync.
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
