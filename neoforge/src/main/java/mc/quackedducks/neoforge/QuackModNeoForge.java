package mc.quackedducks.neoforge;

import mc.quackedducks.QuackMod;
import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.entities.DuckEntity;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.entities.projectile.DuckEggEntity;
import mc.quackedducks.items.DuckEggItem;
import mc.quackedducks.items.QuackyModItems;
import mc.quackedducks.sound.QuackedSounds;

import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import mc.quackedducks.config.QuackConfig;
import mc.quackedducks.network.QuackNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.world.BiomeModifier;
import com.mojang.serialization.MapCodec;

/**
 * NeoForge entrypoint.
 *
 * Uses NeoForge's DeferredRegister for all registry operations since
 * vanilla Registry.register() throws "Registry is already frozen" on NeoForge.
 *
 * After registration, common static fields are back-filled so shared code
 * (DuckEntity, DuckEggEntity, etc.) can reference them normally.
 */
@Mod(QuackMod.MOD_ID)
public final class QuackModNeoForge {

        // --- Deferred Registers ---
        private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
                        QuackMod.MOD_ID);
        private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, QuackMod.MOD_ID);
        private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
                        QuackMod.MOD_ID);
        // NeoForge specialized registries
        private static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS = DeferredRegister
                        .create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
                                        QuackMod.MOD_ID);

        // --- Entity Types ---
        public static final DeferredHolder<EntityType<?>, EntityType<DuckEggEntity>> DUCK_EGG_PROJECTILE = ENTITIES
                        .register("duck_egg_projectile",
                                        () -> EntityType.Builder.<DuckEggEntity>of(DuckEggEntity::new, MobCategory.MISC)
                                                        .sized(0.25f, 0.50f)
                                                        .clientTrackingRange(64)
                                                        .updateInterval(10)
                                                        .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                                                        ResourceLocation.fromNamespaceAndPath(
                                                                                        QuackMod.MOD_ID,
                                                                                        "duck_egg_projectile"))));

        public static final DeferredHolder<EntityType<?>, EntityType<DuckEntity>> DUCK = ENTITIES.register("duck",
                        () -> EntityType.Builder.of(DuckEntity::new, MobCategory.CREATURE)
                                        .sized(0.75f, 0.95f)
                                        .eyeHeight(0.95f)
                                        .passengerAttachments(1.36875f)
                                        .clientTrackingRange(10)
                                        .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                                        ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                                        "duck"))));

        // --- Sounds ---
        public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_AMBIENT_SOUND = SOUNDS
                        .register("entity.duck.ambient", () -> {
                                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                "entity.duck.ambient");
                                return SoundEvent.createVariableRangeEvent(id);
                        });
        public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_HURT_SOUND = SOUNDS.register("entity.duck.hurt",
                        () -> {
                                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                "entity.duck.hurt");
                                return SoundEvent.createVariableRangeEvent(id);
                        });
        public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_DEATH_SOUND = SOUNDS.register(
                        "entity.duck.death",
                        () -> {
                                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID,
                                                "entity.duck.death");
                                return SoundEvent.createVariableRangeEvent(id);
                        });

        // --- Serializers ---
        public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfigBiomeModifier>> CONFIG_SPAWNS_SERIALIZER = BIOME_MODIFIER_SERIALIZERS
                        .register("config_spawns", () -> ConfigBiomeModifier.CODEC);

        // --- Items ---
        // --- Items ---
        public static final DeferredHolder<Item, Item> DUCK_EGG_ITEM = ITEMS.register("duck_egg", () -> new DuckEggItem(
                        QuackyModItems.baseProperties("duck_egg").stacksTo(64)));

        public static final DeferredHolder<Item, Item> DUCK_SPAWN_EGG_ITEM = ITEMS.register("duck_spawn_egg",
                        () -> new SpawnEggItem(
                                        DUCK.get(),
                                        QuackyModItems.baseProperties("duck_spawn_egg")));

        public static final DeferredHolder<Item, Item> EMPTY_FOIE_GRAS_BOWL_ITEM = ITEMS.register(
                        "empty_foie_gras_bowl",
                        () -> new Item(
                                        QuackyModItems.baseProperties("empty_foie_gras_bowl")));

        public static final DeferredHolder<Item, Item> DUCK_MEAT_ITEM = ITEMS.register("duck_meat", () -> new Item(
                        QuackyModItems.baseProperties("duck_meat")
                                        .food(new FoodProperties.Builder()
                                                        .nutrition(3)
                                                        .saturationModifier(0.3f)
                                                        .build())
                                        .component(DataComponents.CONSUMABLE,
                                                        Consumable.builder()
                                                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                                                        new MobEffectInstance(
                                                                                                        MobEffects.HUNGER,
                                                                                                        20 * 60, 0),
                                                                                        0.90f))
                                                                        .build())
                                        .stacksTo(64)));

        public static final DeferredHolder<Item, Item> DUCK_FEATHER_ITEM = ITEMS.register("duck_feather",
                        () -> new Item(
                                        QuackyModItems.baseProperties("duck_feather")));

        public static final DeferredHolder<Item, Item> DUCK_FEATHER_ARROW_ITEM = ITEMS.register("duck_feather_arrow",
                        () -> new ArrowItem(
                                        QuackyModItems.baseProperties("duck_feather_arrow")));

        public static final DeferredHolder<Item, Item> COOKED_DUCK_ITEM = ITEMS.register("cooked_duck", () -> new Item(
                        QuackyModItems.baseProperties("cooked_duck")
                                        .food(new FoodProperties.Builder()
                                                        .nutrition(4)
                                                        .saturationModifier(0.8f)
                                                        .build())
                                        .stacksTo(64)));

        public static final DeferredHolder<Item, Item> FOIE_GRAS_ITEM = ITEMS.register("foie_gras", () -> new Item(
                        QuackyModItems.baseProperties("foie_gras")
                                        .food(new FoodProperties.Builder()
                                                        .nutrition(8)
                                                        .saturationModifier(0.8f)
                                                        .build())
                                        .usingConvertsTo(EMPTY_FOIE_GRAS_BOWL_ITEM.get())
                                        .component(DataComponents.CONSUMABLE,
                                                        Consumable.builder()
                                                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                                                        new MobEffectInstance(
                                                                                                        MobEffects.REGENERATION,
                                                                                                        200, 1),
                                                                                        1.0f))
                                                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                                                        new MobEffectInstance(
                                                                                                        MobEffects.ABSORPTION,
                                                                                                        20 * 60, 0),
                                                                                        1.0f))
                                                                        .onConsume(new ApplyStatusEffectsConsumeEffect(
                                                                                        new MobEffectInstance(
                                                                                                        MobEffects.SATURATION,
                                                                                                        20, 0),
                                                                                        1.0f))
                                                                        .build())
                                        .stacksTo(64)));

        // --- Constructor ---
        public QuackModNeoForge(IEventBus modBus) {
                // Register deferred registers on the mod bus
                ENTITIES.register(modBus);
                ITEMS.register(modBus);
                SOUNDS.register(modBus);
                BIOME_MODIFIER_SERIALIZERS.register(modBus);

                // Log init (don't call QuackMod.init() — that uses vanilla Registry.register)
                QuackMod.LOGGER.info("QuackMod initialized!");

                modBus.addListener(QuackModNeoForge::onEntityAttributeCreation);
                modBus.addListener(QuackModNeoForge::onCreativeModeTabBuild);
                modBus.addListener(this::registerNetworking);

                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

                // Common init
                mc.quackedducks.config.QuackConfig.load(); // Ensure config is loaded on NeoForge side

                // GUI Opener Hook
                QuackMod.CONFIG_OPENER = (player) -> {
                        player.connection.send(new QuackNetwork.OpenConfigGuiPayload());
                };
        }

        private void onRegisterCommands(RegisterCommandsEvent event) {
                mc.quackedducks.command.QuackCommands.register(event.getDispatcher());
        }

        private void onPlayerLoggedIn(
                        net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
                if (event.getEntity() instanceof ServerPlayer player) {
                        player.connection.send(QuackNetwork.SyncConfigPayload.fromCurrent());
                }
        }

        private void registerNetworking(final RegisterPayloadHandlersEvent event) {
                final PayloadRegistrar registrar = event.registrar(QuackMod.MOD_ID).versioned("1.0");

                registrar.configurationToClient(QuackNetwork.SYNC_CONFIG, QuackNetwork.SyncConfigPayload.STREAM_CODEC,
                                (payload, context) -> {
                                        // Configuration sync during config phase
                                        var c = QuackConfig.get().genericDucks;
                                        c.duckWidth = payload.duckWidth();
                                        c.duckHeight = payload.duckHeight();
                                        c.movementSpeed = payload.movementSpeed();
                                        c.ambientSoundInterval = payload.ambientSoundInterval();
                                        QuackConfig.get().validate();
                                });

                registrar.playToClient(QuackNetwork.SYNC_CONFIG, QuackNetwork.SyncConfigPayload.STREAM_CODEC,
                                (payload, context) -> {
                                        if (context.flow().isClientbound()) {
                                                mc.quackedducks.neoforge.client.QuackNeoForgeClientNetworking
                                                                .handleSyncConfig(payload, context);
                                        }
                                });

                registrar.playToClient(QuackNetwork.OPEN_CONFIG_GUI, QuackNetwork.OpenConfigGuiPayload.STREAM_CODEC,
                                (payload, context) -> {
                                        if (context.flow().isClientbound()) {
                                                mc.quackedducks.neoforge.client.QuackNeoForgeClientNetworking
                                                                .handleOpenConfigGui(payload, context);
                                        }
                                });

                registrar.playToServer(QuackNetwork.UPDATE_CONFIG, QuackNetwork.UpdateConfigPayload.STREAM_CODEC,
                                (payload, context) -> {
                                        context.enqueueWork(() -> {
                                                var c = QuackConfig.get().genericDucks;
                                                c.duckWidth = payload.duckWidth();
                                                c.duckHeight = payload.duckHeight();
                                                c.movementSpeed = payload.movementSpeed();
                                                c.ambientSoundInterval = payload.ambientSoundInterval();
                                                QuackConfig.get().validate();
                                                QuackConfig.save();

                                                // Sync back to all clients
                                                for (ServerPlayer p : context.player().getServer().getPlayerList()
                                                                .getPlayers()) {
                                                        p.connection.send(QuackNetwork.SyncConfigPayload.fromCurrent());
                                                }

                                                // Update all existing ducks on server
                                                for (var level : context.player().getServer().getAllLevels()) {
                                                        for (var duck : level.getEntities(QuackEntityTypes.DUCK,
                                                                        e -> true)) {
                                                                duck.updateFromConfig();
                                                        }
                                                }
                                        });
                                });
        }

        /**
         * Back-fill common static fields after deferred registration resolves.
         * Called from entity attribute event which fires after registries are done.
         */
        private static void backfillCommonFields() {
                QuackEntityTypes.DUCK = DUCK.get();
                QuackEntityTypes.DUCK_EGG_PROJECTILE = DUCK_EGG_PROJECTILE.get();

                QuackedSounds.DUCK_AMBIENT = DUCK_AMBIENT_SOUND.get();
                QuackedSounds.DUCK_HURT = DUCK_HURT_SOUND.get();
                QuackedSounds.DUCK_DEATH = DUCK_DEATH_SOUND.get();

                QuackyModItems.DUCK_EGG = DUCK_EGG_ITEM.get();
                QuackyModItems.DUCK_SPAWN_EGG = DUCK_SPAWN_EGG_ITEM.get();
                QuackyModItems.EMPTY_FOIE_GRAS_BOWL = EMPTY_FOIE_GRAS_BOWL_ITEM.get();
                QuackyModItems.DUCK_MEAT = DUCK_MEAT_ITEM.get();
                QuackyModItems.DUCK_FEATHER = DUCK_FEATHER_ITEM.get();
                QuackyModItems.DUCK_FEATHER_ARROW = DUCK_FEATHER_ARROW_ITEM.get();
                QuackyModItems.COOKED_DUCK = COOKED_DUCK_ITEM.get();
                QuackyModItems.FOIE_GRAS = FOIE_GRAS_ITEM.get();
        }

        private static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
                backfillCommonFields();
                event.put(QuackEntityTypes.DUCK, DuckEntity.createAttributes().build());
        }

        private static void onCreativeModeTabBuild(BuildCreativeModeTabContentsEvent event) {
                if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
                        event.accept(DUCK_EGG_ITEM.get());
                        event.accept(EMPTY_FOIE_GRAS_BOWL_ITEM.get());
                        event.accept(DUCK_MEAT_ITEM.get());
                        event.accept(COOKED_DUCK_ITEM.get());
                        event.accept(FOIE_GRAS_ITEM.get());
                } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                        event.accept(DUCK_SPAWN_EGG_ITEM.get());
                } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                        event.accept(DUCK_FEATHER_ITEM.get());
                } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                        event.accept(DUCK_FEATHER_ARROW_ITEM.get());
                }
        }

        @EventBusSubscriber(modid = QuackMod.MOD_ID, value = Dist.CLIENT)
        public static final class ClientModEvents {
                @SubscribeEvent
                public static void onClientSetup(final FMLClientSetupEvent event) {
                        event.enqueueWork(() -> {
                                EntityRenderers.register(
                                                DUCK.get(),
                                                DuckRenderer::new);

                                EntityRenderers.register(
                                                DUCK_EGG_PROJECTILE.get(),
                                                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<mc.quackedducks.entities.projectile.DuckEggEntity>(
                                                                ctx, 1.0f, false));

                                // Hooks
                                QuackMod.PACKET_SENDER = (payload) -> {
                                        net.neoforged.neoforge.client.network.ClientPacketDistributor
                                                        .sendToServer(payload);
                                };
                        });
                }
        }
}
