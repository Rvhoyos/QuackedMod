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
import mc.quackedducks.config.QuackedConfig;
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
        //*Load config */
        final QuackedConfig config = QuackedConfig.get();
        final Set<ResourceLocation> duckBiomes = config.getDuckBiomes();
       
        for (String id : config.biomeAllowlist) {
           try { duckBiomes.add(ResourceLocation.parse(id)); } catch (Exception ignored) {}
        }

        // === Natural spawns (Architectury common) ===
        // === Natural spawns (Architectury common) ===
        BiomeModifications.addProperties((ctx, props) -> {
            var keyOpt = ctx.getKey();
            if (keyOpt.isEmpty()) return;
            var key = keyOpt.get();

            if (!duckBiomes.contains(key)) return;

            // use config wet-biome list (Set<ResourceLocation>)
            final var wetBiomes = config.getWetBiomes();
            final boolean veryWet = wetBiomes.contains(key);

            int weight = veryWet ? 24 : 12; // unchanged multiplier

            props.getSpawnProperties().addSpawn(
                MobCategory.CREATURE,
                new MobSpawnSettings.SpawnerData(
                    mc.quackedducks.entities.QuackEntityTypes.DUCK.get(),
                    3, 5
                ),
                weight
            );
        });


    }
}
