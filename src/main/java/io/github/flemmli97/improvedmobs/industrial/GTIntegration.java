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
            
            // 检查 Forge Energy Capability
            net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY);
            if (cap.isPresent()) {
                return cap.orElse(null).getEnergyStored() > 0;
            }
        }
        return false;
    }
}
