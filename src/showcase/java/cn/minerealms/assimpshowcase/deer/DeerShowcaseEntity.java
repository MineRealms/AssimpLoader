package cn.minerealms.assimpshowcase.deer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** An opt-in asset test creature. It has no natural spawning or player-model hooks. */
public final class DeerShowcaseEntity extends PathfinderMob {
    public static final String ID = "deer_showcase";
    private static final EntityDataAccessor<String> SHOWCASE_STATE =
            SynchedEntityData.defineId(DeerShowcaseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> GROUND_IK = SynchedEntityData.defineId(DeerShowcaseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EFFECTS = SynchedEntityData.defineId(DeerShowcaseEntity.class, EntityDataSerializers.BOOLEAN);

    public DeerShowcaseEntity(EntityType<? extends DeerShowcaseEntity> type, Level level) {
        super(type, level);
        setMaxUpStep(.6f);
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, .22).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SHOWCASE_STATE, "auto");
        entityData.define(GROUND_IK,true); entityData.define(EFFECTS,false);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public String getShowcaseState() { return entityData.get(SHOWCASE_STATE); }
    public boolean groundIkEnabled() { return entityData.get(GROUND_IK); }
    public void setGroundIkEnabled(boolean value) { entityData.set(GROUND_IK,value); }
    public boolean effectsEnabled() { return entityData.get(EFFECTS); }
    public void setEffectsEnabled(boolean value) { entityData.set(EFFECTS,value); }

    public void setShowcaseState(String state) {
        if (!validState(state)) throw new IllegalArgumentException("Invalid deer showcase state");
        entityData.set(SHOWCASE_STATE, state);
    }

    public static boolean validState(String state) {
        return state != null && state.length() <= 64 && state.matches("[a-zA-Z0-9_.-]+");
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ShowcaseState", getShowcaseState());
        tag.putBoolean("GroundIK",groundIkEnabled()); tag.putBoolean("ShowcaseEffects",effectsEnabled());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String state = tag.getString("ShowcaseState");
        setShowcaseState(validState(state) ? state : "auto");
        setGroundIkEnabled(!tag.contains("GroundIK") || tag.getBoolean("GroundIK"));
        setEffectsEnabled(tag.getBoolean("ShowcaseEffects"));
    }
}
