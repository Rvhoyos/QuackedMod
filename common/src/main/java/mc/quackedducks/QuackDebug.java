package mc.quackedducks;

/**
 * Central switch for verbose duck AI/animation logging.
 *
 * <p>Off by default so servers are not spammed with per-duck state transitions.
 * Enable at launch with the JVM flag {@code -Dquack.debug=true}.
 */
public final class QuackDebug {

    private QuackDebug() {}

    /** True when {@code -Dquack.debug=true} was passed on the command line. */
    public static final boolean ENABLED = Boolean.getBoolean("quack.debug");
}
