package mc.quackedducks.network;

import mc.quackedducks.QuackMod;
import mc.quackedducks.config.QuackConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class QuackNetwork {
    public static final CustomPacketPayload.Type<SyncConfigPayload> SYNC_CONFIG = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "sync_config"));
    public static final CustomPacketPayload.Type<OpenConfigGuiPayload> OPEN_CONFIG_GUI = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "open_config_gui"));
    public static final CustomPacketPayload.Type<UpdateConfigPayload> UPDATE_CONFIG = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "update_config"));

    public record SyncConfigPayload(float duckWidth, float duckHeight, double movementSpeed, int ambientSoundInterval)
            implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> STREAM_CODEC = StreamCodec
                .composite(
                        ByteBufCodecs.FLOAT, SyncConfigPayload::duckWidth,
                        ByteBufCodecs.FLOAT, SyncConfigPayload::duckHeight,
                        ByteBufCodecs.DOUBLE, SyncConfigPayload::movementSpeed,
                        ByteBufCodecs.VAR_INT, SyncConfigPayload::ambientSoundInterval,
                        SyncConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SYNC_CONFIG;
        }

        public static SyncConfigPayload fromCurrent() {
            var c = QuackConfig.get().genericDucks;
            return new SyncConfigPayload(c.duckWidth, c.duckHeight, c.movementSpeed, c.ambientSoundInterval);
        }
    }

    public record OpenConfigGuiPayload() implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, OpenConfigGuiPayload> STREAM_CODEC = StreamCodec
                .unit(new OpenConfigGuiPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return OPEN_CONFIG_GUI;
        }
    }

    public record UpdateConfigPayload(float duckWidth, float duckHeight, double movementSpeed, int ambientSoundInterval)
            implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, UpdateConfigPayload> STREAM_CODEC = StreamCodec
                .composite(
                        ByteBufCodecs.FLOAT, UpdateConfigPayload::duckWidth,
                        ByteBufCodecs.FLOAT, UpdateConfigPayload::duckHeight,
                        ByteBufCodecs.DOUBLE, UpdateConfigPayload::movementSpeed,
                        ByteBufCodecs.VAR_INT, UpdateConfigPayload::ambientSoundInterval,
                        UpdateConfigPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return UPDATE_CONFIG;
        }
    }
}
