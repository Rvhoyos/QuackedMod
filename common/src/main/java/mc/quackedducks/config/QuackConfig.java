package mc.quackedducks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.quackedducks.QuackMod;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON-backed singleton configuration for QuackedMod.
 *
 * <p>Loaded from {@code config/quack.json} on startup. If the file is missing
 * or the version doesn't match {@code CURRENT_VERSION}, defaults are written.
 * Call {@link #get()} anywhere to access the live instance; call {@link #load()}
 * to reload from disk (e.g. via {@code /quack reload}).
 *
 * <p>Two sub-objects are exposed:
 * <ul>
 *   <li>{@link GenericDucks} - per-duck physical and audio parameters</li>
 *   <li>{@link Spawning} - world-spawn weights, group sizes, and biome lists</li>
 * </ul>
 */
public class QuackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;
    private static final String CONFIG_FILE_NAME = "quack.json";

    // Singleton instance
    private static QuackConfig instance;

    /**
     * Parsed biome lists, cached because {@code ConfigBiomeModifier} asks for them once
     * per biome in the game on every world load. Cleared by {@link #load()}; nothing else
     * mutates the biome lists ({@link #apply(ConfigFields)} only touches {@link ConfigFields}).
     */
    private static Set<Identifier> duckBiomeCache;
    private static Set<Identifier> wetBiomeCache;

    // --- Config Fields ---
    public int configVersion = CURRENT_VERSION;
    public GenericDucks genericDucks = new GenericDucks();
    public Spawning spawning = new Spawning();

    /**
     * Physical and audio parameters applied to every duck entity.
     * All values are validated and clamped by {@link QuackConfig#validate()}.
     *
     * <p>Implements {@link ConfigFields} so the live config and the config screen's draft
     * can both be handed straight to {@link mc.quackedducks.network.QuackNetwork#from}
     * without copying the field list by hand. Gson serialises fields, not methods, so the
     * accessors do not affect the on-disk format.
     */
    public static class GenericDucks implements ConfigFields {
        public double maxHealth = 6.0;
        public double movementSpeed = 0.25;
        public float duckWidth = 0.75f;
        public float duckHeight = 0.95f;
        /** Multiplier applied to a baby duck's hitbox. 1.0 = same size as an adult. */
        public float babyScale = 0.5f;
        public int ambientSoundInterval = 120;
        /** Ticks between migration attempts (3600 = 3 min at 20 TPS). Min 20 for testing. */
        public int migrationCooldownTicks = 3600;
        /**
         * 1-in-N chance to dab when the idle variant window opens (~every 9–11 s).
         * 1 = always dab, 5 = 20% (default), 20 = 5% (rare).
         */
        public int dabChance = 5;

        public GenericDucks() {
        }

        /** Deep-enough copy for the config screen's draft - all fields are primitives. */
        public GenericDucks(GenericDucks src) {
            this.maxHealth = src.maxHealth;
            this.movementSpeed = src.movementSpeed;
            this.duckWidth = src.duckWidth;
            this.duckHeight = src.duckHeight;
            this.babyScale = src.babyScale;
            this.ambientSoundInterval = src.ambientSoundInterval;
            this.migrationCooldownTicks = src.migrationCooldownTicks;
            this.dabChance = src.dabChance;
        }

        // --- ConfigFields ---

        @Override
        public float duckWidth() {
            return duckWidth;
        }

        @Override
        public float duckHeight() {
            return duckHeight;
        }

        @Override
        public float babyScale() {
            return babyScale;
        }

        @Override
        public double movementSpeed() {
            return movementSpeed;
        }

        @Override
        public int ambientSoundInterval() {
            return ambientSoundInterval;
        }

        @Override
        public int migrationCooldownTicks() {
            return migrationCooldownTicks;
        }

        @Override
        public int dabChance() {
            return dabChance;
        }
    }

    /**
     * Clamps all {@link GenericDucks} values to safe ranges.
     * Called automatically after loading and after client-side config updates.
     */
    public void validate() {
        genericDucks.maxHealth = Math.clamp(genericDucks.maxHealth, 0.1, 1000.0);
        genericDucks.movementSpeed = Math.clamp(genericDucks.movementSpeed, 0.05, 1.0);
        genericDucks.duckWidth = Math.clamp(genericDucks.duckWidth, 0.1f, 5.0f);
        genericDucks.duckHeight = Math.clamp(genericDucks.duckHeight, 0.1f, 5.0f);
        genericDucks.babyScale = Math.clamp(genericDucks.babyScale, 0.1f, 1.0f);
        genericDucks.ambientSoundInterval = Math.max(20, genericDucks.ambientSoundInterval);
        genericDucks.migrationCooldownTicks = Math.clamp(genericDucks.migrationCooldownTicks, 20, 12000);
        genericDucks.dabChance = Math.clamp(genericDucks.dabChance, 1, 100);
    }

    /**
     * Copies the networked {@link ConfigFields} into {@link #genericDucks} and re-validates.
     * The single source of truth for applying an incoming config payload (in either
     * direction) - used by both loaders' client sync and server update handlers.
     */
    public void apply(ConfigFields src) {
        genericDucks.duckWidth = src.duckWidth();
        genericDucks.duckHeight = src.duckHeight();
        genericDucks.babyScale = src.babyScale();
        genericDucks.movementSpeed = src.movementSpeed();
        genericDucks.ambientSoundInterval = src.ambientSoundInterval();
        genericDucks.migrationCooldownTicks = src.migrationCooldownTicks();
        genericDucks.dabChance = src.dabChance();
        validate();
    }

    /**
     * World-spawn configuration for duck entities.
     * Wet biomes receive {@code baseWeight + wetBiomeBonusWeight} total spawn weight.
     */
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

    /**
     * Loads config from disk, creating defaults if missing or version-mismatched.
     * Always calls {@link #validate()} before returning.
     */
    public static void load() {
        duckBiomeCache = null;
        wetBiomeCache = null;

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

        // Version migration: preserve existing values, update version, re-save so any
        // fields added in the new version are written with their defaults.
        // Never wipe the config - users lose their customizations that way.
        if (instance.configVersion != CURRENT_VERSION) {
            QuackMod.LOGGER.info("Quack config migrated from version {} to {}; new fields use defaults.",
                    instance.configVersion, CURRENT_VERSION);
            instance.configVersion = CURRENT_VERSION;
            save();
        }
        instance.validate();
    }

    /** Writes the current instance to {@code config/quack.json}. */
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

    /**
     * Returns the singleton config instance, loading from disk on first call.
     *
     * @return the current {@link QuackConfig} instance
     */
    public static QuackConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    /**
     * Returns the set of biome {@link Identifier}s where ducks spawn normally.
     * Cached until the next {@link #load()}.
     */
    public static Set<Identifier> getDuckBiomes() {
        if (duckBiomeCache == null) {
            duckBiomeCache = parseBiomes(get().spawning.duckBiomes, "biome");
        }
        return duckBiomeCache;
    }

    /**
     * Returns the set of wet biome {@link Identifier}s that receive bonus spawn weight.
     * Cached until the next {@link #load()}.
     */
    public static Set<Identifier> getWetBiomes() {
        if (wetBiomeCache == null) {
            wetBiomeCache = parseBiomes(get().spawning.wetBiomes, "wet biome");
        }
        return wetBiomeCache;
    }

    /** Parses a configured biome id list, skipping and logging any malformed entry. */
    private static Set<Identifier> parseBiomes(List<String> raw, String label) {
        Set<Identifier> parsed = new LinkedHashSet<>();
        for (String s : raw) {
            try {
                parsed.add(Identifier.parse(s));
            } catch (Exception e) {
                QuackMod.LOGGER.error("Invalid {} in config: {}", label, s);
            }
        }
        return parsed;
    }
}
