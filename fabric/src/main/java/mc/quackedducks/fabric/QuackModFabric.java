package mc.quackedducks.fabric;

import mc.quackedducks.QuackMod;
import net.fabricmc.api.ModInitializer;
/**
 * Fabric **common** entrypoint.
 *
 * Responsibilities:
 * - Bridges Fabric's mod lifecycle to shared/common initialization.
 *
 * Lifecycle:
 * - Invoked by Fabric Loader via {@code fabric.mod.json} → {@code "entrypoints": {"main": ...}}.
 * - Calls {@link QuackMod#init()} which performs all cross-loader setup (registries and biome spawns).
 *
 * See also:
 * - Client-only entrypoint: {@link mc.quackedducks.fabric.client.QuackModFabricClient}
 */
public final class QuackModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        
        QuackMod.init();
    }
}
