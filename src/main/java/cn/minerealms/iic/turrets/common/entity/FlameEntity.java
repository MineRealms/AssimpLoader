package cn.minerealms.iic.turrets.common.entity;

import cn.minerealms.iic.turrets.MekanismTurretsConfig;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretTier;
import cn.minerealms.iic.turrets.common.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 火焰弹实体 - 模拟真实火焰喷射器
 *
 * 工作原理（基于真实火焰喷射器）：
 * 1. 喷射燃料液体流（带重力）
 * 2. 直接命中实体造成即时伤害（5 damage）并穿透
 * 3. 接触地面后产生范围火焰云（3x3区域）
 * 4. 火焰云持续5秒，每tick造成持续伤害
 * 5. 点燃可燃方块
 *
 * Sources:
 * - https://www.slashgear.com/1351290/how-a-flamethrower-works-wwii/
 * - https://militarysphere.com/flamethrower-mechanisms/
 */
public class FlameEntity extends Entity {

    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(FlameEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(FlameEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(FlameEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(FlameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_REACHED_GROUND = SynchedEntityData.defineId(FlameEntity.class, EntityDataSerializers.BOOLEAN);

    private Vec3 startPos;
    private Vec3 targetPos;
    private Vec3 velocity;
    private int lifeTicks = 0;
    private static final float FLIGHT_SPEED = 0.8F; // 飞行速度（方块/tick）
    private static final float GRAVITY = 0.03F; // 重力加速度
    private boolean hasReachedGround = false;
    private Set<Integer> hitEntities = new HashSet<>(); // 已命中的实体ID（防止重复伤害）

    public FlameEntity(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.noPhysics = false; // 启用物理碰撞检测
        this.setInvisible(true); // 实体本身隐形，只显示粒子效果
    }

    public FlameEntity(Level pLevel, Vec3 startPos, LivingEntity target, FlameThrowerTurretTier tier) {
        this(EntityRegistry.FLAME.get(), pLevel);
        this.startPos = startPos;
        this.setPos(startPos);
        this.entityData.set(TIER, tier.ordinal());

        // 计算目标位置
        this.targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        updateTargetPos(targetPos);

        // 计算抛物线弹道的初速度（考虑重力）
        this.velocity = calculateBallisticVelocity(startPos, targetPos, FLIGHT_SPEED, GRAVITY);
    }

    /**
     * 计算抛物线弹道的初速度
     *
     * @param start 起点
     * @param target 目标点
     * @param speed 初速度大小
     * @param gravity 重力加速度
     * @return 初速度向量
     */
    private Vec3 calculateBallisticVelocity(Vec3 start, Vec3 target, float speed, float gravity) {
        // 计算水平和垂直距离
        double dx = target.x - start.x;
        double dy = target.y - start.y;
        double dz = target.z - start.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // 使用抛物线公式计算发射角度
        // v² = g * (d + sqrt(d² + h²))，其中 d = 水平距离，h = 垂直距离
        double v2 = speed * speed;
        double g = gravity;
        double discriminant = v2 * v2 - g * (g * horizontalDist * horizontalDist + 2 * dy * v2);

        // 如果无法到达目标（距离太远），使用45度角最大射程
        double angle;
        if (discriminant < 0) {
            angle = Math.PI / 4; // 45度角
        } else {
            // 选择较低的弹道（更直接）
            angle = Math.atan((v2 - Math.sqrt(discriminant)) / (g * horizontalDist));
        }

        // 计算水平和垂直速度分量
        double horizontalSpeed = speed * Math.cos(angle);
        double verticalSpeed = speed * Math.sin(angle);

        // 计算水平方向的单位向量
        double horizontalNorm = Math.sqrt(dx * dx + dz * dz);
        if (horizontalNorm < 0.001) {
            // 目标在正上方或正下方，直接垂直发射
            return new Vec3(0, dy > 0 ? speed : -speed, 0);
        }

        double vx = (dx / horizontalNorm) * horizontalSpeed;
        double vz = (dz / horizontalNorm) * horizontalSpeed;

        return new Vec3(vx, verticalSpeed, vz);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            lifeTicks++;

            // 飞行阶段
            if (!hasReachedGround) {
                // 应用重力
                velocity = velocity.add(0, -GRAVITY, 0);

                // 移动
                Vec3 newPos = position().add(velocity);
                this.setPos(newPos);

                // 检测直接命中实体（穿透）
                AABB collisionBox = getBoundingBox().inflate(0.3);
                List<LivingEntity> entities = level().getEntitiesOfClass(LivingEntity.class, collisionBox);
                for (LivingEntity entity : entities) {
                    if (!hitEntities.contains(entity.getId())) {
                        // 直接命中伤害
                        double directDamage = MekanismTurretsConfig.flameDirectHitDamage.get();
                        entity.hurt(level().damageSources().onFire(), (float) directDamage);
                        entity.setSecondsOnFire(5);
                        hitEntities.add(entity.getId());

                        // 穿透效果 - 不停止飞行
                    }
                }

                // 检测地面碰撞
                BlockPos belowPos = new BlockPos((int)Math.floor(getX()), (int)Math.floor(getY() - 0.1), (int)Math.floor(getZ()));
                BlockState belowState = level().getBlockState(belowPos);
                if (!belowState.isAir() && belowState.getFluidState().isEmpty()) {
                    // 接触地面，产生火焰云
                    reachGround();
                    return;
                }

                // 超时保护（最多飞行10秒）
                if (lifeTicks > 200) {
                    this.discard();
                    return;
                }
            }
            // 地面火焰云阶段
            else {
                int groundDuration = MekanismTurretsConfig.flameGroundDuration.get();
                if (lifeTicks >= groundDuration) {
                    this.discard();
                    return;
                }

                // 范围伤害
                double damageRadius = MekanismTurretsConfig.flameDamageRadius.get();
                AABB damageBox = new AABB(
                    position().subtract(damageRadius, damageRadius, damageRadius),
                    position().add(damageRadius, damageRadius, damageRadius)
                );
                List<LivingEntity> entities = level().getEntitiesOfClass(LivingEntity.class, damageBox);

                double damagePerTick = MekanismTurretsConfig.flameGroundDamagePerTick.get();
                for (LivingEntity entity : entities) {
                    if (entity.distanceToSqr(position()) <= damageRadius * damageRadius) {
                        entity.hurt(level().damageSources().onFire(), (float) damagePerTick);
                        entity.setSecondsOnFire(5);
                    }
                }

                // 生成火焰粒子效果
                if (level() instanceof ServerLevel serverLevel) {
                    // 火焰粒子
                    for (int i = 0; i < 8; i++) {
                        double offsetX = (random.nextDouble() - 0.5) * damageRadius * 2;
                        double offsetY = random.nextDouble() * 0.5;
                        double offsetZ = (random.nextDouble() - 0.5) * damageRadius * 2;
                        serverLevel.sendParticles(ParticleTypes.FLAME,
                                position().x + offsetX,
                                position().y + offsetY,
                                position().z + offsetZ,
                                1, 0, 0, 0, 0.02);
                    }
                    // 烟雾粒子
                    if (lifeTicks % 2 == 0) {
                        for (int i = 0; i < 4; i++) {
                            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                                    position().x + (random.nextDouble() - 0.5) * damageRadius,
                                    position().y,
                                    position().z + (random.nextDouble() - 0.5) * damageRadius,
                                    1, 0, 0.1, 0, 0.05);
                        }
                    }
                    // 熔岩粒子（增强视觉效果）
                    if (lifeTicks % 3 == 0) {
                        for (int i = 0; i < 2; i++) {
                            serverLevel.sendParticles(ParticleTypes.LAVA,
                                    position().x + (random.nextDouble() - 0.5) * damageRadius,
                                    position().y,
                                    position().z + (random.nextDouble() - 0.5) * damageRadius,
                                    1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        } else {
            // 客户端粒子效果 - 密集的火焰流
            if (!hasReachedGround) {
                // 检查 velocity 是否已初始化（客户端同步可能延迟）
                if (velocity == null) {
                    velocity = getDeltaMovement(); // 使用实体的运动向量作为备用
                    if (velocity.lengthSqr() < 0.001) {
                        // 如果还是没有速度，使用默认向前方向
                        velocity = new Vec3(0, 0, FLIGHT_SPEED);
                    }
                }

                // 飞行中的密集火焰流效果（沿着轨迹生成）
                Vec3 currentPos = position();
                Vec3 prevPos = currentPos.subtract(velocity.scale(0.5)); // 上一帧位置

                // 在当前位置和上一帧位置之间插值，形成连续的火焰流
                int particleCount = 20; // 增加粒子数量
                for (int i = 0; i < particleCount; i++) {
                    double t = i / (double) particleCount;
                    Vec3 interpPos = prevPos.add(velocity.scale(0.5 * t));

                    // 火焰粒子（主体）
                    for (int j = 0; j < 3; j++) {
                        double spread = 0.15; // 减小扩散，更集中
                        double offsetX = (random.nextDouble() - 0.5) * spread;
                        double offsetY = (random.nextDouble() - 0.5) * spread;
                        double offsetZ = (random.nextDouble() - 0.5) * spread;

                        level().addParticle(ParticleTypes.FLAME,
                                interpPos.x + offsetX,
                                interpPos.y + offsetY,
                                interpPos.z + offsetZ,
                                velocity.x * 0.1, velocity.y * 0.1, velocity.z * 0.1);
                    }

                    // 小火焰粒子（增加密度）
                    if (i % 2 == 0) {
                        level().addParticle(ParticleTypes.SMALL_FLAME,
                                interpPos.x + (random.nextDouble() - 0.5) * 0.1,
                                interpPos.y + (random.nextDouble() - 0.5) * 0.1,
                                interpPos.z + (random.nextDouble() - 0.5) * 0.1,
                                0, 0, 0);
                    }
                }

                // 烟雾尾迹（稀疏一些）
                for (int i = 0; i < 3; i++) {
                    level().addParticle(ParticleTypes.SMOKE,
                            position().x + (random.nextDouble() - 0.5) * 0.2,
                            position().y + (random.nextDouble() - 0.5) * 0.2,
                            position().z + (random.nextDouble() - 0.5) * 0.2,
                            0, 0.02, 0);
                }
            } else {
                // 地面火焰云效果
                double damageRadius = MekanismTurretsConfig.flameDamageRadius.get();
                for (int i = 0; i < 10; i++) {
                    double offsetX = (random.nextDouble() - 0.5) * damageRadius * 2;
                    double offsetY = random.nextDouble() * 0.5;
                    double offsetZ = (random.nextDouble() - 0.5) * damageRadius * 2;
                    level().addParticle(ParticleTypes.FLAME,
                            position().x + offsetX,
                            position().y + offsetY,
                            position().z + offsetZ,
                            0, 0.05, 0);
                }
                // 大量烟雾
                if (random.nextInt(2) == 0) {
                    for (int i = 0; i < 3; i++) {
                        level().addParticle(ParticleTypes.LARGE_SMOKE,
                                position().x + (random.nextDouble() - 0.5) * damageRadius,
                                position().y,
                                position().z + (random.nextDouble() - 0.5) * damageRadius,
                                0, 0.1, 0);
                    }
                }
            }
        }
    }

    /**
     * 接触地面，产生火焰云
     */
    private void reachGround() {
        if (hasReachedGround) return;
        hasReachedGround = true;
        entityData.set(HAS_REACHED_GROUND, true);
        lifeTicks = 0; // 重置计时器用于火焰云阶段

        if (level() instanceof ServerLevel serverLevel) {
            double damageRadius = MekanismTurretsConfig.flameDamageRadius.get();
            int radius = (int) Math.ceil(damageRadius);

            // 点燃周围可燃方块
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = blockPosition().offset(x, y, z);
                        BlockState state = level().getBlockState(pos);

                        // 检查是否在伤害半径内
                        if (Math.sqrt(x*x + y*y + z*z) <= damageRadius) {
                            // 点燃可燃方块
                            if (state.isFlammable(level(), pos, null)) {
                                BlockPos abovePos = pos.above();
                                if (level().getBlockState(abovePos).isAir()) {
                                    level().setBlockAndUpdate(abovePos, Blocks.FIRE.defaultBlockState());
                                }
                            }
                        }
                    }
                }
            }

            // 爆炸式火焰粒子效果
            for (int i = 0; i < 50; i++) {
                double offsetX = (random.nextDouble() - 0.5) * damageRadius * 2;
                double offsetY = random.nextDouble() * damageRadius;
                double offsetZ = (random.nextDouble() - 0.5) * damageRadius * 2;
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        position().x + offsetX,
                        position().y + offsetY,
                        position().z + offsetZ,
                        3, 0, 0, 0, 0.1);
            }
            // 大量烟雾
            for (int i = 0; i < 20; i++) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        position().x + (random.nextDouble() - 0.5) * damageRadius,
                        position().y,
                        position().z + (random.nextDouble() - 0.5) * damageRadius,
                        2, 0, 0.2, 0, 0.1);
            }
            // 熔岩粒子
            for (int i = 0; i < 10; i++) {
                serverLevel.sendParticles(ParticleTypes.LAVA,
                        position().x + (random.nextDouble() - 0.5) * damageRadius,
                        position().y,
                        position().z + (random.nextDouble() - 0.5) * damageRadius,
                        1, 0, 0, 0, 0);
            }
        }
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

    public FlameThrowerTurretTier getTier() {
        return FlameThrowerTurretTier.values()[entityData.get(TIER)];
    }

    public boolean hasReachedGround() {
        return entityData.get(HAS_REACHED_GROUND);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TARGET_X, 0F);
        entityData.define(TARGET_Y, 0F);
        entityData.define(TARGET_Z, 0F);
        entityData.define(TIER, 0);
        entityData.define(HAS_REACHED_GROUND, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.contains("Tier")) {
            entityData.set(TIER, pCompound.getInt("Tier"));
        }
        lifeTicks = pCompound.getInt("LifeTicks");
        hasReachedGround = pCompound.getBoolean("HasReachedGround");
        if (pCompound.contains("StartX")) {
            startPos = new Vec3(
                    pCompound.getDouble("StartX"),
                    pCompound.getDouble("StartY"),
                    pCompound.getDouble("StartZ")
            );
        }
        if (pCompound.contains("TargetPosX")) {
            targetPos = new Vec3(
                    pCompound.getDouble("TargetPosX"),
                    pCompound.getDouble("TargetPosY"),
                    pCompound.getDouble("TargetPosZ")
            );
        }
        if (pCompound.contains("VelocityX")) {
            velocity = new Vec3(
                    pCompound.getDouble("VelocityX"),
                    pCompound.getDouble("VelocityY"),
                    pCompound.getDouble("VelocityZ")
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        pCompound.putInt("Tier", entityData.get(TIER));
        pCompound.putInt("LifeTicks", lifeTicks);
        pCompound.putBoolean("HasReachedGround", hasReachedGround);
        if (startPos != null) {
            pCompound.putDouble("StartX", startPos.x);
            pCompound.putDouble("StartY", startPos.y);
            pCompound.putDouble("StartZ", startPos.z);
        }
        if (targetPos != null) {
            pCompound.putDouble("TargetPosX", targetPos.x);
            pCompound.putDouble("TargetPosY", targetPos.y);
            pCompound.putDouble("TargetPosZ", targetPos.z);
        }
        if (velocity != null) {
            pCompound.putDouble("VelocityX", velocity.x);
            pCompound.putDouble("VelocityY", velocity.y);
            pCompound.putDouble("VelocityZ", velocity.z);
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
}
