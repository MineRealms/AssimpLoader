# 污染系统深度分析文档

## 目录
1. [系统概述](#系统概述)
2. [双层污染架构](#双层污染架构)
3. [污染生成机制](#污染生成机制)
4. [污染扩散与衰减](#污染扩散与衰减)
5. [环境吸收系统](#环境吸收系统)
6. [污染转化机制](#污染转化机制)
7. [视觉效果系统](#视觉效果系统)
8. [性能优化策略](#性能优化策略)
9. [配置参数](#配置参数)
10. [集成接口](#集成接口)

---

## 系统概述

污染系统是 ImprovedMobs Industrial Integration 的核心机制之一，通过追踪工业机器产生的环境污染，动态调整游戏难度和触发威胁事件。

### 核心设计理念
- **双层污染模型**：临时污染（可逆）+ 永久污染（不可逆）
- **异步处理**：所有重型计算在后台线程执行，避免主线程卡顿
- **区块级精度**：以区块为单位追踪污染，平衡精度与性能
- **环境交互**：树叶、水、草等自然方块可吸收污染

### 主要文件
```
pollution/
├── PollutionManager.java          # 核心管理器（454行）
└── visual/
    └── PollutionVisualEffects.java # 视觉效果（285行）

integration/gregtech/
└── GTPollutionScanner.java         # GT污染扫描器（293行）
```

---

## 双层污染架构

### 1. 临时污染（Temporary Pollution）

**特性：**
- **存储结构**：`ConcurrentHashMap<ChunkPos, Double>`
- **作用范围**：区块级别，可扩散到相邻区块
- **生命周期**：可被环境吸收，会自然衰减
- **触发效果**：
  - 威胁生成（僵尸、苦力怕攻击机器）
  - 视觉效果（屏幕模糊、恶心效果）
  - 不直接影响 ImprovedMobs 难度

**数据结构：**
```java
// 区块污染映射
private static final Map<ChunkPos, Double> temporaryPollution = new ConcurrentHashMap<>();

// 环境吸收缓存（每5分钟更新）
private static final Map<ChunkPos, Double> environmentalReductionCache = new ConcurrentHashMap<>();
```

**污染阈值：**
| 污染等级 | 数值范围 | 触发效果 |
|---------|---------|---------|
| 轻度 | 50-100 | 绿色粒子效果 |
| 中度 | 100-150 | 黄色粒子 + 恶心 I |
| 重度 | 150-200 | 橙色粒子 + 恶心 II |
| 严重 | 200+ | 红色粒子 + 恶心 III + 失明 |

### 2. 永久污染（Permanent Pollution）

**特性：**
- **存储结构**：`static double permanentPollution`（全局单一值）
- **作用范围**：全服务器共享
- **生命周期**：永久累积，不可逆转
- **触发效果**：
  - 直接增加 ImprovedMobs 全局难度
  - 代表长期工业发展的环境代价
  - 影响 Spore 进化速度

**转化条件：**
```java
// 当临时污染超过阈值时，开始转化为永久污染
if (temporaryPollution > TEMP_TO_PERM_THRESHOLD) {  // 默认 200.0
    double converted = temporaryPollution * TEMP_TO_PERM_RATE;  // 默认 0.001
    addPermanentPollution(converted);
}
```

**难度转化：**
```java
// 当永久污染累积到一定程度，转化为全局难度
if (permanentPollution >= PERM_TO_DIFFICULTY_RATE) {  // 默认 0.1
    DifficultyData.addDifficulty((float)permanentPollution, server);
    permanentPollution = 0.0;  // 重置计数器
}
```

---

## 污染生成机制

### 1. GregTech 机器污染

**扫描逻辑：**
```java
// 每秒扫描一次（20 ticks）
if (tickCounter % 20 == 0) {
    processPollutionDecayAndScanning(level);
}
```

**污染计算公式：**
```java
// 基础污染 = 0.01 * (1 + tier * 0.5) 污染/秒
double pollutionValue = 0.01 * (1 + tier * 0.5);

// 多方块结构 ×3
if (GTIntegration.isMultiblock(be)) {
    pollutionValue *= 3.0;
}
```

**电压等级污染表：**
| 等级 | 名称 | 单方块污染/秒 | 多方块污染/秒 |
|-----|------|-------------|-------------|
| 0 | ULV | 0.010 | 0.030 |
| 1 | LV | 0.015 | 0.045 |
| 2 | MV | 0.020 | 0.060 |
| 3 | HV | 0.025 | 0.075 |
| 4 | EV | 0.030 | 0.090 |
| 5 | IV | 0.035 | 0.105 |
| 6 | LuV | 0.040 | 0.120 |
| 7 | ZPM | 0.045 | 0.135 |
| 8 | UV | 0.050 | 0.150 |

**扫描范围：**
- 以玩家为中心，半径 4 区块（9×9 区块区域）
- 只扫描已加载的区块
- 使用 `HashSet` 避免重复扫描

### 2. GT 原生污染集成

**GTPollutionScanner 工作流程：**

1. **反射加载 GT API**
```java
// 加载 EnvironmentalHazardSavedData 类
hazardSavedDataClass = Class.forName(
    "com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData"
);

// 缓存方法引用
getOrCreateMethod = hazardSavedDataClass.getMethod("getOrCreate", ServerLevel.class);
getZoneByPosMethod = hazardSavedDataClass.getMethod("getZoneByPos", ChunkPos.class);
strengthMethod = hazardZoneClass.getMethod("strength");
```

2. **扫描区块污染**
```java
// 获取区块的 HazardZone
Object hazardZone = getZoneByPosMethod.invoke(hazardData, chunkPos);
float strength = (float) strengthMethod.invoke(hazardZone);

// 统计 3×3 区块范围内的污染源数量
int sourceCount = countNearbyPollutionSources(hazardData, chunkPos);
```

3. **计算污染贡献**
```java
// 污染源密度倍率
double multiplier = calculateSourceMultiplier(sourceCount);
// 1-10源: 1.0x
// 11-20源: 1.5x
// 21-30源: 2.0x
// 31-50源: 3.0x
// 51+源: 4.0x

// 最终贡献 = GT强度 × 密度倍率 × 配置权重
double contribution = strength * multiplier * TriAxisConfig.gtPollutionWeight;
```

**GT 污染源识别：**
- 主要来源：Muffler（消音器）排放的 CO
- 每个 Muffler 约产生 2.5 强度/tick
- 100 强度 ≈ 1 个污染源

---

## 污染扩散与衰减

### 1. 自然衰减

**基础衰减率：**
```java
double reduction = 0.05;  // 每秒减少 0.05 污染
```

### 2. 环境吸收

**吸收计算：**
```java
// 获取周围 9×9 区块的环境吸收
reduction += getSurroundingEnvironmentalReduction(chunkPos);

// 更新污染值
double nextVal = Math.max(0.0, current - reduction + added);
```

### 3. 污染更新公式

```
新污染 = max(0, 当前污染 - 基础衰减 - 环境吸收 + 新增污染)
```

**清除条件：**
```java
if (nextVal <= 0.001) {
    temporaryPollution.remove(chunkPos);  // 污染过低，移除记录
}
```

---

## 环境吸收系统

### 1. 吸收方块类型

**吸收效率表：**
| 方块类型 | 每方块吸收 | 说明 |
|---------|-----------|------|
| 树叶（Leaves） | 0.0001 | 主要净化源 |
| 草/花（Grass/Flowers） | 0.0001 | 与树叶相同 |
| 水（Water） | 0.00005 | 净化效率较低 |

### 2. 扫描算法

**超快速区块扫描：**
```java
private static double calculateChunkReduction(LevelChunk chunk) {
    int leafCount = 0;
    int waterCount = 0;
    int grassCount = 0;

    // 遍历区块的所有 Section（16×16×16 子区块）
    for (LevelChunkSection section : chunk.getSections()) {
        if (section.hasOnlyAir()) continue;

        // 三层循环扫描方块
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.is(Blocks.WATER)) waterCount++;
                    else if (state.is(BlockTags.LEAVES)) leafCount++;
                    else if (state.is(BlockTags.FLOWERS) || 
                             state.is(Blocks.GRASS)) grassCount++;
                }
            }
        }
    }

    return (leafCount * 0.0001) + (waterCount * 0.00005) + (grassCount * 0.0001);
}
```

### 3. 缓存策略

**更新频率：**
```java
// 每 5 分钟更新一次环境缓存（6000 ticks）
if (tickCounter % 6000 == 0) {
    updateEnvironmentalCache(level);
}
```

**缓存范围：**
- 只缓存污染区块周围 9×9 区块的环境数据
- 避免扫描整个世界，节省性能

**线程安全：**
```java
// 在独立线程中执行扫描
new Thread(() -> {
    // 扫描逻辑...
}, "ImprovedMobs-Pollution-Scanner").start();
```

### 4. 吸收范围

**周围区块吸收：**
```java
// 获取中心区块周围 9×9 区块的总吸收
private static double getSurroundingEnvironmentalReduction(ChunkPos center) {
    double totalReduction = 0.0;
    for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
            ChunkPos p = new ChunkPos(center.x + x, center.z + z);
            totalReduction += environmentalReductionCache.getOrDefault(p, 0.0);
        }
    }
    return totalReduction;
}
```

**实际效果示例：**
- 一个 16×16×256 的森林区块（约 4000 片树叶）
- 吸收能力 = 4000 × 0.0001 = 0.4 污染/秒
- 可抵消 20 台 MV 单方块机器的污染（20 × 0.02 = 0.4）

---

## 污染转化机制

### 1. 临时 → 永久转化

**触发条件：**
```java
if (current > TEMP_TO_PERM_THRESHOLD) {  // 默认 200.0
    double converted = current * TEMP_TO_PERM_RATE;  // 默认 0.001
    addPermanentPollution(converted);
}
```

**转化速率：**
- 阈值：200 临时污染
- 速率：0.1% 每秒
- 示例：300 临时污染 → 每秒转化 0.3 永久污染

### 2. 永久 → 难度转化

**触发条件：**
```java
if (permanentPollution >= PERM_TO_DIFFICULTY_RATE) {  // 默认 0.1
    DifficultyData.addDifficulty((float)permanentPollution, server);
    permanentPollution = 0.0;
}
```

**难度影响：**
- 永久污染直接增加 ImprovedMobs 全局难度
- 难度增加后，所有怪物变强（血量、伤害、装备）
- 不可逆转，代表长期工业发展的代价

### 3. 转化流程图

```
机器运行
    ↓
生成临时污染（区块级）
    ↓
临时污染 > 200 ?
    ↓ 是
转化为永久污染（0.1%/秒）
    ↓
永久污染 ≥ 0.1 ?
    ↓ 是
增加全局难度
    ↓
怪物变强
```

---

## 视觉效果系统

### 1. 效果等级

**污染阈值与效果：**

| 等级 | 污染范围 | 视觉效果 | 药水效果 | 持续时间 |
|-----|---------|---------|---------|---------|
| 轻度 | 50-100 | 绿色粒子 | 无 | - |
| 中度 | 100-150 | 黄色粒子 | 恶心 I | 10秒 |
| 重度 | 150-200 | 橙色粒子 | 恶心 II | 15秒 |
| 严重 | 200+ | 红色粒子 | 恶心 III + 失明 | 20秒 + 5秒 |

### 2. 触发机制

**Mixin 注入：**
```java
// VisualManagerMixin.java
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    Player player = Minecraft.getInstance().player;
    if (player == null) return;

    ChunkPos chunkPos = player.chunkPosition();
    double pollution = PollutionManager.getTemporaryPollution(chunkPos);

    // 触发视觉效果
    PollutionVisualEffects.checkAndTriggerEffects(player, pollution);
}
```

### 3. 冷却系统

**防止效果刷屏：**
```java
// 每个玩家 5 秒冷却（100 ticks）
private static final int EFFECT_COOLDOWN = 100;
private static final Map<UUID, Long> lastEffectTick = new ConcurrentHashMap<>();

// 检查冷却
long currentTick = Minecraft.getInstance().level.getGameTime();
Long lastTick = lastEffectTick.get(playerUUID);

if (lastTick != null && currentTick - lastTick < EFFECT_COOLDOWN) {
    return;  // 仍在冷却中
}
```

### 4. EnhancedVisuals 集成

**效果委托：**
```java
// 轻度污染
EnhancedVisualsHelper.triggerLightEffects();

// 中度污染
EnhancedVisualsHelper.triggerModerateEffects();
player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));

// 重度污染
EnhancedVisualsHelper.triggerHeavyEffects();
player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 1));

// 严重污染
EnhancedVisualsHelper.triggerSevereEffects();
player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400, 2));
player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
```

---

## 性能优化策略

### 1. 异步处理

**CompletableFuture 异步扫描：**
```java
CompletableFuture.runAsync(() -> {
    // 重型扫描逻辑（机器检测、污染计算）
    // 在后台线程执行，不阻塞主线程
}).thenAccept(v -> {
    // 结果处理（更新污染值、触发威胁）
    // 在主线程执行，保证线程安全
});
```

### 2. 分级扫描频率

**不同任务的更新频率：**
```java
// 每秒（20 ticks）：污染生成与衰减
if (tickCounter % 20 == 0) {
    processPollutionDecayAndScanning(level);
}

// 每 5 分钟（6000 ticks）：环境缓存更新
if (tickCounter % 6000 == 0) {
    updateEnvironmentalCache(level);
}
```

### 3. 区块级精度

**避免方块级追踪：**
- 污染以区块为单位存储（16×16 区域）
- 不追踪每个方块的污染值
- 大幅减少内存占用和计算量

### 4. 智能缓存

**环境吸收缓存：**
- 只缓存污染区块周围的环境数据
- 5 分钟更新一次，避免频繁扫描
- 使用 `ConcurrentHashMap` 保证线程安全

### 5. 扫描范围限制

**玩家中心扫描：**
```java
int scanRadius = 4;  // 半径 4 区块
for (ServerPlayer player : level.players()) {
    ChunkPos center = player.chunkPosition();
    // 只扫描玩家周围 9×9 区块
}
```

### 6. 去重机制

**避免重复扫描：**
```java
Set<ChunkPos> scannedChunks = new HashSet<>();
if (scannedChunks.add(scanPos)) {
    // 只扫描未扫描过的区块
}
```

### 7. 性能指标

**理论性能：**
- 1000 台机器：每秒扫描 1 次，异步处理
- 环境扫描：每 5 分钟 1 次，后台线程
- 主线程影响：< 1ms/tick

---

## 配置参数

### 1. 污染生成

```properties
# 临时污染转永久污染阈值
TEMP_TO_PERM_THRESHOLD = 200.0

# 临时污染转永久污染速率（每秒）
TEMP_TO_PERM_RATE = 0.001

# 永久污染转难度速率
PERM_TO_DIFFICULTY_RATE = 0.1
```

### 2. GT 污染集成

```properties
# GT 污染权重（影响 GT 原生污染的贡献）
gtPollutionWeight = 1.0

# GT 污染源密度阈值
gtSourceThreshold = 10

# GT 污染源密度倍率基数
gtSourceMultiplierBase = 0.5
```

### 3. 环境吸收

```properties
# 树叶吸收效率
leafAbsorption = 0.0001

# 水吸收效率
waterAbsorption = 0.00005

# 草/花吸收效率
grassAbsorption = 0.0001
```

### 4. 空气净化器

```properties
# 空气净化器效率
airScrubberEfficiency = 1.0

# 空气净化器范围（区块）
airScrubberRadius = 3
```

---

## 集成接口

### 1. 污染查询 API

```java
// 获取区块临时污染
double pollution = PollutionManager.getTemporaryPollution(chunkPos);

// 获取全局永久污染
double permanent = PollutionManager.getPermanentPollution();
```

### 2. 污染操作 API

```java
// 设置区块污染（命令用）
PollutionManager.setChunkPollution(level, chunkPos, amount);

// 设置永久污染（命令用）
PollutionManager.setPermanentPollution(amount);

// 添加永久污染
PollutionManager.addPermanentPollution(amount);
```

### 3. 空气净化器集成

```java
// 清理指定范围的污染
PollutionManager.cleanPollutionInRadius(
    level,          // 服务器世界
    center,         // 中心位置
    radiusChunks,   // 半径（区块）
    amount          // 清理量
);
```

**清理公式：**
```java
// 清理量随距离衰减
double cleanAmount = (amount * efficiency) / Math.max(1.0, distance);
```

### 4. Spore 集成

```java
// 污染反馈到 Spore 进化系统
if (SporeIntegration.isSporeLoaded() && totalTempPollution > 100.0) {
    SporeIntegration.applyPollutionFeedback(level, totalTempPollution);
}
```

### 5. 威胁系统集成

```java
// 检查并触发威胁（僵尸、苦力怕攻击机器）
double avgTier = ThreatManager.getChunkAverageVoltageTier(level, chunkPos);
ThreatManager.checkAndTriggerThreats(level, chunkPos, pollution, avgTier);
```

---

## 数据流图

```
┌─────────────────────────────────────────────────────────────┐
│                      污染系统数据流                           │
└─────────────────────────────────────────────────────────────┘

1. 污染生成
   ┌──────────────┐
   │ GT 机器运行   │
   └──────┬───────┘
          │
          ├─→ 机器扫描（每秒）
          │   └─→ 计算污染值（tier × 0.01）
          │
          ├─→ GT 原生污染扫描
          │   └─→ 反射读取 HazardZone
          │
          └─→ 累加到临时污染 Map

2. 污染处理
   ┌──────────────┐
   │ 临时污染 Map  │
   └──────┬───────┘
          │
          ├─→ 环境吸收（树叶、水、草）
          │   └─→ 每 5 分钟更新缓存
          │
          ├─→ 自然衰减（0.05/秒）
          │
          ├─→ 污染 > 200 ?
          │   └─→ 转化为永久污染（0.1%/秒）
          │
          └─→ 触发威胁检测
              └─→ ThreatManager

3. 永久污染
   ┌──────────────┐
   │ 永久污染值    │
   └──────┬───────┘
          │
          ├─→ 累积 ≥ 0.1 ?
          │   └─→ 增加全局难度
          │       └─→ ImprovedMobs DifficultyData
          │
          └─→ Spore 进化反馈
              └─→ SporeIntegration

4. 视觉效果
   ┌──────────────┐
   │ 玩家位置污染  │
   └──────┬───────┘
          │
          ├─→ Mixin 注入（每 tick）
          │   └─→ VisualManagerMixin
          │
          ├─→ 检查冷却（5 秒）
          │
          └─→ 触发效果
              ├─→ EnhancedVisuals 粒子
              └─→ 药水效果（恶心、失明）
```

---

## 游戏阶段污染示例

### 早期（MV 阶段）
- **机器数量**：20-30 台 MV 机器
- **临时污染**：30-60
- **永久污染**：0-5
- **效果**：轻度视觉效果，偶尔僵尸攻击机器

### 中期（HV-EV 阶段）
- **机器数量**：50-80 台 HV/EV 机器
- **临时污染**：100-180
- **永久污染**：10-30
- **效果**：中度视觉效果，频繁威胁生成，难度开始上升

### 后期（IV-LuV 阶段）
- **机器数量**：100-200 台 IV/LuV 机器
- **临时污染**：200-400
- **永久污染**：50-100
- **效果**：重度视觉效果，持续威胁，显著难度提升

### 末期（ZPM+ 阶段）
- **机器数量**：200+ 台 ZPM/UV 机器
- **临时污染**：400+
- **永久污染**：100+
- **效果**：严重视觉效果，极限威胁，极高难度

---

## 总结

### 系统优势
1. **双层设计**：临时污染可逆，永久污染不可逆，平衡游戏性
2. **性能优化**：异步处理、智能缓存、分级扫描
3. **环境交互**：自然方块可吸收污染，鼓励绿化
4. **渐进式挑战**：污染随工业发展逐步累积，难度平滑上升
5. **多系统集成**：与 GT、Spore、ImprovedMobs、EnhancedVisuals 深度集成

### 设计哲学
- **工业发展有代价**：高级机器产生更多污染
- **环境保护有意义**：种树、建湖可减少污染
- **长期规划重要**：永久污染不可逆，需要提前规划
- **视觉反馈直观**：污染等级通过屏幕效果直观呈现

### 未来扩展方向
1. **污染类型多样化**：CO、SO2、辐射等不同污染类型
2. **区域性永久污染**：不同区域独立的永久污染值
3. **污染治理科技树**：高级空气净化器、碳捕获技术
4. **生态系统影响**：污染导致植物枯萎、动物变异

---

**文档版本**：1.0  
**最后更新**：2026-04-20  
**作者**：ImprovedMobs Team
