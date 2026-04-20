/**
 * <h1>Improved Mobs Industrial Integration API</h1>
 *
 * <p>This package provides the public API for extending Improved Mobs with custom
 * difficulty providers, pollution sources, threat handlers, and mod integrations.
 *
 * <h2>Main Entry Point</h2>
 * <p>Use {@link cn.minerealms.iic.api.I3CAPI} as the main entry point for all API operations.
 *
 * <h2>Quick Start</h2>
 *
 * <h3>Query Player Difficulty</h3>
 * <pre>{@code
 * float difficulty = ImprovedMobsAPI.getPlayerDifficulty(player);
 * }</pre>
 *
 * <h3>Add Pollution</h3>
 * <pre>{@code
 * ChunkPos chunkPos = new ChunkPos(blockPos);
 * ImprovedMobsAPI.addTemporaryPollution(chunkPos, 10.0);
 * }</pre>
 *
 * <h3>Clean Pollution</h3>
 * <pre>{@code
 * ImprovedMobsAPI.cleanPollutionInRadius(level, centerPos, radiusChunks, cleanAmount);
 * }</pre>
 *
 * <h3>Custom Difficulty Provider</h3>
 * <pre>{@code
 * public class MyDifficultyProvider implements DifficultyGetter {
 *     @Override
 *     public float getDifficulty(ServerLevel level, Vec3 pos) {
 *         return customDifficultyValue;
 *     }
 *
 *     @Override
 *     public Config.IntegrationType getType() {
 *         return Config.IntegrationType.ADD;
 *     }
 * }
 *
 * // Register in mod constructor
 * DifficultyFetcher.add(new MyDifficultyProvider());
 * }</pre>
 *
 * <h2>Core Systems</h2>
 *
 * <h3>Difficulty System</h3>
 * <p>Provides dynamic difficulty scaling based on:
 * <ul>
 *   <li>Player industrial progression (machine count, voltage tiers)</li>
 *   <li>Local temporary pollution (chunk-based)</li>
 *   <li>Global permanent pollution (world-wide accumulation)</li>
 *   <li>Time factor (game days elapsed)</li>
 * </ul>
 *
 * <h3>Pollution System</h3>
 * <p>Implements a dual-layer pollution model:
 * <ul>
 *   <li><b>Temporary Pollution</b>: Chunk-based, can be absorbed by environment</li>
 *   <li><b>Permanent Pollution</b>: Global accumulation, directly affects difficulty</li>
 * </ul>
 *
 * <h3>Threat System</h3>
 * <p>Spawns hostile mobs based on pollution levels and voltage tiers:
 * <ul>
 *   <li>MV (Tier 2): Zombies attack machines when pollution ≥ 50</li>
 *   <li>HV (Tier 3+): Active zombie spawning when pollution ≥ 80</li>
 *   <li>HV (Tier 3+): Active creeper spawning when pollution ≥ 120</li>
 *   <li>Extreme: Charged creeper spawning when pollution ≥ 200</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All API methods are thread-safe and can be called from any thread. Heavy
 * operations are automatically offloaded to background threads to prevent server lag.
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>Pollution scanning: Async, every 1 second</li>
 *   <li>Environment caching: Background thread, every 5 minutes</li>
 *   <li>Threat detection: Probability-based, every 1 second</li>
 *   <li>Optimized for large modpacks with hundreds of machines</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>All system parameters are configurable via {@code triaxis-difficulty.properties}:
 * <ul>
 *   <li>Pollution generation rates and thresholds</li>
 *   <li>Threat trigger conditions and spawn probabilities</li>
 *   <li>Difficulty weights and calculation formulas</li>
 *   <li>Voltage tier thresholds for threat escalation</li>
 * </ul>
 *
 * <h2>Documentation</h2>
 * <p>For comprehensive documentation, see:
 * <ul>
 *   <li>{@code DEVELOPER_GUIDE.md} - Complete developer guide with examples</li>
 *   <li>{@code CLAUDE.md} - Project overview and architecture</li>
 *   <li>{@code POLLUTION_SYSTEM_ANALYSIS.md} - Detailed pollution system analysis</li>
 * </ul>
 *
 * @author ImprovedMobs Team
 * @version 1.0.0
 * @since 1.0.0
 */
package cn.minerealms.iic.api;
