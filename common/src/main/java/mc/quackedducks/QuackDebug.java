package mc.quackedducks;

/**
 * The one switch for duck AI and animation logging.
 *
 * Off unless you launch with -Dquack.debug=true, so a normal server never sees
 * per-duck state transitions in its log.
 */
public final class QuackDebug {

    private QuackDebug() {}

    /** True when -Dquack.debug=true was passed on the command line. */
    public static final boolean ENABLED = Boolean.getBoolean("quack.debug");
}
