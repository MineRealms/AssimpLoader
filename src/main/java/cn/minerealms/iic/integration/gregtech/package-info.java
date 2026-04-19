/**
 * GregTech CEu Modern integration.
 * <p>
 * This package provides integration with GregTech CEu Modern, detecting machines,
 * reading voltage tiers, and scanning for pollution sources.
 * <p>
 * <b>Key Components:</b>
 * <ul>
 *   <li>{@link cn.minerealms.iic.integration.gregtech.GTIntegration} - Machine detection and voltage tier reading</li>
 *   <li>{@link cn.minerealms.iic.integration.gregtech.GTPollutionScanner} - Pollution source scanning</li>
 * </ul>
 * <p>
 * <b>Voltage Tiers:</b>
 * <ul>
 *   <li>ULV (0), LV (1), MV (2), HV (3), EV (4), IV (5)</li>
 *   <li>LuV (6), ZPM (7), UV (8), UHV (9), UEV (10)</li>
 *   <li>UIV (11), UXV (12), OpV (13), MAX (14)</li>
 * </ul>
 * <p>
 * Supports both single-block machines and multiblock structures.
 *
 * @since 1.0.0
 * @author ImprovedMobs Team
 */
package cn.minerealms.iic.integration.gregtech;
