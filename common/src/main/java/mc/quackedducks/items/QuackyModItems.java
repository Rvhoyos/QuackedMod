package mc.quackedducks.items;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.QuackEntityTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

/**
 * Central item registry for the mod.
 *
 * Uses vanilla {@link Registry#register} instead of Architectury's
 * DeferredRegister.
 * Creative tab additions are handled per-platform in the entrypoints.
 */
public class QuackyModItems {

        public static Item DUCK_MEAT;
        public static Item DUCK_FEATHER;
        public static Item DUCK_FEATHER_ARROW;
        public static Item COOKED_DUCK;
        public static Item FOIE_GRAS;
        public static Item EMPTY_FOIE_GRAS_BOWL;
        public static Item DUCK_EGG;
        public static Item DUCK_SPAWN_EGG;

        /**
         * Registers all mod items into the vanilla item registry.
         * Called from {@link mc.quackedducks.QuackMod#init()} on Fabric;
         * on NeoForge items are registered via DeferredRegisters in
         * {@link mc.quackedducks.neoforge.QuackModNeoForge} instead.
         */
        public static void init() {
                DUCK_MEAT = registerItem("duck_meat", new Item(
                                baseProperties("duck_meat")
                                                .food(new FoodProperties.Builder()
                                                                .nutrition(2)
                                                                .saturationModifier(0.3f)
                                                                .build())));

                DUCK_SPAWN_EGG = registerItem("duck_spawn_egg", new SpawnEggItem(
                                baseProperties("duck_spawn_egg")
                                                .component(DataComponents.ENTITY_DATA,
                                                                TypedEntityData.of(
                                                                                (EntityType<?>) QuackEntityTypes.DUCK,
                                                                                new CompoundTag()))));

                EMPTY_FOIE_GRAS_BOWL = registerItem("empty_foie_gras_bowl", new Item(
                                baseProperties("empty_foie_gras_bowl")));

                DUCK_FEATHER = registerItem("duck_feather", new Item(
                                baseProperties("duck_feather")));

                DUCK_FEATHER_ARROW = registerItem("duck_feather_arrow", new ArrowItem(
                                baseProperties("duck_feather_arrow")));

                DUCK_EGG = registerItem("duck_egg", new DuckEggItem(
                                baseProperties("duck_egg")
                                                .stacksTo(16)));

                COOKED_DUCK = registerItem("cooked_duck", new Item(
                                baseProperties("cooked_duck")
                                                .food(new FoodProperties.Builder()
                                                                .nutrition(6)
                                                                .saturationModifier(0.6f)
                                                                .build())));

                FOIE_GRAS = registerItem("foie_gras", new Item(
                                baseProperties("foie_gras")
                                                .food(new FoodProperties.Builder()
                                                                .nutrition(4)
                                                                .saturationModifier(0.4f)
                                                                .build())
                                                .component(DataComponents.CONSUMABLE,
                                                                Consumable.builder()
                                                                                .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                                                                new MobEffectInstance(
                                                                                                                MobEffects.REGENERATION,
                                                                                                                100,
                                                                                                                1)))
                                                                                .build())
                                                .stacksTo(64)));

                // TODO:
                // - Add a block that feeds ducks
                // - Add new duck leather/feather (armour or tool) material
                // - duck saddle
                // - duck shoots projectile
                // - GOOFY: make duck panic when player eats duck meat/foie gras nearby
                // - GOOFY: give saddled ducks ability fly and glide short distances
                // - GOOFY: Easter egg turret mode for saddled ducks
        }

        /**
         * Accessor for the duck egg as a supplier.
         */
        public static java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike> duckEggSupplier() {
                return () -> DUCK_EGG;
        }

        /** Registers {@code item} under the mod namespace with the given {@code name}. */
        private static Item registerItem(String name, Item item) {
                return Registry.register(
                                BuiltInRegistries.ITEM,
                                Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, name),
                                item);
        }

        /**
         * Creates a base {@link Item.Properties} with the mod-namespaced resource key
         * for the given item name. Used by both Fabric and NeoForge registrations.
         */
        public static Item.Properties baseProperties(String name) {
                return new Item.Properties().setId(
                                ResourceKey.create(Registries.ITEM,
                                                Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, name)));
        }
}