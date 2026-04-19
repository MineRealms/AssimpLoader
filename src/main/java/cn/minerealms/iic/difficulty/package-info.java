/**
 * Difficulty calculation and tracking system.
 * <p>
 * This package contains the core difficulty calculation logic that determines
 * how industrial progression affects mob difficulty in the game.
 * <p>
 * <b>Key Components:</b>
 * <ul>
 *   <li>{@link cn.minerealms.iic.difficulty.DifficultyManager} - Per-player difficulty state management</li>
 *   <li>{@link cn.minerealms.iic.difficulty.DifficultyProvider} - ImprovedMobs API integration</li>
 *   <li>{@link cn.minerealms.iic.difficulty.TriAxisDifficultyManager} - Three-axis difficulty calculation (time, voltage, pollution)</li>
 *   <li>{@link cn.minerealms.iic.difficulty.MachineScanner} - Scans nearby machines for voltage tier</li>
 *   <li>{@link cn.minerealms.iic.difficulty.HazardScanner} - Scans environmental hazards</li>
 *   <li>{@link cn.minerealms.iic.difficulty.DifficultySmoother} - Smooths difficulty transitions</li>
 *   <li>{@link cn.minerealms.iic.difficulty.GameStageCalculator} - Calculates game progression stage</li>
 * </ul>
 * <p>
 * <b>Difficulty Calculation Flow:</b>
 * <ol>
 *   <li>MachineScanner detects nearby machines and determines median voltage tier</li>
 *   <li>HazardScanner evaluates pollution levels around the player</li>
 *   <li>TriAxisDifficultyManager combines time, voltage, and pollution factors</li>
 *   <li>DifficultySmoother prevents sudden difficulty spikes</li>
 *   <li>DifficultyManager stores per-player difficulty values</li>
 *   <li>DifficultyProvider exposes difficulty to ImprovedMobs API</li>
 * </ol>
 *
 * @since 1.0.0
 * @author ImprovedMobs Team
 */
package cn.minerealms.iic.difficulty;
