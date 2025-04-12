package io.github.flemmli97.improvedmobs.industrial;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;

public class GTIntegration {

    private static boolean isGTLoaded = false;
    private static boolean checkDone = false;

    private static boolean isGTLoaded() {
        if (!checkDone) {
            try {
                Class.forName("com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity");
                isGTLoaded = true;
            } catch (ClassNotFoundException e) {
                isGTLoaded = false;
            }
            checkDone = true;
        }
        return isGTLoaded;
    }

    public static boolean isGTMachine(BlockEntity be) {
        if (!isGTLoaded()) return false;
        try {
            return be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getVoltageTier(BlockEntity be) {
        if (!isGTLoaded()) return -1;
        try {
            if (be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity machineBE) {
                com.gregtechceu.gtceu.api.machine.MetaMachine machine = machineBE.getMetaMachine();
                if (machine != null) {
                    return machine.getDefinition().getTier();
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return -1;
    }

    public static boolean isMachineActive(BlockEntity be) {
        if (!isGTLoaded()) return false;
        try {
            if (be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity machineBE) {
                com.gregtechceu.gtceu.api.machine.MetaMachine machine = machineBE.getMetaMachine();
                if (machine instanceof com.gregtechceu.gtceu.api.capability.IWorkable workable) {
                    return workable.isActive();
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return false;
    }

    public static boolean isMultiblock(BlockEntity be) {
        if (!isGTLoaded()) return false;
        try {
            if (be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity machineBE) {
                return machineBE.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
            }
        } catch (Throwable t) {
            // Ignore
        }
        return false;
    }

    /**
     * 判断机器、线缆或能源仓内是否有电通过/储存
     */
    public static boolean hasEnergyOrActive(BlockEntity be) {
        if (!isGTLoaded()) return false;
        try {
            if (be instanceof com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity machineBE) {
                com.gregtechceu.gtceu.api.machine.MetaMachine machine = machineBE.getMetaMachine();
                if (machine == null) return false;
                
                if (machine instanceof com.gregtechceu.gtceu.api.capability.IWorkable workable && workable.isActive()) {
                    return true;
                }
                
                LazyOptional<?> cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY);
                if (cap.isPresent()) {
                    return ((net.minecraftforge.energy.IEnergyStorage) cap.orElse(null)).getEnergyStored() > 0;
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return false;
    }
}
