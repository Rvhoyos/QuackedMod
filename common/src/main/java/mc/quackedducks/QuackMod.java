package mc.quackedducks;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.sound.QuackedSounds;
import net.minecraft.resources.ResourceLocation;

/**
 * Common entrypoint and shared bootstrap.
 *
 * Responsibilities:
 * - Defines the mod id and logger.
 * - Initializes registries for entities, sounds, and items via their
 * {@code init()} methods.
 *
 * Biome spawns and creative tabs are handled per-platform in the loader
 * entrypoints.
 *
 * Lifecycle:
 * - This method is invoked from loader-specific entrypoints:
 * - Fabric: {@code mc.quackedducks.fabric.QuackModFabric#onInitialize()}
 * - NeoForge: {@code mc.quackedducks.neoforge.QuackModNeoForge} constructor
 */
public final class QuackMod {
    public static final String MOD_ID = "quack";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * Biomes that should naturally spawn ducks.
     * Used by platform entrypoints for biome modification.
     */
    public static final Set<ResourceLocation> DUCK_BIOMES = Set.of(
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

            // Windswept (forested/grass)
            ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_forest"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_hills"));

    /**
     * Set of water-heavy biomes for increased spawn weight.
     */
    public static final Set<ResourceLocation> WET_BIOMES = Set.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "river"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "frozen_river"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "swamp"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "mangrove_swamp"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "beach"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "stony_shore"));

    /**
     * Common initialization.
     *
     * Steps:
     * 1) Initialize entity, sound, and item registries (vanilla Registry.register).
     *
     * Biome spawns, attribute binding, and creative tabs are handled per-platform.
     */
    public static void init() {
        LOGGER.info("QuackMod initialized!");

        QuackEntityTypes.init();
        QuackedSounds.init();
        QuackyModItems.init();
    }
}
