package io.github.flemmli97.improvedmobs.mekanism_turrets.common.entity;

import io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry.DamageTypeRegistry;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry.EntityRegistry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class LaserEntity extends Entity {

    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);

    private UUID targetUUID;
    private LivingEntity cachedTarget;
    private float damagePerTick = 1.0F;

    public LaserEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = true;
    }

    public LaserEntity(Level pLevel, Vec3 pos, LivingEntity target, float damagePerTick) {
        this(EntityRegistry.LASER.get(), pLevel);
        this.setPos(pos);
        this.targetUUID = target.getUUID();
        this.cachedTarget = target;
        this.damagePerTick = damagePerTick;
        updateTargetPos(target.position().add(0, target.getBbHeight() * 0.5, 0));
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive()) {
                this.discard();
                return;
            }

            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            updateTargetPos(targetPos);

            // 造成激光伤害（魔法伤害，绕过护甲）
            target.hurt(level().damageSources().magic(), damagePerTick);

            // 损坏护甲（每tick对所有护甲造成伤害）
            target.getArmorSlots().forEach(itemStack -> {
                if (!itemStack.isEmpty() && itemStack.isDamageableItem()) {
                    itemStack.hurtAndBreak((int)(damagePerTick * 0.5), target,
                        (entity) -> entity.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                }
            });

            // 点燃目标5秒
            target.setSecondsOnFire(5);
        }
    }

    private LivingEntity getTarget() {
        if (cachedTarget != null && cachedTarget.isAlive()) {
            return cachedTarget;
        }

        if (targetUUID != null && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(targetUUID);
            if (entity instanceof LivingEntity living) {
                cachedTarget = living;
                return living;
            }
        }

        return null;
    }

    public LivingEntity getTargetEntity() {
        return getTarget();
    }

    private void updateTargetPos(Vec3 pos) {
        entityData.set(TARGET_X, (float) pos.x);
        entityData.set(TARGET_Y, (float) pos.y);
        entityData.set(TARGET_Z, (float) pos.z);
    }

    public Vec3 getTargetPos() {
        return new Vec3(
            entityData.get(TARGET_X),
            entityData.get(TARGET_Y),
            entityData.get(TARGET_Z)
        );
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_X, 0F);
        entityData.define(TARGET_Y, 0F);
        entityData.define(TARGET_Z, 0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.hasUUID("Target")) {
            targetUUID = pCompound.getUUID("Target");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (targetUUID != null) {
            pCompound.putUUID("Target", targetUUID);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public int getPortalWaitTime() {
        return Integer.MAX_VALUE;
    }

    @SubscribeEvent
    public static void enterChunk(EntityEvent.EnteringSection event) {
        if (!event.getEntity().level().isClientSide() && event.didChunkChange() && event.getEntity() instanceof LaserEntity) {
            ServerLevel level = ((ServerLevel) event.getEntity().level());
            if (!level.isPositionEntityTicking(SectionPos.of(event.getPackedNewPos()).center())) {
                event.getEntity().discard();
            }
        }
    }
}
