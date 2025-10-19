package mc.quackedducks.items;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
/**
 * Duck egg item that throws a {@link mc.quackedducks.entities.projectile.DuckEggEntity}
 * when used.
 *
 * Behavior:
 * - Awards the {@link net.minecraft.stats.Stats#ITEM_USED} stat.
 * - Server-side: spawns a projectile entity, sets its displayed item to a single-egg copy,
 *   and launches it from the player's rotation (speed 1.5f, inaccuracy 1.0f).
 * - Consumes one egg unless the player has {@code instabuild} abilities (e.g., creative).
 * - Returns {@link InteractionResult#SUCCESS} on the client and
 *   {@link InteractionResult#CONSUME} on the server.
 *
 * Rendering and hatch behavior are handled by the projectile entity.
 */
public class DuckEggItem extends Item {

    public DuckEggItem(Properties properties) {
        super(properties);
    }
    
    /**
     * Handles right-click use of the item.
     *
     * @param level  world context
     * @param player using player
     * @param hand   hand used
     * @return client: {@link InteractionResult#SUCCESS}, server: {@link InteractionResult#CONSUME}
     */
    @Override
    public InteractionResult use(
            Level level,
            Player player,
            InteractionHand hand) {

        var stack = player.getItemInHand(hand);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        // Play vanilla egg throw sound (server broadcasts to nearby clients)
        level.playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.EGG_THROW,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.5F,
            0.4F / (player.getRandom().nextFloat() * 0.4F + 0.8F)
        );
        // Spawn and launch the projectile server-side only
        if (!level.isClientSide) {
            var proj = new mc.quackedducks.entities.projectile.DuckEggEntity(level, player);
            proj.setItem(stack.copyWithCount(1));
            proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(proj);
        }
        // Consume one egg unless in creative / instabuild
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;

    }
}
