# The Hordes Integration System - 完整实现文档

## 系统概述

成功实现了 The Hordes 尸潮系统与 ImprovedMobs 工业难度系统的深度集成，创造了基于污染、电压等级和难度的动态尸潮机制。

## 核心功能

### 1. 动态尸潮强度调整
- **基于 Difficulty**: 尸潮强度随 ImprovedMobs difficulty 动态调整
- **三轴联动**: 时间/电压/污染三轴难度影响尸潮参数
- **电压等级缩放**: 不同电压阶段有不同的尸潮强度倍率
  - ULV (Tier 0): 1.0x
  - LV  (Tier 1): 1.1x
  - MV  (Tier 2): 1.3x - 小股袭扰开始
  - HV  (Tier 3): 1.5x - 中等威胁
  - EV  (Tier 4): 1.8x
  - IV  (Tier 5): 2.2x - 大尸潮开始
  - LuV (Tier 6): 2.6x
  - ZPM (Tier 7): 3.0x
  - UV  (Tier 8): 3.5x
  - UHV (Tier 9): 4.0x - 极限挑战

### 2. 污染触发尸潮
- **自动触发**: 区块污染 ≥150 时自动触发尸潮
- **大尸潮**: 污染 ≥200 时触发大尸潮（2倍强度）
- **触发机制**:
  - 检查间隔: 600 tick (30秒)
  - 触发概率: 5% 每次检查
  - 冷却时间: 6000 tick (5分钟)

### 3. 小股袭扰系统
- **触发条件**: 污染 ≥80 且电压等级 ≥MV
- **生成数量**: 3-8 个怪物（基于污染和电压等级）
- **触发间隔**: 1200 tick (1分钟)
- **特点**: 不触发完整尸潮，只生成小股怪物骚扰

### 4. 机器攻击系统
- **目标选择**: 30% 的尸潮僵尸会攻击附近机器
- **检测范围**: 32 方块
- **行为**: 僵尸优先攻击机器而非玩家
- **集成**: 使用 ImprovedMobs 的 ZombieDestroyMachineGoal

## 文件结构

### 新增文件

1. **HordeManager.java** (`cn.minerealms.iic.industrial`)
   - The Hordes mod 的反射式集成管理器
   - 提供所有 Hordes API 的安全访问
   - 非强依赖，Hordes 未安装时安全降级

2. **HordeIntegrationManager.java** (`cn.minerealms.iic.industrial`)
   - 尸潮集成的核心逻辑
   - 污染触发、小股袭扰、机器攻击
   - 难度动态调整

3. **HordesCommands.java** (`cn.minerealms.iic.commands`)
   - 尸潮系统指令
   - 测试尸潮生成
   - 配置管理

### 修改文件

1. **TriAxisConfig.java**
   - 新增 Hordes 集成配置参数
   - 所有参数可通过配置文件调整

2. **EventHandler.java**
   - 添加 HordeIntegrationManager.tick() 调用

3. **ImprovedMobsCommand.java**
   - 注册 HordesCommands
   - 更新帮助信息

## 指令系统

### 查看状态
```
/im hordes status
```
显示当前尸潮状态、污染、电压等级、冷却等信息

### 测试尸潮生成
```
/im hordes test <difficulty> <pollution> <tier>
```
- `difficulty`: 0.0-100.0 (ImprovedMobs difficulty 值)
- `pollution`: 0.0-1000.0 (区块污染值)
- `tier`: ULV/LV/MV/HV/EV/IV/LuV/ZPM/UV/UHV/UEV/UIV/UXV/OpV/MAX

**示例**:
```
/im hordes test 50.0 180.0 HV
```
生成一个 difficulty=50, pollution=180, HV 阶段的测试尸潮

**自动计算**:
- 是否为大尸潮（pollution ≥200）
- 电压等级倍率
- Difficulty 加成
- 最终持续时间和生成数量

**电压等级限制**:
- 超过配置的最大电压等级自动封顶
- 例如配置 maxIndustrialTier=9 (UHV)，输入 UEV 会自动降为 UHV

### 配置管理
```
/im hordes config enable <true|false>          # 启用/禁用集成
/im hordes config intensity <0.5-3.0>          # 全局强度倍率
/im hordes config difficultyFactor <0.1-2.0>   # Difficulty 转换系数
/im hordes config pollutionTrigger <true|false> # 污染触发开关
/im hordes config pollutionThreshold <100-300>  # 污染触发阈值
/im hordes config skirmishes <true|false>       # 小股袭扰开关
/im hordes config machineTargeting <true|false> # 机器攻击开关
/im hordes config machineTargetingChance <0.1-1.0> # 机器攻击概率
```

### 重载和重置
```
/im hordes reload  # 重载配置
/im hordes reset   # 重置所有冷却
```

## 配置文件

所有配置保存在 `config/triaxis-difficulty.properties`

### Hordes 集成配置

```properties
# 启用 Hordes 集成
enableHordeIntegration=true

# 全局尸潮强度倍率 (0.5-3.0)
hordeIntensityMultiplier=1.0

# Difficulty 转换系数 (0.1-2.0)
difficultyToIntensityFactor=0.5

# 污染触发尸潮
enablePollutionTriggeredHordes=true
pollutionHordeTriggerThreshold=150.0
pollutionHordeCheckInterval=600.0
pollutionHordeTriggerChance=0.05

# 小股袭扰
enableSkirmishes=true
skirmishPollutionThreshold=80.0
skirmishInterval=1200.0
skirmishMinCount=3
skirmishMaxCount=8

# 大尸潮
majorHordePollutionThreshold=200.0
majorHordeMultiplier=2.0

# 机器攻击
enableMachineTargeting=true
machineTargetingRange=32.0
machineTargetingChance=0.3

# 电压等级缩放
enableVoltageTierScaling=true
```

## 游戏阶段平衡

### MV 阶段 (Tier 2)
- **机器数量**: 20-30 台
- **污染**: 30-60
- **尸潮强度**: 1.3x
- **威胁**: 小股袭扰开始（污染 ≥80）

### HV 阶段 (Tier 3)
- **机器数量**: 40-60 台
- **污染**: 80-150
- **尸潮强度**: 1.5x
- **威胁**: 频繁袭扰，可能触发尸潮

### IV-LuV 阶段 (Tier 5-6)
- **机器数量**: 100+ 台
- **污染**: 150-300
- **尸潮强度**: 2.2x-2.6x
- **威胁**: 大尸潮频繁，机器攻击常见

### ZPM-UHV 阶段 (Tier 7-9)
- **机器数量**: 200+ 台
- **污染**: 300+
- **尸潮强度**: 3.0x-4.0x
- **威胁**: 极限挑战，持续高压

## 技术实现

### 反射式集成
- **HordeManager**: 完全反射式访问 The Hordes API
- **非强依赖**: Hordes 未安装时所有方法安全返回默认值
- **缓存优化**: 反射方法和字段缓存，避免重复查找

### 性能优化
- **冷却系统**: 防止频繁触发尸潮
- **异步检查**: 污染检查不阻塞主线程
- **清理机制**: 定期清理无效的僵尸引用

### 集成点
1. **Tick 系统**: EventHandler.onServerTick()
2. **Difficulty 系统**: IndustrialDifficultyGetter
3. **机器扫描**: MachineScanner
4. **污染系统**: PollutionManager

## 使用示例

### 场景 1: 测试 HV 阶段尸潮
```
/im hordes test 30.0 120.0 HV
```
- Difficulty: 30.0 (中等难度)
- Pollution: 120.0 (高污染)
- Tier: HV (1.5x 倍率)
- 结果: 普通尸潮，强度适中

### 场景 2: 测试 IV 阶段大尸潮
```
/im hordes test 80.0 250.0 IV
```
- Difficulty: 80.0 (高难度)
- Pollution: 250.0 (极高污染，触发大尸潮)
- Tier: IV (2.2x 倍率)
- 结果: 大尸潮（2x），强度极高

### 场景 3: 调整机器攻击概率
```
/im hordes config machineTargetingChance 0.5
```
将机器攻击概率从 30% 提升到 50%

## 权限系统

- **OP 等级 2**: 所有 `/im hordes` 指令（除 debug 和 hud）
- **OP 等级 4**: debug 和 hud 指令

## 调试信息

使用 `/im hordes status` 查看完整调试信息：
- Hordes 是否加载
- 当前尸潮状态
- 污染和电压等级
- 冷却状态
- 机器攻击僵尸数量

## 注意事项

1. **配置持久化**: 所有通过 `/im` 指令修改的配置都会保存到配置文件
2. **电压等级限制**: 测试指令会自动限制到配置的最大电压等级
3. **冷却机制**: 防止尸潮过于频繁，可通过 `/im hordes reset` 重置
4. **非强依赖**: The Hordes 未安装时系统自动禁用，不影响其他功能

## 构建信息

- **构建状态**: ✓ 成功
- **JAR 位置**: `build/libs/integratedindustrialcraft-1.0.0.jar`
- **Minecraft 版本**: 1.20.1
- **Forge 版本**: 47.1.3

## 未来扩展

可能的扩展方向：
1. 尸潮实体属性动态调整（基于 difficulty）
2. 特殊尸潮类型（例如：工业尸潮，只攻击机器）
3. 尸潮预警系统（HUD 显示即将到来的尸潮）
4. 尸潮奖励系统（击败尸潮获得奖励）
5. 与 Spore Hivemind 的联动（虫巢附近尸潮更强）
