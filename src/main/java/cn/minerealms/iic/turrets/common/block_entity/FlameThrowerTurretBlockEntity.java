package cn.minerealms.iic.turrets.common.block_entity;

import cn.minerealms.iic.turrets.MekanismTurretsConfig;
import cn.minerealms.iic.turrets.common.entity.FlameEntity;
import cn.minerealms.iic.turrets.common.registry.SoundRegistry;
import mekanism.api.*;
import mekanism.api.chemical.ChemicalTankBuilder;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.math.FloatingLong;
import mekanism.api.providers.IBlockProvider;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
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

/**
 * 火焰喷射器炮塔方块实体
 *
 * 特性：
 * - 32格射程
 * - 发射火焰弹，有飞行时间
 * - 火焰弹到达目标后形成范围伤害区域
 * - 消耗汽油（从下方方块获取）
 * - 支持目标过滤（敌对/被动/玩家）
 */
public class FlameThrowerTurretBlockEntity extends TileEntityMekanism implements GeoBlockEntity {

    @WrappingComputerMethod(wrapper = SpecialComputerMethodWrapper.ComputerIInventorySlotWrapper.class, methodNames = "getEnergyItem", docPlaceholder = "energy slot")
    EnergyInventorySlot energySlot;

    public static SerializableDataTicket<Boolean> HAS_TARGET;
    public static SerializableDataTicket<Double> TARGET_POS_X;
    public static SerializableDataTicket<Double> TARGET_POS_Y;
    public static SerializableDataTicket<Double> TARGET_POS_Z;

    private static final RawAnimation SHOOT_ANIMATION = RawAnimation.begin().then("shoot", Animation.LoopType.PLAY_ONCE);
    private static final ResourceLocation GASOLINE_ID = new ResourceLocation("gtceu", "gasoline");

    private AABB targetBox;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private FlameThrowerTurretTier tier;
    private MachineEnergyContainer<FlameThrowerTurretBlockEntity> energyContainer;

    // 目标过滤设置
    private boolean targetsHostile = true;
    private boolean targetsPassive = false;
    private boolean targetsPlayers = false;
    private boolean targetsTrusted = true;

    // 攻击模式：false = 精准模式，true = 范围模式
    private boolean areaMode = false;

    private @Nullable LivingEntity target;
    public float xRot0 = 0;
    public float yRot0 = 0;
    private int coolDown = 0;
    private int idleTicks = 0;
    private int fuelPerShot = 10; // 每次发射消耗10mB汽油
    private int lastUpgradeCount = -1;
    private int targetValidationCounter = 0;

    public FlameThrowerTurretBlockEntity(IBlockProvider blockProvider, BlockPos pos, BlockState state) {
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

    public MachineEnergyContainer<FlameThrowerTurretBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    @Override
    protected @Nullable IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(this::getDirection);
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 143, 35), RelativeSide.BACK);
        return builder.build();
    }

    public FlameThrowerTurretTier getTier() {
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

    public boolean isAreaMode() {
        return areaMode;
    }

    public void setAreaMode(boolean areaMode) {
        this.areaMode = areaMode;
    }

    public boolean hasTarget() {
        return target != null;
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();

        // 冷却计时
        if (coolDown > 0) {
            coolDown--;
        }

        // 每5 tick验证一次目标有效性
        if (++targetValidationCounter >= 5) {
            targetValidationCounter = 0;
            tryInvalidateTarget();
        }

        tryFindTarget();

        // 更新燃料消耗值
        int currentUpgradeCount = upgradeComponent.getUpgrades(Upgrade.SPEED);
        if (currentUpgradeCount != lastUpgradeCount) {
            lastUpgradeCount = currentUpgradeCount;
            fuelPerShot = MekanismTurretsConfig.flameThrowerTurretFuelPerShot.get() * (currentUpgradeCount + 1);
        }

        if (target != null) {
            // 发射前再次验证目标有效性
            if (!target.isAlive() || target.isRemoved()) {
                target = null;
                setAnimData(HAS_TARGET, false);
                return;
            }

            Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
            setAnimData(TARGET_POS_X, targetPos.x);
            setAnimData(TARGET_POS_Y, targetPos.y);
            setAnimData(TARGET_POS_Z, targetPos.z);
            setAnimData(HAS_TARGET, true);

            // 冷却完成且有足够燃料时发射
            if (coolDown <= 0 && hasSufficientFuel()) {
                shootFlame();
                consumeFuel();
                coolDown = tier.getCooldown() / (upgradeComponent.getUpgrades(Upgrade.SPEED) + 1);
            }
        } else {
            setAnimData(HAS_TARGET, false);
        }
    }

    /**
     * 检查是否有足够的燃料（从下方方块获取）
     */
    private boolean hasSufficientFuel() {
        BlockPos below = getBlockPos().below();
        var blockEntity = level.getBlockEntity(below);
        if (blockEntity != null) {
            var fluidHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            if (fluidHandler != null) {
                // 检查是否有足够的汽油
                FluidStack drained = fluidHandler.drain(fuelPerShot, IFluidHandler.FluidAction.SIMULATE);
                if (!drained.isEmpty() && drained.getAmount() >= fuelPerShot) {
                    // 检查是否是汽油
                    String fluidId = ForgeRegistries.FLUIDS.getKey(drained.getFluid()).toString();
                    return fluidId.contains("gasoline");
                }
            }
        }
        return false;
    }

    /**
     * 消耗燃料（从下方方块抽取）
     */
    private void consumeFuel() {
        BlockPos below = getBlockPos().below();
        var blockEntity = level.getBlockEntity(below);
        if (blockEntity != null) {
            var fluidHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
            if (fluidHandler != null) {
                fluidHandler.drain(fuelPerShot, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private void shootFlame() {
        if (target != null) {
            int mufflerCount = getComponent().getUpgrades(Upgrade.MUFFLING);
            float volume = 1.0F - (mufflerCount / (float) Upgrade.MUFFLING.getMax());
            level.playSound(null, getBlockPos(), SoundRegistry.TURRET_SHOOT.get(), SoundSource.BLOCKS, volume, 0.8F);

            triggerAnim("controller", "shoot");

            // Quagmire节点在模型坐标系中的位置：pivot = [21.5, 27, -1]
            // bone节点有rotation [0, -90, 0]，所以模型坐标系被旋转了-90度
            // 模型坐标系的+X对应世界坐标系的+Z
            // 转换为方块坐标系（除以16）
            Vec3 center = getBlockPos().getCenter();

            // Quagmire在模型坐标系中：[21.5, 27, -1]
            // 应用bone的-90度Y轴旋转：X -> Z, Z -> -X
            // 世界坐标系偏移：[1, 1.6875, 1.34375]（相对于方块中心）
            double muzzleOffsetX = -(-1.0) / 16.0; // 模型Z -> 世界-X，取反：0.0625格
            double muzzleOffsetY = 27.0 / 16.0 - 0.5; // Y不变：1.1875格
            double muzzleOffsetZ = 21.5 / 16.0; // 模型X -> 世界Z：1.34375格

            // 应用yaw旋转（turret的Y轴旋转）
            double cosYaw = Math.cos(yRot0);
            double sinYaw = Math.sin(yRot0);
            double rotatedX = muzzleOffsetX * cosYaw - muzzleOffsetZ * sinYaw;
            double rotatedZ = muzzleOffsetX * sinYaw + muzzleOffsetZ * cosYaw;

            Vec3 muzzlePos = center.add(rotatedX, muzzleOffsetY, rotatedZ);

            FlameEntity flame = new FlameEntity(level, muzzlePos, target, tier);
            level.addFreshEntity(flame);
        }
    }

    public void tryInvalidateTarget() {
        if (target == null) {
            return;
        }

        if (!target.isAlive() || !target.canBeSeenAsEnemy()) {
            setAnimData(HAS_TARGET, false);
            target = null;
            return;
        }

        double maxRangeSq = getTier().getRange() * getTier().getRange();
        if (target.distanceToSqr(this.getBlockPos().getCenter()) > maxRangeSq) {
            setAnimData(HAS_TARGET, false);
            target = null;
            return;
        }

        if (!isValidTarget(target)) {
            setAnimData(HAS_TARGET, false);
            target = null;
        }
    }

    private void tryFindTarget() {
        if (idleTicks-- > 0) {
            return;
        }
        if (target == null && (level.getGameTime() + this.hashCode()) % 3 == 0) {
            List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, targetBox, this::isValidTarget);

            if (candidates.isEmpty()) {
                idleTicks = 20 * 4;
                return;
            }

            // 范围模式：优先选择密集区域
            if (areaMode) {
                target = findDensestTarget(candidates);
            }
            // 精准模式：选择最近的目标
            else {
                Optional<LivingEntity> optional = candidates.stream()
                        .min((o1, o2) -> Double.compare(
                                o1.distanceToSqr(this.getBlockPos().getCenter()),
                                o2.distanceToSqr(this.getBlockPos().getCenter())
                        ));
                target = optional.orElse(null);
            }

            if (target != null) {
                setAnimData(HAS_TARGET, true);
            } else {
                idleTicks = 20 * 4;
            }
        }
    }

    /**
     * 范围模式：找到6x6范围内实体最密集的目标
     */
    private LivingEntity findDensestTarget(List<LivingEntity> candidates) {
        LivingEntity bestTarget = null;
        int maxDensity = 0;

        for (LivingEntity candidate : candidates) {
            // 计算该候选目标周围6x6范围内的实体数量
            AABB densityBox = new AABB(
                    candidate.position().subtract(3, 3, 3),
                    candidate.position().add(3, 3, 3)
            );
            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    densityBox,
                    this::isValidTarget
            );

            int density = nearbyEntities.size();
            if (density > maxDensity) {
                maxDensity = density;
                bestTarget = candidate;
            }
        }

        // 如果没有找到密集区域（maxDensity < 2），回退到最近目标
        if (maxDensity < 2 && !candidates.isEmpty()) {
            return candidates.stream()
                    .min((o1, o2) -> Double.compare(
                            o1.distanceToSqr(this.getBlockPos().getCenter()),
                            o2.distanceToSqr(this.getBlockPos().getCenter())
                    ))
                    .orElse(null);
        }

        return bestTarget;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null) {
            return false;
        }
        if (!e.canBeSeenAsEnemy()) {
            return false;
        }
        if (e.distanceToSqr(this.getBlockPos().getCenter()) > getTier().getRange() * getTier().getRange()) {
            return false;
        }
        if (MekanismTurretsConfig.blacklistedEntities == null) {
            return false;
        }
        if (MekanismTurretsConfig.blacklistedEntities.get().stream()
                .map(s -> ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(s)))
                .anyMatch(entityType -> e.getType().equals(entityType))) {
            return false;
        }
        if (!turretFlagsAllowTargeting(e)) {
            return false;
        }
        if (!canSeeTarget(e)) {
            return false;
        }
        return true;
    }

    private boolean turretFlagsAllowTargeting(LivingEntity e) {
        MobCategory category = e.getType().getCategory();
        if (this.targetsHostile && !category.isFriendly()) {
            return true;
        }
        if (this.targetsPassive && category.isFriendly() && !category.equals(MobCategory.MISC)) {
            return true;
        }
        UUID owner = SecurityUtils.get().getOwnerUUID(this);
        if (this.targetsPlayers && e instanceof Player player) {
            if (!player.getUUID().equals(owner)) {
                if (this.targetsTrusted) {
                    return true;
                } else {
                    SecurityFrequency frequency = FrequencyType.SECURITY.getManager(null).getFrequency(owner);
                    if (frequency == null) {
                        return true;
                    }
                    if (!frequency.getTrustedUUIDs().contains(player.getUUID())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canSeeTarget(LivingEntity e) {
        Vec3 center = getBlockPos().getCenter();
        Vec3 targetPos = e.position().add(0, (e.getBbHeight() * 0.75), 0);
        Vec3 lookVec = center.vectorTo(targetPos).normalize().scale(0.75F);
        ClipContext ctx = new ClipContext(center.add(lookVec), targetPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, null);
        return level.clip(ctx).getType().equals(HitResult.Type.MISS);
    }

    @Override
    public void parseUpgradeData(@NotNull IUpgradeData data) {
        if (data instanceof FlameThrowerTurretUpgradeData upgradeData) {
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
        return new FlameThrowerTurretUpgradeData(targetsHostile, targetsPassive, targetsPlayers, targetsTrusted, getComponents());
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
        tag.putBoolean("areaMode", areaMode);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        NBTUtils.setBooleanIfPresent(tag, "targetsHostile", value -> targetsHostile = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPassive", value -> targetsPassive = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPlayers", value -> targetsPlayers = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsTrusted", value -> targetsTrusted = value);
        NBTUtils.setBooleanIfPresent(tag, "areaMode", value -> areaMode = value);
    }

    @Override
    public @NotNull CompoundTag getReducedUpdateTag() {
        CompoundTag tag = super.getReducedUpdateTag();
        tag.putBoolean("targetsHostile", targetsHostile);
        tag.putBoolean("targetsPassive", targetsPassive);
        tag.putBoolean("targetsPlayers", targetsPlayers);
        tag.putBoolean("targetsTrusted", targetsTrusted);
        tag.putBoolean("areaMode", areaMode);
        return tag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag) {
        super.handleUpdateTag(tag);
        NBTUtils.setBooleanIfPresent(tag, "targetsHostile", value -> targetsHostile = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPassive", value -> targetsPassive = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsPlayers", value -> targetsPlayers = value);
        NBTUtils.setBooleanIfPresent(tag, "targetsTrusted", value -> targetsTrusted = value);
        NBTUtils.setBooleanIfPresent(tag, "areaMode", value -> areaMode = value);
    }

    public void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        if (!this.level.isClientSide()) sendUpdatePacket();
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        tier = Attribute.getTier(getBlockType(), FlameThrowerTurretTier.class);
        double range = tier.getRange();
        targetBox = AABB.ofSize(getBlockPos().getCenter(), range * 2, range * 2, range * 2);
    }

    @Override
    public @NotNull net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable(getBlockType().getDescriptionId());
    }

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
