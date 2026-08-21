package mc.quackedducks.config;

/**
 * The subset of {@link QuackConfig.GenericDucks} fields that are carried over the
 * network and mirrored between server and client.
 *
 * <p>Both {@link mc.quackedducks.network.QuackNetwork.SyncConfigPayload} and
 * {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload} implement this
 * interface (their record accessors already match these names), so a single
 * {@link QuackConfig#apply(ConfigFields)} is the one place that copies the field
 * list into the live config - regardless of which direction the payload travelled.
 */
public interface ConfigFields {
    float duckWidth();

    float duckHeight();

    double movementSpeed();

    int ambientSoundInterval();

    int migrationCooldownTicks();

    int dabChance();
}
