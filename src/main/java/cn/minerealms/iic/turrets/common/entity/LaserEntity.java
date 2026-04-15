package cn.minerealms.iic.turrets.common.entity;

import cn.minerealms.iic.turrets.common.block_entity.LaserTurretTier;
import cn.minerealms.iic.turrets.common.registry.DamageTypeRegistry;
import cn.minerealms.iic.turrets.common.registry.EntityRegistry;
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

    // 目标位置
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(LaserEntity.class, EntityDataSerializers.INT);

    private UUID targetUUID;
    private LivingEntity cachedTarget;
    private float damagePerTick = 1.0F;
    private Vec3 lastSyncedPos = Vec3.ZERO;
    private int armorDamageTicks = 0;

    public LaserEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = true;
    }

    public LaserEntity(Level pLevel, Vec3 pos, LivingEntity target, float damagePerTick, LaserTurretTier tier) {
        this(EntityRegistry.LASER.get(), pLevel);
        this.setPos(pos);
        this.targetUUID = target.getUUID();
        this.cachedTarget = target;
        this.damagePerTick = damagePerTick;
        this.entityData.set(TIER, tier.ordinal());
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

            // 只在目标位置变化超过0.1格时才同步，减少网络流量
            if(lastSyncedPos.distanceToSqr(targetPos) > 0.01) {
                updateTargetPos(targetPos);
                lastSyncedPos = targetPos;
            }

            // 直接扣血，绕过无敌时间机制，允许多个激光叠加伤害
            // 使用 setHealth 而不是 hurt，这样多个炮塔的伤害可以累积
            float currentHealth = target.getHealth();
            float newHealth = Math.max(0, currentHealth - damagePerTick);
            target.setHealth(newHealth);

            // 触发受伤动画和音效
            target.hurt(level().damageSources().magic(), 0.01f);

            // 每10 tick损坏一次护甲，而不是每tick，减少事件触发
            if(++armorDamageTicks >= 10) {
                armorDamageTicks = 0;
                float accumulatedDamage = damagePerTick * 10 * 0.5f;
                target.getArmorSlots().forEach(itemStack -> {
                    if (!itemStack.isEmpty() && itemStack.isDamageableItem()) {
                        itemStack.hurtAndBreak((int)accumulatedDamage, target,
                            (entity) -> entity.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                    }
                });
            }

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

    public LaserTurretTier getTier() {
        return LaserTurretTier.values()[entityData.get(TIER)];
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_X, 0F);
        entityData.define(TARGET_Y, 0F);
        entityData.define(TARGET_Z, 0F);
        entityData.define(TIER, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.hasUUID("Target")) {
            targetUUID = pCompound.getUUID("Target");
        }
        if (pCompound.contains("Tier")) {
            entityData.set(TIER, pCompound.getInt("Tier"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (targetUUID != null) {
            pCompound.putUUID("Target", targetUUID);
        }
        pCompound.putInt("Tier", entityData.get(TIER));
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
