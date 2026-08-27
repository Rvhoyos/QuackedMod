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
 * The mod's config, read from and written to config/quack.json.
 *
 * get() hands out the live instance and loads the file on first use. load() re-reads
 * it, which is what /quack reload does.
 *
 * Two halves: GenericDucks is what a duck is like, Spawning is where they come from.
 * The duck half can change while the server runs. The spawning half is read at world
 * load, so it needs a restart.
 */
public class QuackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_VERSION = 1;
    private static final String CONFIG_FILE_NAME = "quack.json";

    private static QuackConfig instance;

    /**
     * The biome lists, already parsed. Worth caching because ConfigBiomeModifier asks
     * for them once per biome in the game, every world load.
     *
     * Only load() clears these. Nothing else can invalidate them, since apply() never
     * touches the biome lists.
     */
    private static Set<Identifier> duckBiomeCache;
    private static Set<Identifier> wetBiomeCache;

    public int configVersion = CURRENT_VERSION;
    public GenericDucks genericDucks = new GenericDucks();
    public Spawning spawning = new Spawning();

    /**
     * What every duck is like: size, speed, health, how often it quacks.
     *
     * It implements ConfigFields so both the live config and the screen's draft can be
     * handed straight to the network layer. The accessors that come with that do not
     * change the JSON, because Gson serialises fields and ignores methods.
     */
    public static class GenericDucks implements ConfigFields {
        public double maxHealth = 6.0;
        public double movementSpeed = 0.25;
        public float duckWidth = 0.75f;
        public float duckHeight = 0.95f;
        /** Baby hitbox as a fraction of the adult one. 1.0 means no difference. */
        public float babyScale = 0.5f;
        public int ambientSoundInterval = 120;
        /** Ticks between migration attempts. 3600 is three minutes; 20 is the floor, for testing. */
        public int migrationCooldownTicks = 3600;
        /**
         * A 1-in-N roll for a dab, taken whenever the idle animation comes back around.
         * 1 dabs every time, 5 is the default, 20 is rare.
         */
        public int dabChance = 5;

        public GenericDucks() {
        }

        /** Copy constructor for the screen's draft. Every field is a primitive, so this is enough. */
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

    /** Clamps everything into a range the game can survive. Runs after every load and apply. */
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
     * The one place a config payload gets copied into the live config, whichever
     * direction it arrived from.
     *
     * Note what is missing: maxHealth. It is not a networked field, and a client that
     * wrote it here would overwrite the server's value with whatever is in its own file.
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
     * Where ducks spawn and how many at a time.
     * A biome on both lists ends up at baseWeight + wetBiomeBonusWeight.
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

    /** Reads the file, writing defaults first if there is no file yet. */
    public static void load() {
        duckBiomeCache = null;
        wetBiomeCache = null;

        File configFile = new File("config", CONFIG_FILE_NAME);

        if (!configFile.exists()) {
            instance = new QuackConfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            instance = GSON.fromJson(reader, QuackConfig.class);
        } catch (IOException e) {
            QuackMod.LOGGER.error("Failed to load generic quack config", e);
            instance = new QuackConfig();
        }

        // On a version bump, keep what the user set and re-save so fields added in the
        // new version land in the file with their defaults. Never wipe the config.
        if (instance.configVersion != CURRENT_VERSION) {
            QuackMod.LOGGER.info("Quack config migrated from version {} to {}; new fields use defaults.",
                    instance.configVersion, CURRENT_VERSION);
            instance.configVersion = CURRENT_VERSION;
            save();
        }
        instance.validate();
    }

    /** Writes the live config back to config/quack.json. */
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

    /** The live config. Loads from disk the first time anyone asks. */
    public static QuackConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    /** Biomes ducks spawn in. Parsed once, then cached until the next load(). */
    public static Set<Identifier> getDuckBiomes() {
        if (duckBiomeCache == null) {
            duckBiomeCache = parseBiomes(get().spawning.duckBiomes, "biome");
        }
        return duckBiomeCache;
    }

    /** Biomes that get the bonus weight on top. Cached the same way. */
    public static Set<Identifier> getWetBiomes() {
        if (wetBiomeCache == null) {
            wetBiomeCache = parseBiomes(get().spawning.wetBiomes, "wet biome");
        }
        return wetBiomeCache;
    }

    /** Turns the configured strings into ids, logging and skipping anything malformed. */
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
