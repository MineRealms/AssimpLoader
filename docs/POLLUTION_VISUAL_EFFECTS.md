# Pollution Visual Effects Integration

## 概述

污染视觉效果管理器 (PollutionVisualManager) 集成了 EnhancedVisuals 模组，根据区块污染等级触发视觉特效和负面效果。

## 功能特性

### 1. 污染等级阈值

| 污染等级 | 阈值 | 视觉效果 | 负面效果 |
|---------|------|---------|---------|
| 轻度污染 | 50-100 | 绿色粒子 (3个) | 无 |
| 中度污染 | 100-150 | 黄色粒子 (5个) | 反胃 I (10秒) |
| 重度污染 | 150-200 | 橙色粒子 (8个) | 反胃 II (15秒) |
| 严重污染 | 200+ | 红色粒子 (12个) | 反胃 III (20秒) + 失明 (5秒) |

### 2. 线程安全

- **反射初始化**: 线程安全单例模式（双重检查锁）
- **特效触发**: 必须在主线程执行
- **冷却追踪**: ConcurrentHashMap 保证线程安全
- **特效冷却**: 每个玩家 5 秒冷却时间

### 3. 调试系统

- **默认状态**: 所有调试日志默认关闭
- **专用 Logger**: 使用独立的 "PollutionVisuals" Logger
- **完整异常捕获**: 所有反射调用都有 try-catch 保护
- **优雅降级**: EnhancedVisuals 未安装时自动禁用

## 使用指令

### 1. 切换调试模式

```
/im pollution-visual debug
```

- 开启/关闭调试日志输出
- 需要 OP 权限 (level 2)
- 调试信息输出到专用 Logger

### 2. 测试特效

```
/im pollution-visual test <type>
```

**可用类型**:
- `light` - 轻度污染效果
- `moderate` - 中度污染效果
- `heavy` - 重度污染效果
- `severe` - 严重污染效果
- `all` - 依次触发所有效果（间隔 0.5 秒）

**示例**:
```
/im pollution-visual test severe
/im pollution-visual test all
```

### 3. 查看诊断信息

```
/im pollution-visual diagnostics
```

显示内容：
- EnhancedVisuals 加载状态
- 调试模式状态
- 活跃冷却数量
- 反射状态详情
- 污染阈值配置

## 集成方式

### 自动触发

污染特效会在以下情况自动触发：

1. **玩家位置检测**: 每秒检测玩家所在区块的污染值
2. **阈值判断**: 污染值超过 50 时开始触发效果
3. **冷却控制**: 每个玩家 5 秒冷却，避免特效过于频繁
4. **主线程执行**: 所有特效触发都在主线程执行，保证线程安全

### 手动集成

如果需要在代码中手动触发特效：

```java
// 检查并触发特效（自动判断污染等级）
PollutionVisualManager.checkAndTriggerEffects(player, level);

// 测试特定特效
PollutionVisualManager.triggerTestEffect(player, "severe");

// 检查 EnhancedVisuals 是否加载
if (PollutionVisualManager.isEVLoaded()) {
    // 可以使用特效系统
}

// 开启调试
PollutionVisualManager.setDebugEnabled(true);
```

## 反射 API 详情

### 加载的类

- `team.creative.enhancedvisuals.client.VisualManager`
- `team.creative.enhancedvisuals.api.type.VisualType`
- `team.creative.enhancedvisuals.api.type.VisualTypeParticleColored`
- `team.creative.enhancedvisuals.api.VisualHandler`
- `team.creative.creativecore.common.util.type.Color`
- `team.creative.creativecore.common.config.premade.IntMinMax`

### 加载的方法

- `VisualManager.addParticlesFadeOut()` - 添加粒子特效
- `VisualManager.addVisualFadeOut()` - 添加覆盖特效
- `Color(float, float, float, float)` - 颜色构造函数
- `IntMinMax(int, int)` - 时间范围构造函数

## 调试日志示例

开启调试后的日志输出：

```
[PollutionVisuals] Initializing EnhancedVisuals reflection...
[PollutionVisuals] Classes loaded successfully
[PollutionVisuals] Methods loaded successfully
[PollutionVisuals] Constructors loaded successfully
[PollutionVisuals] EnhancedVisuals integration initialized successfully
[DEBUG] Triggered effects for PlayerName at pollution 175.3
[DEBUG] Heavy effects triggered: pollution=175.3
```

## 错误处理

### EnhancedVisuals 未安装

```
[PollutionVisuals] EnhancedVisuals not found, visual effects disabled
```

系统会优雅降级，不会影响游戏运行。

### 反射失败

```
[PollutionVisuals] EnhancedVisuals API method not found: <method>
```

会记录详细错误信息，但不会崩溃。

### 特效触发失败

```
[PollutionVisuals] Error triggering <type> effects: <error>
```

单次特效失败不会影响后续触发。

## 性能优化

1. **反射缓存**: 所有反射对象只初始化一次
2. **冷却机制**: 每个玩家 5 秒冷却，避免频繁触发
3. **提前退出**: 污染低于阈值时立即返回
4. **线程安全**: 使用 ConcurrentHashMap 避免锁竞争

## 配置建议

### 开发环境

```
/im pollution-visual debug  # 开启调试
/im pollution-visual test all  # 测试所有特效
```

### 生产环境

- 保持调试模式关闭（默认）
- 特效会根据实际污染自动触发
- 使用 `/im pollution-visual diagnostics` 检查状态

## 兼容性

- **Minecraft**: 1.20.1
- **Forge**: 47.1.3+
- **EnhancedVisuals**: 可选依赖（未安装时自动禁用）
- **线程安全**: 完全线程安全，支持异步环境

## 故障排除

### 特效不触发

1. 检查 EnhancedVisuals 是否安装
   ```
   /im pollution-visual diagnostics
   ```

2. 检查污染值是否足够
   ```
   /im industrial status
   ```

3. 检查冷却时间（5秒）

### 调试信息不显示

1. 确认调试模式已开启
   ```
   /im pollution-visual debug
   ```

2. 检查日志文件中的 "PollutionVisuals" 标签

### 反射错误

1. 确认 EnhancedVisuals 版本兼容
2. 查看完整错误堆栈
3. 使用 `/im pollution-visual diagnostics` 检查反射状态

## 更新日志

### v1.0.0 (2026-04-16)

- ✅ 初始版本
- ✅ 四级污染特效系统
- ✅ 线程安全反射集成
- ✅ 完整调试系统
- ✅ 测试指令支持
- ✅ 优雅降级处理
