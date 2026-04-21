package cn.minerealms.iic.turrets.common.block_entity;

import cn.minerealms.iic.turrets.common.registry.BlockEntityTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Energy Pedestal Block Entity - 能量底座方块实体
 *
 * 功能：
 * - 存储 FE 能量（128k FE）
 * - 存储流体（16k mB汽油）
 * - 从一个面接收能量（FE 和 GTEU）
 * - 从东南西北四个侧面接收流体（无论是否是能量输入面）
 * - 向其他五个面输出 FE 能量和流体（供炮塔使用）
 * - 单个面可同时输出能量和流体
 * - 自动向所有非输入面的相邻方块供能和供液
 */
public class EnergyPedestalBlockEntity extends BlockEntity implements Nameable {

    private static final int MAX_ENERGY = 128_000;  // 128k FE
    private static final int MAX_FLUID = 16_000;    // 16k mB
    private static final int TRANSFER_RATE = 10_000; // 10k FE/t
    private static final int FLUID_TRANSFER_RATE = 1000; // 1000 mB/t
    private static final boolean GTCEU_LOADED = ModList.get().isLoaded("gtceu");

    private final EnergyStorage energyStorage = new EnergyStorage(MAX_ENERGY);
    private final FluidTank fluidTank = new FluidTank(MAX_FLUID, fluid -> {
        // 只接受汽油
        String fluidId = ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString();
        return fluidId.contains("gasoline");
    });
    private final LazyOptional<IEnergyStorage> feHandler = LazyOptional.of(() -> energyStorage);
    private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(() -> fluidTank);
    private LazyOptional<?> gteuHandler = LazyOptional.empty();

    public EnergyPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityTypeRegistry.ENERGY_PEDESTAL.get(), pos, state);
        if (GTCEU_LOADED) {
            try {
                gteuHandler = LazyOptional.of(() -> new GTEUEnergyWrapper(energyStorage));
            } catch (Exception e) {
                // GTCEU 不可用，忽略
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyPedestalBlockEntity blockEntity) {
        blockEntity.supplyEnergyToTurret();
        blockEntity.supplyFluidToTurret();
    }

    /**
     * 获取能量输入面（玩家放置时面向的方向）
     */
    private Direction getEnergyInputFace() {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return Direction.DOWN; // 默认底部
    }

    /**
     * 检查指定面是否可以接收能量
     */
    private boolean canReceiveEnergyFromSide(@Nullable Direction side) {
        if (side == null) return false;
        return side == getEnergyInputFace();
    }

    /**
     * 检查指定面是否可以放置炮塔（即不是能量输入面）
     */
    public boolean canPlaceTurretOnSide(Direction side) {
        return side != getEnergyInputFace();
    }

    /**
     * 检查指定面是否可以接收流体
     * 东南西北四个侧面都可以接收流体
     */
    private boolean canReceiveFluidFromSide(@Nullable Direction side) {
        if (side == null) return false;
        // 上下面不接收流体，只有东南西北四个侧面可以
        return side != Direction.UP && side != Direction.DOWN;
    }

    /**
     * 向所有可输出面的相邻方块传输能量
     */
    private void supplyEnergyToTurret() {
        if (level == null || energyStorage.getEnergyStored() == 0) {
            return;
        }

        Direction inputFace = getEnergyInputFace();

        // 遍历所有6个方向
        for (Direction direction : Direction.values()) {
            // 跳过能量输入面
            if (direction == inputFace) {
                continue;
            }

            // 检查该方向是否还有能量可以传输
            if (energyStorage.getEnergyStored() == 0) {
                break;
            }

            // 获取该方向的相邻方块
            BlockPos neighborPos = worldPosition.relative(direction);
            BlockEntity neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                // 从相邻方块的相对面接收能量（例如：我们向上传输，炮塔从下方接收）
                Direction receivingSide = direction.getOpposite();

                neighborEntity.getCapability(ForgeCapabilities.ENERGY, receivingSide).ifPresent(handler -> {
                    if (handler.canReceive()) {
                        int toExtract = Math.min(TRANSFER_RATE, energyStorage.getEnergyStored());
                        int received = handler.receiveEnergy(toExtract, false);
                        energyStorage.extractEnergy(received, false);
                    }
                });
            }
        }
    }

    /**
     * 向所有可输出面的相邻方块传输流体
     */
    private void supplyFluidToTurret() {
        if (level == null || fluidTank.getFluidAmount() == 0) {
            return;
        }

        Direction inputFace = getEnergyInputFace();

        // 遍历所有6个方向
        for (Direction direction : Direction.values()) {
            // 跳过输入面
            if (direction == inputFace) {
                continue;
            }

            // 检查该方向是否还有流体可以传输
            if (fluidTank.getFluidAmount() == 0) {
                break;
            }

            // 获取该方向的相邻方块
            BlockPos neighborPos = worldPosition.relative(direction);
            BlockEntity neighborEntity = level.getBlockEntity(neighborPos);

            if (neighborEntity != null) {
                // 从相邻方块的相对面接收流体
                Direction receivingSide = direction.getOpposite();

                neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, receivingSide).ifPresent(handler -> {
                    FluidStack toTransfer = fluidTank.drain(FLUID_TRANSFER_RATE, IFluidHandler.FluidAction.SIMULATE);
                    if (!toTransfer.isEmpty()) {
                        int filled = handler.fill(toTransfer, IFluidHandler.FluidAction.EXECUTE);
                        if (filled > 0) {
                            fluidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                        }
                    }
                });
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.put("FluidTank", fluidTank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStorage.setEnergy(tag.getInt("Energy"));
        if (tag.contains("FluidTank")) {
            fluidTank.readFromNBT(tag.getCompound("FluidTank"));
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // FE 能量系统
        if (cap == ForgeCapabilities.ENERGY) {
            Direction inputFace = getEnergyInputFace();

            // 能量输入面：只能接收能量
            if (side == inputFace) {
                return feHandler.cast();
            }
            // 其他面：只能输出能量（供炮塔使用）
            else if (side != null) {
                LazyOptional<IEnergyStorage> outputHandler = LazyOptional.of(() -> new OutputOnlyEnergyStorage(energyStorage));
                return outputHandler.cast();
            }
            return LazyOptional.empty();
        }

        // 流体系统
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            // 东南西北四个侧面：可以接收和输出流体
            if (canReceiveFluidFromSide(side)) {
                return fluidHandler.cast();
            }
            // 上下面：只能输出流体（供炮塔使用）
            else if (side != null) {
                LazyOptional<IFluidHandler> outputHandler = LazyOptional.of(() -> new OutputOnlyFluidHandler(fluidTank));
                return outputHandler.cast();
            }
            return LazyOptional.empty();
        }

        // GTEU 能量系统（只在输入面）
        if (GTCEU_LOADED && gteuHandler.isPresent()) {
            try {
                // 使用反射获取 GTCapability.CAPABILITY_ENERGY_CONTAINER
                Class<?> gtCapClass = Class.forName("com.gregtechceu.gtceu.api.capability.GTCapability");
                Object capabilityField = gtCapClass.getField("CAPABILITY_ENERGY_CONTAINER").get(null);
                if (cap.equals(capabilityField) && canReceiveEnergyFromSide(side)) {
                    return gteuHandler.cast();
                }
            } catch (Exception e) {
                // 忽略错误
            }
        }

        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHandler.invalidate();
        fluidHandler.invalidate();
        if (GTCEU_LOADED) {
            gteuHandler.invalidate();
        }
    }

    @NotNull
    @Override
    public Component getName() {
        return Component.translatable("block.integratedindustrialcraft.energy_pedestal");
    }

    /**
     * 只输出能量的包装器 - 用于非输入面
     */
    private static class OutputOnlyEnergyStorage implements IEnergyStorage {
        private final EnergyStorage storage;

        public OutputOnlyEnergyStorage(EnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0; // 不接收能量
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return storage.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return storage.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return storage.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false; // 不能接收
        }
    }

    /**
     * 只输出流体的包装器 - 用于非输入面
     */
    private static class OutputOnlyFluidHandler implements IFluidHandler {
        private final FluidTank tank;

        public OutputOnlyFluidHandler(FluidTank tank) {
            this.tank = tank;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return this.tank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return false; // 不接收
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // 不接收流体
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return tank.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return tank.drain(maxDrain, action);
        }
    }

    /**
     * 内部能量存储实现
     */
    private static class EnergyStorage implements IEnergyStorage {
        private int energy;
        private final int capacity;

        public EnergyStorage(int capacity) {
            this.capacity = capacity;
            this.energy = 0;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int energyReceived = Math.min(capacity - energy, maxReceive);
            if (!simulate) {
                energy += energyReceived;
            }
            return energyReceived;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int energyExtracted = Math.min(energy, maxExtract);
            if (!simulate) {
                energy -= energyExtracted;
            }
            return energyExtracted;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        public void setEnergy(int energy) {
            this.energy = Math.min(energy, capacity);
        }
    }
}
