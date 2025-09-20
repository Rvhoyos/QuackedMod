package mc.quackedducks.entities;

import net.minecraft.advancements.critereon.TameAnimalTrigger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.PolarBear;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Quacked Ducks - main duck entity.
 * 
 * - Eats seeds (tempt goal).
 * - Avoids common predators, swims, breeds, and follows a parent when young.
 * - GeckoLib-powered idle/walk animations.
 * - Basic “follow a leader” chain via {@code following}/{@code followedBy}.
 */
public class DuckEntity extends TamableAnimal implements GeoEntity {
    // --- Simple “follow the leader” chain state ---
    @Nullable private DuckEntity following; // who I’m following
    @Nullable private DuckEntity followedBy; // who is following me
    // --- GeckoLib animation cache & clips --- 
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.duck.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.duck.walk");
    // --- Owner-follow goal (taming) ---
    private FollowOwnerGoal followOwnerGoal;
    private static final int OWNER_FOLLOW_PRIORITY = 7;
    private boolean ownerFollowPaused = false;
    private boolean followGoalAdded = false;
    // --- Debug logging ---
    private static final Logger LOG = LogUtils.getLogger();
    /** Flip to false to silence all duck debug logs without rebuilding logic. */
    private static final boolean DEBUG_DUCKS = true;



    // Ducks like seeds (same as chickens)
    private static final Ingredient DUCK_FOOD = Ingredient.of(
    Items.WHEAT_SEEDS,
    Items.BEETROOT_SEEDS,
    Items.MELON_SEEDS,
    Items.PUMPKIN_SEEDS,
    Items.TORCHFLOWER_SEEDS
    );
    /** Standard constructor used by the engine/registries. */
    public DuckEntity(EntityType<? extends DuckEntity> type, Level level) {
        super(type, level);
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
        return mc.quackedducks.entities.QuackEntityTypes.DUCK.get().create(
            level,
            net.minecraft.world.entity.EntitySpawnReason.BREEDING
        );
    }

    /** @return whether this duck currently has a valid follower linked. */
    public boolean hasFollower() {
        return this.followedBy != null
            && this.followedBy.isAlive()
            && !this.followedBy.isRemoved()
            && this.followedBy.getLeader() == this;
    }

    /** @return the leader this duck is following (may be null). */
    @Nullable public DuckEntity getLeader() { return this.following; }

    /** Attempts to claim the given duck as our follower (tail). */
    public boolean claimFollower(DuckEntity duck) {
        if (this.isBaby() || duck == null || duck.isBaby()) return false; // any invalid ⇒ reject
        if (this.followedBy == null || !this.followedBy.isAlive()) {
            this.followedBy = duck;
            return true;
        }
        return false;
    }
    /** Releases the follower if it matches the given duck. */
    public void releaseFollower(DuckEntity duck) {
        if (this.followedBy == duck) this.followedBy = null;
    }
    /** Sets the leader this duck follows (may be null to detach). */
   public void setLeader(@org.jetbrains.annotations.Nullable DuckEntity newLeader) {
    if (this.isBaby()) return;                      // babies don’t join custom chains
    if (newLeader != null && newLeader.isBaby()) return; // can’t follow a baby
    
    
    final DuckEntity prev = this.following;
    this.following = newLeader;
    dbg("leader set: {} -> {}", prev == null ? "null" : prev.getId(), newLeader == null ? "null" : newLeader.getId());
    updateOwnerFollowGoal();
}


    /** GeckoLib controller: switches idle/walk based on movement. */    
    @Override
    public void registerControllers(ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animatable.processing.AnimationController<>(
            "main",
            0,
             state-> {
                if(state.isMoving()){ 
                    state.setAndContinue(WALK);
                }else{
                    state.setAndContinue(IDLE);
                }
                return PlayState.CONTINUE;
            }
        ));
    }
    /** Cleans up leader/follower links on removal. */
    @Override
    public void remove(RemovalReason reason) {
        if (following != null) following.releaseFollower(this);
        if (followedBy != null) followedBy.setLeader(null);
        following = null; followedBy = null;
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
        return mc.quackedducks.sound.QuackedSounds.DUCK_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return mc.quackedducks.sound.QuackedSounds.DUCK_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return mc.quackedducks.sound.QuackedSounds.DUCK_DEATH.get();
    }
/**
 * Debug logger with consistent context (id/tame/leader/paused/goalAdded).
 * Does nothing when {@code DEBUG_DUCKS} is false.
 */
private void dbg(String fmt, Object... args) {
    if (!DEBUG_DUCKS) return;
    String sfmt = fmt.replace("{}", "%s");
    LOG.info("[duck id={} tame={} leader={} paused={} goalAdded={}] {}",
            this.getId(), this.isTame(), this.isLeader(), this.ownerFollowPaused, this.followGoalAdded,
            String.format(sfmt, args));
}


    /** @return true if this duck is a leader (not following another duck). */
    public boolean isLeader() {
        return !this.isBaby() && this.getLeader() == null;
    }


    /** Keep the owner-follow goal in sync with leader/paused/tame state (and log transitions). */
    private void updateOwnerFollowGoal() {
        if (this.followOwnerGoal == null) return; // not constructed yet
        final boolean shouldHave = this.isTame() && !this.ownerFollowPaused && this.isLeader();

        if (shouldHave && !followGoalAdded) {
            this.goalSelector.addGoal(OWNER_FOLLOW_PRIORITY, this.followOwnerGoal);
            followGoalAdded = true;
            dbg("follow-goal ADDED (prio={}, min=6.0, max=22.0)", OWNER_FOLLOW_PRIORITY);
        } else if (!shouldHave && followGoalAdded) {
            this.goalSelector.removeGoal(this.followOwnerGoal);
            followGoalAdded = false;
            dbg("follow-goal REMOVED");
        }
    }

/**
 * Handles right-click interaction for taming and for pausing owner-follow (leader only).
 *
 * Behavior:
 * 1) Taming: if this duck is not yet tamed and the held item is accepted by
 *    {@link #isFood(ItemStack)}, one item is consumed in Survival
 *    (unless {@code player.getAbilities().instabuild} is true), {@link #tame(Player)}
 *    is invoked to set owner/tame, the entity is marked persistent via {@link #setPersistenceRequired()},
 *    a default name is applied if this duck is a leader and has no custom name, {@link #updateOwnerFollowGoal()}
 *    is called to sync goals, and heart particles are emitted (entity event {@code 7}).
 *    Returns {@link InteractionResult#SUCCESS} on the client and
 *    {@link InteractionResult#SUCCESS_SERVER} on the server.
 *
 * 2) Pause/resume owner-follow: if already tamed, the interacting player is the owner, this duck is a leader,
 *    the player is sneaking, and the hand is empty, flips a custom “ownerFollowPaused” flag and calls
 *    {@link #updateOwnerFollowGoal()} to add/remove the follow-owner goal at runtime.
 *    Returns {@link InteractionResult#SUCCESS} (client) or
 *    {@link InteractionResult#SUCCESS_SERVER} (server).
 *
 * 3) Otherwise, defers to {@code super.mobInteract(player, hand)}.
 *
 * Note: applying a name with a renamed Name Tag is handled by
 * {@link net.minecraft.world.item.NameTagItem} before this method runs.
 *
 * @param player the interacting {@link Player}
 * @param hand   the {@link InteractionHand} used for the interaction
 * @return {@link InteractionResult#SUCCESS} on client or
 *         {@link InteractionResult#SUCCESS_SERVER} on server for the handled cases;
 *         otherwise the result from {@code super}
 */

    @Override
public InteractionResult mobInteract(Player player, InteractionHand hand) {
    
    final ItemStack stack = player.getItemInHand(hand);
    final boolean client = level().isClientSide;

    // Pause/resume owner-follow: sneak + empty hand by owner (leader only)
    if (this.isTame() && this.isOwnedBy(player)
        && player.isShiftKeyDown() && stack.isEmpty()) {
        if (!client) {
            this.ownerFollowPaused = !this.ownerFollowPaused;
            dbg("pause toggled -> {}", this.ownerFollowPaused);
            updateOwnerFollowGoal();
        }
        return client ? InteractionResult.SUCCESS
                      : InteractionResult.SUCCESS_SERVER;
    }

    // Tame with existing food (seeds)
    if (!this.isTame() && this.isFood(stack)) {
        if (!client) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            this.tame(player);
            this.setPersistenceRequired();
            dbg("tamed by {}", player.getName().getString());
            updateOwnerFollowGoal();           // ensures follow state matches policy
            level().broadcastEntityEvent(this, (byte)7); // hearts
        }
        return client ? InteractionResult.SUCCESS
                      : InteractionResult.SUCCESS_SERVER;
    }

    return super.mobInteract(player, hand);
}




    /*
    *  --- Persistence ---
    * Saves/restores the custom "owner-follow paused" state.
    */
@Override
protected void addAdditionalSaveData(ValueOutput out) {
    super.addAdditionalSaveData(out);
    out.putBoolean("OwnerFollowPaused", this.ownerFollowPaused);
    dbg("save: OwnerFollowPaused={}", this.ownerFollowPaused);
}

@Override
protected void readAdditionalSaveData(ValueInput in) {
    super.readAdditionalSaveData(in);
    this.ownerFollowPaused = in.getBooleanOr("OwnerFollowPaused", false);
    dbg("load: OwnerFollowPaused={}", this.ownerFollowPaused);
    updateOwnerFollowGoal();
}

    


    /**
     * Base attributes for ducks.
     * - Light health, slow-ish on land, good at water movement, decent follow/tempt ranges.
     */
    public static AttributeSupplier.Builder createAttributes() {
    return Animal.createLivingAttributes()
        .add(Attributes.MAX_HEALTH, 6.0D)       // fragile like a chicken
        .add(Attributes.MOVEMENT_SPEED, 0.25D) // slow-ish walker
        .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D) // good swimmer
        .add(Attributes.FLYING_SPEED, 0.9D) 
        .add(Attributes.TEMPT_RANGE, 16.0D)   // how far it notices food/player
        .add(Attributes.FOLLOW_RANGE, 12.0D);  // how far it notices food/player
}   
    @Override
    /** Registers AI goals (priority order matters). */
    protected void registerGoals() {
    // Survival / Interrupts
    this.goalSelector.addGoal(0, new FloatGoal(this));                 // stay buoyant
    this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));          // flee when hurt
    // Threat avoidance & breeding Interrupts
    this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 12.0F, 1.0D, 1.25D));
    this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Wolf.class,   12.0F, 1.0D, 1.25D));
    this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Bee.class,     8.0F, 1.0D, 1.25D));
    this.goalSelector.addGoal(5, new AvoidEntityGoal<>(this, PolarBear.class, 12.0F, 1.0D, 1.25D));
    this.goalSelector.addGoal(6, new BreedGoal(this, 1.0D));
    //goofy interrrupts go above taming and below breeding.
    // Construct once; updateOwnerFollowGoal() decides when it’s active
    this.followOwnerGoal = new FollowOwnerGoal(this, 1.05D, 8.0F, 22.0F);
    updateOwnerFollowGoal(); // set initial state based on tame/leader/paused

    // Custom behavior : leading duck occasional migration Interrupt
    this.goalSelector.addGoal(8, new mc.quackedducks.entities.ai.LeaderMigrationGoal(
        this,
        1.1D,         // gentle pace
        600, 1200    // wait 30s - 1 min on average before trying again
));
    // Lay eggs (intervals V1; adjust as needed (Remember how the struggle with custom goals.)
    this.goalSelector.addGoal(10, new mc.quackedducks.entities.ai.LayEggGoal(this, 9000, 100000, mc.quackedducks.items.QuackyModItems.duckEggSupplier()));
    // Follow a leader if not part of a chain already
    this.goalSelector.addGoal(11, new mc.quackedducks.entities.ai.FollowLeaderIfFreeGoal(
        this, 1.1D, 18.0F, 4.2F, 2.0F));
    // Player interaction & idle behavior
    this.goalSelector.addGoal(12, new TemptGoal(this, 1.5D, DUCK_FOOD, false));
    this.goalSelector.addGoal(13, new FollowParentGoal(this, 1.1D));
    this.goalSelector.addGoal(14, new RandomStrollGoal(this,   1.1D));    
    this.goalSelector.addGoal(15,  new LookAtPlayerGoal(this, Player.class, 6.0F));
    this.goalSelector.addGoal(16, new RandomLookAroundGoal(this)); // if a duck isnt being tempted, has no parent (or following a duck) and isnt following a player, it will look around randomly between strolls and (rare) migrations
}

}
