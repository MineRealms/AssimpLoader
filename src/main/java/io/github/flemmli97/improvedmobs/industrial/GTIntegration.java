package io.github.flemmli97.improvedmobs.industrial;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class GTIntegration {

    public static boolean isGTMachine(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity;
    }

    public static int getVoltageTier(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity machineBE) {
            MetaMachine machine = machineBE.getMetaMachine();
            if (machine != null) {
                return machine.getDefinition().getTier();
            }
        }
        return -1;
    }

    public static boolean isMachineActive(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity machineBE) {
            MetaMachine machine = machineBE.getMetaMachine();
            if (machine instanceof IWorkable workable) {
                return workable.isActive();
            }
        }
        return false;
    }

    public static boolean isMultiblock(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity machineBE) {
            return machineBE.getMetaMachine() instanceof MultiblockControllerMachine;
        }
        return false;
    }

    /**
     * 判断机器、线缆或能源仓内是否有电通过/储存
     */
    public static boolean hasEnergyOrActive(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity machineBE) {
            MetaMachine machine = machineBE.getMetaMachine();
            if (machine == null) return false;
            
            // 如果是正在工作的机器，直接算作有电/活跃
            if (machine instanceof IWorkable workable && workable.isActive()) {
                return true;
            }
            
            // 检查是否有储电（用于线缆和能源仓）
            // 注意：线缆虽然不一定实现完整的 IEnergyContainer，但在 GTCEu API 中
            // 我们可以通过 trait 或直接强转来安全获取，这里简化为通用获取能量的方法
            if (machine.getTraits() != null) {
                IEnergyContainer energyContainer = machine.getTraits().getTrait(IEnergyContainer.class);
                if (energyContainer != null) {
                    return energyContainer.getEnergyStored() > 0;
                }
            }
        }
        return false;
    }
}
