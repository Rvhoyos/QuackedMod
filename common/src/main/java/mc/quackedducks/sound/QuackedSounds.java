package mc.quackedducks.sound;

import java.util.function.Supplier;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mc.quackedducks.QuackMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
/**
 * Sound registry for the mod.
 *
 * Responsibilities:
 * - Defines and registers {@link SoundEvent} entries under this mod id.
 * - Exposes stable {@link RegistrySupplier} handles used by entities (e.g., duck ambient/hurt/death).
 *
 * Lifecycle:
 * - Call {@link #init()} from common init (see {@code QuackMod.init()}).
 *
 * Data requirements:
 * - Each registered id must be declared in {@code assets/quack/sounds.json}
 *   and backed by an OGG file under {@code assets/quack/sounds/}.
 *   Example key: {@code "entity.duck.ambient"} → file path referenced by sounds.json.
 */
public final class QuackedSounds {
    /** Deferred register for all sound events in this mod. */
    private static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(QuackMod.MOD_ID, Registries.SOUND_EVENT);

    public static RegistrySupplier<SoundEvent> DUCK_AMBIENT;
    public static RegistrySupplier<SoundEvent> DUCK_HURT;
    public static RegistrySupplier<SoundEvent> DUCK_DEATH;

    /**
     * Registers all sound events and submits the deferred register.
     * Invoke once during common initialization.
     */
    public static void init() {
        DUCK_AMBIENT = register("entity.duck.ambient");
        DUCK_HURT    = register("entity.duck.hurt");
        DUCK_DEATH   = register("entity.duck.death");
        SOUNDS.register();
    }
    /**
     * Helper to register a variable-range {@link SoundEvent}.
     *
     * @param path registry path under this mod id (e.g., {@code entity.duck.ambient})
     * @return supplier handle for the registered sound event
     */
    private static RegistrySupplier<SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(QuackMod.MOD_ID, path);
        Supplier<SoundEvent> sup = () -> SoundEvent.createVariableRangeEvent(id);
        return SOUNDS.register(id, sup);
    }
    private QuackedSounds() {} //todo why again?
}
