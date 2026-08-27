package mc.quackedducks.entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * The duck.
 *
 * Tames on seeds, breeds, swims, keeps away from anything that would eat it, and
 * strings itself into a flock: every duck either leads or follows one other duck.
 * All of its movement comes from DuckBrainGoal; the rest of the AI here is vanilla goals.
 */
public class DuckEntity extends TamableAnimal implements GeoEntity {
    @Nullable
    private DuckEntity following;       // the duck ahead of me
    @Nullable
    private DuckEntity followedByLeft;  // any duck can carry one follower here
    @Nullable
    private DuckEntity followedByRight; // leaders carry a second, which is what makes the V

    private GroundPathNavigation groundNav;
    private FlyingPathNavigation flyingNav;
    private net.minecraft.world.entity.ai.control.MoveControl groundMoveControl;
    private FlyingMoveControl flyingMoveControl;
    private boolean inFlyingMode = false;

    // Sent to the client so it knows which animation to play. Flying is not in here:
    // the client works that out from onGround() on its own.
    private static final EntityDataAccessor<Byte> DATA_ANIM_HINT =
            SynchedEntityData.defineId(DuckEntity.class, EntityDataSerializers.BYTE);
    private static final byte HINT_IDLE  = 0;
    private static final byte HINT_WALK  = 1;
    private static final byte HINT_PANIC = 2;

    private final AnimatableInstanceCache cache = com.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE       = RawAnimation.begin().thenLoop("animation.duck.idle");
    private static final RawAnimation WALK       = RawAnimation.begin().thenLoop("animation.duck.walk");
    private static final RawAnimation PANIC_ANIM = RawAnimation.begin().thenLoop("animation.duck.panic");
    private static final RawAnimation FLY        = RawAnimation.begin().thenLoop("animation.duck.fly");
    private static final RawAnimation DAB        = RawAnimation.begin().thenPlay("animation.duck.dab");

    // How long this duck has led without interruption. A tamed duck will not start
    // following its owner until this is up, so it does not abandon a flock mid-shuffle.
    private int headStableTicks = 0;
    private static final int HEAD_STABLE_REQUIRED = 120; // six seconds

    private mc.quackedducks.entities.ai.DuckBrainGoal brainGoal;

    private static final Logger LOG = LogUtils.getLogger();
    /** Same flag as QuackDebug.ENABLED. Off unless -Dquack.debug=true is set. */
    private static final boolean DEBUG_DUCKS = mc.quackedducks.QuackDebug.ENABLED;

    /** Which side of the leader a follower sits on. */
    public enum FollowerSlot { LEFT, RIGHT }

    private int idleVariantCooldown = 10;
    private int oneShotLockTicks = 0;
    private RawAnimation currentOneShot = null;
    // The controller below is not called exactly once per tick, so it remembers the last
    // tick it saw and ages its timers by the difference.
    private int lastTickSeen = -1;

    /** A quarter again as fast, so the leader can actually pull away and be followed. */
    private static final Identifier LEADER_SPEED_ID = Identifier.fromNamespaceAndPath("quack", "leader_speed");
    private static final AttributeModifier LEADER_SPEED_MODIFIER =
            new AttributeModifier(LEADER_SPEED_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    // TODO: add breadcrumb item.
    private static final Ingredient DUCK_FOOD = Ingredient.of(
            Items.WHEAT_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.TORCHFLOWER_SEEDS);

    public DuckEntity(EntityType<? extends DuckEntity> type, Level level) {
        super(type, level);
        // Mob's constructor has already set moveControl, so this grabs the ground one
        // before we replace it. Both are kept for the rest of the duck's life.
        this.groundMoveControl = this.moveControl;
        // hoversInPlace, so a flying duck holds its altitude between waypoints instead
        // of sagging. Coming down is stopFlying()'s job and nothing else's.
        this.flyingMoveControl = new FlyingMoveControl(this, 10, true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANIM_HINT, HINT_IDLE);
    }

    /**
     * Builds both navigators up front so flying is a swap rather than a rebuild.
     * Ground is the default; the flying one is live only while inFlyingMode.
     *
     * setRequiredPathLength is deliberately not called here. Bees use it, but a migrating
     * duck covers 80 to 140 blocks and that call would cut the path short.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        this.groundNav = new GroundPathNavigation(this, level);
        this.flyingNav = new FlyingPathNavigation(this, level);
        flyingNav.setCanOpenDoors(false);
        flyingNav.setCanFloat(true);
        return groundNav;
    }

    /** Seeds. The same list TemptGoal follows and taming accepts. */
    @Override
    public boolean isFood(ItemStack stack) {
        return DUCK_FOOD.test(stack);
    }

    /** A duckling. Built through the entity type so both loaders get the same thing. */
    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return mc.quackedducks.entities.QuackEntityTypes.DUCK.create(
                level,
                net.minecraft.world.entity.EntitySpawnReason.BREEDING);
    }

    /** True when there is no room left behind this duck. Leaders have two places, others one. */
    public boolean hasFollower() {
        boolean leftFilled = followedByLeft != null && followedByLeft.isAlive()
                && !followedByLeft.isRemoved() && followedByLeft.getLeader() == this;
        if (this.isLeader()) {
            boolean rightFilled = followedByRight != null && followedByRight.isAlive()
                    && !followedByRight.isRemoved() && followedByRight.getLeader() == this;
            return leftFilled && rightFilled;
        }
        return leftFilled;
    }

    /** The duck ahead of this one, or null if this duck leads. */
    @Nullable
    public DuckEntity getLeader() {
        return this.following;
    }

    /**
     * Puts a duck in behind this one and says which side it got, or null if there was
     * no room. Leaders fill left then right; everyone else only has a left.
     */
    @Nullable
    public FollowerSlot claimFollower(DuckEntity duck) {
        if (this.isBaby() || duck == null || duck.isBaby()) return null;
        if (followedByLeft == null || !followedByLeft.isAlive()) {
            followedByLeft = duck;
            return FollowerSlot.LEFT;
        }
        if (this.isLeader() && (followedByRight == null || !followedByRight.isAlive())) {
            followedByRight = duck;
            return FollowerSlot.RIGHT;
        }
        return null;
    }

    /** Lets a follower go, whichever side it was on. */
    public void releaseFollower(DuckEntity duck) {
        if (followedByLeft == duck) followedByLeft = null;
        if (followedByRight == duck) followedByRight = null;
    }

    /** Follows a new duck, or null to strike out alone. */
    public void setLeader(@org.jetbrains.annotations.Nullable DuckEntity newLeader) {
        if (this.isBaby())
            return; // ducklings follow a parent, not a flock
        if (newLeader != null && newLeader.isBaby())
            return; // and nobody follows a duckling

        final DuckEntity prev = this.following;
        this.following = newLeader;
        this.headStableTicks = 0; // this duck is not a settled leader any more
        dbg("leader set: {} -> {}", prev == null ? "null" : prev.getId(),
                newLeader == null ? "null" : newLeader.getId());
        updateLeaderSpeedModifier();
    }

    /**
     * Picks the animation, from the duck's own physics plus the hint byte the server sent.
     *
     * First match wins, in the order written below: a dab that is still playing, then
     * flying, then panic, then walking, then standing around.
     */
    @Override
    public void registerControllers(
            com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                "main",
                2,
                state -> {
                    // Age the timers by however many ticks have actually gone by since
                    // this last ran, rather than assuming one.
                    final int tickNow = this.tickCount;
                    if (lastTickSeen < 0) lastTickSeen = tickNow;
                    final int tickDelta = tickNow - lastTickSeen;
                    if (tickDelta > 0) {
                        if (oneShotLockTicks > 0)
                            oneShotLockTicks = Math.max(0, oneShotLockTicks - tickDelta);
                        if (idleVariantCooldown > 0)
                            idleVariantCooldown = Math.max(0, idleVariantCooldown - tickDelta);
                        lastTickSeen = tickNow;
                    }

                    // A dab in progress beats everything. Cutting it looks broken.
                    if (oneShotLockTicks > 0 && currentOneShot != null) {
                        state.setAndContinue(currentOneShot);
                        if (oneShotLockTicks <= 0) currentOneShot = null;
                        return PlayState.CONTINUE;
                    }

                    // A duck floating on water is not onGround() either, so water has to be
                    // ruled out here or every swimming duck flaps its wings.
                    if (!this.onGround() && !this.isInWater()) {
                        state.setAndContinue(FLY);
                        return PlayState.CONTINUE;
                    }

                    if (this.entityData.get(DATA_ANIM_HINT) == HINT_PANIC) {
                        state.setAndContinue(PANIC_ANIM);
                        return PlayState.CONTINUE;
                    }

                    // Having a path is not enough; it has to be moving, or a duck stuck
                    // against a fence walks on the spot.
                    final boolean pathing = !this.getNavigation().isDone() && this.getNavigation().getPath() != null;
                    final double horizVel2 = this.getDeltaMovement().horizontalDistanceSqr();
                    if ((pathing || horizVel2 > 0.0001D) && state.isMoving()) {
                        state.setAndContinue(WALK);
                        return PlayState.CONTINUE;
                    }

                    // Every nine to eleven seconds the duck gets a chance to dab instead.
                    if (idleVariantCooldown <= 0) {
                        final int dabN = mc.quackedducks.config.QuackConfig.get().genericDucks.dabChance;
                        if (this.random.nextInt(dabN) == 0) {
                            state.controller().reset();
                            currentOneShot = DAB;
                            oneShotLockTicks = 45; // the clip is 35 ticks, plus a little slack
                            state.setAndContinue(DAB);
                            dbg("dab triggered (lock={})", oneShotLockTicks);
                        } else {
                            state.setAndContinue(IDLE);
                        }
                        idleVariantCooldown = 180 + this.random.nextInt(60);
                    } else {
                        state.setAndContinue(IDLE);
                    }

                    return PlayState.CONTINUE;
                }));
    }

    /** Leaves the flock properly, so nobody is left following a dead duck. */
    @Override
    public void remove(RemovalReason reason) {
        if (following != null)
            following.releaseFollower(this);
        if (followedByLeft != null) followedByLeft.setLeader(null);
        if (followedByRight != null) followedByRight.setLeader(null);
        following = null;
        followedByLeft = null;
        followedByRight = null;
        stopFlying();
        super.remove(reason);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return mc.quackedducks.sound.QuackedSounds.DUCK_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return mc.quackedducks.sound.QuackedSounds.DUCK_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return mc.quackedducks.sound.QuackedSounds.DUCK_DEATH;
    }

    /**
     * Logs with the duck's id, tame flag and leader flag in front, so lines from a flock
     * can be told apart. Silent unless debug is on.
     */
    private void dbg(String fmt, Object... args) {
        if (!DEBUG_DUCKS) return;
        Object[] all = new Object[3 + args.length];
        all[0] = this.getId();
        all[1] = this.isTame();
        all[2] = this.isLeader();
        System.arraycopy(args, 0, all, 3, args.length);
        LOG.info("[duck id={} tame={} leader={}] " + fmt, all);
    }

    /** A leader is any adult duck that is not following someone else. */
    public boolean isLeader() {
        return !this.isBaby() && this.getLeader() == null;
    }

    /** True once this duck has led long enough to be trusted with a new job. */
    public boolean isHeadStable() {
        return this.headStableTicks >= HEAD_STABLE_REQUIRED;
    }

    @Override
    public int getAmbientSoundInterval() {
        return mc.quackedducks.config.QuackConfig.get().genericDucks.ambientSoundInterval;
    }

    @Override
    public void tick() {
        super.tick();

        // The animation timers are aged in the controller. Don't also do it here, or
        // they run down twice as fast and one-shots get cut off.

        if (!this.level().isClientSide()) {
            if (!this.isBaby() && this.isLeader()) {
                headStableTicks = Math.min(headStableTicks + 1, HEAD_STABLE_REQUIRED + 200);
            } else {
                headStableTicks = 0;
            }

            // Transient modifiers do not survive a world reload, so the leader's speed
            // bonus is re-applied on a timer rather than only when the leader changes.
            if ((this.tickCount % 40) == 0) {
                updateLeaderSpeedModifier();
            }

            // A little sparkle over the leader's head so you can pick it out of the flock.
            if (isLeader() && !isTame() && (tickCount % 30) == 0) {
                ((ServerLevel) level()).sendParticles(
                        ParticleTypes.END_ROD,
                        getX(), getY() + getBbHeight() + 0.25, getZ(),
                        1, 0.15, 0.08, 0.15, 0.02);
            }

            // Every other tick is often enough for an animation hint.
            if ((this.tickCount & 1) == 0) {
                updateDuckState();
            }
        }
    }

    /**
     * Works out what the duck looks like it is doing and tells the client.
     *
     * The brain goal answers for most of it, but the goals that can preempt the brain
     * (fleeing, breeding, being tempted) are not visible from its state, so they are
     * checked separately.
     */
    private void updateDuckState() {
        byte hint = HINT_IDLE;

        if (brainGoal != null) {
            hint = switch (brainGoal.getBrainState()) {
                case PANIC_FLY, PANIC_GROUND         -> HINT_PANIC;
                case MIGRATE, FOLLOW_LEADER_AIR,
                     FOLLOW_OWNER, FOLLOW_LEADER_GROUND,
                     FOLLOW_PARENT, WANDER           -> HINT_WALK;
                // LANDING says idle, but the duck is still off the ground, so the
                // controller picks FLY anyway. That is what we want on the way down.
                case LANDING, IDLE                   -> HINT_IDLE;
            };
        }

        // Fleeing a predator is its own goal, outside the brain.
        if (hint == HINT_IDLE || hint == HINT_WALK) {
            for (var w : this.goalSelector.getAvailableGoals()) {
                if (w.isRunning() && w.getGoal() instanceof AvoidEntityGoal) {
                    hint = HINT_PANIC;
                    break;
                }
            }
        }

        // As are breeding and chasing seeds. Both are just walking, as far as the client cares.
        if (hint == HINT_IDLE) {
            for (var w : this.goalSelector.getAvailableGoals()) {
                if (w.isRunning()
                        && (w.getGoal() instanceof BreedGoal
                                || w.getGoal() instanceof TemptGoal)) {
                    hint = HINT_WALK;
                    break;
                }
            }
        }

        this.entityData.set(DATA_ANIM_HINT, hint);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult interactionResult = super.mobInteract(player, hand);
        if (interactionResult.consumesAction()) {
            return interactionResult;
        }
        final ItemStack stack = player.getItemInHand(hand);
        final boolean client = level().isClientSide();

        // Seeds tame it. There is no separate breeding item.
        if (!this.isTame() && this.isFood(stack)) {
            if (!client) {
                if (!player.getAbilities().instabuild)
                    stack.shrink(1);
                this.tame(player);
                this.setPersistenceRequired();
                dbg("tamed by {}", player.getName().getString());
                level().broadcastEntityEvent(this, (byte) 7); // the taming hearts
            }
            return client ? InteractionResult.SUCCESS
                    : InteractionResult.SUCCESS_SERVER;
        }

        return super.mobInteract(player, hand);
    }

    /** Fragile like a chicken, unhurried on land, at home in water. */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, mc.quackedducks.config.QuackConfig.get().genericDucks.maxHealth)
                .add(Attributes.MOVEMENT_SPEED, mc.quackedducks.config.QuackConfig.get().genericDucks.movementSpeed)
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
                .add(Attributes.FLYING_SPEED, 0.9D)
                .add(Attributes.TEMPT_RANGE, 16.0D)   // how far off it spots seeds
                .add(Attributes.FOLLOW_RANGE, 12.0D); // how far off it spots anything else
    }

    @Override
    protected void registerGoals() {
        // Only holds JUMP, so it keeps the duck's head above water without stopping
        // whatever else it was doing.
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));

        // Holds no flags at all, so a duck can lay an egg mid-stroll.
        this.goalSelector.addGoal(0, new mc.quackedducks.entities.ai.LayEggGoal(this, 600, 40000000,
                mc.quackedducks.items.QuackyModItems.duckEggSupplier()));

        // Predators, in the order a duck should worry about them. Ground only.
        this.goalSelector.addGoal(1, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Monster.class, 12.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(2, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Wolf.class, 12.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(3, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Bee.class, 8.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(4, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, PolarBear.class, 12.0F, 1.0D, 1.25D));

        this.goalSelector.addGoal(5, new BreedGoal(this, 1.0D));

        this.goalSelector.addGoal(6, new TemptGoal(this, 1.1D, DUCK_FOOD, false));

        // Everything above this line can take the wheel off the brain. Everything the
        // duck does under its own steam is in here, and it is the only thing that ever
        // calls startFlying() or stopFlying().
        this.brainGoal = new mc.quackedducks.entities.ai.DuckBrainGoal(this);
        this.goalSelector.addGoal(7, this.brainGoal);

        // Look goals only hold LOOK, so they run alongside whatever won the MOVE fight.
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /**
     * The duck's hitbox, straight from the config, shrunk by babyScale for ducklings.
     * Pose is ignored; a duck is one size for its age. Called from EntityMixin.
     */
    public net.minecraft.world.entity.EntityDimensions getDuckDimensions(net.minecraft.world.entity.Pose pose) {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;
        net.minecraft.world.entity.EntityDimensions dims = net.minecraft.world.entity.EntityDimensions
                .scalable(config.duckWidth, config.duckHeight)
                .withEyeHeight(config.duckHeight);

        return this.isBaby() ? dims.scale(config.babyScale) : dims;
    }

    /**
     * Gives the speed bonus to untamed adult leaders and takes it off everyone else.
     *
     * Cheap enough to call every tick, since addOrUpdateTransientModifier only dirties
     * the attribute when the value really changed.
     */
    private void updateLeaderSpeedModifier() {
        if (level().isClientSide()) return;
        var attr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        if (isLeader() && !isTame() && !isBaby()) {
            attr.addOrUpdateTransientModifier(LEADER_SPEED_MODIFIER);
        } else {
            attr.removeModifier(LEADER_SPEED_ID);
        }
    }

    /** Swaps in the flying navigator and move control. Only DuckBrainGoal calls this. */
    public void startFlying() {
        if (inFlyingMode) return;
        inFlyingMode = true;
        groundNav.stop();
        this.navigation = flyingNav;
        this.moveControl = flyingMoveControl;
        dbg("startFlying() - tick={}", this.tickCount);
    }

    /**
     * Swaps back to the ground navigator and hands the duck back to gravity.
     *
     * Only DuckBrainGoal calls this, and only once the duck is nearly down. Calling it
     * with height still under the duck drops it out of the sky.
     */
    public void stopFlying() {
        if (!inFlyingMode) return;
        inFlyingMode = false;
        flyingNav.stop();
        this.navigation = groundNav;
        this.moveControl = groundMoveControl;
        this.setNoGravity(false);
        dbg("stopFlying() - tick={}", this.tickCount);
    }

    /** True while the flying navigator is the active one. */
    public boolean inFlyingMode() {
        return inFlyingMode;
    }

    /** Ducks are birds. Empty on purpose: no fall damage. */
    @Override
    protected void checkFallDamage(double y, boolean onGround,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.core.BlockPos pos) {
    }

    /**
     * Re-reads speed, health and hitbox from the config, so a config change lands on
     * ducks that are already in the world. Called on every loaded duck after a save
     * or a reload.
     */
    public void updateFromConfig() {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;

        // Server only. Attributes replicate on their own, and maxHealth is not a networked
        // field, so a client doing this would overwrite the server's value with its own.
        if (!this.level().isClientSide()) {
            var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(config.movementSpeed);
            }
            var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(config.maxHealth);
            }
        }

        // The hitbox comes from fields that do sync, so both sides redo it.
        this.refreshDimensions();
    }
}
