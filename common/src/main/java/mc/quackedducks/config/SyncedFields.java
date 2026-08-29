package mc.quackedducks.config;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import mc.quackedducks.config.QuackConfig.GenericDucks;
import net.minecraft.nbt.CompoundTag;

/**
 * The config fields that go over the network, as one table.
 *
 * Both directions read this list, so a new setting is added in one place and nothing copies
 * fields by hand. The body is keyed rather than positional: a receiver ignores a key it does
 * not know, and a key the sender left out keeps the value the receiver already has. That is
 * what stops an added setting from kicking players who are still on an older jar.
 *
 * maxHealth is deliberately absent. It is server-only.
 */
public final class SyncedFields {
    /**
     * Bumped only when an existing key changes meaning. Adding a key never needs it, which is
     * the whole point of a keyed body. Nothing branches on it yet.
     */
    private static final int SCHEMA = 1;

    private SyncedFields() {
    }

    /** One networked setting: how it goes into a tag, and how it comes back out. */
    private record Field(BiConsumer<CompoundTag, GenericDucks> write, BiConsumer<CompoundTag, GenericDucks> read) {
    }

    private static final List<Field> FIELDS = List.of(
            ofFloat("duck_width", d -> d.duckWidth, (d, v) -> d.duckWidth = v),
            ofFloat("duck_height", d -> d.duckHeight, (d, v) -> d.duckHeight = v),
            ofFloat("baby_scale", d -> d.babyScale, (d, v) -> d.babyScale = v),
            ofDouble("movement_speed", d -> d.movementSpeed, (d, v) -> d.movementSpeed = v),
            ofInt("ambient_sound_interval", d -> d.ambientSoundInterval, (d, v) -> d.ambientSoundInterval = v),
            ofInt("migration_cooldown_ticks", d -> d.migrationCooldownTicks, (d, v) -> d.migrationCooldownTicks = v),
            ofInt("dab_chance", d -> d.dabChance, (d, v) -> d.dabChance = v));

    /** Everything a client needs, ready to send. */
    public static CompoundTag write(GenericDucks src) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        for (Field field : FIELDS) {
            field.write().accept(tag, src);
        }
        return tag;
    }

    /** Copies an incoming tag onto a config. Keys that are not in the tag are left as they were. */
    public static void read(CompoundTag tag, GenericDucks target) {
        for (Field field : FIELDS) {
            field.read().accept(tag, target);
        }
    }

    private static Field ofFloat(String key, Function<GenericDucks, Float> get, BiConsumer<GenericDucks, Float> set) {
        return new Field(
                (tag, src) -> tag.putFloat(key, get.apply(src)),
                (tag, target) -> set.accept(target, tag.getFloatOr(key, get.apply(target))));
    }

    private static Field ofDouble(String key, Function<GenericDucks, Double> get,
            BiConsumer<GenericDucks, Double> set) {
        return new Field(
                (tag, src) -> tag.putDouble(key, get.apply(src)),
                (tag, target) -> set.accept(target, tag.getDoubleOr(key, get.apply(target))));
    }

    private static Field ofInt(String key, Function<GenericDucks, Integer> get,
            BiConsumer<GenericDucks, Integer> set) {
        return new Field(
                (tag, src) -> tag.putInt(key, get.apply(src)),
                (tag, target) -> set.accept(target, tag.getIntOr(key, get.apply(target))));
    }
}
