package mc.quackedducks.sound;

import mc.quackedducks.QuackMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound registry for the mod.
 *
 * Responsibilities:
 * - Defines and registers {@link SoundEvent} entries under this mod id.
 *
 * Lifecycle:
 * - Call {@link #init()} from common init (see {@code QuackMod.init()}).
 *
 * Data requirements:
 * - Each registered id must be declared in {@code assets/quack/sounds.json}
 * and backed by an OGG file under {@code assets/quack/sounds/}.
 */
public final class QuackedSounds {

    public static SoundEvent DUCK_AMBIENT;
    public static SoundEvent DUCK_HURT;
    public static SoundEvent DUCK_DEATH;

    /**
     * Registers all sound events.
     * Invoke once during common initialization.
     */
    public static void init() {
        DUCK_AMBIENT = register("entity.duck.ambient");
        DUCK_HURT = register("entity.duck.hurt");
        DUCK_DEATH = register("entity.duck.death");
    }

    private static SoundEvent register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
    }

    private QuackedSounds() {
    }
}
