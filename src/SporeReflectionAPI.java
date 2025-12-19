package your.mod.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Spore模组反射API工具类
 *
 * 功能：
 * 1. 检测附近的Mound实体
 * 2. 获取Mound的属性（年龄、连接状态等）
 * 3. 计算感染强度
 * 4. 检测感染方块密度
 * 5. 检测生物群系类型
 *
 * 使用方式：
 * SporeReflectionAPI api = SporeReflectionAPI.getInstance();
 * if (api.isSporeLoaded()) {
 *     float intensity = api.calculateInfectionIntensity(player);
 * }
 *
 * @author Your Name
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public class SporeReflectionAPI {

    // ==================== 单例模式 ====================

    private static SporeReflectionAPI INSTANCE = null;

    public static SporeReflectionAPI getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SporeReflectionAPI();
        }
        return INSTANCE;
    }

    // ==================== 反射缓存 ====================

    private boolean initialized = false;
    private boolean sporeLoaded = false;

    // Mound类相关
    private Class<?> moundClass = null;
    private Method getAgeMethod = null;
    private Method getLinkedMethod = null;
    private Method getCounterMethod = null;
    private Method getMaxCounterMethod = null;
    private Method getMaxAgeMethod = null;

    // Proto类相关
    private Class<?> protoClass = null;

    // SporeSavedData类相关
    private Class<?> savedDataClass = null;
    private Method getHivemindsMethod = null;
    private Method getAmountOfHivemindsMethod = null;

    // Organoid基类
    private Class<?> organoidClass = null;

    // ==================== 初始化 ====================

    private SporeReflectionAPI() {
        // 私有构造函数
    }

    /**
     * 初始化反射，只执行一次
     * 自动在第一次调用时执行
     */
    private void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            // 加载Mound类
            moundClass = Class.forName("com.Harbinger.Spore.Sentities.Organoids.Mound");

            // 加载Mound的方法
            getAgeMethod = moundClass.getMethod("getAge");
            getLinkedMethod = moundClass.getMethod("getLinked");
            getCounterMethod = moundClass.getMethod("getCounter");
            getMaxCounterMethod = moundClass.getMethod("getMaxCounter");
            getMaxAgeMethod = moundClass.getMethod("getMaxAge");

            // 加载Proto类
            protoClass = Class.forName("com.Harbinger.Spore.Sentities.Organoids.Proto");

            // 加载SporeSavedData类
            savedDataClass = Class.forName("com.Harbinger.Spore.ExtremelySusThings.SporeSavedData");
            getHivemindsMethod = savedDataClass.getMethod("getHiveminds");
            getAmountOfHivemindsMethod = savedDataClass.getMethod("getAmountOfHiveminds");

            // 加载Organoid基类
            organoidClass = Class.forName("com.Harbinger.Spore.Sentities.BaseEntities.Organoid");

            sporeLoaded = true;
            System.out.println("[SporeReflectionAPI] Successfully initialized Spore mod reflection");

        } catch (ClassNotFoundException e) {
            System.out.println("[SporeReflectionAPI] Spore mod not found - API disabled");
            sporeLoaded = false;
        } catch (NoSuchMethodException e) {
            System.err.println("[SporeReflectionAPI] Failed to find required methods: " + e.getMessage());
            sporeLoaded = false;
        } catch (Exception e) {
            System.err.println("[SporeReflectionAPI] Unexpected error during initialization: " + e.getMessage());
            e.printStackTrace();
            sporeLoaded = false;
        }
    }

    /**
     * 检查Spore模组是否已加载
     * @return true如果Spore可用
     */
    public boolean isSporeLoaded() {
        if (!initialized) {
            initialize();
        }
        return sporeLoaded;
    }

    // ==================== Mound实体检测 ====================

    /**
     * 获取指定范围内的所有Mound实体
     *
     * @param level 世界
     * @param center 中心位置
     * @param radius 搜索半径（方块数）
     * @return Mound实体列表，如果Spore未加载则返回空列表
     */
    public List<MoundData> getMoundsInRange(Level level, Vec3 center, double radius) {
        if (!isSporeLoaded() || moundClass == null) {
            return Collections.emptyList();
        }

        try {
            AABB searchBox = new AABB(center.x - radius, center.y - radius, center.z - radius,
                                      center.x + radius, center.y + radius, center.z + radius);

            List<? extends Entity> entities = level.getEntitiesOfClass(moundClass, searchBox);

            List<MoundData> result = new ArrayList<>();
            for (Entity entity : entities) {
                result.add(new MoundData(entity));
            }

            return result;

        } catch (Exception e) {
            System.err.println("[SporeReflectionAPI] Error getting mounds: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取玩家附近的Mound实体
     *
     * @param player 玩家
     * @param radius 搜索半径
     * @return Mound数据列表
     */
    public List<MoundData> getMoundsNearPlayer(LocalPlayer player, double radius) {
        return getMoundsInRange(player.level(), player.position(), radius);
    }

    /**
     * 获取最近的Mound实体
     *
     * @param player 玩家
     * @param maxRadius 最大搜索半径
     * @return 最近的Mound，如果没有则返回null
     */
    public MoundData getNearestMound(LocalPlayer player, double maxRadius) {
        List<MoundData> mounds = getMoundsNearPlayer(player, maxRadius);

        if (mounds.isEmpty()) {
            return null;
        }

        MoundData nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (MoundData mound : mounds) {
            double distance = mound.getDistanceTo(player.position());
            if (distance < minDistance) {
                minDistance = distance;
                nearest = mound;
            }
        }

        return nearest;
    }

    // ==================== 感染强度计算 ====================

    /**
     * 计算玩家位置的感染强度（综合所有因素）
     *
     * @param player 玩家
     * @return 感染强度 (0.0 - 1.0)
     */
    public float calculateInfectionIntensity(LocalPlayer player) {
        if (!isSporeLoaded()) {
            return 0f;
        }

        float moundIntensity = calculateMoundIntensity(player);
        float blockIntensity = calculateBlockInfectionDensity(player);
        float biomeIntensity = calculateBiomeIntensity(player);

        // 取最大值，但方块密度作为加成
        float baseIntensity = Math.max(moundIntensity, biomeIntensity);
        float totalIntensity = baseIntensity + (blockIntensity * 0.3f);

        return Math.min(totalIntensity, 1.0f);
    }

    /**
     * 计算Mound实体的影响强度
     *
     * @param player 玩家
     * @return Mound强度 (0.0 - 1.0)
     */
    public float calculateMoundIntensity(LocalPlayer player) {
        List<MoundData> mounds = getMoundsNearPlayer(player, 64.0);

        if (mounds.isEmpty()) {
            return 0f;
        }

        float maxIntensity = 0f;

        for (MoundData mound : mounds) {
            double distance = mound.getDistanceTo(player.position());

            // 距离衰减
            float distanceIntensity;
            if (distance < 16) {
                distanceIntensity = 1.0f;
            } else if (distance < 64) {
                distanceIntensity = (float) ((64 - distance) / 48.0);
            } else {
                continue;
            }

            // 年龄加成 (age 1-4 -> 1.0x - 1.6x)
            float ageMultiplier = 1.0f + (mound.getAge() * 0.15f);

            // 连接状态加成
            float linkedMultiplier = mound.isLinked() ? 1.3f : 1.0f;

            // 扩散进度加成（即将扩散时强度更高）
            float spreadProgress = mound.getSpreadProgress();
            float spreadMultiplier = 1.0f + (spreadProgress * 0.2f);

            // 综合计算
            float entityIntensity = distanceIntensity * ageMultiplier * linkedMultiplier * spreadMultiplier;

            maxIntensity = Math.max(maxIntensity, entityIntensity);
        }

        return Math.min(maxIntensity, 1.0f);
    }

    /**
     * 计算周围感染方块的密度
     *
     * @param player 玩家
     * @return 方块密度强度 (0.0 - 1.0)
     */
    public float calculateBlockInfectionDensity(LocalPlayer player) {
        BlockPos playerPos = player.blockPosition();
        int radius = 8;
        int infectedCount = 0;
        int totalChecked = 0;

        // 采样周围方块（每隔2格采样一次以提高性能）
        for (int x = -radius; x <= radius; x += 2) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z += 2) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    BlockState state = player.level().getBlockState(checkPos);

                    if (isInfectedBlock(state)) {
                        infectedCount++;
                    }
                    totalChecked++;
                }
            }
        }

        return totalChecked > 0 ? (float) infectedCount / totalChecked : 0f;
    }

    /**
     * 检查方块是否是感染方块
     *
     * @param state 方块状态
     * @return true如果是感染方块
     */
    public boolean isInfectedBlock(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) return false;

        String namespace = blockId.getNamespace();
        String path = blockId.getPath();

        // Spore模组的方块
        if (namespace.equals("spore")) {
            return path.contains("infested") ||
                   path.contains("biomass") ||
                   path.contains("mycelium") ||
                   path.contains("rotten") ||
                   path.contains("fungal") ||
                   path.contains("remains") ||
                   path.contains("membrane");
        }

        return false;
    }

    /**
     * 计算生物群系的感染强度
     *
     * @param player 玩家
     * @return 生物群系强度 (0.0 - 1.0)
     */
    public float calculateBiomeIntensity(LocalPlayer player) {
        try {
            Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());

            // 检查是否是蘑菇群系
            if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_MUSHROOM)) {
                return 0.3f;
            }

            // 检查是否是Spore自定义生物群系
            Optional<ResourceLocation> biomeKey = biomeHolder.unwrapKey()
                .map(key -> key.location());

            if (biomeKey.isPresent()) {
                ResourceLocation location = biomeKey.get();
                if (location.getNamespace().equals("spore")) {
                    return 0.5f;
                }
            }

            return 0f;

        } catch (Exception e) {
            return 0f;
        }
    }

    // ==================== Proto/HiveMind相关 ====================

    /**
     * 获取世界中Proto（HiveMind）的数量
     *
     * @param level 世界
     * @return Proto数量
     */
    public int getHiveMindCount(Level level) {
        if (!isSporeLoaded() || protoClass == null) {
            return 0;
        }

        try {
            // 方法1：通过SporeSavedData获取（可能不准确）
            if (savedDataClass != null && getAmountOfHivemindsMethod != null) {
                // 注意：这个方法需要ServerLevel，客户端可能无法使用
                // 这里仅作为示例
            }

            // 方法2：直接扫描世界（更可靠）
            AABB worldBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
            List<? extends Entity> protos = level.getEntitiesOfClass(protoClass, worldBox);
            return protos.size();

        } catch (Exception e) {
            System.err.println("[SporeReflectionAPI] Error getting hivemind count: " + e.getMessage());
            return 0;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 计算距离衰减系数
     *
     * @param distance 距离
     * @param minRange 最小范围（满强度）
     * @param maxRange 最大范围（零强度）
     * @return 衰减系数 (0.0 - 1.0)
     */
    public static float calculateDistanceFalloff(double distance, double minRange, double maxRange) {
        if (distance < minRange) {
            return 1.0f;
        } else if (distance < maxRange) {
            return (float) ((maxRange - distance) / (maxRange - minRange));
        } else {
            return 0f;
        }
    }

    /**
     * 平滑插值
     *
     * @param current 当前值
     * @param target 目标值
     * @param speed 插值速度 (0.0 - 1.0)
     * @return 插值后的值
     */
    public static float lerp(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    // ==================== Mound数据包装类 ====================

    /**
     * Mound实体的数据包装类
     * 提供便捷的访问方法，隐藏反射细节
     */
    public class MoundData {
        private final Entity entity;
        private final Vec3 position;

        // 缓存的属性
        private Integer age = null;
        private Boolean linked = null;
        private Integer counter = null;
        private Integer maxCounter = null;
        private Integer maxAge = null;

        public MoundData(Entity entity) {
            this.entity = entity;
            this.position = entity.position();
        }

        /**
         * 获取Mound的年龄 (1-4)
         * 年龄越高，扩散范围越大
         */
        public int getAge() {
            if (age == null) {
                age = invokeMethod(getAgeMethod, entity, 1);
            }
            return age;
        }

        /**
         * 是否连接到Proto（HiveMind）
         * 连接的Mound会更强大
         */
        public boolean isLinked() {
            if (linked == null) {
                linked = invokeMethod(getLinkedMethod, entity, false);
            }
            return linked;
        }

        /**
         * 获取当前扩散计数器
         */
        public int getCounter() {
            if (counter == null) {
                counter = invokeMethod(getCounterMethod, entity, 0);
            }
            return counter;
        }

        /**
         * 获取最大扩散计数器（冷却时间）
         */
        public int getMaxCounter() {
            if (maxCounter == null) {
                maxCounter = invokeMethod(getMaxCounterMethod, entity, 100);
            }
            return maxCounter;
        }

        /**
         * 获取最大年龄
         */
        public int getMaxAge() {
            if (maxAge == null) {
                maxAge = invokeMethod(getMaxAgeMethod, entity, 4);
            }
            return maxAge;
        }

        /**
         * 获取扩散进度 (0.0 - 1.0)
         * 接近1.0时即将触发扩散
         */
        public float getSpreadProgress() {
            int current = getCounter();
            int max = getMaxCounter();
            return max > 0 ? (float) current / max : 0f;
        }

        /**
         * 获取位置
         */
        public Vec3 getPosition() {
            return position;
        }

        /**
         * 获取方块位置
         */
        public BlockPos getBlockPosition() {
            return entity.blockPosition();
        }

        /**
         * 计算到指定位置的距离
         */
        public double getDistanceTo(Vec3 pos) {
            return position.distanceTo(pos);
        }

        /**
         * 计算到指定位置的距离平方（性能更好）
         */
        public double getDistanceToSqr(Vec3 pos) {
            return position.distanceToSqr(pos);
        }

        /**
         * 获取底层实体（高级用途）
         */
        public Entity getEntity() {
            return entity;
        }

        /**
         * 是否存活
         */
        public boolean isAlive() {
            return entity.isAlive();
        }

        @Override
        public String toString() {
            return String.format("Mound{age=%d, linked=%b, progress=%.2f, pos=%s}",
                getAge(), isLinked(), getSpreadProgress(), position);
        }
    }

    // ==================== 反射辅助方法 ====================

    /**
     * 安全地调用反射方法
     */
    @SuppressWarnings("unchecked")
    private <T> T invokeMethod(Method method, Object target, T defaultValue) {
        if (method == null || target == null) {
            return defaultValue;
        }

        try {
            return (T) method.invoke(target);
        } catch (Exception e) {
            System.err.println("[SporeReflectionAPI] Failed to invoke method: " + method.getName());
            return defaultValue;
        }
    }
}
