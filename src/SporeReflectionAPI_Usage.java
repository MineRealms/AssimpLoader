package your.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import your.mod.integration.SporeReflectionAPI;
import your.mod.integration.SporeReflectionAPI.MoundData;

import java.util.List;

/**
 * SporeReflectionAPI 使用示例
 *
 * 这个类展示了如何使用SporeReflectionAPI来：
 * 1. 检测附近的Mound实体
 * 2. 计算感染强度
 * 3. 根据强度更新shader效果
 *
 * 使用步骤：
 * 1. 复制SporeReflectionAPI.java到你的项目
 * 2. 复制这个示例类并修改包名
 * 3. 在你的主类中注册事件总线
 * 4. 实现updateShaderEffect方法
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "yourmodid", value = Dist.CLIENT)
public class InfectionShaderHandler {

    // ==================== 配置参数 ====================

    // 检测间隔（tick）
    private static final int CHECK_INTERVAL = 10; // 每0.5秒检测一次

    // 距离范围
    private static final double DETECTION_RADIUS = 64.0; // 检测半径

    // 平滑过渡速度
    private static final float FADE_SPEED = 0.1f; // 每tick变化10%

    // ==================== 状态变量 ====================

    private static SporeReflectionAPI api = null;
    private static float currentIntensity = 0f;
    private static int checkCooldown = 0;

    // ==================== 主要逻辑 ====================

    /**
     * 客户端Tick事件
     * 每tick执行一次，但实际检测每0.5秒一次
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 获取玩家
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        // 初始化API（只执行一次）
        if (api == null) {
            api = SporeReflectionAPI.getInstance();
            if (!api.isSporeLoaded()) {
                System.out.println("[InfectionShader] Spore mod not detected, shader disabled");
                return;
            }
            System.out.println("[InfectionShader] Spore mod detected, shader enabled");
        }

        // 检测冷却
        if (--checkCooldown > 0) {
            // 即使不检测，也要平滑过渡当前强度
            smoothTransition();
            return;
        }

        // 重置冷却
        checkCooldown = CHECK_INTERVAL;

        // 计算目标强度
        float targetIntensity = api.calculateInfectionIntensity(player);

        // 平滑过渡到目标强度
        currentIntensity = SporeReflectionAPI.lerp(currentIntensity, targetIntensity, FADE_SPEED);

        // 更新shader效果
        updateShaderEffect(currentIntensity);

        // 调试输出（每秒一次）
        if (player.tickCount % 20 == 0 && currentIntensity > 0.01f) {
            debugOutput(player);
        }
    }

    /**
     * 平滑过渡（在不检测的tick中也要执行）
     */
    private static void smoothTransition() {
        if (Math.abs(currentIntensity) > 0.001f) {
            // 如果没有新的目标，逐渐衰减到0
            currentIntensity = SporeReflectionAPI.lerp(currentIntensity, 0f, FADE_SPEED * 0.5f);
            updateShaderEffect(currentIntensity);
        }
    }

    /**
     * 更新shader效果
     * TODO: 在这里实现你的shader逻辑
     *
     * @param intensity 感染强度 (0.0 - 1.0)
     */
    private static void updateShaderEffect(float intensity) {
        if (intensity < 0.01f) {
            // 强度太低，禁用shader
            disableShader();
        } else {
            // 启用shader并传递强度参数
            enableShader(intensity);
        }
    }

    /**
     * 启用shader
     */
    private static void enableShader(float intensity) {
        // TODO: 实现shader启用逻辑
        // 示例：
        // Minecraft.getInstance().gameRenderer.loadEffect(new ResourceLocation("yourmod", "infection"));
        // 然后通过uniform传递intensity参数
    }

    /**
     * 禁用shader
     */
    private static void disableShader() {
        // TODO: 实现shader禁用逻辑
        // 示例：
        // Minecraft.getInstance().gameRenderer.shutdownEffect();
    }

    /**
     * 调试输出
     */
    private static void debugOutput(LocalPlayer player) {
        System.out.println("=== Infection Shader Debug ===");
        System.out.println("Intensity: " + String.format("%.3f", currentIntensity));

        // 获取附近的Mound
        List<MoundData> mounds = api.getMoundsNearPlayer(player, DETECTION_RADIUS);
        System.out.println("Nearby Mounds: " + mounds.size());

        for (int i = 0; i < Math.min(mounds.size(), 3); i++) {
            MoundData mound = mounds.get(i);
            System.out.println("  " + (i + 1) + ". " + mound.toString());
        }

        // 获取最近的Mound
        MoundData nearest = api.getNearestMound(player, DETECTION_RADIUS);
        if (nearest != null) {
            double distance = nearest.getDistanceTo(player.position());
            System.out.println("Nearest Mound: " + String.format("%.1f", distance) + " blocks away");
        }

        System.out.println("==============================");
    }

    // ==================== 公共API ====================

    /**
     * 获取当前感染强度
     * 供其他模块使用
     */
    public static float getCurrentIntensity() {
        return currentIntensity;
    }

    /**
     * 手动设置强度（用于测试）
     */
    public static void setIntensity(float intensity) {
        currentIntensity = Math.max(0f, Math.min(1f, intensity));
    }

    /**
     * 检查shader是否激活
     */
    public static boolean isShaderActive() {
        return currentIntensity > 0.01f;
    }
}


// ==================== 高级用法示例 ====================

/**
 * 高级用法：自定义感染强度计算
 */
class AdvancedInfectionCalculator {

    private final SporeReflectionAPI api = SporeReflectionAPI.getInstance();

    /**
     * 示例1：只考虑高龄Mound
     */
    public float calculateHighAgeMoundIntensity(LocalPlayer player) {
        List<MoundData> mounds = api.getMoundsNearPlayer(player, 64.0);

        float maxIntensity = 0f;

        for (MoundData mound : mounds) {
            // 只考虑年龄>=3的Mound
            if (mound.getAge() < 3) continue;

            double distance = mound.getDistanceTo(player.position());
            float intensity = SporeReflectionAPI.calculateDistanceFalloff(distance, 16, 64);

            maxIntensity = Math.max(maxIntensity, intensity);
        }

        return maxIntensity;
    }

    /**
     * 示例2：考虑Mound的扩散进度
     */
    public float calculateSpreadProgressIntensity(LocalPlayer player) {
        List<MoundData> mounds = api.getMoundsNearPlayer(player, 64.0);

        float totalIntensity = 0f;

        for (MoundData mound : mounds) {
            // 即将扩散的Mound（进度>80%）会产生更强的效果
            float progress = mound.getSpreadProgress();
            if (progress > 0.8f) {
                double distance = mound.getDistanceTo(player.position());
                float baseIntensity = SporeReflectionAPI.calculateDistanceFalloff(distance, 16, 64);

                // 扩散进度加成
                float progressBonus = (progress - 0.8f) * 5f; // 0.8-1.0 -> 0-1.0
                totalIntensity += baseIntensity * (1f + progressBonus);
            }
        }

        return Math.min(totalIntensity, 1.0f);
    }

    /**
     * 示例3：多层次强度计算
     */
    public IntensityLevels calculateMultiLevelIntensity(LocalPlayer player) {
        MoundData nearest = api.getNearestMound(player, 64.0);

        if (nearest == null) {
            return new IntensityLevels(0f, 0f, 0f);
        }

        double distance = nearest.getDistanceTo(player.position());

        // 三个层次的强度
        float closeIntensity = distance < 16 ? 1.0f : 0f;
        float mediumIntensity = (distance >= 16 && distance < 32) ? 1.0f : 0f;
        float farIntensity = (distance >= 32 && distance < 64) ? 1.0f : 0f;

        return new IntensityLevels(closeIntensity, mediumIntensity, farIntensity);
    }

    /**
     * 强度层次数据类
     */
    public static class IntensityLevels {
        public final float close;   // 近距离强度
        public final float medium;  // 中距离强度
        public final float far;     // 远距离强度

        public IntensityLevels(float close, float medium, float far) {
            this.close = close;
            this.medium = medium;
            this.far = far;
        }
    }
}


// ==================== 性能优化示例 ====================

/**
 * 性能优化：使用缓存减少反射调用
 */
class CachedInfectionDetector {

    private final SporeReflectionAPI api = SporeReflectionAPI.getInstance();

    // 缓存
    private List<MoundData> cachedMounds = null;
    private int lastCacheUpdate = 0;
    private static final int CACHE_LIFETIME = 100; // 5秒

    /**
     * 获取Mound列表（带缓存）
     */
    public List<MoundData> getCachedMounds(LocalPlayer player, int currentTick) {
        // 检查缓存是否过期
        if (cachedMounds == null || currentTick - lastCacheUpdate > CACHE_LIFETIME) {
            cachedMounds = api.getMoundsNearPlayer(player, 64.0);
            lastCacheUpdate = currentTick;
        }

        return cachedMounds;
    }

    /**
     * 使用缓存计算强度
     */
    public float calculateIntensityWithCache(LocalPlayer player, int currentTick) {
        List<MoundData> mounds = getCachedMounds(player, currentTick);

        float maxIntensity = 0f;

        for (MoundData mound : mounds) {
            double distance = mound.getDistanceTo(player.position());
            float intensity = SporeReflectionAPI.calculateDistanceFalloff(distance, 16, 64);
            maxIntensity = Math.max(maxIntensity, intensity);
        }

        return maxIntensity;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cachedMounds = null;
    }
}
