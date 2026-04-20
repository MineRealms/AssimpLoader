# 污染系统需求规格文档

## 版本信息
- **文档版本**: 2.0
- **最后更新**: 2026-04-20
- **状态**: 需求确认

---

## 核心需求

### 1. 污染生成机制

#### 1.1 触发条件
**所有 GregTech 机器在执行配方时产生污染**，不仅限于消音仓（Muffler）。

**检测逻辑**：
- 机器必须是 GT 机器（`isGTMachine()`）
- 机器必须正在执行配方（`isWorkingEnabled() && isActive()`）
- 不仅仅检查是否有能量（`hasEnergy()`）

**特殊机器类型**：
- **采矿机（Miner）**：执行配方时产生污染
- **钻油井（Oil Drilling Rig）**：执行配方时产生污染
- **所有其他机器**：执行配方时产生污染

#### 1.2 污染计算公式

**基础公式**（可配置）：
```
污染值 = 基础污染系数 × (1 + 电压等级 × 等级增长系数)
```

**配置参数**：
- `basePollutionRate`: 基础污染系数（默认 0.01）
- `tierGrowthFactor`: 等级增长系数（默认 0.5）

**示例**：
- ULV (tier 0): `0.01 × (1 + 0 × 0.5) = 0.01` 污染/秒
- MV (tier 2): `0.01 × (1 + 2 × 0.5) = 0.02` 污染/秒
- HV (tier 3): `0.01 × (1 + 3 × 0.5) = 0.025` 污染/秒
- UHV (tier 9): `0.01 × (1 + 9 × 0.5) = 0.055` 污染/秒

**多方块倍率**（可配置）：
- `multiblockPollutionMultiplier`: 多方块污染倍率（默认 3.0）
- 多方块结构污染 = 基础污染 × 多方块倍率

---

### 2. 电压等级系统

#### 2.1 灵活配置
**不硬编码任何电压等级**，所有等级通过配置文件定义。

**配置参数**：
```properties
# 最大电压等级（理论支持 ULV 到 MAX）
maxVoltageTier = 9  # 用户可设置为 0-14

# 电压等级名称映射（可选，用于日志）
tierNames = ULV,LV,MV,HV,EV,IV,LuV,ZPM,UV,UHV,UEV,UIV,UXV,OpV,MAX
```

**代码要求**：
- 不能出现 `tier == 2` 或 `tier >= 3` 这样的硬编码判断
- 所有等级判断必须使用配置参数
- 支持用户自定义最大等级（如只到 UHV，或扩展到 MAX）

#### 2.2 等级范围
- **最小等级**: 0 (ULV)
- **最大等级**: 用户配置（默认 9 = UHV，理论最大 14 = MAX）
- **标准等级**: ULV(0), LV(1), MV(2), HV(3), EV(4), IV(5), LuV(6), ZPM(7), UV(8), UHV(9), UEV(10), UIV(11), UXV(12), OpV(13), MAX(14)

---

### 3. 威胁系统配置化

#### 3.1 威胁触发条件
**所有威胁触发条件必须可配置**，不能硬编码。

**配置参数**：
```properties
# 僵尸攻击机器的最低电压等级
zombieAttackMinTier = 2  # MV

# 主动生成僵尸的最低电压等级
zombieSpawnMinTier = 3  # HV

# 主动生成苦力怕的最低电压等级
creeperSpawnMinTier = 3  # HV

# 污染阈值
mvZombieAttackThreshold = 50.0
hvZombieSpawnThreshold = 80.0
hvCreeperSpawnThreshold = 120.0
chargedCreeperThreshold = 200.0

# 生成概率
zombieSpawnChance = 0.01
creeperSpawnChance = 0.005
chargedCreeperChance = 0.001
```

#### 3.2 威胁等级
| 威胁类型 | 触发条件 | 配置参数 |
|---------|---------|---------|
| 僵尸攻击机器（被动） | 电压等级 ≥ `zombieAttackMinTier` && 污染 ≥ `mvZombieAttackThreshold` | 可配置 |
| 僵尸主动生成 | 电压等级 ≥ `zombieSpawnMinTier` && 污染 ≥ `hvZombieSpawnThreshold` | 可配置 |
| 苦力怕主动生成 | 电压等级 ≥ `creeperSpawnMinTier` && 污染 ≥ `hvCreeperSpawnThreshold` | 可配置 |
| 闪电苦力怕 | 污染 ≥ `chargedCreeperThreshold` | 可配置 |

---

### 4. 难度曲线配置化

#### 4.1 难度计算公式
**所有难度计算参数必须可配置**。

**配置参数**：
```properties
# 难度曲线类型
difficultyCurveType = LINEAR  # LINEAR, EXPONENTIAL, LOGARITHMIC

# 线性曲线参数
linearDifficultyBase = 0.1
linearDifficultyPerTier = 0.05

# 指数曲线参数
exponentialDifficultyBase = 0.1
exponentialDifficultyGrowth = 1.2

# 对数曲线参数
logarithmicDifficultyBase = 0.1
logarithmicDifficultyScale = 2.0
```

#### 4.2 难度曲线类型

**线性曲线**（默认）：
```
难度 = 基础难度 + (电压等级 × 每级增长)
```

**指数曲线**：
```
难度 = 基础难度 × (增长系数 ^ 电压等级)
```

**对数曲线**：
```
难度 = 基础难度 × log(1 + 电压等级 × 缩放系数)
```

---

## 配置文件示例

### config/triaxis-difficulty.properties

```properties
# ============================================
# 污染生成配置
# ============================================

# 基础污染系数（每秒）
basePollutionRate = 0.01

# 电压等级增长系数
tierGrowthFactor = 0.5

# 多方块污染倍率
multiblockPollutionMultiplier = 3.0

# ============================================
# 电压等级配置
# ============================================

# 最大电压等级 (0=ULV, 9=UHV, 14=MAX)
# 用户可根据整合包调整
maxVoltageTier = 9

# 电压等级名称（用于日志显示）
tierNames = ULV,LV,MV,HV,EV,IV,LuV,ZPM,UV,UHV,UEV,UIV,UXV,OpV,MAX

# ============================================
# 威胁系统配置
# ============================================

# 僵尸攻击机器的最低电压等级
zombieAttackMinTier = 2

# 主动生成僵尸的最低电压等级
zombieSpawnMinTier = 3

# 主动生成苦力怕的最低电压等级
creeperSpawnMinTier = 3

# 污染阈值
mvZombieAttackThreshold = 50.0
hvZombieSpawnThreshold = 80.0
hvCreeperSpawnThreshold = 120.0
chargedCreeperThreshold = 200.0

# 生成概率（每秒）
zombieSpawnChance = 0.01
creeperSpawnChance = 0.005
chargedCreeperChance = 0.001

# ============================================
# 难度曲线配置
# ============================================

# 难度曲线类型: LINEAR, EXPONENTIAL, LOGARITHMIC
difficultyCurveType = LINEAR

# 线性曲线参数
linearDifficultyBase = 0.1
linearDifficultyPerTier = 0.05

# 指数曲线参数
exponentialDifficultyBase = 0.1
exponentialDifficultyGrowth = 1.2

# 对数曲线参数
logarithmicDifficultyBase = 0.1
logarithmicDifficultyScale = 2.0
```

---

## 代码实现要求

### 1. 污染生成检测

**当前代码**（错误）：
```java
if (GTIntegration.hasEnergyOrActive(be)) {
    // 生成污染
}
```

**修改后代码**（正确）：
```java
if (GTIntegration.isWorkingEnabled(be) && GTIntegration.isActive(be)) {
    // 机器正在执行配方，生成污染
    int tier = GTIntegration.getVoltageTier(be);
    double pollution = TriAxisConfig.basePollutionRate * 
                      (1 + tier * TriAxisConfig.tierGrowthFactor);
    
    if (GTIntegration.isMultiblock(be)) {
        pollution *= TriAxisConfig.multiblockPollutionMultiplier;
    }
}
```

### 2. 威胁触发判断

**当前代码**（错误）：
```java
if (avgVoltageTier >= 3.0) { // 硬编码 HV
    // 触发威胁
}
```

**修改后代码**（正确）：
```java
if (avgVoltageTier >= TriAxisConfig.zombieSpawnMinTier) {
    if (pollution >= TriAxisConfig.hvZombieSpawnThreshold) {
        // 触发僵尸生成
    }
}

if (avgVoltageTier >= TriAxisConfig.creeperSpawnMinTier) {
    if (pollution >= TriAxisConfig.hvCreeperSpawnThreshold) {
        // 触发苦力怕生成
    }
}
```

### 3. 电压等级判断

**禁止的写法**：
```java
if (tier == 2) { ... }  // ❌ 硬编码 MV
if (tier >= 3) { ... }  // ❌ 硬编码 HV
if (tier <= 9) { ... }  // ❌ 硬编码 UHV
```

**正确的写法**：
```java
if (tier >= TriAxisConfig.zombieAttackMinTier) { ... }  // ✅ 配置化
if (tier <= TriAxisConfig.maxVoltageTier) { ... }       // ✅ 配置化
```

---

## 测试场景

### 场景 1：用户只玩到 HV
```properties
maxVoltageTier = 3
zombieAttackMinTier = 2
zombieSpawnMinTier = 3
```
- MV 机器产生污染，污染高时僵尸攻击机器
- HV 机器产生更多污染，主动生成僵尸

### 场景 2：用户玩到 MAX
```properties
maxVoltageTier = 14
zombieAttackMinTier = 2
zombieSpawnMinTier = 5
creeperSpawnMinTier = 7
```
- IV 以下：只有污染和被动威胁
- IV-LuV：主动生成僵尸
- ZPM+：主动生成苦力怕

### 场景 3：高难度整合包
```properties
basePollutionRate = 0.02
tierGrowthFactor = 0.8
zombieAttackMinTier = 1
zombieSpawnMinTier = 2
```
- LV 就开始有僵尸攻击
- MV 就开始主动生成威胁
- 污染产生速度翻倍

---

## 优先级

### P0（必须修复）
1. ✅ 污染生成检测：从 `hasEnergyOrActive` 改为 `isWorkingEnabled && isActive`
2. ✅ 威胁触发等级：从硬编码改为配置参数
3. ✅ 污染计算公式：从硬编码改为配置参数

### P1（高优先级）
1. ✅ 添加所有配置参数到 `TriAxisConfig`
2. ✅ 添加配置参数到 `triaxis-difficulty.properties`
3. ✅ 更新配置加载和保存逻辑

### P2（中优先级）
1. ⚠️ 难度曲线类型支持（LINEAR/EXPONENTIAL/LOGARITHMIC）
2. ⚠️ 电压等级名称映射（用于日志）

---

## 验收标准

### 代码层面
- [ ] 代码中不存在 `tier == 2`, `tier >= 3` 等硬编码判断
- [ ] 所有污染计算使用配置参数
- [ ] 所有威胁触发使用配置参数
- [ ] 污染生成检测使用 `isWorkingEnabled && isActive`

### 配置层面
- [ ] 所有参数在配置文件中可调
- [ ] 配置文件有详细注释
- [ ] 默认值合理（ULV-UHV）

### 功能层面
- [ ] 用户可以设置 `maxVoltageTier = 3`，系统正常工作
- [ ] 用户可以设置 `maxVoltageTier = 14`，系统正常工作
- [ ] 用户可以调整污染生成速率
- [ ] 用户可以调整威胁触发等级

---

**文档状态**: ✅ 需求确认完成，等待代码实现
