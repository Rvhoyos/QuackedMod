package mc.quackedducks.entities;

import java.util.function.Supplier;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.quackedducks.QuackMod;
import mc.quackedducks.entities.projectile.DuckEggEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.minecraft.world.entity.Entity;

/**
 * Central entity type registry for the mod.
 * Registers the duck and its thrown-egg projectile in a loader-agnostic way.
 */
public class QuackEntityTypes {
    /** Deferred register for entity types. */
    private static DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(QuackMod.MOD_ID,Registries.ENTITY_TYPE);
    /** The main duck entity type. */
    public static RegistrySupplier<EntityType<DuckEntity>> DUCK;
    /** The throwable egg projectile entity type. */
    public static RegistrySupplier<EntityType<DuckEggEntity>> DUCK_EGG_PROJECTILE;
    /**
     * - Registers all mod entity types and binds their attributes.
     * 
     * - Called from {@link mc.quackedducks.QuackMod#init()} which is invoked by each
     * loader’s bootstrap:
     * 
     * - - NeoForge: {@link mc.quackedducks.neoforge.QuackModNeoForge#QuackModNeoForge() ctor}
     * - - Fabric: {@link mc.quackedducks.fabric.QuackModFabric#onInitialize() onInitialize()}
     *
     *
     * @see mc.quackedducks.QuackMod#init()
     * @see mc.quackedducks.neoforge.QuackModNeoForge#QuackModNeoForge()
     * @see mc.quackedducks.fabric.QuackModFabric#onInitialize()
     */
    public static void init(){
        DUCK_EGG_PROJECTILE = registerEntityType("duck_egg_projectile",
            () -> EntityType.Builder.<mc.quackedducks.entities.projectile.DuckEggEntity>of(
                    mc.quackedducks.entities.projectile.DuckEggEntity::new, MobCategory.MISC)
                .sized(0.25f, 0.25f)
                .clientTrackingRange(4)
                .updateInterval(10)
                .build(ResourceKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "duck_egg_projectile")))
    );


        DUCK = registerEntityType("duck", () -> EntityType.Builder.of(DuckEntity::new, MobCategory.CREATURE) //todo play with values and properties
                .sized(0.55f, 0.70f) // was 0.9f and 1.4f
                .eyeHeight(1.3f)
                .passengerAttachments(1.36875f)
                .clientTrackingRange(10)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, "duck"))));

        ENTITIES.register();
        EntityAttributeRegistry.register(DUCK, DuckEntity::createAttributes);
    }
    /** Helper for concise entity registration. */
    private static <T extends Entity> RegistrySupplier<EntityType<T>> registerEntityType(String name, Supplier<EntityType<T>> entityType){
        return ENTITIES.register(ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, name), entityType);
    }
}
