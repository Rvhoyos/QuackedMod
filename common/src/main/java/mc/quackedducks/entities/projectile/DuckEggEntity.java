package mc.quackedducks.entities.projectile;

import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import mc.quackedducks.entities.QuackEntityTypes;
import mc.quackedducks.items.QuackyModItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * The thrown duck egg. Same hatch odds as a vanilla egg: 1 in 8 to hatch, and if it
 * does, 1 in 32 that four ducklings come out instead of one.
 *
 * onHit deliberately never calls super. We handle the impact start to finish, so
 * vanilla's egg side effects stay out of it.
 */
public class DuckEggEntity extends ThrowableItemProjectile {
    public DuckEggEntity(EntityType<? extends DuckEggEntity> type, Level level) {
        super(type, level);
    }

    public DuckEggEntity(Level level, LivingEntity thrower, net.minecraft.world.item.ItemStack stack) {
        super(mc.quackedducks.entities.QuackEntityTypes.DUCK_EGG_PROJECTILE, thrower, level, stack);
    }

    /** What the projectile looks like in flight. */
    @Override
    protected net.minecraft.world.item.Item getDefaultItem() {
        return mc.quackedducks.items.QuackyModItems.duckEggSupplier().get().asItem();
    }

    /** Roll for ducklings, crack the egg, remove the projectile. Server-side only. */
    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            this.onHitEntity((net.minecraft.world.phys.EntityHitResult) result);
        }
        if (!this.level().isClientSide()) {
            hatchBabies();
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

    /** An egg to the face is worth 2 damage. Hitting a block does nothing here. */
    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult hit) {
        final net.minecraft.world.entity.Entity target = hit.getEntity();

        if (!this.level().isClientSide()) {
            final net.minecraft.server.level.ServerLevel server = (net.minecraft.server.level.ServerLevel) this.level();
            target.hurt(this.damageSources().thrown(this, this.getOwner()), 2.0F);
        }

    }

    /** Event 3 is the one onHit broadcasts: scatter the shell particles. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            final net.minecraft.world.item.ItemStack stack = this.getItem();
            for (int i = 0; i < 8; ++i) {
                this.level().addParticle(
                        new net.minecraft.core.particles.ItemParticleOption(
                                net.minecraft.core.particles.ParticleTypes.ITEM, stack.getItem()),
                        this.getX(), this.getY(), this.getZ(),
                        (this.getRandom().nextDouble() - 0.5D) * 0.08D,
                        (this.getRandom().nextDouble() - 0.5D) * 0.08D,
                        (this.getRandom().nextDouble() - 0.5D) * 0.08D);
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Rolls the odds and puts any ducklings where the egg landed. */
    private void hatchBabies() {
        int count = 0;
        if (this.getRandom().nextInt(8) == 0) {
            count = (this.getRandom().nextInt(32) == 0) ? 4 : 1;
        }
        if (count <= 0)
            return;

        ServerLevel server = (ServerLevel) this.level();
        for (int i = 0; i < count; i++) {
            // Constructed directly. EntityType#create(...) is overloaded differently
            // across mappings and picking the wrong one is a compile break per version.
            var baby = new mc.quackedducks.entities.DuckEntity(
                    mc.quackedducks.entities.QuackEntityTypes.DUCK,
                    server);
            if (baby != null) {
                baby.setAge(-24000); // a full duckling's worth of growing up
                baby.setPos(this.getX(), this.getY(), this.getZ());
                baby.setYRot(this.getYRot());
                baby.setXRot(0.0F);
                baby.setYHeadRot(baby.getYRot());
                baby.setYBodyRot(baby.getYRot());
                server.addFreshEntity(baby);
            }
        }
    }

}
