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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
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
 * Quacked Ducks - main duck entity.
 * 
 * - Eats seeds (tempt goal).
 * - Avoids common predators, swims, breeds, and follows a parent when young.
 * - GeckoLib-powered idle/walk animations.
 * - Basic "follow a leader" chain via {@code following}/{@code followedBy}.
 */
public class DuckEntity extends TamableAnimal implements GeoEntity {
    // --- Follow the leader chain state ---
    @Nullable
    private DuckEntity following;       // who I’m following
    @Nullable
    private DuckEntity followedByLeft;  // left-slot follower (all ducks)
    @Nullable
    private DuckEntity followedByRight; // right-slot follower (leaders only → V formation)

    // --- Flying navigation ---
    private GroundPathNavigation groundNav;
    private FlyingPathNavigation flyingNav;
    private net.minecraft.world.entity.ai.control.MoveControl groundMoveControl;
    private FlyingMoveControl flyingMoveControl;
    private boolean inFlyingMode = false;

    // --- Synched animation hint (server → client) ---
    // 0 = idle, 1 = walk, 2 = panic
    // Flying is determined client-side by onGround(); this covers ground state distinctions.
    private static final EntityDataAccessor<Byte> DATA_ANIM_HINT =
            SynchedEntityData.defineId(DuckEntity.class, EntityDataSerializers.BYTE);
    private static final byte HINT_IDLE  = 0;
    private static final byte HINT_WALK  = 1;
    private static final byte HINT_PANIC = 2;

    // --- GeckoLib animation cache & clips ---
    private final AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE       = RawAnimation.begin().thenLoop("animation.duck.idle");
    private static final RawAnimation WALK       = RawAnimation.begin().thenLoop("animation.duck.walk");
    private static final RawAnimation PANIC_ANIM = RawAnimation.begin().thenLoop("animation.duck.panic");
    private static final RawAnimation FLY        = RawAnimation.begin().thenLoop("animation.duck.fly");
    private static final RawAnimation DAB        = RawAnimation.begin().thenPlay("animation.duck.dab");

    // --- Owner-follow gating (stability) ---
    private int headStableTicks = 0; // counts how long we've been the head
    private static final int HEAD_STABLE_REQUIRED = 120; // 6s @ 20tps

    // --- Reference to the brain goal for animation hints ---
    private mc.quackedducks.entities.ai.DuckBrainGoal brainGoal;

    // --- Debug logging ---
    private static final Logger LOG = LogUtils.getLogger();
    /** Flip to false to silence all duck debug logs without rebuilding logic. */
    private static final boolean DEBUG_DUCKS = true;

    /** Which follower slot a duck occupies behind its leader (used for V-formation offsets). */
    public enum FollowerSlot { LEFT, RIGHT }

    private int idleVariantCooldown = 10; // ticks until next idle variant allowed
    // one-shot playback lock (add these)
    private int oneShotLockTicks = 0; // ticks left while a one-shot plays
    private RawAnimation currentOneShot = null;
    // -- hopefully the fix for desync / animation cut offs --
    private int lastTickSeen = -1;

    // --- Movement tuning ---
    /** +25% movement speed on the head duck's MOVEMENT_SPEED attribute. Transient — not saved. */
    private static final Identifier LEADER_SPEED_ID = Identifier.fromNamespaceAndPath("quack", "leader_speed");
    private static final AttributeModifier LEADER_SPEED_MODIFIER =
            new AttributeModifier(LEADER_SPEED_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    // Ducks like seeds - TODO: add breadcrumb item.
    private static final Ingredient DUCK_FOOD = Ingredient.of(
            Items.WHEAT_SEEDS,
            Items.BEETROOT_SEEDS,
            Items.MELON_SEEDS,
            Items.PUMPKIN_SEEDS,
            Items.TORCHFLOWER_SEEDS);

    /** Standard constructor used by the engine/registries. */
    public DuckEntity(EntityType<? extends DuckEntity> type, Level level) {
        super(type, level);
        // groundMoveControl is set by Mob's constructor before this line runs
        this.groundMoveControl = this.moveControl;
        // hoversInPlace=true: holds altitude between nav waypoints instead of falling.
        // Landing is triggered explicitly by stopFlying() → setNoGravity(false).
        this.flyingMoveControl = new FlyingMoveControl(this, 10, true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANIM_HINT, HINT_IDLE);
    }

    /**
     * Creates both navigators at construction time so we can swap them at runtime.
     * Returns {@link GroundPathNavigation} as the default; {@link FlyingPathNavigation}
     * is only active while {@link #inFlyingMode} is true.
     *
     * <p>Note: {@code setRequiredPathLength} is intentionally omitted. Ducks migrate
     * 80–140 blocks and that call (borrowed from Bee) would cap/constrain path length
     * in ways that break long-distance aerial navigation.
     */
    @Override
    protected PathNavigation createNavigation(Level level) {
        this.groundNav = new GroundPathNavigation(this, level);
        this.flyingNav = new FlyingPathNavigation(this, level);
        flyingNav.setCanOpenDoors(false);
        flyingNav.setCanFloat(true);
        return groundNav;
    }

    /** @return true if the provided stack is valid duck food (tempt goal). */
    @Override
    public boolean isFood(ItemStack stack) {
        return DUCK_FOOD.test(stack); // no breeding item (for now)
    }

    /**
     * Produces a child duck when breeding.
     * Uses the entity type’s factory (keeps loader-agnostic semantics).
     */
    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return mc.quackedducks.entities.QuackEntityTypes.DUCK.create(
                level,
                net.minecraft.world.entity.EntitySpawnReason.BREEDING);
    }

    /** @return true when all available follower slot(s) are filled. Leaders require both slots filled. */
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

    /** @return the leader this duck is following (may be null). */
    @Nullable
    public DuckEntity getLeader() {
        return this.following;
    }

    /**
     * Attempts to claim the given duck as a follower.
     * Leaders offer LEFT then RIGHT (V-formation); non-leaders offer LEFT only.
     * @return the assigned {@link FollowerSlot}, or {@code null} if no slot is available.
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

    /** Releases the follower if it matches the given duck (checks both slots). */
    public void releaseFollower(DuckEntity duck) {
        if (followedByLeft == duck) followedByLeft = null;
        if (followedByRight == duck) followedByRight = null;
    }

    /** Sets the leader this duck follows (may be null to detach). */
    public void setLeader(@org.jetbrains.annotations.Nullable DuckEntity newLeader) {
        if (this.isBaby())
            return; // babies don’t join custom chains
        if (newLeader != null && newLeader.isBaby())
            return; // can’t follow a baby

        final DuckEntity prev = this.following;
        this.following = newLeader;
        this.headStableTicks = 0; // reset stability on leader change
        dbg("leader set: {} -> {}", prev == null ? "null" : prev.getId(),
                newLeader == null ? "null" : newLeader.getId());
        updateLeaderSpeedModifier();
    }

    /**
     * GeckoLib controller driven by physics + synched hint byte.
     *
     * Priority order:
     *   0) Active DAB one-shot — holds until finished
     *   1) Airborne (!onGround()) → FLY (loop)
     *   2) PANIC hint (fleeing/avoiding) → PANIC_ANIM (loop)
     *   3) Moving on ground → WALK (loop)
     *   4) Idle → IDLE (loop), with rare DAB one-shot variant
     */
    @Override
    public void registerControllers(
            software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                "main",
                2,
                state -> {
                    // ---- TICK GATE ----
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

                    // 0) Hold active DAB until it finishes
                    if (oneShotLockTicks > 0 && currentOneShot != null) {
                        state.setAndContinue(currentOneShot);
                        if (oneShotLockTicks <= 0) currentOneShot = null;
                        return PlayState.CONTINUE;
                    }

                    // 1) Flying — checked before ground states
                    if (!this.onGround()) {
                        state.setAndContinue(FLY);
                        return PlayState.CONTINUE;
                    }

                    // 2) Panic / avoid hint — duck is fleeing on ground
                    if (this.entityData.get(DATA_ANIM_HINT) == HINT_PANIC) {
                        state.setAndContinue(PANIC_ANIM);
                        return PlayState.CONTINUE;
                    }

                    // 3) Walking — must actually be moving to avoid micro-jitter
                    final boolean pathing = !this.getNavigation().isDone() && this.getNavigation().getPath() != null;
                    final double horizVel2 = this.getDeltaMovement().horizontalDistanceSqr();
                    if ((pathing || horizVel2 > 0.0001D) && state.isMoving()) {
                        state.setAndContinue(WALK);
                        return PlayState.CONTINUE;
                    }

                    // 4) Idle — occasional DAB variant (~9–11 s window, configurable chance)
                    if (idleVariantCooldown <= 0) {
                        final int dabN = mc.quackedducks.config.QuackConfig.get().genericDucks.dabChance;
                        if (this.random.nextInt(dabN) == 0) {
                            state.controller().reset();
                            currentOneShot = DAB;
                            oneShotLockTicks = 45; // 1.75 s * 20 tps + 10 tick buffer
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

    /** Cleans up leader/follower links on removal. */
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

    /** Required GeckoLib cache accessor. */
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // --- Sound Getters ---
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
     * Debug logger with consistent context (id/tame/leader).
     * Uses SLF4J {} placeholders natively — no String.format overhead.
     * Does nothing when {@code DEBUG_DUCKS} is false.
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

    /** @return true if this duck is a leader (not following another duck). */
    public boolean isLeader() {
        return !this.isBaby() && this.getLeader() == null;
    }

    /** @return true when the duck has been a stable head for HEAD_STABLE_REQUIRED ticks. */
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

        // Animation timers (oneShotLockTicks, idleVariantCooldown) are decremented
        // by the controller's tick gate — no duplicate decrement here.

        if (!this.level().isClientSide()) {
            // Track head stability for FOLLOW_OWNER gating in DuckBrainGoal.
            if (!this.isBaby() && this.isLeader()) {
                headStableTicks = Math.min(headStableTicks + 1, HEAD_STABLE_REQUIRED + 200);
            } else {
                headStableTicks = 0;
            }

            // Re-apply leader speed modifier every 40 ticks — transient modifiers are
            // dropped on world reload, so we need this to recover without a setLeader() call.
            if ((this.tickCount % 40) == 0) {
                updateLeaderSpeedModifier();
            }

            // Leader crown — one END_ROD particle above the head every 1.5 s so the
            // head duck is easy to spot. Only untamed leaders emit this.
            if (isLeader() && !isTame() && (tickCount % 30) == 0) {
                ((ServerLevel) level()).sendParticles(
                        ParticleTypes.END_ROD,
                        getX(), getY() + getBbHeight() + 0.25, getZ(),
                        1, 0.15, 0.08, 0.15, 0.02);
            }

            // Drive anim hint from brain goal state (server authoritative).
            if ((this.tickCount & 1) == 0) {
                updateDuckState();
            }
        }
    }

    /** Derives the animation hint from DuckBrainGoal state + any running avoid/breed/tempt goals. */
    private void updateDuckState() {
        byte hint = HINT_IDLE;

        // Brain goal drives the primary motion states.
        if (brainGoal != null) {
            hint = switch (brainGoal.getBrainState()) {
                case PANIC_FLY, PANIC_GROUND         -> HINT_PANIC;
                case MIGRATE, FOLLOW_LEADER_AIR,
                     FOLLOW_OWNER, FOLLOW_LEADER_GROUND,
                     FOLLOW_PARENT, WANDER           -> HINT_WALK;
                case IDLE                            -> HINT_IDLE;
            };
        }

        // GroundOnlyAvoidGoal (outside DuckBrainGoal) → PANIC hint if running.
        if (hint == HINT_IDLE || hint == HINT_WALK) {
            for (var w : this.goalSelector.getAvailableGoals()) {
                if (w.isRunning() && w.getGoal() instanceof AvoidEntityGoal) {
                    hint = HINT_PANIC;
                    break;
                }
            }
        }

        // BreedGoal or TemptGoal → WALK hint.
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
    public InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        InteractionResult interactionResult = super.mobInteract(player, hand);
        if (interactionResult.consumesAction()) {
            return interactionResult;
        }
        final ItemStack stack = player.getItemInHand(hand);
        final boolean client = level().isClientSide();

        // Tame with food (seeds)
        if (!this.isTame() && this.isFood(stack)) {
            if (!client) {
                if (!player.getAbilities().instabuild)
                    stack.shrink(1);
                this.tame(player);
                this.setPersistenceRequired();
                dbg("tamed by {}", player.getName().getString());
                level().broadcastEntityEvent(this, (byte) 7); // hearts
            }
            return client ? InteractionResult.SUCCESS
                    : InteractionResult.SUCCESS_SERVER;
        }

        return super.mobInteract(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
    }

    /**
     * Base attributes for ducks.
     * - Light health, slow-ish on land, good at water movement, decent follow/tempt
     * ranges.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, mc.quackedducks.config.QuackConfig.get().genericDucks.maxHealth) // fragile
                                                                                                             // like a
                                                                                                             // chicken
                .add(Attributes.MOVEMENT_SPEED, mc.quackedducks.config.QuackConfig.get().genericDucks.movementSpeed) // slow-ish
                                                                                                                     // walker
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D) // good swimmer
                .add(Attributes.FLYING_SPEED, 0.9D)
                .add(Attributes.TEMPT_RANGE, 16.0D) // how far it notices food/player
                .add(Attributes.FOLLOW_RANGE, 12.0D); // how far it notices food/player
    }

    @Override
    protected void registerGoals() {
        // 0: Float (JUMP flag only — never blocks MOVE goals)
        this.goalSelector.addGoal(0, new mc.quackedducks.entities.ai.DuckFloatGoal(this));

        // 0: Lay eggs (no flags — runs independently of all movement goals)
        this.goalSelector.addGoal(0, new mc.quackedducks.entities.ai.LayEggGoal(this, 600, 40000000,
                mc.quackedducks.items.QuackyModItems.duckEggSupplier()));

        // 1–4: Avoid predators (ground only; suppressed while airborne by GroundOnlyAvoidGoal)
        this.goalSelector.addGoal(1, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Monster.class, 12.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(2, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Wolf.class, 12.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(3, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, Bee.class, 8.0F, 1.0D, 1.25D));
        this.goalSelector.addGoal(4, new mc.quackedducks.entities.ai.GroundOnlyAvoidGoal<>(this, PolarBear.class, 12.0F, 1.0D, 1.25D));

        // 5: Breed
        this.goalSelector.addGoal(5, new BreedGoal(this, 1.0D));

        // 6: Tempt (seeds)
        this.goalSelector.addGoal(6, new TemptGoal(this, 1.1D, DUCK_FOOD, false));

        // 7: Brain — replaces FlyingPanicGoal, LeaderMigrationGoal,
        //    FollowLeaderIfFreeGoal, DuckWaterAvoidingStrollGoal, FollowParentGoal.
        //    Only goal that calls startFlying() / stopFlying().
        this.brainGoal = new mc.quackedducks.entities.ai.DuckBrainGoal(this);
        this.goalSelector.addGoal(7, this.brainGoal);

        // 8–9: Ambient look goals (LOOK flag only; never conflict with MOVE)
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    /**
     * Returns config-driven {@link net.minecraft.world.entity.EntityDimensions} for this duck.
     * Baby ducks are scaled to 50%. Called from {@link mc.quackedducks.mixin.EntityMixin}.
     *
     * @param pose the entity's current pose (unused; ducks have one size per age)
     * @return scaled dimensions based on current config values
     */
    public net.minecraft.world.entity.EntityDimensions getDuckDimensions(net.minecraft.world.entity.Pose pose) {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;
        net.minecraft.world.entity.EntityDimensions dims = net.minecraft.world.entity.EntityDimensions
                .scalable(config.duckWidth, config.duckHeight)
                .withEyeHeight(config.duckHeight);

        var finalDims = this.isBaby() ? dims.scale(0.5F) : dims;

        // Diagnostic log tracking removed

        return finalDims;
    }

    @Override
    public void refreshDimensions() {
        super.refreshDimensions();
    }

    /**
     * Applies or removes the {@link #LEADER_SPEED_MODIFIER} based on current leader status.
     * Uses {@code addOrUpdateTransientModifier} so it is safe to call every tick — it only
     * marks dirty when the value actually changes. Transient modifiers are not saved to NBT,
     * so this must also be called periodically in tick() to survive world reloads.
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

    /** Switch to flying navigation + move control. Called by flying goals in start(). */
    public void startFlying() {
        if (inFlyingMode) return;
        inFlyingMode = true;
        groundNav.stop();
        this.navigation = flyingNav;
        this.moveControl = flyingMoveControl;
        dbg("startFlying() — tick={}", this.tickCount);
    }

    /** Switch back to ground navigation. Called by flying goals in stop(). */
    public void stopFlying() {
        if (!inFlyingMode) return;
        inFlyingMode = false;
        flyingNav.stop();
        this.navigation = groundNav;
        this.moveControl = groundMoveControl;
        this.setNoGravity(false);
        dbg("stopFlying() — tick={}", this.tickCount);
    }

    /** @return true if this duck is currently in flying mode (nav swapped to air). */
    public boolean inFlyingMode() {
        return inFlyingMode;
    }

    /** @return true when not physically on the ground. */
    public boolean isFlying() {
        return !this.onGround();
    }

    /** Ducks are birds — no fall damage. */
    @Override
    protected void checkFallDamage(double y, boolean onGround,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.core.BlockPos pos) {
    }

    /**
     * Hot-applies the current config values (movement speed, max health, hitbox)
     * to this entity without requiring a restart. Called on all loaded ducks after
     * the server receives an {@link mc.quackedducks.network.QuackNetwork.UpdateConfigPayload}.
     */
    public void updateFromConfig() {
        var config = mc.quackedducks.config.QuackConfig.get().genericDucks;

        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(config.movementSpeed);
        }
        var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(config.maxHealth);
        }
        this.refreshDimensions();
    }
}
