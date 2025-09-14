package mc.quackedducks.entities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;

import mc.quackedducks.sound.QuackedSounds;

import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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

/**
 * Quacked Ducks - main duck entity.
 * 
 * - Eats seeds (tempt goal).
 * - Avoids common predators, swims, breeds, and follows a parent when young.
 * - GeckoLib-powered idle/walk animations.
 * - Basic “follow a leader” chain via {@code following}/{@code followedBy}.
 */
public class DuckEntity extends Animal implements GeoEntity {
    // --- Simple “follow the leader” chain state ---
    @Nullable private DuckEntity following; // who I’m following
    @Nullable private DuckEntity followedBy; // who is following me
    // --- GeckoLib animation cache & clips --- 
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.duck.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.duck.walk");
    
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
    public void setLeader(@Nullable DuckEntity newLeader) {
        this.following = newLeader;
    }
    /** GeckoLib controller: switches idle/walk based on movement. */    @Override
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
