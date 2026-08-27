package mc.quackedducks.config;

/**
 * The config fields that go over the network.
 *
 * GenericDucks and both config payloads implement this, which is why nothing has to
 * copy the seven fields by hand: apply() reads the interface and QuackNetwork.from()
 * writes it. Add a field here and both directions pick it up.
 *
 * maxHealth is deliberately absent. It is server-only.
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
