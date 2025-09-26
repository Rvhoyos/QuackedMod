package mc.quackedducks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple JSON-backed config for QuackMod.
 * Creates config/quack.json with defaults if missing.
 * Exposes duck biome lists for spawn registration.
 */
public final class QuackedConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/quack.json");

    // persisted fields
    public List<String> biomeAllowlist;
    public List<String> wetBiomeList;

    // singleton 
    private static QuackedConfig INSTANCE;

    public static QuackedConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    private static QuackedConfig load() {
        try {
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    return GSON.fromJson(reader, QuackedConfig.class);
                }
            }
        } catch (Exception ignored) {}

        // write defaults if missing or invalid
        QuackedConfig def = defaults();
        save(def);
        return def;
    }

    private static void save(QuackedConfig cfg) {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(cfg, writer);
            }
        } catch (Exception ignored) {}
    }

    // === accessors ===
    public Set<ResourceLocation> getDuckBiomes() {
        Set<ResourceLocation> out = new HashSet<>();
        if (biomeAllowlist != null) {
            for (String id : biomeAllowlist) {
                try { out.add(ResourceLocation.parse(id)); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public Set<ResourceLocation> getWetBiomes() {
        Set<ResourceLocation> out = new HashSet<>();
        if (wetBiomeList != null) {
            for (String id : wetBiomeList) {
                try { out.add(ResourceLocation.parse(id)); } catch (Exception ignored) {}
            }
        }
        return out;
    }

    // === defaults (fill with your current biome sets) ===
    private static QuackedConfig defaults() {
        QuackedConfig cfg = new QuackedConfig();
        cfg.biomeAllowlist = List.of(
        "minecraft:plains", "minecraft:sunflower_plains",
        "minecraft:forest", "minecraft:flower_forest",
        "minecraft:birch_forest", "minecraft:dark_forest",
        "minecraft:old_growth_birch_forest", "minecraft:meadow",
        "minecraft:cherry_grove", "minecraft:swamp",
        "minecraft:mangrove_swamp", "minecraft:river",
        "minecraft:frozen_river", "minecraft:beach",
        "minecraft:snowy_beach", "minecraft:stony_shore",
        "minecraft:taiga", "minecraft:snowy_taiga",
        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
        "minecraft:savanna", "minecraft:savanna_plateau",
        "minecraft:windswept_forest", "minecraft:windswept_hills"
        );
        cfg.wetBiomeList = List.of(
            "minecraft:river", "minecraft:frozen_river",
            "minecraft:swamp", "minecraft:mangrove_swamp",
            "minecraft:beach", "minecraft:stony_shore"
        );
        return cfg;
    }
}
