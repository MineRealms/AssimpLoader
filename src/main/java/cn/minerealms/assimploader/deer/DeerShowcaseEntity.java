package cn.minerealms.assimploader.deer;

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
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public String getShowcaseState() { return entityData.get(SHOWCASE_STATE); }

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
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        String state = tag.getString("ShowcaseState");
        setShowcaseState(validState(state) ? state : "auto");
    }
}
