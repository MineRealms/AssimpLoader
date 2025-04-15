package cn.minerealms.iic.turrets.common.block_entity;

import cn.minerealms.iic.turrets.MekanismTurretsConfig;
import cn.minerealms.iic.turrets.LaserDamageConfig;
import cn.minerealms.iic.turrets.common.entity.LaserEntity;
import cn.minerealms.iic.turrets.common.registry.SoundRegistry;
import cn.minerealms.iic.turrets.common.scheduler.Scheduler;
import mekanism.api.*;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.integration.computer.SpecialComputerMethodWrapper;
import mekanism.common.integration.computer.annotation.WrappingComputerMethod;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.security.SecurityFrequency;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.upgrade.IUpgradeData;
import mekanism.common.util.NBTUtils;
import mekanism.common.util.SecurityUtils;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.network.SerializableDataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.*;

public class LaserTurretBlockEntity extends TileEntityMekanism implements GeoBlockEntity {

    @WrappingComputerMethod(wrapper = SpecialComputerMethodWrapper.ComputerIInventorySlotWrapper.class, methodNames = "getEnergyItem", docPlaceholder = "energy slot")
    EnergyInventorySlot energySlot;
    public static SerializableDataTicket<Boolean> HAS_TARGET;
    public static SerializableDataTicket<Double> TARGET_POS_X;
    public static SerializableDataTicket<Double> TARGET_POS_Y;
    public static SerializableDataTicket<Double> TARGET_POS_Z;
    private static final RawAnimation SHOOT_ANIMATION = RawAnimation.begin().then("shoot", Animation.LoopType.PLAY_ONCE);
    private AABB targetBox;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private LaserTurretTier tier;
    private MachineEnergyContainer<LaserTurretBlockEntity> energyContainer;
    private boolean targetsHostile = true;
    private boolean targetsPassive = false;
    private boolean targetsPlayers = false;
    private boolean targetsTrusted = true;
    private @Nullable LivingEntity target;
    private @Nullable LaserEntity activeLaser;
    public float xRot0 = 0;
    public float yRot0 = 0;
    private int coolDown = 0;
    private int idleTicks = 0;
    private FloatingLong cachedEnergyPerTick = FloatingLong.ZERO;
    private int lastUpgradeCount = -1;
    private int targetValidationCounter = 0;

    public LaserTurretBlockEntity(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
        super(blockProvider, pos, state);
    }

    @Override
    public void blockRemoved() {
        Vec3 pos = getBlockPos().getCenter();
        for (Upgrade upgrade : upgradeComponent.getInstalledTypes()) {
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z,
                    UpgradeUtils.getStack(upgrade, upgradeComponent.getUpgrades(upgrade))));
        }
        super.blockRemoved();
    }

    @Override
    protected @Nullable IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(this::getDirection);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));

        return builder.build();
    }

    public MachineEnergyContainer<LaserTurretBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    @Override
    protected @Nullable IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 143, 35), RelativeSide.BACK);
        return builder.build();
    }

    public LaserTurretTier getTier() {
        return tier;
    }

    public boolean targetsHostile() {
        return targetsHostile;
    }

    public void setTargetsHostile(boolean targetsHostile) {
        this.targetsHostile = targetsHostile;
    }

    public boolean targetsPassive() {
        return targetsPassive;
    }

    public void setTargetsPassive(boolean targetsPassive) {
        this.targetsPassive = targetsPassive;
    }

    public boolean targetsPlayers() {
        return targetsPlayers;
    }

    public void setTargetsPlayers(boolean targetsPlayers) {
        this.targetsPlayers = targetsPlayers;
    }

    public boolean targetsTrusted() {
        return targetsTrusted;
    }

    public void setTargetsTrusted(boolean targetsTrusted) {
        this.targetsTrusted = targetsTrusted;
    }

    public boolean hasTarget() {
        return target != null;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();

        // 每5 tick验证一次目标有效性，而不是每tick
        if(++targetValidationCounter >= 5) {
            targetValidationCounter = 0;
            tryInvalidateTarget();
        }

        tryFindTarget();

        // 缓存能量消耗值，只在升级变化时重新计算
        int currentUpgradeCount = upgradeComponent.getUpgrades(Upgrade.SPEED);
        if(currentUpgradeCount != lastUpgradeCount) {
            lastUpgradeCount = currentUpgradeCount;
            cachedEnergyPerTick = FloatingLong.create(laserEnergyPerTick());
        }
        energyContainer.setEnergyPerTick(cachedEnergyPerTick);

        if(target != null) {
            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            setAnimData(TARGET_POS_X, targetPos.x);
            setAnimData(TARGET_POS_Y, targetPos.y);
            setAnimData(TARGET_POS_Z, targetPos.z);
            setAnimData(HAS_TARGET, true);

            if(energyContainer.getEnergy().greaterOrEqual(cachedEnergyPerTick)) {
                if(activeLaser == null || !activeLaser.isAlive()) {
                    startLaser();
                }
                energyContainer.extract(cachedEnergyPerTick, Action.EXECUTE, AutomationType.INTERNAL);
            } else {
                stopLaser();
            }
        } else {
            stopLaser();
        }
    }

    private void startLaser() {
        if(target != null && activeLaser == null) {
            int mufflerCount = getComponent().getUpgrades(Upgrade.MUFFLING);
            float volume = 1.0F - (mufflerCount / (float) Upgrade.MUFFLING.getMax());
            level.playSound(null, getBlockPos(), SoundRegistry.TURRET_SHOOT.get(), SoundSource.BLOCKS, volume, 1.0F);

            triggerAnim("controller", "shoot");

            Vec3 center = getBlockPos().getCenter().add(0, -0.15, 0);
            float damagePerTick = switch(tier) {
                case BASIC -> LaserDamageConfig.getBasicDamage();
                case ADVANCED -> LaserDamageConfig.getAdvancedDamage();
                case ELITE -> LaserDamageConfig.getEliteDamage();
                case ULTIMATE -> LaserDamageConfig.getUltimateDamage();
            };
            activeLaser = new LaserEntity(level, center, target, damagePerTick, tier);
            level.addFreshEntity(activeLaser);
        }
    }

    private void stopLaser() {
        if(activeLaser != null) {
            activeLaser.discard();
            activeLaser = null;
        }
    }

    private int laserEnergyPerTick() {
        return 50 * (tier.ordinal() + 1) * (upgradeComponent.getUpgrades(Upgrade.SPEED) + 1);
    }

    public void tryInvalidateTarget() {
        if(target == null) {
            return;
        }

        // 快速路径：先检查简单条件
        if(!target.isAlive() || !target.canBeSeenAsEnemy()) {
            setAnimData(HAS_TARGET, false);
            stopLaser();
            target = null;
            return;
        }

        // 快速距离检查（避免平方根计算）
        double maxRangeSq = getTier().getRange() * getTier().getRange();
        if(target.distanceToSqr(this.getBlockPos().getCenter()) > maxRangeSq) {
            setAnimData(HAS_TARGET, false);
            stopLaser();
            target = null;
            return;
        }

        // 完整验证（包括射线追踪等昂贵操作）
        if(!isValidTarget(target)) {
            setAnimData(HAS_TARGET, false);
            stopLaser();
            target = null;
        }
    }

    private void tryFindTarget() {
        if(idleTicks-- > 0) {
            return;
        }
        if(target == null && (level.getGameTime()+this.hashCode()) % 3 == 0) {
            List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, targetBox, this::isValidTarget);

            if(candidates.isEmpty()) {
                idleTicks = 20 * 4;
                return;
            }

            // 一次性计算所有候选目标被瞄准的次数，避免重复扫描
            Map<LivingEntity, Integer> targetCounts = new HashMap<>();
            AABB searchBox = AABB.ofSize(getBlockPos().getCenter(), 100, 100, 100);
            List<LaserEntity> nearbyLasers = level.getEntitiesOfClass(LaserEntity.class, searchBox);

            for(LivingEntity candidate : candidates) {
                int count = 0;
                for(LaserEntity laser : nearbyLasers) {
                    if(laser.getTargetEntity() != null && laser.getTargetEntity().equals(candidate)) {
                        count++;
                    }
                }
                targetCounts.put(candidate, count);
            }

            // 选择被瞄准次数最少且距离最近的目标
            Optional<LivingEntity> optional = candidates.stream()
                    .filter(entity -> targetCounts.get(entity) < 8)
                    .min((o1, o2) -> {
                        int count1 = targetCounts.get(o1);
                        int count2 = targetCounts.get(o2);
                        if(count1 != count2) {
                            return Integer.compare(count1, count2);
                        }
                        return Double.compare(
                                o1.distanceToSqr(this.getBlockPos().getCenter()),
                                o2.distanceToSqr(this.getBlockPos().getCenter())
                        );
                    });
            if(optional.isPresent()) {
                this.target = optional.get();
                setAnimData(HAS_TARGET, true);
            } else {
                idleTicks = 20 * 4;
            }
        }
    }

    private boolean isValidTarget(LivingEntity e) {
        if(e == null) {
            return false;
        }
        if(!e.canBeSeenAsEnemy()) {
            return false;
        }
        if(e.distanceToSqr(this.getBlockPos().getCenter()) > getTier().getRange()*getTier().getRange()) {
            return false;
        }
        if(MekanismTurretsConfig.blacklistedEntities == null) {
            return false;
        }
        if(MekanismTurretsConfig.blacklistedEntities.get().stream().map(s -> ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(s))).anyMatch(entityType -> e.getType().equals(entityType))) {
            return false;
        }
        if(!turretFlagsAllowTargeting(e)) {
            return false;
        }
        if(!canSeeTarget(e)) {
            return false;
        }
        return true;
    }

    private boolean turretFlagsAllowTargeting(LivingEntity e) {
        MobCategory category = e.getType().getCategory();
        if(this.targetsHostile && !category.isFriendly()) {
            return true;
        }
        if(this.targetsPassive && category.isFriendly() && !category.equals(MobCategory.MISC)) {
            return true;
        }
        UUID owner = SecurityUtils.get().getOwnerUUID(this);
        if(this.targetsPlayers && e instanceof Player player) {
            if(!player.getUUID().equals(owner)) {
                if(this.targetsTrusted) {
                    // turret targets ALL players
                    return true;
                } else {
                    SecurityFrequency frequency = FrequencyType.SECURITY.getManager(null).getFrequency(owner);
                    if(frequency == null) {
                        // if frequency is null, the owner has not "trusted" any players, return true
                        return true;
                    }
                    if(!frequency.getTrustedUUIDs().contains(player.getUUID())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canSeeTarget(LivingEntity e) {
        Vec3 center = getBlockPos().getCenter();
        Vec3 targetPos = e.position().add(0, (e.getBbHeight()*0.75), 0);
        Vec3 lookVec = center.vectorTo(targetPos).normalize().scale(0.75F);
        ClipContext ctx = new ClipContext(center.add(lookVec), targetPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null);
        return level.clip(ctx).getType().equals(HitResult.Type.MISS);
    }

    @Override
    public void parseUpgradeData(@NotNull IUpgradeData data) {
        if(data instanceof LaserTurretUpgradeData upgradeData) {
            this.targetsHostile = upgradeData.targetsHostile();
            this.targetsPassive = upgradeData.targetsPassive();
            this.targetsPlayers = upgradeData.targetsPlayers();
            this.targetsTrusted = upgradeData.targetsTrusted();
            for (ITileComponent component : getComponents()) {
                component.read(upgradeData.components());
            }
        } else {
            super.parseUpgradeData(data);
        }
    }

    @Override
    public @Nullable IUpgradeData getUpgradeData() {
        return new LaserTurretUpgradeData(targetsHostile, targetsPassive, targetsPlayers, targetsTrusted, getComponents());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        markUpdated();
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("targetsHostile", targetsHostile);
        tag.putBoolean("targetsPassive", targetsPassive);
        tag.putBoolean("targetsPlayers", targetsPlayers);
        tag.putBoolean("targetsTrusted", targetsTrusted);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        NBTUtils.setBooleanIfPresent(tag, "targetsHostile", value -> targetsHostile = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPassive", value -> targetsPassive = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPlayers", value -> targetsPlayers = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsTrusted", value -> targetsTrusted = value);
    }

    @Override
    public @NotNull CompoundTag getReducedUpdateTag() {
        CompoundTag tag = super.getReducedUpdateTag();
        tag.putBoolean("targetsHostile", targetsHostile);
        tag.putBoolean("targetsPassive", targetsPassive);
        tag.putBoolean("targetsPlayers", targetsPlayers);
        tag.putBoolean("targetsTrusted", targetsTrusted);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        super.handleUpdateTag(tag);
        NBTUtils.setBooleanIfPresent(tag, "targetsHostile", value -> targetsHostile = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPassive", value -> targetsPassive = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPlayers", value -> targetsPlayers = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsTrusted", value -> targetsTrusted = value);
    }

    public void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        if(!this.level.isClientSide()) sendUpdatePacket();
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        tier = Attribute.getTier(getBlockType(), LaserTurretTier.class);
        // 在tier设置后初始化targetBox
        double range = tier.getRange();
        targetBox = AABB.ofSize(getBlockPos().getCenter(), range*2, range*2, range*2);
    }

    @Override
    public @NotNull net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable(getBlockType().getDescriptionId());
    }

    // ==================== 修复：移回类内部 ====================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE)
                .triggerableAnim("shoot", SHOOT_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}