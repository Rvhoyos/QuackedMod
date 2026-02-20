package mc.quackedducks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.quackedducks.QuackMod;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QuackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;
    private static final String CONFIG_FILE_NAME = "quack.json";

    // Singleton instance
    private static QuackConfig instance;

    // --- Config Fields ---
    public int configVersion = CURRENT_VERSION;
    public GenericDucks genericDucks = new GenericDucks();
    public Spawning spawning = new Spawning();

    public static class GenericDucks {
        public double maxHealth = 6.0;
        public double movementSpeed = 0.25;
    }

    public static class Spawning {
        public int baseWeight = 3;
        public int wetBiomeBonusWeight = 3;
        public int minGroupSize = 1;
        public int maxGroupSize = 1;

        public List<String> duckBiomes = new ArrayList<>(List.of(
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest",
                "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:old_growth_birch_forest",
                "minecraft:meadow",
                "minecraft:cherry_grove", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river",
                "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore",
                "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga",
                "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_forest",
                "minecraft:windswept_hills"));

        public List<String> wetBiomes = new ArrayList<>(List.of(
                "minecraft:river", "minecraft:frozen_river", "minecraft:swamp", "minecraft:mangrove_swamp",
                "minecraft:beach", "minecraft:stony_shore"));
    }

    // --- Loading / Saving ---

    public static void load() {
        File configFile = new File("config", CONFIG_FILE_NAME); // Helper assumes 'config' dir usually exists or is
                                                                // relative to run dir

        if (!configFile.exists()) {
            // Create default
            instance = new QuackConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            instance = GSON.fromJson(reader, QuackConfig.class);
        } catch (IOException e) {
            QuackMod.LOGGER.error("Failed to load generic quack config", e);
            instance = new QuackConfig(); // Fallback
        }

        // Version check
        if (instance.configVersion != CURRENT_VERSION) {
            QuackMod.LOGGER.info("Quack config version mismatch (current: " + CURRENT_VERSION + ", file: "
                    + instance.configVersion + "). Resetting to defaults.");
            instance = new QuackConfig();
            save();
        }
    }

    public static void save() {
        File configDir = new File("config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File configFile = new File(configDir, CONFIG_FILE_NAME);
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            QuackMod.LOGGER.error("Failed to save generic quack config", e);
        }
    }

    public static QuackConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    // Helper to get ResourceLocations from strings
    public static List<ResourceLocation> getDuckBiomes() {
        List<ResourceLocation> list = new ArrayList<>();
        for (String s : get().spawning.duckBiomes) {
            try {
                list.add(ResourceLocation.parse(s));
            } catch (Exception e) {
                QuackMod.LOGGER.error("Invalid biome in config: " + s);
            }
        }
        return list;
    }

    public static List<ResourceLocation> getWetBiomes() {
        List<ResourceLocation> list = new ArrayList<>();
        for (String s : get().spawning.wetBiomes) {
            try {
                list.add(ResourceLocation.parse(s));
            } catch (Exception e) {
                QuackMod.LOGGER.error("Invalid wet biome in config: " + s);
            }
        }
        return list;
    }
}
