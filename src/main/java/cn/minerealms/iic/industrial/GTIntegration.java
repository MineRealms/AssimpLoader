package cn.minerealms.iic.industrial;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GregTech CEu Modern integration module.
 * <p>
 * This class provides integration with GregTech CEu Modern, allowing the mod to detect
 * and interact with GT machines, read their voltage tiers, check their active status,
 * and determine if they have energy.
 * <p>
 * Supports two integration modes:
 * <ol>
 *   <li>Direct type checking (if GTCEU is available at compile time)</li>
 *   <li>Reflection (if GTCEU is only available at runtime)</li>
 * </ol>
 * <p>
 * The integration automatically detects which mode to use and caches reflection
 * methods for performance. It supports both single-block machines and multiblock
 * structures, with special handling for multiblock voltage tier detection via energy hatches.
 * <p>
 * Debug logging is sampled (1 in {@value #SAMPLE_RATE} machines) to avoid log spam
 * when scanning large machine setups.
 *
 * @see MachineScanner
 * @see PollutionManager
 * @see ThreatManager
 * @author ImprovedMobs Team
 */
public class GTIntegration {

    private static boolean isGTLoaded = false;
    private static boolean checkDone = false;
    private static boolean useReflection = false;

    // Cached reflection classes and methods
    private static Class<?> metaMachineClass = null;
    private static Class<?> metaMachineBlockEntityClass = null;
    private static Class<?> iWorkableClass = null;
    private static Class<?> multiblockControllerClass = null;
    private static Method getMetaMachineMethod = null;
    private static Method getDefinitionMethod = null;
    private static Method getTierMethod = null;
    private static Method isActiveMethod = null;

    /**
     * Sample counter for debug logging.
     * Only logs 1 in SAMPLE_RATE machines to avoid spam.
     */
    private static final AtomicInteger sampleCounter = new AtomicInteger(0);

    /**
     * Sample rate for debug logging (1 in 1000 machines).
     */
    private static final int SAMPLE_RATE = 1000;

    /**
     * Checks if GregTech CEu is loaded and initializes integration.
     * <p>
     * This method is called automatically on first use. It attempts to load GT classes
     * using reflection and caches the results. Supports both MetaMachineBlockEntity
     * (older versions) and direct MetaMachine (newer versions) modes.
     *
     * @return true if GregTech CEu is loaded and integration is successful
     */
    public static boolean isGTLoaded() {
        if (!checkDone) {
            IndustrialLogger.info("=== Initializing GregTech CEu Integration ===");

            // Attempt mode 1: MetaMachineBlockEntity (older or some GTCEU versions)
            try {
                metaMachineBlockEntityClass = Class.forName("com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity");
                metaMachineClass = Class.forName("com.gregtechceu.gtceu.api.machine.MetaMachine");
                iWorkableClass = Class.forName("com.gregtechceu.gtceu.api.capability.IWorkable");
                multiblockControllerClass = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine");

                // Cache methods
                getMetaMachineMethod = metaMachineBlockEntityClass.getMethod("getMetaMachine");
                getDefinitionMethod = metaMachineClass.getMethod("getDefinition");
                isActiveMethod = iWorkableClass.getMethod("isActive");

                useReflection = true;
                isGTLoaded = true;
                IndustrialLogger.info("✓ GregTech CEu detected: Using MetaMachineBlockEntity mode");
                IndustrialLogger.info("  - MetaMachineBlockEntity: " + metaMachineBlockEntityClass.getName());
                IndustrialLogger.info("  - MetaMachine: " + metaMachineClass.getName());
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                IndustrialLogger.info("✗ MetaMachineBlockEntity not found, trying direct MetaMachine mode...");

                // Attempt mode 2: Direct MetaMachine (newer GTCEU)
                try {
                    metaMachineClass = Class.forName("com.gregtechceu.gtceu.api.machine.MetaMachine");
                    iWorkableClass = Class.forName("com.gregtechceu.gtceu.api.capability.IWorkable");
                    multiblockControllerClass = Class.forName("com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine");

                    getDefinitionMethod = metaMachineClass.getMethod("getDefinition");
                    isActiveMethod = iWorkableClass.getMethod("isActive");

                    useReflection = true;
                    isGTLoaded = true;
                    IndustrialLogger.info("✓ GregTech CEu detected: Using direct MetaMachine mode");
                    IndustrialLogger.info("  - MetaMachine: " + metaMachineClass.getName());
                } catch (ClassNotFoundException | NoSuchMethodException e2) {
                    isGTLoaded = false;
                    IndustrialLogger.warn("✗ GregTech CEu not found or incompatible version");
                    IndustrialLogger.warn("  Error 1: " + e.getMessage());
                    IndustrialLogger.warn("  Error 2: " + e2.getMessage());
                }
            }

            checkDone = true;
            IndustrialLogger.info("=== GregTech CEu Integration: " + (isGTLoaded ? "ENABLED" : "DISABLED") + " ===");
        }
        return isGTLoaded;
    }

    /**
     * Checks if a BlockEntity is a GT machine (silent mode, no logging).
     * <p>
     * This method is optimized for frequent calls during scanning. It does not
     * output any debug logs to avoid spam.
     *
     * @param be the BlockEntity to check
     * @return true if the BlockEntity is a GT machine
     */
    public static boolean isGTMachine(BlockEntity be) {
        if (!isGTLoaded() || be == null) return false;

        try {
            // Mode 1: MetaMachineBlockEntity
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                return true;
            }

            // Mode 2: Direct MetaMachine
            if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                return true;
            }

            return false;
        } catch (Throwable t) {
            // Silent failure, no logging
            return false;
        }
    }

    /**
     * Checks if a BlockEntity is a GT machine (verbose mode, with logging).
     * <p>
     * This method should only be used for the scan command or debugging purposes.
     * It outputs debug logs for each machine found.
     *
     * @param be the BlockEntity to check
     * @return true if the BlockEntity is a GT machine
     */
    public static boolean isGTMachineVerbose(BlockEntity be) {
        if (!isGTLoaded() || be == null) return false;

        try {
            // Mode 1: MetaMachineBlockEntity
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                IndustrialLogger.debugGT("Found MetaMachineBlockEntity at " + be.getBlockPos());
                return true;
            }

            // Mode 2: Direct MetaMachine
            if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                IndustrialLogger.debugGT("Found MetaMachine at " + be.getBlockPos());
                return true;
            }

            return false;
        } catch (Throwable t) {
            IndustrialLogger.error("Error checking if BlockEntity is GT machine: " + t.getMessage());
            return false;
        }
    }

    /**
     * Gets the voltage tier of a GT machine.
     * <p>
     * For multiblock structures, this method attempts to get the tier from energy hatches
     * first, as they provide the most accurate voltage tier for the multiblock. For single-block
     * machines, it gets the tier from the machine definition.
     * <p>
     * Debug logging is sampled (1 in {@value #SAMPLE_RATE}) to avoid spam.
     *
     * @param be the BlockEntity to query
     * @return the voltage tier (0=ULV, 1=LV, 2=MV, etc.), or -1 if not a GT machine or error
     */
    public static int getVoltageTier(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) return -1;

        try {
            Object machine = null;

            // Mode 1: Via MetaMachineBlockEntity.getMetaMachine()
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
                if (machine == null) {
                    return -1;
                }
            }
            // Mode 2: BlockEntity itself is MetaMachine
            else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
            }

            if (machine != null) {
                // Special handling: If multiblock controller, try to get voltage tier from energy hatch
                if (multiblockControllerClass != null && multiblockControllerClass.isInstance(machine)) {
                    int multiblockTier = getMultiblockVoltageTier(machine);
                    if (multiblockTier >= 0) {
                        // Sampled output
                        if (IndustrialLogger.isDebugEnabled() && sampleCounter.incrementAndGet() % SAMPLE_RATE == 0) {
                            IndustrialLogger.debugGT(String.format("Sample: Multiblock at %s has tier: %d (from energy hatch)", be.getBlockPos(), multiblockTier));
                        }
                        return multiblockTier;
                    }
                }

                // Standard method: Get from definition
                Object definition = getDefinitionMethod.invoke(machine);
                if (definition != null) {
                    if (getTierMethod == null) {
                        getTierMethod = definition.getClass().getMethod("getTier");
                    }
                    int tier = (int) getTierMethod.invoke(definition);

                    // Sampled output
                    if (IndustrialLogger.isDebugEnabled() && sampleCounter.incrementAndGet() % SAMPLE_RATE == 0) {
                        IndustrialLogger.debugGT(String.format("Sample: Machine at %s has tier: %d", be.getBlockPos(), tier));
                    }

                    return tier;
                }
            }
        } catch (Throwable t) {
            // Silent failure
        }
        return -1;
    }

    /**
     * Gets the voltage tier of a multiblock structure from its energy hatches.
     * <p>
     * This method scans all parts of a multiblock structure looking for energy hatches
     * or tiered IO parts, and returns the highest tier found. This provides more accurate
     * voltage tier information for multiblocks than the controller's definition.
     *
     * @param multiblockController the multiblock controller machine
     * @return the highest voltage tier from energy hatches, or -1 if none found
     */
    private static int getMultiblockVoltageTier(Object multiblockController) {
        try {
            // Get parts list
            java.lang.reflect.Method getPartsMethod = multiblockController.getClass().getMethod("getParts");
            Object parts = getPartsMethod.invoke(multiblockController);

            if (parts instanceof java.util.List) {
                int maxTier = -1;
                for (Object part : (java.util.List<?>) parts) {
                    // Check if it's an EnergyHatchPartMachine or parent class TieredIOPartMachine
                    String partClassName = part.getClass().getName();
                    if (partClassName.contains("EnergyHatchPartMachine") ||
                        partClassName.contains("TieredIOPartMachine")) {
                        try {
                            // Try to get tier (inherited from TieredPartMachine)
                            java.lang.reflect.Method getTierMethod = part.getClass().getMethod("getTier");
                            int tier = (int) getTierMethod.invoke(part);
                            if (tier > maxTier) {
                                maxTier = tier;
                                if (IndustrialLogger.isDebugEnabled()) {
                                    IndustrialLogger.debugGT(String.format("Found energy hatch with tier: %d in multiblock", tier));
                                }
                            }
                        } catch (Exception e) {
                            // Single part failure doesn't affect others
                        }
                    }
                }
                if (maxTier >= 0) {
                    return maxTier;
                }
            }
        } catch (Throwable t) {
            // Silent failure
            if (IndustrialLogger.isDebugEnabled()) {
                IndustrialLogger.debugGT("Failed to get multiblock tier from energy hatch: " + t.getMessage());
            }
        }
        return -1;
    }

    /**
     * Gets the voltage tier of a GT machine (verbose mode, with logging).
     * <p>
     * This method should only be used for the scan command. It outputs detailed
     * debug logs for troubleshooting.
     *
     * @param be the BlockEntity to query
     * @return the voltage tier, or -1 if not a GT machine or error
     */
    public static int getVoltageTierVerbose(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) return -1;

        try {
            Object machine = null;

            // 方案1：通过 MetaMachineBlockEntity.getMetaMachine()
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
                if (machine == null) {
                    IndustrialLogger.debugGT("MetaMachine is null at " + be.getBlockPos());
                    return -1;
                }
            }
            // 方案2：BlockEntity 本身就是 MetaMachine
            else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
            }

            if (machine != null) {
                Object definition = getDefinitionMethod.invoke(machine);
                if (definition != null) {
                    if (getTierMethod == null) {
                        getTierMethod = definition.getClass().getMethod("getTier");
                    }
                    int tier = (int) getTierMethod.invoke(definition);
                    IndustrialLogger.debugGT(String.format("Machine at %s has tier: %d", be.getBlockPos(), tier));
                    return tier;
                } else {
                    IndustrialLogger.debugGT("Definition is null at " + be.getBlockPos());
                }
            }
        } catch (Throwable t) {
            IndustrialLogger.error("Error getting voltage tier at " + be.getBlockPos() + ": " + t.getMessage());
        }
        return -1;
    }

    /**
     * Checks if a GT machine is currently active (working).
     * <p>
     * A machine is considered active if it implements IWorkable and its isActive() method returns true.
     * This method does not output logs to avoid spam during frequent checks.
     *
     * @param be the BlockEntity to check
     * @return true if the machine is active
     */
    public static boolean isMachineActive(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) return false;

        try {
            Object machine = null;

            // Get MetaMachine instance
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
            } else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
            }

            if (machine != null && iWorkableClass != null && iWorkableClass.isInstance(machine)) {
                boolean active = (boolean) isActiveMethod.invoke(machine);
                // No logging to avoid spam
                return active;
            }
        } catch (Throwable t) {
            // Silent failure
        }
        return false;
    }

    /**
     * Checks if a GT machine is a multiblock structure.
     * <p>
     * This method does not output logs to avoid spam during frequent checks.
     *
     * @param be the BlockEntity to check
     * @return true if the machine is a multiblock controller
     */
    public static boolean isMultiblock(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) return false;

        try {
            Object machine = null;

            // Get MetaMachine instance
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
            } else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
            }

            if (machine != null && multiblockControllerClass != null) {
                boolean isMulti = multiblockControllerClass.isInstance(machine);
                // No logging to avoid spam
                return isMulti;
            }
        } catch (Throwable t) {
            // Silent failure
        }
        return false;
    }

    /**
     * Checks if a machine, cable, or energy hatch has energy flowing through it or stored.
     * <p>
     * For multiblock structures, this method checks if energy hatches are present.
     * For single-block machines, it checks the energy capability.
     * <p>
     * This is used to determine if a machine should generate pollution and be targeted by mobs.
     *
     * @param be the BlockEntity to check
     * @return true if the machine has energy or is active
     */
    public static boolean hasEnergyOrActive(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) return false;

        try {
            Object machine = null;

            // Get MetaMachine instance
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
            } else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
            }

            // 1. Check if currently working
            if (machine != null && iWorkableClass != null && iWorkableClass.isInstance(machine)) {
                boolean active = (boolean) isActiveMethod.invoke(machine);
                if (active) {
                    return true;
                }
            }

            // 2. Special handling: Check energy hatches for multiblock structures
            if (machine != null && multiblockControllerClass != null && multiblockControllerClass.isInstance(machine)) {
                try {
                    // Get parts list
                    java.lang.reflect.Method getPartsMethod = machine.getClass().getMethod("getParts");
                    Object parts = getPartsMethod.invoke(machine);

                    if (parts instanceof java.util.List) {
                        for (Object part : (java.util.List<?>) parts) {
                            // Check if it's an energy hatch
                            String partClassName = part.getClass().getName();
                            if (partClassName.contains("EnergyHatchPartMachine")) {
                                // Energy hatch exists means multiblock has energy
                                return true;
                            }
                        }
                    }
                } catch (Exception e) {
                    // If getting energy hatch fails, continue to check energy storage
                }
            }

            // 3. Check energy storage (single-block machines or energy hatches)
            LazyOptional<?> cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY);
            if (cap.isPresent()) {
                Object energyStorage = cap.orElse(null);
                if (energyStorage instanceof net.minecraftforge.energy.IEnergyStorage storage) {
                    boolean hasEnergy = storage.getEnergyStored() > 0;
                    return hasEnergy;
                }
            }
        } catch (Throwable t) {
            // Silent failure
        }
        return false;
    }

    /**
     * Checks if a machine has energy (verbose mode, with detailed logging).
     * <p>
     * This method should only be used for the scan command. It outputs detailed
     * debug logs for troubleshooting energy detection issues.
     *
     * @param be the BlockEntity to check
     * @return true if the machine has energy or is active
     */
    public static boolean hasEnergyOrActiveVerbose(BlockEntity be) {
        if (!isGTLoaded() || be == null || !isGTMachine(be)) {
            IndustrialLogger.debugMachine("hasEnergyOrActive: Not GT machine or null");
            return false;
        }

        try {
            Object machine = null;

            // Get MetaMachine instance
            if (metaMachineBlockEntityClass != null && metaMachineBlockEntityClass.isInstance(be)) {
                machine = getMetaMachineMethod.invoke(be);
                IndustrialLogger.debugMachine("Got machine from MetaMachineBlockEntity: " + (machine != null));
            } else if (metaMachineClass != null && metaMachineClass.isInstance(be)) {
                machine = be;
                IndustrialLogger.debugMachine("BlockEntity is MetaMachine directly");
            }

            // 1. Check if currently working
            if (machine != null && iWorkableClass != null && iWorkableClass.isInstance(machine)) {
                boolean active = (boolean) isActiveMethod.invoke(machine);
                IndustrialLogger.debugMachine("Machine active check: " + active);
                if (active) {
                    return true;
                }
            } else {
                IndustrialLogger.debugMachine("Machine is not IWorkable");
            }

            // 2. Check energy storage
            LazyOptional<?> cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY);
            IndustrialLogger.debugMachine("Energy capability present: " + cap.isPresent());

            if (cap.isPresent()) {
                Object energyStorage = cap.orElse(null);
                IndustrialLogger.debugMachine("Energy storage type: " + (energyStorage != null ? energyStorage.getClass().getName() : "null"));

                if (energyStorage instanceof net.minecraftforge.energy.IEnergyStorage storage) {
                    int stored = storage.getEnergyStored();
                    IndustrialLogger.debugMachine("Energy stored: " + stored + " FE");
                    boolean hasEnergy = stored > 0;
                    return hasEnergy;
                } else {
                    IndustrialLogger.debugMachine("Energy storage is not IEnergyStorage");
                }
            }
        } catch (Throwable t) {
            IndustrialLogger.error("Error checking energy: " + t.getMessage(), t);
        }

        IndustrialLogger.debugMachine("hasEnergyOrActive: Returning false");
        return false;
    }
}
