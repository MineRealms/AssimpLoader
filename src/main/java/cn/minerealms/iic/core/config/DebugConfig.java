package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Debug configuration section.
 */
public class DebugConfig {
    public final ForgeConfigSpec.BooleanValue enableDebugLines;
    public final ForgeConfigSpec.BooleanValue enableDifficultyLogging;
    public final ForgeConfigSpec.BooleanValue enableSporeDebug;

    public DebugConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Debug Configuration - For development and troubleshooting")
                .push("debug");

        enableDebugLines = builder
                .comment("Enable debug line rendering (e.g., zombie attack lines)")
                .define("enable_debug_lines", false);

        enableDifficultyLogging = builder
                .comment("Enable detailed difficulty logging")
                .define("enable_difficulty_logging", false);

        enableSporeDebug = builder
                .comment("Enable Spore integration debug information")
                .define("enable_spore_debug", false);

        builder.pop();
    }
}
