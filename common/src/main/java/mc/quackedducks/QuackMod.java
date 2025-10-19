package mc.quackedducks;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.architectury.registry.level.biome.BiomeModifications;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.sound.QuackedSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
/**
 * Common entrypoint and shared bootstrap.
 *
 * Responsibilities:
 * - Defines the mod id and logger.
 * - Initializes registries for entities, sounds, and items via their {@code init()} methods.
 * - Adds natural spawn rules using Architectury's biome modification API so ducks appear in
 *   selected biomes on both loaders.
 *
 * Lifecycle:
 * - This method is invoked from loader-specific entrypoints:
 *   - Fabric: {@code mc.quackedducks.fabric.QuackModFabric#onInitialize()}
 *   - NeoForge: {@code mc.quackedducks.neoforge.QuackModNeoForge} constructor
 */
public final class QuackMod {
    public static final String MOD_ID = "quack";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    
    /**
     * Biomes that should naturally spawn ducks.
     *
     * Notes:
     * - Overworld grassland/forest/wetland-style biomes are targeted.
     * - This is used by the biome modification below to attach spawn entries.
     */
    private static final Set<ResourceLocation> DUCK_BIOMES = Set.of(
    // Temperate/flowery
    ResourceLocation.fromNamespaceAndPath("minecraft", "plains"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "sunflower_plains"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "flower_forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "birch_forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "dark_forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_birch_forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "meadow"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "cherry_grove"),

    // Wetlands / rivers / shores
    ResourceLocation.fromNamespaceAndPath("minecraft", "swamp"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "mangrove_swamp"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "river"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "frozen_river"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "beach"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_beach"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "stony_shore"),

    // Taiga & cool forests
    ResourceLocation.fromNamespaceAndPath("minecraft", "taiga"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_taiga"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_pine_taiga"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_spruce_taiga"),

    // Savanna (grassland-ish)
    ResourceLocation.fromNamespaceAndPath("minecraft", "savanna"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "savanna_plateau"),

    // Windswept (forested/grass, not the gravel badlands)
    ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_forest"),
    ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_hills")
);
    /**
     * Common initialization.
     *
     * Steps:
     * 1) Initialize entity, sound, and item registries.
     * 2) Register natural spawns across selected biomes using {@link BiomeModifications}.
     *
     * The spawn weights slightly increase near water biomes to bias ducks toward lakes/rivers.
     */
    public static void init() {
        // Write common init code here.
        LOGGER.info("QuackMod initialized!");
        
        QuackEntityTypes.init(); // Initialize entities (duck)
        QuackedSounds.init(); // Initialize sounds (duck)
        QuackyModItems.init(); // Initialize items
      
        // === Natural spawns (Architectury common) ===
        // Plains, Swamp, River; weight 12; group 3–5 (biomes also mentioned elsewhere. one is for fabric other is neoforge)
        BiomeModifications.addProperties((ctx, props) -> {
            var keyOpt = ctx.getKey();
            if (keyOpt.isEmpty()) return;
            var key = keyOpt.get();

            if (!DUCK_BIOMES.contains(key)) return;

            // heavier in water-heavy biomes so ducks tend to spawn at lakes/rivers
            boolean veryWet =
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "river")) ||
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "frozen_river")) ||
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "swamp")) ||
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "mangrove_swamp")) ||
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "beach")) ||
                key.equals(ResourceLocation.fromNamespaceAndPath("minecraft", "stony_shore"));

            // SPAWN RATES 
            int weight = veryWet ? 6 : 3; // double weight near water

            props.getSpawnProperties().addSpawn(
                MobCategory.CREATURE,
                new MobSpawnSettings.SpawnerData(
                    mc.quackedducks.entities.QuackEntityTypes.DUCK.get(),
                    3, 5 // min, max group size
                ),
                weight
            );
        });

    }
}
