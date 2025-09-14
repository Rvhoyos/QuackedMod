package mc.quackedducks.items;

import net.minecraft.world.InteractionResult;

import net.minecraft.world.item.Item;

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
    public net.minecraft.world.InteractionResult use(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {

        var stack = player.getItemInHand(hand);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        // Spawn and launch the projectile server-side only
        if (!level.isClientSide) {
            var proj = new mc.quackedducks.entities.projectile.DuckEggEntity(level, player);
            proj.setItem(stack.copyWithCount(1));
            proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(proj);
        }
        // Consume one egg unless in creative / instabuild
        if (!player.getAbilities().instabuild) stack.shrink(1);
        // Mirror vanilla pattern: SUCCESS client-side (so the hand animates),
        // CONSUME server-side (action handled
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;

    }
}
