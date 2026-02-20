package mc.quackedducks.entities;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.projectile.DuckEggEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import mc.quackedducks.config.QuackConfig;

/**
 * Central entity type registry for the mod.
 * Registers the duck and its thrown-egg projectile in a loader-agnostic way
 * using vanilla {@link Registry#register}.
 */
public class QuackEntityTypes {
        /** The main duck entity type. */
        public static EntityType<DuckEntity> DUCK;
        /** The throwable egg projectile entity type. */
        public static EntityType<DuckEggEntity> DUCK_EGG_PROJECTILE;

        /**
         * Registers all mod entity types.
         *
         * Attribute binding is done per-platform in the entrypoints.
         */
        public static void init() {
                DUCK_EGG_PROJECTILE = Registry.register(
                                BuiltInRegistries.ENTITY_TYPE,
                                ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "duck_egg_projectile"),
                                EntityType.Builder.<DuckEggEntity>of(
                                                DuckEggEntity::new, MobCategory.MISC)
                                                .sized(0.25f, 0.50f)
                                                .clientTrackingRange(64)
                                                .updateInterval(10)
                                                .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                                                ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                                                "duck_egg_projectile"))));

                DUCK = Registry.register(
                                BuiltInRegistries.ENTITY_TYPE,
                                ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "duck"),
                                EntityType.Builder.of(DuckEntity::new, MobCategory.CREATURE)
                                                .sized(QuackConfig.get().genericDucks.duckWidth,
                                                                QuackConfig.get().genericDucks.duckHeight)
                                                .eyeHeight(QuackConfig.get().genericDucks.duckEyeHeight)
                                                .passengerAttachments(1.36875f)
                                                .clientTrackingRange(10)
                                                .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                                                ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                                                "duck"))));
        }
}
