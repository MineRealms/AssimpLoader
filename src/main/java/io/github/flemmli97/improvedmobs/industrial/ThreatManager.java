package io.github.flemmli97.improvedmobs.industrial;

import io.github.flemmli97.improvedmobs.ai.CreeperTargetMachineGoal;
import io.github.flemmli97.improvedmobs.ai.ZombieDestroyMachineGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Threat Manager - Triggers different threat mechanisms based on voltage tier and pollution levels.
 *
 * <p>This system creates dynamic threats that scale with industrial progression:
 * <ul>
 *   <li><b>MV (Tier 2)</b>: Nearby zombies attack machines (passive threat)</li>
 *   <li><b>HV (Tier 3+)</b>: Active zombie spawning to attack machines</li>
 *   <li><b>HV + High Pollution</b>: Active creeper spawning to attack machines</li>
 *   <li><b>Extreme Pollution</b>: Charged creeper spawning</li>
 * </ul>
 *
 * <p>Threat levels are configurable via {@link TriAxisConfig} and can be adjusted
 * based on gameplay balance requirements.
 *
 * @see TriAxisConfig
 * @see PollutionManager
 * @author ImprovedMobs Industrial Integration
 */
public class ThreatManager {

    // === Configuration Parameters ===

    /** Pollution threshold for MV-tier zombies to attack machines */
    public static double MV_ZOMBIE_ATTACK_THRESHOLD = 50.0;

    /** Pollution threshold for HV-tier active zombie spawning */
    public static double HV_ZOMBIE_SPAWN_THRESHOLD = 80.0;

    /** Pollution threshold for HV-tier active creeper spawning */
    public static double HV_CREEPER_SPAWN_THRESHOLD = 120.0;

    /** Pollution threshold for charged creeper spawning */
    public static double CHARGED_CREEPER_THRESHOLD = 200.0;

    /** Spawn probability per second for zombies (1%) */
    public static double ZOMBIE_SPAWN_CHANCE = 0.01;

    /** Spawn probability per second for creepers (0.5%) */
    public static double CREEPER_SPAWN_CHANCE = 0.005;

    /** Spawn probability per second for charged creepers (0.1%) */
    public static double CHARGED_CREEPER_CHANCE = 0.001;

    /**
     * Checks and triggers threat mechanisms based on current conditions.
     * This method should be called once per second.
     *
     * @param level The server level where threats will spawn
     * @param chunkPos The chunk position to check for threats
     * @param pollution Current pollution level in the chunk
     * @param avgVoltageTier Average voltage tier of machines in the chunk
     */
    public static void checkAndTriggerThreats(ServerLevel level, ChunkPos chunkPos, double pollution, double avgVoltageTier) {
        // MV stage: Nearby zombies will attack machines (implemented via AI, no handling needed here)

        // HV stage: Active threat spawning
        if (avgVoltageTier >= 3.0) { // HV+
            // Spawn zombies
            if (pollution >= HV_ZOMBIE_SPAWN_THRESHOLD && level.random.nextDouble() < ZOMBIE_SPAWN_CHANCE) {
                spawnHostileZombie(level, chunkPos);
            }

            // Spawn creepers
            if (pollution >= HV_CREEPER_SPAWN_THRESHOLD && level.random.nextDouble() < CREEPER_SPAWN_CHANCE) {
                boolean charged = pollution >= CHARGED_CREEPER_THRESHOLD && level.random.nextDouble() < CHARGED_CREEPER_CHANCE;
                spawnHostileCreeper(level, chunkPos, charged);
            }
        }
    }

    /**
     * Spawns a hostile zombie that will attack machines.
     * The zombie is given high-priority AI to target and destroy machines.
     *
     * @param level The server level to spawn in
     * @param chunkPos The chunk position for spawn location
     */
    private static void spawnHostileZombie(ServerLevel level, ChunkPos chunkPos) {
        BlockPos spawnPos = findSpawnPosition(level, chunkPos);
        if (spawnPos == null) return;

        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie != null) {
            zombie.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);

            // Add machine-attacking AI (high priority)
            zombie.goalSelector.addGoal(1, new ZombieDestroyMachineGoal(zombie));

            level.addFreshEntity(zombie);

            if (IndustrialLogger.isDebugEnabled()) {
                IndustrialLogger.debugPollution(String.format(
                        "Spawned hostile zombie at %s (chunk %s)", spawnPos, chunkPos));
            }
        }
    }

    /**
     * Spawns a hostile creeper that will attack machines.
     * Can optionally spawn as a charged creeper for extreme pollution levels.
     *
     * @param level The server level to spawn in
     * @param chunkPos The chunk position for spawn location
     * @param charged Whether to spawn as a charged creeper
     */
    private static void spawnHostileCreeper(ServerLevel level, ChunkPos chunkPos, boolean charged) {
        BlockPos spawnPos = findSpawnPosition(level, chunkPos);
        if (spawnPos == null) return;

        Creeper creeper = EntityType.CREEPER.create(level);
        if (creeper != null) {
            creeper.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);

            // Add machine-attacking AI (high priority)
            creeper.goalSelector.addGoal(1, new CreeperTargetMachineGoal(creeper));

            level.addFreshEntity(creeper);

            // Charged creeper: Summon lightning immediately after spawn
            if (charged) {
                net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                if (lightning != null) {
                    lightning.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    lightning.setVisualOnly(true); // Visual effect only, no damage
                    level.addFreshEntity(lightning);
                }
            }

            if (IndustrialLogger.isDebugEnabled()) {
                IndustrialLogger.debugPollution(String.format(
                        "Spawned hostile %screeper at %s (chunk %s)",
                        charged ? "CHARGED " : "", spawnPos, chunkPos));
            }
        }
    }

    /**
     * Finds a suitable spawn position within the chunk.
     * Ensures the position is valid for mob spawning (solid ground, air above).
     *
     * @param level The server level
     * @param chunkPos The chunk to search in
     * @return A valid spawn position, or null if none found
     */
    private static BlockPos findSpawnPosition(ServerLevel level, ChunkPos chunkPos) {
        // Randomly select position within chunk
        int x = chunkPos.getMinBlockX() + level.random.nextInt(16);
        int z = chunkPos.getMinBlockZ() + level.random.nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        BlockPos pos = new BlockPos(x, y, z);

        // Check if spawn position is valid
        if (!level.hasChunkAt(pos)) return null;
        if (!level.getBlockState(pos).isAir()) return null;
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) return null;

        return pos;
    }

    /**
     * Calculates the average voltage tier of machines in a chunk.
     * Only considers machines that have energy or are active.
     *
     * @param level The server level
     * @param chunkPos The chunk position to scan
     * @return Average voltage tier, or 0 if no machines found
     */
    public static double getChunkAverageVoltageTier(ServerLevel level, ChunkPos chunkPos) {
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) return 0;

        int totalTier = 0;
        int count = 0;

        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        for (var be : chunk.getBlockEntities().values()) {
            if (GTIntegration.isGTMachine(be) && GTIntegration.hasEnergyOrActive(be)) {
                int tier = GTIntegration.getVoltageTier(be);
                if (tier >= 0) {
                    totalTier += tier;
                    count++;
                }
            }
        }

        return count > 0 ? (double) totalTier / count : 0;
    }
}
