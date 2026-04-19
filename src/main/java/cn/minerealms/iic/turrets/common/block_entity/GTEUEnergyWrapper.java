package cn.minerealms.iic.turrets.common.block_entity;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.config.ConfigHolder;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * GTEU 能量包装器 - 将 FE 转换为 GTEU
 * 默认转换比例：1 GTEU = 4 FE
 *
 * 注意：此类仅在 GTCEU 模组加载时使用
 */
public class GTEUEnergyWrapper implements IEnergyContainer {
    private final IEnergyStorage feStorage;

    public GTEUEnergyWrapper(IEnergyStorage feStorage) {
        this.feStorage = feStorage;
    }

    private int getConversionRatio() {
        return ConfigHolder.INSTANCE.compat.energy.euToFeRatio;
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
        // GTEU -> FE: 1 GTEU = 4 FE (默认)
        long euToInsert = voltage * amperage;
        int feToInsert = FeCompat.toFe(euToInsert, getConversionRatio());
        int feInserted = feStorage.receiveEnergy(feToInsert, false);
        long euInserted = FeCompat.toEu(feInserted, getConversionRatio());
        return euInserted / voltage; // 返回使用的安培数
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return feStorage.canReceive();
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        if (differenceAmount > 0) {
            int feToAdd = FeCompat.toFe(differenceAmount, getConversionRatio());
            int feAdded = feStorage.receiveEnergy(feToAdd, false);
            return FeCompat.toEu(feAdded, getConversionRatio());
        } else {
            int feToRemove = FeCompat.toFe(-differenceAmount, getConversionRatio());
            int feRemoved = feStorage.extractEnergy(feToRemove, false);
            return -FeCompat.toEu(feRemoved, getConversionRatio());
        }
    }

    @Override
    public long getEnergyStored() {
        return FeCompat.toEu(feStorage.getEnergyStored(), getConversionRatio());
    }

    @Override
    public long getEnergyCapacity() {
        return FeCompat.toEu(feStorage.getMaxEnergyStored(), getConversionRatio());
    }

    @Override
    public long getInputAmperage() {
        return 2; // 允许 2A 输入
    }

    @Override
    public long getInputVoltage() {
        // 根据容量计算合适的电压等级
        long capacity = getEnergyCapacity();
        if (capacity >= 512) return 512; // HV
        if (capacity >= 128) return 128; // MV
        if (capacity >= 32) return 32;   // LV
        return 8; // ULV
    }
}
