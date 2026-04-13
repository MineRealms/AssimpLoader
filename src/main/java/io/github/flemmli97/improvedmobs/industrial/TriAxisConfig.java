package io.github.flemmli97.improvedmobs.industrial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class TriAxisConfig {
    // 权重配置 (Weights)
    public static double weightTime = 0.35;
    public static double weightVoltage = 0.35;
    public static double weightPollution = 0.30;
    
    // 全局放缩与公式系数 (Math Constants)
    public static double globalMultiplier = 1.8;
    public static double emaAlpha = 0.05;
    public static double pollutionDenominator = 6000.0;
    
    // 扫描与阈值 (Scanning & Thresholds)
    public static int scanRadiusBlocks = 64;
    public static int maxGTTier = 14; 
    public static double maxChangePerSec = 0.01; 
    
    // 时间曲线参数 (Time Scaling)
    public static double targetDays = 540.0; 
    public static double baseDays = 30.0;    

    // 战斗属性倍率系数 (Combat Multipliers Base)
    public static double hpMultFactor = 0.45;
    public static double attackMultFactor = 0.20;

    // 调试设置
    public static boolean enableDebugLines = false;

    private static final File CONFIG_FILE = new File("config/triaxis-difficulty.properties");

    public static void load() {
        Properties props = new Properties();
        if (CONFIG_FILE.exists()) {
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
                weightTime = Double.parseDouble(props.getProperty("weightTime", String.valueOf(weightTime)));
                weightVoltage = Double.parseDouble(props.getProperty("weightVoltage", String.valueOf(weightVoltage)));
                weightPollution = Double.parseDouble(props.getProperty("weightPollution", String.valueOf(weightPollution)));
                globalMultiplier = Double.parseDouble(props.getProperty("globalMultiplier", String.valueOf(globalMultiplier)));
                emaAlpha = Double.parseDouble(props.getProperty("emaAlpha", String.valueOf(emaAlpha)));
                pollutionDenominator = Double.parseDouble(props.getProperty("pollutionDenominator", String.valueOf(pollutionDenominator)));
                scanRadiusBlocks = Integer.parseInt(props.getProperty("scanRadiusBlocks", String.valueOf(scanRadiusBlocks)));
                maxGTTier = Integer.parseInt(props.getProperty("maxGTTier", String.valueOf(maxGTTier)));
                maxChangePerSec = Double.parseDouble(props.getProperty("maxChangePerSec", String.valueOf(maxChangePerSec)));
                targetDays = Double.parseDouble(props.getProperty("targetDays", String.valueOf(targetDays)));
                baseDays = Double.parseDouble(props.getProperty("baseDays", String.valueOf(baseDays)));
                hpMultFactor = Double.parseDouble(props.getProperty("hpMultFactor", String.valueOf(hpMultFactor)));
                attackMultFactor = Double.parseDouble(props.getProperty("attackMultFactor", String.valueOf(attackMultFactor)));
                enableDebugLines = Boolean.parseBoolean(props.getProperty("enableDebugLines", String.valueOf(enableDebugLines)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        Properties props = new Properties();
        props.setProperty("weightTime", String.valueOf(weightTime));
        props.setProperty("weightVoltage", String.valueOf(weightVoltage));
        props.setProperty("weightPollution", String.valueOf(weightPollution));
        props.setProperty("globalMultiplier", String.valueOf(globalMultiplier));
        props.setProperty("emaAlpha", String.valueOf(emaAlpha));
        props.setProperty("pollutionDenominator", String.valueOf(pollutionDenominator));
        props.setProperty("scanRadiusBlocks", String.valueOf(scanRadiusBlocks));
        props.setProperty("maxGTTier", String.valueOf(maxGTTier));
        props.setProperty("maxChangePerSec", String.valueOf(maxChangePerSec));
        props.setProperty("targetDays", String.valueOf(targetDays));
        props.setProperty("baseDays", String.valueOf(baseDays));
        props.setProperty("hpMultFactor", String.valueOf(hpMultFactor));
        props.setProperty("attackMultFactor", String.valueOf(attackMultFactor));
        props.setProperty("enableDebugLines", String.valueOf(enableDebugLines));

        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                String comments = "Tri-Axis Difficulty Configuration / 三轴难度配置文件\n" +
                        "# weightTime: Time factor weight | 时间因子权重\n" +
                        "# weightVoltage: Machine voltage weight | 机器电压中位数权重\n" +
                        "# weightPollution: Spore pollution weight | 孢子生态污染权重\n" +
                        "# targetDays: MC Days to reach peak time difficulty | 达到最高时间难度的MC天数\n" +
                        "# baseDays: Base for logarithmic scaling | 对数缩放的基数（越小爬升越快）\n" +
                        "# emaAlpha: Smoothness factor (0.01 - 1.0) | 平滑过渡常数，越小越平滑\n" +
                        "# scanRadiusBlocks: Radius to scan machines | 扫描机器和污染的半径范围\n" +
                        "# maxGTTier: Maximum machine tier (GTCEu) | 设定的最高电压等级";
                props.store(out, comments);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasGTCEu() {
        try {
            Class.forName("com.gregtechceu.gtceu.GTCEu");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
