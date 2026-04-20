package cn.minerealms.iic.integration.alexscaves;

import cn.minerealms.iic.difficulty.MachineScanner;
import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.integration.spore.SporeIntegration;
import cn.minerealms.iic.pollution.PollutionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Manages Nucleeper spawning based on industrial progression and Hivemind infestation.
 *
 * <h2>Spawn Conditions</h2>
 *
 * <h3>Condition 1: High Voltage + High Pollution</h3>
 * <ul>
 *   <li>Average voltage tier: UV~UHV (8~10)</li>
 *   <li>Pollution level: ≥ 200</li>
 *   <li>Base spawn chance: 2% per second</li>
 * </ul>
 *
 * <h3>Condition 2: Hivemind Infestation</h3>
 * <ul>
 *   <li>Hivemind count: ≥ 5</li>
 *   <li>Total biomass: ≥ 500</li>
 *   <li>Voltage tier: ZPM~UHV (7~10)</li>
 *   <li>Base spawn chance: 2% + 0.75% per 10 biomass</li>
 * </ul>
 *
 * <h3>Condition 3: Horde Event</h3>
 * <ul>
 *   <li>Hivemind count: ≥ 5</li>
 *   <li>Voltage tier: ≥ UV (8)</li>
 *   <li>Pollution: ≥ 200</li>
 *   <li>Difficulty: ≥ 150</li>
 *   <li>Spawn chance: 10% per Horde event</li>
 * </ul>
 *
 * @author I3C Team
 * @since 1.0.0
 */
public class NucleeperSpawnManager {

    private static int tickCounter = 0;
    private static boolean isAlexsCavesLoaded = false;
    private static boolean checkDone = false;
    private static EntityType<?> nucleeperEntityType = null;

    /**
     * Checks if AlexsCaves mod is loaded and caches the Nucleeper entity type.
     *
     * @return true if AlexsCaves is loaded and Nucleeper entity type is available
     */
    public static boolean isAlexsCavesLoaded() {
        if (!checkDone) {
            try {
                nucleeperEntityType = ForgeRegistries.ENTITY_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation("alexscaves", "nucleeper")
                );
                isAlexsCavesLoaded = nucleeperEntityType != null;

                if (isAlexsCavesLoaded) {
                    IndustrialLogger.info("✓ AlexsCaves detected: Nucleeper spawning enabled");
                } else {
                    IndustrialLogger.info("✗ AlexsCaves Nucleeper entity not found");
                }
            } catch (Exception e) {
                isAlexsCavesLoaded = false;
                IndustrialLogger.warn("✗ AlexsCaves not found or incompatible version");
            }
            checkDone = true;
        }
        return isAlexsCavesLoaded;
    }

    /**
     * Main tick method called from ServerTickEvent.
     * Checks spawn conditions and attempts to spawn Nucleepers.
     *
     * @param level the server level to process
     */
    public static void tick(ServerLevel level) {
        if (!isAlexsCavesLoaded()) {
            return;
        }

        tickCounter++;

        // Check every second (20 ticks)
        if (tickCounter % NucleeperSpawnConfig.spawnCooldown == 0) {
            processNucleeperSpawning(level);
        }
    }

    /**
     * Processes Nucleeper spawning for all players in the level.
     *
     * @param level the server level
     */
    private static void processNucleeperSpawning(ServerLevel level) {
        // Check if max Nucleeper count reached
        int currentCount = countNucleepers(level);
        if (currentCount >= NucleeperSpawnConfig.maxNucleeperCount) {
            if (NucleeperSpawnConfig.debugLogging) {
                IndustrialLogger.debug(String.format(
                    "[Nucleeper] Max count reached: %d/%d",
                    currentCount, NucleeperSpawnConfig.maxNucleeperCount
                ));
            }
            return;
        }

        for (ServerPlayer player : level.players()) {
            // Check Condition 1: High Voltage + High Pollution
            if (NucleeperSpawnConfig.enableVoltagePollutionSpawn) {
                if (checkVoltagePollutionCondition(level, player)) {
                    double spawnChance = NucleeperSpawnConfig.baseSpawnChanceVoltage;
                    if (level.random.nextDouble() < spawnChance) {
                        trySpawnNucleeper(level, player, "High Voltage + Pollution");
                        return; // Only spawn one per tick
                    }
                }
            }

            // Check Condition 2: Hivemind Infestation
            if (NucleeperSpawnConfig.enableHivemindSpawn) {
                if (checkHivemindCondition(level, player)) {
                    double spawnChance = calculateHivemindSpawnChance(level);
                    if (level.random.nextDouble() < spawnChance) {
                        trySpawnNucleeper(level, player, "Hivemind Infestation");
                        return; // Only spawn one per tick
                    }
                }
            }
        }
    }

    /**
     * Checks if Condition 1 (High Voltage + High Pollution) is met.
     *
     * @param level the server level
     * @param player the player to check around
     * @return true if conditions are met
     */
    private static boolean checkVoltagePollutionCondition(ServerLevel level, ServerPlayer player) {
        // Check voltage tier
        MachineScanner.ScanResult scanResult = MachineScanner.scanNearbyMachines(player, 64);
        if (scanResult.tiers().isEmpty()) {
            return false;
        }

        double avgTier = scanResult.tiers().stream().mapToDouble(Integer::doubleValue).average().orElse(0.0);

        if (avgTier < NucleeperSpawnConfig.minVoltageTierForNucleeper ||
            avgTier > NucleeperSpawnConfig.maxVoltageTierForNucleeper) {
            return false;
        }

        // Check pollution
        ChunkPos chunkPos = player.chunkPosition();
        double pollution = PollutionManager.getTemporaryPollution(chunkPos);

        if (pollution < NucleeperSpawnConfig.minPollutionForNucleeper) {
            return false;
        }

        if (NucleeperSpawnConfig.debugLogging) {
            IndustrialLogger.debug(String.format(
                "[Nucleeper] Condition 1 met: avgTier=%.2f, pollution=%.2f",
                avgTier, pollution
            ));
        }

        return true;
    }

    /**
     * Checks if Condition 2 (Hivemind Infestation) is met.
     *
     * @param level the server level
     * @param player the player to check around
     * @return true if conditions are met
     */
    private static boolean checkHivemindCondition(ServerLevel level, ServerPlayer player) {
        if (!SporeIntegration.isSporeLoaded()) {
            return false;
        }

        // Check Hivemind count and biomass
        int hivemindCount = SporeIntegration.getHivemindCount(level);
        int totalBiomass = SporeIntegration.getTotalBiomass(level);

        if (hivemindCount < NucleeperSpawnConfig.minHivemindCount) {
            return false;
        }

        if (totalBiomass < NucleeperSpawnConfig.minBiomassForNucleeper) {
            return false;
        }

        // Check voltage tier
        MachineScanner.ScanResult scanResult = MachineScanner.scanNearbyMachines(player, 64);
        if (scanResult.tiers().isEmpty()) {
            return false;
        }

        double avgTier = scanResult.tiers().stream().mapToDouble(Integer::doubleValue).average().orElse(0.0);

        if (avgTier < NucleeperSpawnConfig.minVoltageTierForHivemindSpawn ||
            avgTier > NucleeperSpawnConfig.maxVoltageTierForHivemindSpawn) {
            return false;
        }

        if (NucleeperSpawnConfig.debugLogging) {
            IndustrialLogger.debug(String.format(
                "[Nucleeper] Condition 2 met: hivemindCount=%d, biomass=%d, avgTier=%.2f",
                hivemindCount, totalBiomass, avgTier
            ));
        }

        return true;
    }

    /**
     * Calculates spawn chance based on Hivemind biomass.
     *
     * @param level the server level
     * @return spawn chance (0.0 - 1.0)
     */
    private static double calculateHivemindSpawnChance(ServerLevel level) {
        int totalBiomass = SporeIntegration.getTotalBiomass(level);
        int excessBiomass = totalBiomass - NucleeperSpawnConfig.minBiomassForNucleeper;

        if (excessBiomass <= 0) {
            return NucleeperSpawnConfig.baseSpawnChanceHivemind;
        }

        int biomassIncrements = excessBiomass / NucleeperSpawnConfig.biomassIncrement;
        double additionalChance = biomassIncrements * NucleeperSpawnConfig.spawnChancePerBiomass;

        return Math.min(1.0, NucleeperSpawnConfig.baseSpawnChanceHivemind + additionalChance);
    }

    /**
     * Checks if Horde event conditions are met for Nucleeper spawning.
     * Called from HordeManager when a Horde event is triggered.
     *
     * @param level the server level
     * @param player the player triggering the Horde
     * @return true if conditions are met
     */
    public static boolean checkHordeCondition(ServerLevel level, ServerPlayer player) {
        if (!isAlexsCavesLoaded() || !NucleeperSpawnConfig.enableHordeSpawn) {
            return false;
        }

        // Check Hivemind count
        if (SporeIntegration.isSporeLoaded()) {
            int hivemindCount = SporeIntegration.getHivemindCount(level);
            if (hivemindCount < NucleeperSpawnConfig.minHivemindCountForHorde) {
                return false;
            }
        } else {
            return false; // Horde spawning requires Spore mod
        }

        // Check voltage tier
        MachineScanner.ScanResult scanResult = MachineScanner.scanNearbyMachines(player, 64);
        if (scanResult.tiers().isEmpty()) {
            return false;
        }

        double avgTier = scanResult.tiers().stream().mapToDouble(Integer::doubleValue).average().orElse(0.0);
        if (avgTier < NucleeperSpawnConfig.minVoltageTierForHorde) {
            return false;
        }

        // Check pollution
        ChunkPos chunkPos = player.chunkPosition();
        double pollution = PollutionManager.getTemporaryPollution(chunkPos);
        if (pollution < NucleeperSpawnConfig.minPollutionForHorde) {
            return false;
        }

        // Check difficulty
        double difficulty = getDifficultyNearPlayer(level, player);
        if (difficulty < NucleeperSpawnConfig.minDifficultyForHorde) {
            return false;
        }

        if (NucleeperSpawnConfig.debugLogging) {
            IndustrialLogger.debug(String.format(
                "[Nucleeper] Horde condition met: avgTier=%.2f, pollution=%.2f, difficulty=%.2f",
                avgTier, pollution, difficulty
            ));
        }

        return true;
    }

    /**
     * Spawns a Nucleeper during a Horde event.
     * Called from HordeManager when Horde conditions are met.
     *
     * @param level the server level
     * @param player the player triggering the Horde
     * @return true if Nucleeper was spawned successfully
     */
    public static boolean spawnNucleeperForHorde(ServerLevel level, ServerPlayer player) {
        if (!checkHordeCondition(level, player)) {
            return false;
        }

        if (level.random.nextDouble() < NucleeperSpawnConfig.hordeSpawnChance) {
            return trySpawnNucleeper(level, player, "Horde Event");
        }

        return false;
    }

    /**
     * Attempts to spawn a Nucleeper near the player.
     *
     * @param level the server level
     * @param player the player to spawn near
     * @param reason the reason for spawning (for logging)
     * @return true if spawned successfully
     */
    private static boolean trySpawnNucleeper(ServerLevel level, ServerPlayer player, String reason) {
        BlockPos spawnPos = findSpawnPosition(level, player);
        if (spawnPos == null) {
            if (NucleeperSpawnConfig.debugLogging) {
                IndustrialLogger.debug("[Nucleeper] No valid spawn position found");
            }
            return false;
        }

        try {
            net.minecraft.world.entity.Entity entity = nucleeperEntityType.create(level);
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos),
                    MobSpawnType.EVENT, null, null);

                // Add machine-targeting AI
                mob.goalSelector.addGoal(1, new NucleeperTargetMachineGoal(mob));

                level.addFreshEntity(mob);

                IndustrialLogger.info(String.format(
                    "[Nucleeper] Spawned at %s (Reason: %s, Player: %s)",
                    spawnPos, reason, player.getName().getString()
                ));

                return true;
            }
        } catch (Exception e) {
            IndustrialLogger.error("[Nucleeper] Failed to spawn: " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Finds a valid spawn position near the player.
     *
     * @param level the server level
     * @param player the player to spawn near
     * @return a valid spawn position, or null if none found
     */
    private static BlockPos findSpawnPosition(ServerLevel level, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();

        // Try 10 times to find a valid position
        for (int i = 0; i < 10; i++) {
            // Random angle and distance
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = NucleeperSpawnConfig.minSpawnDistance +
                level.random.nextDouble() * (NucleeperSpawnConfig.spawnRadius - NucleeperSpawnConfig.minSpawnDistance);

            int x = playerPos.getX() + (int)(Math.cos(angle) * distance);
            int z = playerPos.getZ() + (int)(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos pos = new BlockPos(x, y, z);

            // Check if position is valid
            if (!level.hasChunkAt(pos)) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) continue;

            return pos;
        }

        return null;
    }

    /**
     * Counts the number of Nucleepers currently in the level.
     *
     * @param level the server level
     * @return the number of Nucleepers
     */
    private static int countNucleepers(ServerLevel level) {
        if (nucleeperEntityType == null) {
            return 0;
        }

        AABB searchBox = new AABB(
            level.getSharedSpawnPos().offset(-1000, -1000, -1000),
            level.getSharedSpawnPos().offset(1000, 1000, 1000)
        );

        List<? extends net.minecraft.world.entity.Entity> entities =
            level.getEntities(nucleeperEntityType, searchBox, entity -> true);

        return entities.size();
    }

    /**
     * Gets the difficulty level near a player.
     *
     * @param level the server level
     * @param player the player
     * @return the difficulty value
     */
    private static double getDifficultyNearPlayer(ServerLevel level, ServerPlayer player) {
        // Get difficulty from ImprovedMobs system
        io.github.flemmli97.improvedmobs.difficulty.DifficultyData diffData =
            io.github.flemmli97.improvedmobs.difficulty.DifficultyData.get(level.getServer());

        // Use DifficultyProvider to get location-based difficulty
        cn.minerealms.iic.difficulty.DifficultyProvider provider = new cn.minerealms.iic.difficulty.DifficultyProvider();
        return provider.getDifficulty(level, player.position());
    }
}
