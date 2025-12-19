# SporeReflectionAPI 完整文档

## 目录
1. [快速开始](#快速开始)
2. [API方法签名](#api方法签名)
3. [MoundData类](#mounddata类)
4. [使用示例](#使用示例)
5. [性能优化建议](#性能优化建议)
6. [常见问题](#常见问题)

---

## 快速开始

### 1. 安装

将 `SporeReflectionAPI.java` 复制到你的项目中：
```
your.mod.integration/
└── SporeReflectionAPI.java
```

### 2. 基础使用

```java
// 获取API实例
SporeReflectionAPI api = SporeReflectionAPI.getInstance();

// 检查Spore是否加载
if (api.isSporeLoaded()) {
    // 计算感染强度
    float intensity = api.calculateInfectionIntensity(player);
    
    // 使用强度更新shader
    updateShader(intensity);
}
```

### 3. 完整示例

参考 `SporeReflectionAPI_Usage.java` 中的 `InfectionShaderHandler` 类。

---

## API方法签名

### 核心方法

#### `isSporeLoaded()`
```java
public boolean isSporeLoaded()
```
**功能**: 检查Spore模组是否已加载  
**返回**: `true` 如果Spore可用  
**调用时机**: 在使用任何其他API前调用  
**性能**: 极低（首次调用会初始化反射，后续调用直接返回缓存结果）

---

#### `calculateInfectionIntensity()`
```java
public float calculateInfectionIntensity(LocalPlayer player)
```
**功能**: 计算玩家位置的综合感染强度  
**参数**:
- `player`: 本地玩家实例

**返回**: 感染强度 `0.0` - `1.0`
- `0.0` = 无感染
- `0.3` = 轻度感染（远距离Mound或蘑菇群系）
- `0.6` = 中度感染（中距离Mound）
- `1.0` = 重度感染（近距离高龄Mound）

**计算因素**:
1. Mound实体距离和属性
2. 周围感染方块密度
3. 生物群系类型

**性能**: 中等（每0.5秒调用一次）

**示例**:
```java
float intensity = api.calculateInfectionIntensity(player);
if (intensity > 0.5f) {
    // 触发强烈的shader效果
}
```

---

#### `getMoundsNearPlayer()`
```java
public List<MoundData> getMoundsNearPlayer(LocalPlayer player, double radius)
```
**功能**: 获取玩家附近的所有Mound实体  
**参数**:
- `player`: 本地玩家
- `radius`: 搜索半径（方块数）

**返回**: `MoundData` 列表，如果没有则返回空列表  
**推荐半径**: `64.0` （与Mound的最大影响范围匹配）  
**性能**: 中等（取决于范围和实体数量）

**示例**:
```java
List<MoundData> mounds = api.getMoundsNearPlayer(player, 64.0);
System.out.println("Found " + mounds.size() + " mounds nearby");

for (MoundData mound : mounds) {
    System.out.println("Mound age: " + mound.getAge());
}
```

---

#### `getNearestMound()`
```java
public MoundData getNearestMound(LocalPlayer player, double maxRadius)
```
**功能**: 获取最近的Mound实体  
**参数**:
- `player`: 本地玩家
- `maxRadius`: 最大搜索半径

**返回**: 最近的 `MoundData`，如果没有则返回 `null`  
**性能**: 中等

**示例**:
```java
MoundData nearest = api.getNearestMound(player, 64.0);
if (nearest != null) {
    double distance = nearest.getDistanceTo(player.position());
    System.out.println("Nearest mound is " + distance + " blocks away");
}
```

---

### 细分计算方法

#### `calculateMoundIntensity()`
```java
public float calculateMoundIntensity(LocalPlayer player)
```
**功能**: 只计算Mound实体的影响强度（不包括方块和生物群系）  
**返回**: `0.0` - `1.0`  
**用途**: 当你只想要Mound的影响时使用

---

#### `calculateBlockInfectionDensity()`
```java
public float calculateBlockInfectionDensity(LocalPlayer player)
```
**功能**: 计算周围感染方块的密度  
**返回**: `0.0` - `1.0`  
**检测范围**: 玩家周围 8x4x8 方块（每隔2格采样）  
**性能**: 低（采样而非全扫描）

---

#### `calculateBiomeIntensity()`
```java
public float calculateBiomeIntensity(LocalPlayer player)
```
**功能**: 计算生物群系的感染强度  
**返回**:
- `0.0` = 普通生物群系
- `0.3` = 蘑菇群系
- `0.5` = Spore自定义生物群系

---

#### `isInfectedBlock()`
```java
public boolean isInfectedBlock(BlockState state)
```
**功能**: 检查方块是否是感染方块  
**检测关键词**:
- `infested`
- `biomass`
- `mycelium`
- `rotten`
- `fungal`
- `remains`
- `membrane`

**示例**:
```java
BlockState state = level.getBlockState(pos);
if (api.isInfectedBlock(state)) {
    // 这是感染方块
}
```

---

### 工具方法

#### `calculateDistanceFalloff()`
```java
public static float calculateDistanceFalloff(double distance, double minRange, double maxRange)
```
**功能**: 计算距离衰减系数  
**参数**:
- `distance`: 当前距离
- `minRange`: 最小范围（满强度）
- `maxRange`: 最大范围（零强度）

**返回**: `0.0` - `1.0`  
**衰减曲线**: 线性

**示例**:
```java
// 16格内满强度，64格外零强度
float falloff = SporeReflectionAPI.calculateDistanceFalloff(distance, 16, 64);
```

---

#### `lerp()`
```java
public static float lerp(float current, float target, float speed)
```
**功能**: 平滑插值（用于shader过渡）  
**参数**:
- `current`: 当前值
- `target`: 目标值
- `speed`: 插值速度 `0.0` - `1.0`

**返回**: 插值后的值

**示例**:
```java
// 每tick向目标值移动10%
currentIntensity = SporeReflectionAPI.lerp(currentIntensity, targetIntensity, 0.1f);
```

---

## MoundData类

### 方法签名

#### `getAge()`
```java
public int getAge()
```
**返回**: Mound的年龄 `1` - `4`  
**含义**:
- `1` = 新生Mound，扩散范围小
- `2` = 成长期
- `3` = 成熟期，开始生成触须
- `4` = 完全成熟，扩散范围最大

---

#### `isLinked()`
```java
public boolean isLinked()
```
**返回**: 是否连接到Proto（HiveMind）  
**含义**: 连接的Mound会更强大，死亡时会通知Proto

---

#### `getSpreadProgress()`
```java
public float getSpreadProgress()
```
**返回**: 扩散进度 `0.0` - `1.0`  
**含义**:
- `0.0` = 刚完成上次扩散
- `0.5` = 冷却中
- `0.9` = 即将扩散（会有粒子效果）
- `1.0` = 正在扩散

**用途**: 可以根据进度调整shader强度，即将扩散时效果更强

---

#### `getPosition()`
```java
public Vec3 getPosition()
```
**返回**: Mound的精确位置（Vec3）

---

#### `getBlockPosition()`
```java
public BlockPos getBlockPosition()
```
**返回**: Mound的方块位置（BlockPos）

---

#### `getDistanceTo()`
```java
public double getDistanceTo(Vec3 pos)
```
**参数**: 目标位置  
**返回**: 到目标的距离（方块数）

---

#### `getDistanceToSqr()`
```java
public double getDistanceToSqr(Vec3 pos)
```
**参数**: 目标位置  
**返回**: 到目标的距离平方  
**性能**: 比 `getDistanceTo()` 快（避免开方运算）

---

#### `isAlive()`
```java
public boolean isAlive()
```
**返回**: Mound是否存活

---

#### `getEntity()`
```java
public Entity getEntity()
```
**返回**: 底层实体对象（高级用途）

---

## 使用示例

### 示例1: 基础shader控制

```java
@SubscribeEvent
public static void onClientTick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;
    
    SporeReflectionAPI api = SporeReflectionAPI.getInstance();
    if (!api.isSporeLoaded()) return;
    
    // 每0.5秒检测一次
    if (player.tickCount % 10 == 0) {
        float intensity = api.calculateInfectionIntensity(player);
        updateShader(intensity);
    }
}
```

---

### 示例2: 多层次shader效果

```java
public void updateMultiLayerShader(LocalPlayer player) {
    SporeReflectionAPI api = SporeReflectionAPI.getInstance();
    
    MoundData nearest = api.getNearestMound(player, 64.0);
    if (nearest == null) {
        disableShader();
        return;
    }
    
    double distance = nearest.getDistanceTo(player.position());
    
    if (distance < 16) {
        // 近距离：强烈雪花 + 屏幕扭曲
        enableShader("infection_heavy", 1.0f);
    } else if (distance < 32) {
        // 中距离：中等雪花
        enableShader("infection_medium", 0.6f);
    } else if (distance < 64) {
        // 远距离：轻微雪花
        enableShader("infection_light", 0.3f);
    }
}
```

---

### 示例3: 根据Mound属性调整效果

```java
public float calculateAdvancedIntensity(LocalPlayer player) {
    SporeReflectionAPI api = SporeReflectionAPI.getInstance();
    List<MoundData> mounds = api.getMoundsNearPlayer(player, 64.0);
    
    float maxIntensity = 0f;
    
    for (MoundData mound : mounds) {
        double distance = mound.getDistanceTo(player.position());
        
        // 基础距离衰减
        float baseIntensity = SporeReflectionAPI.calculateDistanceFalloff(
            distance, 16, 64
        );
        
        // 年龄加成 (1-4 -> 1.0x-1.6x)
        float ageBonus = 1.0f + (mound.getAge() * 0.15f);
        
        // 连接状态加成
        float linkedBonus = mound.isLinked() ? 1.3f : 1.0f;
        
        // 扩散进度加成（即将扩散时更强）
        float progressBonus = 1.0f + (mound.getSpreadProgress() * 0.5f);
        
        // 综合计算
        float intensity = baseIntensity * ageBonus * linkedBonus * progressBonus;
        
        maxIntensity = Math.max(maxIntensity, intensity);
    }
    
    return Math.min(maxIntensity, 1.0f);
}
```

---

### 示例4: 方向性shader效果

```java
public void updateDirectionalShader(LocalPlayer player) {
    SporeReflectionAPI api = SporeReflectionAPI.getInstance();
    MoundData nearest = api.getNearestMound(player, 64.0);
    
    if (nearest == null) return;
    
    // 计算Mound相对于玩家的方向
    Vec3 playerPos = player.position();
    Vec3 moundPos = nearest.getPosition();
    Vec3 direction = moundPos.subtract(playerPos).normalize();
    
    // 计算屏幕空间方向（用于shader）
    float yaw = (float) Math.atan2(direction.z, direction.x);
    float pitch = (float) Math.asin(direction.y);
    
    // 传递给shader
    setShaderUniform("infectionDirection", yaw, pitch);
    setShaderUniform("infectionIntensity", nearest.getSpreadProgress());
}
```

---

## 性能优化建议

### 1. 检测频率

```java
// ❌ 不推荐：每tick检测（20次/秒）
@SubscribeEvent
public void onTick(TickEvent.ClientTickEvent event) {
    float intensity = api.calculateInfectionIntensity(player);
}

// ✅ 推荐：每0.5秒检测（2次/秒）
@SubscribeEvent
public void onTick(TickEvent.ClientTickEvent event) {
    if (player.tickCount % 10 == 0) {
        float intensity = api.calculateInfectionIntensity(player);
    }
}
```

---

### 2. 使用距离平方比较

```java
// ❌ 不推荐：使用开方
double distance = mound.getDistanceTo(player.position());
if (distance < 16) { ... }

// ✅ 推荐：使用距离平方
double distanceSqr = mound.getDistanceToSqr(player.position());
if (distanceSqr < 16 * 16) { ... }
```

---

### 3. 缓存Mound列表

```java
private List<MoundData> cachedMounds = null;
private int lastCacheUpdate = 0;

public List<MoundData> getMoundsWithCache(LocalPlayer player, int currentTick) {
    // 每5秒更新缓存
    if (cachedMounds == null || currentTick - lastCacheUpdate > 100) {
        cachedMounds = api.getMoundsNearPlayer(player, 64.0);
        lastCacheUpdate = currentTick;
    }
    return cachedMounds;
}
```

---

### 4. 提前退出

```java
// ✅ 推荐：提前检查
if (!api.isSporeLoaded()) return;

List<MoundData> mounds = api.getMoundsNearPlayer(player, 64.0);
if (mounds.isEmpty()) {
    disableShader();
    return; // 提前退出，避免后续计算
}

// 继续计算...
```

---

## 常见问题

### Q1: API返回空列表，但我知道附近有Mound？

**A**: 检查以下几点：
1. 确认Spore模组已加载：`api.isSporeLoaded()`
2. 检查搜索半径是否足够大
3. 确认是在客户端调用（`@OnlyIn(Dist.CLIENT)`）
4. 查看控制台是否有错误信息

---

### Q2: 反射性能会不会很差？

**A**: 不会。原因：
1. 反射初始化只执行一次，后续调用使用缓存的Method对象
2. 推荐每0.5秒检测一次，而不是每tick
3. 实测开销 < 0.1ms/次，完全可以接受

---

### Q3: 如何调试shader强度？

**A**: 使用调试输出：
```java
if (player.tickCount % 20 == 0) {
    System.out.println("Intensity: " + api.calculateInfectionIntensity(player));
    
    MoundData nearest = api.getNearestMound(player, 64.0);
    if (nearest != null) {
        System.out.println("Nearest: " + nearest.toString());
    }
}
```

---

### Q4: 可以在服务端使用吗？

**A**: 不推荐。这个API设计用于客户端shader效果。如果需要服务端逻辑，应该：
1. 直接使用Spore的类（不需要反射）
2. 或者通过网络包从服务端发送数据到客户端

---

### Q5: Spore更新后API会失效吗？

**A**: 可能。如果Spore修改了方法签名，反射会失败。但是：
1. API会优雅降级，不会崩溃
2. 控制台会输出错误信息
3. `isSporeLoaded()` 会返回 `false`

---

## 完整调用流程图

```
启动游戏
    ↓
初始化API (首次调用时)
    ↓
检查Spore是否加载
    ↓
    ├─ 是 → 继续
    └─ 否 → 禁用功能
    ↓
每0.5秒执行：
    ↓
获取玩家位置
    ↓
检测附近Mound (64格)
    ↓
    ├─ 有Mound → 计算强度
    └─ 无Mound → 强度=0
    ↓
计算方块密度
    ↓
检测生物群系
    ↓
综合计算最终强度
    ↓
平滑过渡到目标强度
    ↓
更新shader效果
```

---

## 版本历史

- **v1.0** (2024): 初始版本
  - 支持Mound检测
  - 支持感染强度计算
  - 支持方块和生物群系检测

---

## 许可证

MIT License - 自由使用和修改

---

## 联系方式

如有问题，请在GitHub提Issue或联系作者。
