package cn.minerealms.iic.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD渲染器 - 优化版
 * 在屏幕右上角显示难度信息
 *
 * 性能优化：
 * - 缓存字符串宽度计算
 * - 减少对象创建
 * - 使用 I18N 支持多语言
 */
public class DifficultyHudRenderer {

    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND_COLOR = 0x80000000; // 半透明黑色
    private static final int BORDER_COLOR = 0xFF00FFFF; // 青色边框

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DifficultyHudData.PlayerHudData data = DifficultyHudData.getOrCreate(mc.player.getUUID());
        if (!data.showHud) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        List<HudLine> lines = buildHudLines(data);
        if (lines.isEmpty()) return;

        // 计算最大宽度
        int maxWidth = 0;
        for (HudLine line : lines) {
            int width = font.width(line.text);
            if (width > maxWidth) maxWidth = width;
        }

        // 计算HUD位置（右上角）
        int hudWidth = maxWidth + PADDING * 2;
        int hudHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
        int hudX = screenWidth - hudWidth - 10;
        int hudY = 10;

        // 绘制背景
        graphics.fill(hudX, hudY, hudX + hudWidth, hudY + hudHeight, BACKGROUND_COLOR);

        // 绘制边框
        graphics.fill(hudX, hudY, hudX + hudWidth, hudY + 1, BORDER_COLOR);
        graphics.fill(hudX, hudY + hudHeight - 1, hudX + hudWidth, hudY + hudHeight, BORDER_COLOR);
        graphics.fill(hudX, hudY, hudX + 1, hudY + hudHeight, BORDER_COLOR);
        graphics.fill(hudX + hudWidth - 1, hudY, hudX + hudWidth, hudY + hudHeight, BORDER_COLOR);

        // 绘制文本（右对齐）
        int currentY = hudY + PADDING;
        for (HudLine line : lines) {
            int textWidth = font.width(line.text);
            int textX = hudX + hudWidth - PADDING - textWidth;
            graphics.drawString(font, line.text, textX, currentY, line.color, false);
            currentY += LINE_HEIGHT;
        }
    }

    private static List<HudLine> buildHudLines(DifficultyHudData.PlayerHudData data) {
        List<HudLine> lines = new ArrayList<>();

        // 标题
        lines.add(new HudLine("§b§l=== " + I18n.get("integratedindustrialcraft.hud.title") + " ===", 0xFFFFFF));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // === 游戏阶段指标（置顶显示）===
        lines.add(new HudLine("§d§l[" + I18n.get("integratedindustrialcraft.hud.game_stage") + "]", 0xFF55FF));
        String stageColor = getGameStageColor(data.globalGameStage);
        String stageDesc = I18n.get(getGameStageTranslationKey(data.globalGameStage));
        lines.add(new HudLine(String.format("%s%.1f%% §7(%s)", stageColor, data.globalGameStage, stageDesc), 0xFFFFFF));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // === 附近污染等级 ===
        lines.add(new HudLine("§6[" + I18n.get("integratedindustrialcraft.hud.local_pollution") + "]", 0xFFAA00));
        String pollutionColor = getPollutionLevelColor(data.pollutionLevel);
        String pollutionText = I18n.get(data.pollutionLevel);
        lines.add(new HudLine(String.format("%s%s §7(%.1f%%)", pollutionColor, pollutionText, data.localPollutionProgress * 100), 0xFFFFFF));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // 三轴难度
        lines.add(new HudLine("§6[" + I18n.get("integratedindustrialcraft.hud.triaxis") + "]", 0xFFAA00));
        lines.add(new HudLine(String.format("§e%s: §f%.4f%%", I18n.get("integratedindustrialcraft.hud.total"), data.totalDifficulty * 100), 0xFFFF55));
        lines.add(new HudLine(String.format("  §7%s: §f%.4f%%", I18n.get("integratedindustrialcraft.hud.time"), data.timeFactor * 100), 0xAAAAAA));
        lines.add(new HudLine(String.format("  §7%s: §f%.4f%%", I18n.get("integratedindustrialcraft.hud.voltage"), data.voltageFactor * 100), 0xAAAAAA));
        lines.add(new HudLine(String.format("  §7%s: §f%.4f%%", I18n.get("integratedindustrialcraft.hud.pollution"), data.pollutionFactor * 100), 0xAAAAAA));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // 工业数据
        lines.add(new HudLine("§a[" + I18n.get("integratedindustrialcraft.hud.industrial") + "]", 0x55FF55));
        lines.add(new HudLine(String.format("§e%s: §f%d", I18n.get("integratedindustrialcraft.hud.machines"), data.nearbyMachines), 0xFFFF55));
        lines.add(new HudLine(String.format("§e%s: §f%.3f", I18n.get("integratedindustrialcraft.hud.median_tier"), data.medianTier), 0xFFFF55));
        lines.add(new HudLine(String.format("§e%s: §f%.3f", I18n.get("integratedindustrialcraft.hud.bonus"), data.industrialBonus), 0xFFFF55));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // 污染数据
        lines.add(new HudLine("§c[" + I18n.get("integratedindustrialcraft.hud.pollution") + "]", 0xFF5555));
        lines.add(new HudLine(String.format("§e%s: §f%.3f", I18n.get("integratedindustrialcraft.hud.local"), data.localPollution), 0xFFFF55));
        lines.add(new HudLine(String.format("§e%s: §f%.3f", I18n.get("integratedindustrialcraft.hud.global"), data.globalPollution), 0xFFFF55));
        lines.add(new HudLine("", 0xFFFFFF)); // 空行

        // Spore数据
        if (data.activeHiveminds > 0 || data.infectionLevel > 0) {
            lines.add(new HudLine("§d[" + I18n.get("integratedindustrialcraft.hud.spore") + "]", 0xFF55FF));
            lines.add(new HudLine(String.format("§e%s: §f%d", I18n.get("integratedindustrialcraft.hud.hiveminds"), data.activeHiveminds), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%d/10", I18n.get("integratedindustrialcraft.hud.evolution"), data.evolutionPhase), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%.1f%%", I18n.get("integratedindustrialcraft.hud.infection"), data.infectionLevel), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%d", I18n.get("integratedindustrialcraft.hud.biomass"), data.totalBiomass), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%d", I18n.get("integratedindustrialcraft.hud.hosts"), data.totalHosts), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%d", I18n.get("integratedindustrialcraft.hud.infected_chunks"), data.infectedChunks), 0xFFFF55));
            lines.add(new HudLine(String.format("§e%s: §f%.2fx", I18n.get("integratedindustrialcraft.hud.multiplier"), data.sporeMultiplier), 0xFFFF55));

            // Evolution breakdown (debug info)
            if (data.evolutionPhase > 0) {
                String evolutionColor = getEvolutionColor(data.evolutionPhase);
                String evolutionDesc = getEvolutionDescription(data.evolutionPhase);
                lines.add(new HudLine(String.format("  §7%s: %s%s",
                    I18n.get("integratedindustrialcraft.hud.threat_level"),
                    evolutionColor, evolutionDesc), 0xAAAAAA));
            }
        }

        return lines;
    }

    /**
     * 获取游戏阶段的颜色
     */
    private static String getGameStageColor(double stagePercent) {
        if (stagePercent < 20.0) return "§a";
        if (stagePercent < 40.0) return "§2";
        if (stagePercent < 60.0) return "§e";
        if (stagePercent < 80.0) return "§6";
        if (stagePercent < 95.0) return "§c";
        return "§4";
    }

    /**
     * 获取游戏阶段翻译键
     */
    private static String getGameStageTranslationKey(double stagePercent) {
        if (stagePercent < 10.0) return "integratedindustrialcraft.stage.early";
        if (stagePercent < 25.0) return "integratedindustrialcraft.stage.developing";
        if (stagePercent < 50.0) return "integratedindustrialcraft.stage.mid";
        if (stagePercent < 75.0) return "integratedindustrialcraft.stage.late";
        if (stagePercent < 90.0) return "integratedindustrialcraft.stage.endgame";
        return "integratedindustrialcraft.stage.final";
    }

    /**
     * 获取污染等级颜色
     */
    private static String getPollutionLevelColor(String translationKey) {
        return switch (translationKey) {
            case "integratedindustrialcraft.pollution.safe" -> "§a";
            case "integratedindustrialcraft.pollution.light" -> "§2";
            case "integratedindustrialcraft.pollution.moderate" -> "§e";
            case "integratedindustrialcraft.pollution.high" -> "§6";
            case "integratedindustrialcraft.pollution.severe" -> "§c";
            case "integratedindustrialcraft.pollution.catastrophic" -> "§4";
            default -> "§7";
        };
    }

    /**
     * 获取进化阶段颜色
     */
    private static String getEvolutionColor(int phase) {
        if (phase <= 2) return "§a";  // Green: Low threat
        if (phase <= 4) return "§2";  // Dark green: Moderate
        if (phase <= 6) return "§e";  // Yellow: Elevated
        if (phase <= 8) return "§6";  // Gold: High
        return "§c";  // Red: Critical
    }

    /**
     * 获取进化阶段描述
     */
    private static String getEvolutionDescription(int phase) {
        if (phase <= 2) return I18n.get("integratedindustrialcraft.evolution.low");
        if (phase <= 4) return I18n.get("integratedindustrialcraft.evolution.moderate");
        if (phase <= 6) return I18n.get("integratedindustrialcraft.evolution.elevated");
        if (phase <= 8) return I18n.get("integratedindustrialcraft.evolution.high");
        return I18n.get("integratedindustrialcraft.evolution.critical");
    }

    private static class HudLine {
        final String text;
        final int color;

        HudLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }
}
