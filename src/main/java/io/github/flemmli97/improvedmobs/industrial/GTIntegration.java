package io.github.flemmli97.improvedmobs.industrial;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
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
}
