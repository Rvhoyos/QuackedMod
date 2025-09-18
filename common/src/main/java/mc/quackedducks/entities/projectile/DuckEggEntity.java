package mc.quackedducks.entities.projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
/**
 * Throwable duck egg projectile.
 *
 * Behavior:
 * - Uses duck egg item as the held/visual item.
 * - On impact (server-side), rolls vanilla-like hatch odds:
 *   1/8 chance to hatch; if hatching, 1/32 chance to spawn 4 babies otherwise 1.
 * - Spawns {@code DuckEntity} babies at the impact position and discards the projectile.
 * - Emits vanilla impact particles via entity event {@code (byte)3}.
 *
 * Notes:
 * - We intentionally do **not** call {@link ThrowableItemProjectile#onHit(HitResult)} to avoid vanilla egg side effects.
 * - Babies are created via a direct constructor call (see comment) to sidestep overloaded {@code EntityType#create(...)} issues across mappings.
 */

public class DuckEggEntity extends ThrowableItemProjectile {
    /**
     * Vanilla-style ctor used by the game when deserializing/spawning the projectile.
     */
    public DuckEggEntity(EntityType<? extends DuckEggEntity> type,
                         Level level) {
        super(type, level);
    }
    /**
     * Convenience ctor for when a player throws the egg.
     */
    public DuckEggEntity(Level level,
                         LivingEntity thrower) {
        super(
            QuackEntityTypes.DUCK_EGG_PROJECTILE.get(),
            thrower,
            level,
            new net.minecraft.world.item.ItemStack(QuackyModItems.duckEggSupplier().get())
        );
    }

    /**
     * Returns the item this projectile visually represents .
     */
    @Override
    protected net.minecraft.world.item.Item getDefaultItem() {
        return mc.quackedducks.items.QuackyModItems.duckEggSupplier().get().asItem();
    }
    /**
     * Handle impact:
     * - server: attempt hatch, send particles, discard
     * - client: no-op
     */
    @Override
    protected void onHit(HitResult result) {
    // no super; we fully handle impact
    if (!this.level().isClientSide) {
        hatchBabies();
        //TODO: do damage to entities? or do knock back only?
        this.level().broadcastEntityEvent(this, (byte)3); // impact particles (doesnt work)
        this.discard();
    }
}

     /**
     * Vanilla-like hatch logic (1/8; if hatch then 1/32 for quads).
     * Spawns babies at the projectile's position and marks them as babies.
     */
    private void hatchBabies() {
    // Vanilla-like odds: 1/8 for one baby; 1/32 for four
        int count = 0;
        if (this.random.nextInt(8) == 0) {
            count = (this.random.nextInt(32) == 0) ? 4 : 1;
        }
        if (count <= 0) return;

        var server = (net.minecraft.server.level.ServerLevel) this.level();
        for (int i = 0; i < count; i++) {
        // Direct-construct the duck; don't use EntityType#create(...)
        var baby = new mc.quackedducks.entities.DuckEntity(
            mc.quackedducks.entities.QuackEntityTypes.DUCK.get(),
            server
        );
        if (baby != null) {
                baby.setAge(-24000); // baby
                baby.setPos(this.getX(), this.getY(), this.getZ());
                baby.setYRot(this.getYRot());           // optional
                baby.setXRot(0.0F);                     // optional
                baby.setYHeadRot(baby.getYRot());       // optional
                baby.setYBodyRot(baby.getYRot());       // optional
                server.addFreshEntity(baby);
            }
        }
    }

}
