package mc.quackedducks.fabric;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
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
    @Override
    public void onInitialize() {
        // Common init (entity types, items, sounds)
        QuackMod.init();

        // Entity attributes
        FabricDefaultAttributeRegistry.register(QuackEntityTypes.DUCK, DuckEntity.createAttributes());

        // Biome spawns
        BiomeModifications.addSpawn(
                ctx -> {
                    var keyOpt = ctx.getBiomeKey();
                    return QuackMod.DUCK_BIOMES.contains(keyOpt.location());
                },
                MobCategory.CREATURE,
                QuackEntityTypes.DUCK,
                3, // weight (default for non-wet biomes)
                1, // min group
                1 // max group
        );

        // Higher weight for wet biomes
        BiomeModifications.addSpawn(
                ctx -> {
                    var keyOpt = ctx.getBiomeKey();
                    return QuackMod.WET_BIOMES.contains(keyOpt.location());
                },
                MobCategory.CREATURE,
                QuackEntityTypes.DUCK,
                3, // extra weight for wet biomes (total 6)
                1,
                1);

        // Creative tab additions
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_EGG);
            entries.accept(QuackyModItems.EMPTY_FOIE_GRAS_BOWL);
            entries.accept(QuackyModItems.DUCK_MEAT);
            entries.accept(QuackyModItems.COOKED_DUCK);
            entries.accept(QuackyModItems.FOIE_GRAS);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_SPAWN_EGG);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
            entries.accept(QuackyModItems.DUCK_FEATHER);
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(QuackyModItems.DUCK_FEATHER_ARROW);
        });
    }
}
