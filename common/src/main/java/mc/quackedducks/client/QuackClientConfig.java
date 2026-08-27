package mc.quackedducks.client;

import mc.quackedducks.client.gui.QuackConfigScreen;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.client.Minecraft;

/**
 * What the client does with an incoming config payload, shared by both loaders.
 *
 * The loaders get us onto the client thread and then call in here.
 */
public final class QuackClientConfig {
    private QuackClientConfig() {
    }

    /**
     * Applies the server's config to ours, then refreshes every loaded duck so a size
     * change shows up right away instead of on the next reload.
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

    /** Opens the config screen. The only place in the mod that swaps the current screen. */
    public static void openConfigScreen() {
        Minecraft.getInstance().gui.setScreen(new QuackConfigScreen());
    }
}
