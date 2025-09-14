package mc.quackedducks.neoforge;

import mc.quackedducks.QuackMod; 
import mc.quackedducks.client.renderer.DuckRenderer;
import mc.quackedducks.entities.QuackEntityTypes;

import net.minecraft.client.renderer.entity.EntityRenderers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
/**
 * NeoForge entrypoint.
 *
 * Responsibilities:
 * - Bridges NeoForge lifecycle to common init by calling QuackMod.init() in the constructor.
 * - Hosts a client-only event subscriber that registers renderers during FMLClientSetupEvent.
 *
 * Lifecycle:
 * - Constructed by NeoForge due to @Mod annotation with mod id.
 * - Client renderer registration runs on the main thread via event.enqueueWork.
 *
 * See also:
 * - Common bootstrap: {@link mc.quackedducks.QuackMod#init()}
 * - Fabric entrypoints: {@link mc.quackedducks.fabric.QuackModFabric},
 *   {@link mc.quackedducks.fabric.client.QuackModFabricClient}
 */
@Mod(QuackMod.MOD_ID) // or @Mod("quack")
public final class QuackModNeoForge {
    public QuackModNeoForge() {
        // your common init if any (TutorialMod.init()-style)
        QuackMod.init();
    }

    /**
     * Client-only registrations for NeoForge.
     *
     * Registers:
     * - Duck entity renderer.
     * - Duck egg projectile renderer (ThrownItemRenderer).
     */
    // === Client-only subscribers, tutorial style ===
    @EventBusSubscriber(modid = QuackMod.MOD_ID, value = Dist.CLIENT)
    public static final class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(final FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                // Duck renderer
                EntityRenderers.register(
                    QuackEntityTypes.DUCK.get(),
                    DuckRenderer::new
                );

                // Duck egg projectile renderer (Context, scale, fullBright)
                EntityRenderers.register(
                    QuackEntityTypes.DUCK_EGG_PROJECTILE.get(),
                    ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<
                        mc.quackedducks.entities.projectile.DuckEggEntity
                    >(ctx, 1.0f, false)
                );
            });
        }
    }
}
