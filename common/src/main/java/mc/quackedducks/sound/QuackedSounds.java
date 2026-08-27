package mc.quackedducks.sound;

import mc.quackedducks.QuackMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The duck's sound events.
 *
 * Every id registered here also needs an entry in assets/quack/sounds.json and an
 * OGG file under assets/quack/sounds/, or it plays silence.
 */
public final class QuackedSounds {

    public static SoundEvent DUCK_AMBIENT;
    public static SoundEvent DUCK_HURT;
    public static SoundEvent DUCK_DEATH;

    /** Registers the sounds. Called once from common init. */
    public static void init() {
        DUCK_AMBIENT = register("entity.duck.ambient");
        DUCK_HURT = register("entity.duck.hurt");
        DUCK_DEATH = register("entity.duck.death");
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(QuackMod.MOD_ID, path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
    }

    private QuackedSounds() {
    }
}
