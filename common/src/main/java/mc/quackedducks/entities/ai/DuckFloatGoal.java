package mc.quackedducks.entities.ai;

import com.mojang.logging.LogUtils;
import mc.quackedducks.entities.DuckEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import org.slf4j.Logger;

/**
 * Diagnostic subclass of {@link net.minecraft.world.entity.ai.goal.FloatGoal} for ducks.
 *
 * <p>Confirmed behavior (from in-game logs): FloatGoal holds only {@code Flag.JUMP},
 * not {@code Flag.MOVE}. It therefore does <em>not</em> block
 * {@link DuckWaterAvoidingStrollGoal} or {@link FollowLeaderIfFreeGoal}.
 *
 * <p>Logs the flag set once on first activation, and logs each start/stop transition
 * so water-surfacing behavior can be traced.
 */
public class DuckFloatGoal extends FloatGoal {
    private static final Logger LOG = LogUtils.getLogger();
    private final DuckEntity duck;
    private boolean flagsLogged = false;

    public DuckFloatGoal(DuckEntity duck) {
        super(duck);
        this.duck = duck;
    }

    @Override
    public boolean canUse() {
        boolean result = super.canUse();
        // Log flags exactly once so we know what FloatGoal is competing for
        if (!flagsLogged && result) {
            flagsLogged = true;
            LOG.info("[FLOAT id={}] FLAGS={}", duck.getId(), getFlags());
        }
        return result;
    }

    @Override
    public void start() {
        LOG.info("[FLOAT id={}] START y={} inWater={} flags={}",
            duck.getId(), String.format("%.2f", duck.getY()),
            duck.isInWater(), getFlags());
        super.start();
    }

    @Override
    public void stop() {
        LOG.info("[FLOAT id={}] STOP y={} inWater={}",
            duck.getId(), String.format("%.2f", duck.getY()), duck.isInWater());
        super.stop();
    }
}
