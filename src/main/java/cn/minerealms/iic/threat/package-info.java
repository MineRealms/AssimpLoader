/**
 * Threat spawning and management system.
 * <p>
 * This package manages dynamic threat mechanisms that scale with industrial
 * progression and pollution levels.
 * <p>
 * <b>Key Components:</b>
 * <ul>
 *   <li>{@link cn.minerealms.iic.threat.ThreatManager} - Core threat spawning logic</li>
 *   <li>{@link cn.minerealms.iic.threat.horde.HordeManager} - Horde wave management</li>
 *   <li>{@link cn.minerealms.iic.threat.horde.HordeIntegrationManager} - Integration with The Hordes mod</li>
 * </ul>
 * <p>
 * <b>Threat Tiers:</b>
 * <ul>
 *   <li><b>MV (Tier 2):</b> Nearby zombies attack machines when pollution ≥ 50</li>
 *   <li><b>HV (Tier 3+):</b> Active zombie spawning when pollution ≥ 80</li>
 *   <li><b>HV+ (Tier 3+):</b> Creeper spawning when pollution ≥ 120</li>
 *   <li><b>Extreme:</b> Lightning creepers when pollution ≥ 200</li>
 * </ul>
 * <p>
 * Threats are triggered based on voltage tier and pollution levels, creating
 * escalating challenges as players advance technologically.
 *
 * @since 1.0.0
 * @author ImprovedMobs Team
 */
package cn.minerealms.iic.threat;
