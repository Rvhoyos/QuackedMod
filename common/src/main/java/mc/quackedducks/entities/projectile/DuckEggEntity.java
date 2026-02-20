package mc.quackedducks.entities.projectile;

import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;

/**
 * Throwable duck egg projectile.
 *
 * Behavior:
 * - Uses duck egg item as the held/visual item.
 * - On impact (server-side), rolls vanilla-like hatch odds:
 * 1/8 chance to hatch; if hatching, 1/32 chance to spawn 4 babies otherwise 1.
 * - Spawns {@code DuckEntity} babies at the impact position and discards the
 * projectile.
 * - Emits vanilla impact particles via entity event {@code (byte)3}.
 *
 * Notes:
 * - We intentionally do **not** call
 * {@link ThrowableItemProjectile#onHit(HitResult)} to avoid vanilla egg side
 * effects.
 * - Babies are created via a direct constructor call (see comment) to sidestep
 * overloaded {@code EntityType#create(...)} issues across mappings.
 */

public class DuckEggEntity extends ThrowableItemProjectile {
    /**
     * Vanilla-style ctor used by the game when deserializing/spawning the
     * projectile.
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
                QuackEntityTypes.DUCK_EGG_PROJECTILE,
                thrower,
                level,
                new net.minecraft.world.item.ItemStack(QuackyModItems.duckEggSupplier().get()));
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

        // handle entity hit specifically
        if (result.getType() == HitResult.Type.ENTITY) {
            this.onHitEntity((net.minecraft.world.phys.EntityHitResult) result);
        }
        if (!this.level().isClientSide) {
            hatchBabies();
            this.level().broadcastEntityEvent(this, (byte) 3); // impact particles
            this.discard();
        }
    }

    /*
     * Handle entity hit specifically (not block hit).
     */
    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult hit) {
        final net.minecraft.world.entity.Entity target = hit.getEntity();

        // server-side damage only
        if (!this.level().isClientSide) {
            final net.minecraft.server.level.ServerLevel server = (net.minecraft.server.level.ServerLevel) this.level();
            final net.minecraft.world.damagesource.DamageSource src = server.damageSources().thrown(this,
                    this.getOwner());

            // use the new 1.21+ side-split API
            target.hurtServer(server, src, 2.0F); // adjust damage as you like
        }

    }

    /**
     * Client-side handler for custom entity events.
     *
     * Purpose:
     * - Renders the egg impact effect when the server broadcasts (byte) 3.
     * - Spawns item particles that visually "crack" the egg on hit.
     *
     * Call flow:
     * - Server: level().broadcastEntityEvent(this, (byte) 3) in onHit(...)
     * - Client: Minecraft calls handleEntityEvent on the matching client entity
     *
     * Parameters:
     * - id: event identifier sent by the server
     *
     * Behavior:
     * - If id == 3:
     * - Emit 8 Item particles using this projectile's item stack
     * - Return without calling the superclass
     * - Otherwise:
     * - Defer to the superclass
     *
     * Notes:
     * - This method runs on the client. Do not spawn or modify server entities
     * here.
     * - Pair this with a sound played server-side in onHit(...) if you want audio.
     */

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            // client-side hatch/impact particles (egg cracks)
            final net.minecraft.world.item.ItemStack stack = this.getItem(); // <- use getItem()
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(
                        new net.minecraft.core.particles.ItemParticleOption(
                                net.minecraft.core.particles.ParticleTypes.ITEM, stack),
                        this.getX(), this.getY(), this.getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.08D,
                        (this.random.nextDouble() - 0.5D) * 0.08D,
                        (this.random.nextDouble() - 0.5D) * 0.08D);
            }
            return; // don’t fall through
        }
        super.handleEntityEvent(id);
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
        if (count <= 0)
            return;

        var server = (net.minecraft.server.level.ServerLevel) this.level();
        for (int i = 0; i < count; i++) {
            // Direct-construct the duck; don't use EntityType#create(...)
            var baby = new mc.quackedducks.entities.DuckEntity(
                    mc.quackedducks.entities.QuackEntityTypes.DUCK,
                    server);
            if (baby != null) {
                baby.setAge(-24000); // baby
                baby.setPos(this.getX(), this.getY(), this.getZ());
                baby.setYRot(this.getYRot()); // optional
                baby.setXRot(0.0F); // optional
                baby.setYHeadRot(baby.getYRot()); // optional
                baby.setYBodyRot(baby.getYRot()); // optional
                server.addFreshEntity(baby);
            }
        }
    }

}
