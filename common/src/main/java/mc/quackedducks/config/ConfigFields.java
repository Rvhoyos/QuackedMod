package mc.quackedducks.config;

/**
 * The subset of {@link QuackConfig.GenericDucks} fields that are carried over the
 * network and mirrored between server and client.
 *
 * <p>Three things implement it: {@link QuackConfig.GenericDucks} itself (the live
 * config and the config screen's draft), and both
 * {@link mc.quackedducks.network.QuackNetwork.SyncConfigPayload} and
 * {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload} (their record
 * accessors already match these names). That makes this interface the single shape
 * the field list is written in: {@link QuackConfig#apply(ConfigFields)} reads it,
 * {@link mc.quackedducks.network.QuackNetwork#from} writes it, and no caller has to
 * copy the fields by hand.
 *
 * <p>Fields not listed here ({@code maxHealth}) are server-only - see
 * {@link mc.quackedducks.entities.DuckEntity#updateFromConfig()}.
 */
public interface ConfigFields {
    float duckWidth();

    float duckHeight();

    float babyScale();

    double movementSpeed();

    int ambientSoundInterval();

    int migrationCooldownTicks();

    int dabChance();
}
