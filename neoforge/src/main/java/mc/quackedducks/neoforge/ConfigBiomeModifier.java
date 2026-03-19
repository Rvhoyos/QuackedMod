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
 * NeoForge {@link BiomeModifier} that injects duck spawn entries from config.
 *
 * <p>During the {@link Phase#ADD} phase, ducks are added to every biome listed
 * in {@link mc.quackedducks.config.QuackConfig.Spawning#duckBiomes} at
 * {@code baseWeight}. Biomes that also appear in {@code wetBiomes} receive a
 * second entry at {@code wetBiomeBonusWeight}, effectively raising their total
 * spawn weight.
 *
 * <p>The codec is registered as a deferred biome-modifier serializer in
 * {@link QuackModNeoForge}.
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

            // Wet biomes (bonus weight)
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
