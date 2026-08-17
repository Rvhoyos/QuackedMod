package mc.quackedducks.client;

import mc.quackedducks.client.gui.QuackConfigScreen;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.Minecraft;

/**
 * Loader-agnostic client-side reactions to config network payloads.
 *
 * <p>The loader entrypoints own transport and thread dispatch (Fabric
 * {@code context.client().execute(...)}, NeoForge {@code context.enqueueWork(...)});
 * they then delegate here. Every method below assumes it is already running on the
 * client thread.
 */
public final class QuackClientConfig {
    private QuackClientConfig() {
    }

    /**
     * Applies an incoming {@link QuackNetwork.SyncConfigPayload} to the live config and
     * refreshes every duck currently loaded in the client world so hitbox/size changes
     * take effect immediately.
     */
    public static void applySyncAndRefresh(final QuackNetwork.SyncConfigPayload payload) {
        QuackConfig.get().apply(payload);

        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (var entity : level.entitiesForRendering()) {
                if (entity instanceof DuckEntity duck) {
                    duck.updateFromConfig();
                }
            }
        }
    }

    /** Opens the {@link QuackConfigScreen}. */
    public static void openConfigScreen() {
        Minecraft.getInstance().gui.setScreen(new QuackConfigScreen());
    }
}
