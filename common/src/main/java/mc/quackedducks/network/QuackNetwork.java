package mc.quackedducks.network;

import io.netty.buffer.ByteBuf;
import mc.quackedducks.QuackMod;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.config.SyncedFields;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The mod's three packets. Sync sends the config down on join and after a save, open
 * tells a client to show the config screen, and update carries the player's edits back up.
 *
 * Sync and update both carry a single compound tag rather than a field per setting, so
 * neither side has to agree on a field order and a jar that predates a setting simply
 * ignores it. SyncedFields owns what goes in the tag.
 *
 * The ids carry a _v2 suffix because the older jars read a positional body. A side that
 * never registered an id discards it, which is why a version gap now costs config sync
 * instead of the connection.
 */
public class QuackNetwork {
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "sync_config_v2"));
    public static final CustomPacketPayload.Type<OpenConfigGuiPayload> OPEN_CONFIG_GUI = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "open_config_gui_v2"));
    public static final CustomPacketPayload.Type<UpdateConfigPayload> UPDATE_CONFIG = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, "update_config_v2"));

    /** Server to client: the config values the client needs to draw and size a duck. */
    public record SyncConfigPayload(CompoundTag config) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, SyncConfigPayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG
                .map(SyncConfigPayload::new, SyncConfigPayload::config);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_CONFIG;
        }

        /** A snapshot of the live config, ready to send. */
        public static SyncConfigPayload fromCurrent() {
            return new SyncConfigPayload(SyncedFields.write(QuackConfig.get().genericDucks));
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
    public record UpdateConfigPayload(CompoundTag config) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, UpdateConfigPayload> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG
                .map(UpdateConfigPayload::new, UpdateConfigPayload::config);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return UPDATE_CONFIG;
        }

        /** Builds one from the screen's draft. */
        public static UpdateConfigPayload from(QuackConfig.GenericDucks draft) {
            return new UpdateConfigPayload(SyncedFields.write(draft));
        }
    }
}
