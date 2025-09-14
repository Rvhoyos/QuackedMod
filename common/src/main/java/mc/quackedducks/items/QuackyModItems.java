package mc.quackedducks.items;
import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.QuackEntityTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.function.Supplier;
/**
 * Central item registry for the mod.
 *
 * Responsibilities:</p>
 * - Defines {@link DeferredRegister} for {@link Item} and registers all item instances.
 * - Exposes a small helper API for item registration and base property tagging.
 * - Provides {@link #duckEggSupplier()} so other code can reference the egg without
 *   depending on initialization order.
 *
 * Lifecycle:
 * Call {@link #init()} from common initialization (e.g., {@code QuackMod.init()}).
 */
public class QuackyModItems {
    // Define your mod items here.

    /** Deferred register bound to this mod id for {@link Item} entries. */
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(QuackMod.MOD_ID, Registries.ITEM); 
    // Registered item handles (populated during init)
    private static RegistrySupplier<Item> DUCK_MEAT;
    private static RegistrySupplier<Item> DUCK_FEATHER;
    private static RegistrySupplier<Item> DUCK_FEATHER_ARROW;
    private static RegistrySupplier<Item> COOKED_DUCK;
    private static RegistrySupplier<Item> FOIE_GRAS;
    private static RegistrySupplier<Item> DUCK_SPAWN_EGG;
    private static RegistrySupplier<Item> DUCK_EGG;
    private static RegistrySupplier<Item> EMPTY_FOIE_GRAS_BOWL;
     /**
     * Registers all items and then submits the deferred register.
     *
     *Invoke once during common init (after registries are available).
     * See: {@link mc.quackedducks.QuackMod#init()} for the call site.
     */
    public static void init(){
        //initialize items here
        DUCK_EGG = registerItem("duck_egg", () -> new DuckEggItem(
            baseProperties("duck_egg")
                .stacksTo(16)// add custom projectile entity here and also to arrow item for flight animation.
                .arch$tab(CreativeModeTabs.FOOD_AND_DRINKS)
        ));
        DUCK_SPAWN_EGG = registerItem("duck_spawn_egg", () -> new SpawnEggItem(
            QuackEntityTypes.DUCK.get(),
            baseProperties("duck_spawn_egg")
                .arch$tab(CreativeModeTabs.SPAWN_EGGS)
        ));
        EMPTY_FOIE_GRAS_BOWL = registerItem("empty_foie_gras_bowl", () -> new Item(
            baseProperties("empty_foie_gras_bowl")
                .arch$tab(CreativeModeTabs.FOOD_AND_DRINKS)
        ));


        DUCK_MEAT = registerItem("duck_meat", () -> new Item(
            baseProperties("duck_meat")
                .food(new FoodProperties.Builder()
                        .nutrition(3)                 // hunger (×0.5)
                        .saturationModifier(0.3f)     // saturation factor                 
                        .build())
                .component(DataComponents.CONSUMABLE,
                    Consumable.builder()
                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                            new MobEffectInstance(MobEffects.HUNGER, 20 * 60, 0), // 60s, level 0
                            0.90f                                                // 90% chance
                        ))
                        .build())
                .stacksTo(16)
                .arch$tab(CreativeModeTabs.FOOD_AND_DRINKS)));

        DUCK_FEATHER = registerItem("duck_feather", () -> new Item(baseProperties("duck_feather").arch$tab(CreativeModeTabs.INGREDIENTS)));
        
        DUCK_FEATHER_ARROW = registerItem("duck_feather_arrow",
            () -> new ArrowItem(baseProperties("duck_feather_arrow")
                .arch$tab(CreativeModeTabs.COMBAT)));
        
        COOKED_DUCK = registerItem("cooked_duck", () -> new Item(
            baseProperties("cooked_duck")
                .food(new FoodProperties.Builder()
                        .nutrition(4)                 
                        .saturationModifier(0.8f)     
                        .build())
                .stacksTo(16)            
            .arch$tab(CreativeModeTabs.FOOD_AND_DRINKS)));
        
        FOIE_GRAS = registerItem("foie_gras", () -> new Item(
            baseProperties("foie_gras")
                .food(new FoodProperties.Builder()
                        .nutrition(8)
                        .saturationModifier(0.8f)
                        .build())
                .usingConvertsTo(QuackyModItems.EMPTY_FOIE_GRAS_BOWL.get())
                .component(DataComponents.CONSUMABLE,
                    Consumable.builder()
                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                            // Regeneration II (10s)
                            new MobEffectInstance(MobEffects.REGENERATION, 200, 1),
                            1.0f
                        ))
                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                            // Absorption I (1 min)
                            new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 0),
                            1.0f
                        ))
                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                            // Saturation (tiny top-up)
                            new MobEffectInstance(MobEffects.SATURATION, 20, 0),
                            1.0f
                        ))
                        .build()
                )
                .stacksTo(1)
                .arch$tab(CreativeModeTabs.FOOD_AND_DRINKS)
        ));


        // TODO: (Morning)
        //
        //- Arrow and egg projectile entities with flight animations
        //- Add Drops/loot: entity loot table (feather/meat/cooked variants), balance numbers.
        //-
        //------------------------------------------------------------------------ THURS_NIGHT -> ?
        //- Add foie gras food item with special effects (strength? speed? regeneration?)
        //- - add empty foie gras bowl item that is returned on eating foie gras and make craft and not a common one for other mods maybe make it take a duck item to craft too.
        // 
        //- Add a block that feeds ducks
        //- Add new duck leather/feather (armour or tool) material
        //- - could be extension of existing tool item with quacky abilities. 
        //- Add functional duck house to store and release ducks?
        //- - - see what's possible with existing villager mechanics
        //- - - - maybe add new villager profession that breeds ducks?
        //
        //--------- FRI -> ?
        //- duck saddle? lol
        //- duck shoots projectile?
        //- GOOFY: make duck panic when player eats duck meat/foie gras nearby
        //- GOOFY: give saddled ducks ability fly and glide short distances
        //- GOOFY: Make saddled ducks have a "easter egg" / secret shortcut to enter turret mode and user can shoot projectiles from the duck and aimed with their mouse (or aimed with orientation of the duck).  
        //--------
    
        ITEMS.register();
    }

    /**
     * Helper to register an item under this mod id.
     *
     * @param name registry path under {@link QuackMod#MOD_ID}
     * @param item item factory
     * @return {@link RegistrySupplier} handle for later access
     */
    private static RegistrySupplier<Item> registerItem(String name, Supplier<Item> item) {
        return ITEMS.register(ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, name), item);
    }
     /**
     * Common base properties for items in this mod, including a stable registry id.
     *
     * @param name registry path to encode in the {@link Item.Properties}
     * @return properties instance tagged with this mod's id/path
     */    public static Item.Properties baseProperties(String name){
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, name)));
    }
    /**
     * Accessor for the duck egg as a supplier. Useful where initialization order
     * or loader boundaries require deferred lookup.
     *
     * @return supplier that yields the registered duck egg item
     */public static java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike> duckEggSupplier() {
    return DUCK_EGG; // RegistrySupplier<Item> is a Supplier<? extends ItemLike>
}

}