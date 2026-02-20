package mc.quackedducks.neoforge.client;

import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class QuackNeoForgeClientNetworking {

    public static void handleSyncConfig(final QuackNetwork.SyncConfigPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var c = QuackConfig.get().genericDucks;
            c.duckWidth = payload.duckWidth();
            c.duckHeight = payload.duckHeight();
            c.movementSpeed = payload.movementSpeed();
            c.ambientSoundInterval = payload.ambientSoundInterval();
            QuackConfig.get().validate();

            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (var entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof DuckEntity duck) {
                        duck.updateFromConfig();
                    }
                }
            }
        });
    }

    public static void handleOpenConfigGui(final QuackNetwork.OpenConfigGuiPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new mc.quackedducks.client.gui.QuackConfigScreen());
        });
    }
}
