package mc.quackedducks.fabric;

import mc.quackedducks.QuackMod;
import mc.quackedducks.QuackServerConfig;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.command.QuackCommands;
import mc.quackedducks.network.QuackNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Fabric common entrypoint.
 *
 * Handles:
 * - Common init via QuackMod.init()
 * - Entity attribute registration (Fabric API)
 * - Biome spawn modifications (Fabric API)
 * - Creative tab item additions (Fabric API)
 */
public final class QuackModFabric implements ModInitializer {
    /**
     * Common Fabric initialisation: runs shared {@link mc.quackedducks.QuackMod#init()},
     * registers commands, networking payloads, biome spawns, and creative tab entries.
     */
    @Override
    public void onInitialize() {
        // Common init (entity types, items, sounds)
        QuackMod.init();

        // Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            QuackCommands.register(dispatcher);
        });

        // Networking Payloads
        PayloadTypeRegistry.clientboundPlay().register(QuackNetwork.SYNC_CONFIG,
                QuackNetwork.SyncConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(QuackNetwork.OPEN_CONFIG_GUI,
                QuackNetwork.OpenConfigGuiPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuackNetwork.UPDATE_CONFIG,
                QuackNetwork.UpdateConfigPayload.STREAM_CODEC);

        // Clientbound send hook - the only loader-specific part of the server → client path.
        QuackMod.CLIENTBOUND_SENDER = ServerPlayNetworking::send;

        // Sync on join
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> QuackMod.sendConfigSync(handler.player));

        // Update Handler - transport + thread dispatch only; logic lives in common.
        ServerPlayNetworking.registerGlobalReceiver(QuackNetwork.UPDATE_CONFIG, (payload, context) -> {
            context.server().execute(
                    () -> QuackServerConfig.applyUpdateSaveAndBroadcast(payload, context.server()));
        });

        // Entity attributes
        FabricDefaultAttributeRegistry.register(QuackEntityTypes.DUCK, DuckEntity.createAttributes());

        // Biome spawns - Load from config
        var duckBiomes = mc.quackedducks.config.QuackConfig.getDuckBiomes();
        var wetBiomes = mc.quackedducks.config.QuackConfig.getWetBiomes();
        var config = mc.quackedducks.config.QuackConfig.get();

        BiomeModifications.addSpawn(
                ctx -> {
                    var key = ctx.getBiomeKey();
                    return duckBiomes.contains(key.identifier());
                },
                MobCategory.CREATURE,
                QuackEntityTypes.DUCK,
                config.spawning.baseWeight,
                config.spawning.minGroupSize,
                config.spawning.maxGroupSize);

        // Higher weight for wet biomes
        BiomeModifications.addSpawn(
                ctx -> {
                    var keyOpt = ctx.getBiomeKey();
                    return wetBiomes.contains(keyOpt.identifier());
                },
                MobCategory.CREATURE,
                QuackEntityTypes.DUCK,
                config.spawning.wetBiomeBonusWeight,
                config.spawning.minGroupSize,
                config.spawning.maxGroupSize);

        // Creative tab additions
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_EGG);
            entries.accept(QuackyModItems.EMPTY_FOIE_GRAS_BOWL);
            entries.accept(QuackyModItems.DUCK_MEAT);
            entries.accept(QuackyModItems.COOKED_DUCK);
            entries.accept(QuackyModItems.FOIE_GRAS);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_SPAWN_EGG);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_FEATHER);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(QuackyModItems.DUCK_FEATHER_ARROW);
        });
    }
}
