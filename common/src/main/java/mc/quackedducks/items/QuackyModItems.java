package mc.quackedducks.items;

import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.QuackEntityTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
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
    public static Item DUCK_SPAWN_EGG;
    public static Item DUCK_EGG;
    public static Item EMPTY_FOIE_GRAS_BOWL;

    public static void init() {
        DUCK_EGG = registerItem("duck_egg", new DuckEggItem(
                baseProperties("duck_egg")
                        .stacksTo(64)));

        DUCK_SPAWN_EGG = registerItem("duck_spawn_egg", new SpawnEggItem(
                QuackEntityTypes.DUCK,
                baseProperties("duck_spawn_egg")));

        EMPTY_FOIE_GRAS_BOWL = registerItem("empty_foie_gras_bowl", new Item(
                baseProperties("empty_foie_gras_bowl")));

        DUCK_MEAT = registerItem("duck_meat", new Item(
                baseProperties("duck_meat")
                        .food(new FoodProperties.Builder()
                                .nutrition(3)
                                .saturationModifier(0.3f)
                                .build())
                        .component(DataComponents.CONSUMABLE,
                                Consumable.builder()
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                new MobEffectInstance(MobEffects.HUNGER, 20 * 60, 0),
                                                0.90f))
                                        .build())
                        .stacksTo(64)));

        DUCK_FEATHER = registerItem("duck_feather", new Item(
                baseProperties("duck_feather")));

        DUCK_FEATHER_ARROW = registerItem("duck_feather_arrow", new ArrowItem(
                baseProperties("duck_feather_arrow")));

        COOKED_DUCK = registerItem("cooked_duck", new Item(
                baseProperties("cooked_duck")
                        .food(new FoodProperties.Builder()
                                .nutrition(4)
                                .saturationModifier(0.8f)
                                .build())
                        .stacksTo(64)));

        FOIE_GRAS = registerItem("foie_gras", new Item(
                baseProperties("foie_gras")
                        .food(new FoodProperties.Builder()
                                .nutrition(8)
                                .saturationModifier(0.8f)
                                .build())
                        .usingConvertsTo(QuackyModItems.EMPTY_FOIE_GRAS_BOWL)
                        .component(DataComponents.CONSUMABLE,
                                Consumable.builder()
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                new MobEffectInstance(MobEffects.REGENERATION, 200, 1),
                                                1.0f))
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 0),
                                                1.0f))
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                new MobEffectInstance(MobEffects.SATURATION, 20, 0),
                                                1.0f))
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

    private static Item registerItem(String name, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, name),
                item);
    }

    public static Item.Properties baseProperties(String name) {
        return new Item.Properties().setId(
                ResourceKey.create(Registries.ITEM,
                        ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, name)));
    }
}