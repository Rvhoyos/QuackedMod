package mc.quackedducks.neoforge;

import com.mojang.serialization.MapCodec;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.entities.QuackEntityTypes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Adds the duck to biome spawn lists on NeoForge, using the biomes named in the config.
 *
 * A biome that appears in both duckBiomes and wetBiomes gets two entries, so its
 * total spawn weight is the base plus the wet bonus.
 */
public class ConfigBiomeModifier implements BiomeModifier {

    public static final MapCodec<ConfigBiomeModifier> CODEC = MapCodec.unit(ConfigBiomeModifier::new);

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) {
            var config = QuackConfig.get();
            var id = biome.unwrapKey().get().identifier();

            // Generic biomes
            if (QuackConfig.getDuckBiomes().contains(id)) {
                builder.getMobSpawnSettings().addSpawn(MobCategory.CREATURE,
                        config.spawning.baseWeight,
                        new MobSpawnSettings.SpawnerData(QuackEntityTypes.DUCK, config.spawning.minGroupSize,
                                config.spawning.maxGroupSize));
            }

            // Wet biomes get a second entry on top of the one above
            if (QuackConfig.getWetBiomes().contains(id)) {
                builder.getMobSpawnSettings().addSpawn(MobCategory.CREATURE,
                        config.spawning.wetBiomeBonusWeight,
                        new MobSpawnSettings.SpawnerData(QuackEntityTypes.DUCK, config.spawning.minGroupSize,
                                config.spawning.maxGroupSize));
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
