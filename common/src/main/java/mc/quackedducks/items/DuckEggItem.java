package mc.quackedducks.items;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;

/**
 * A duck egg you can throw. Right-click launches a DuckEggEntity.
 *
 * What happens where it lands is the projectile's business, not this class's.
 */
public class DuckEggItem extends Item {

    public DuckEggItem(Properties properties) {
        super(properties);
    }

    /** Throws one egg and takes it off the stack, unless the player is in creative. */
    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand) {

        var stack = player.getItemInHand(hand);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        // Vanilla egg throw sound. Null player so everyone nearby hears it.
        level.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.EGG_THROW,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.5F,
                0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
        // The projectile only exists server-side; the client just sees it tracked.
        if (!level.isClientSide()) {
            var proj = new mc.quackedducks.entities.projectile.DuckEggEntity(level, player, stack.copyWithCount(1));
            proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(proj);
        }
        // Creative keeps the egg.
        if (!player.getAbilities().instabuild)
            stack.shrink(1);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;

    }
}
