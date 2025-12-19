# 最终测试指南

## ✅ 已实现的解决方案

### 方案 1: Mixin 系统（主要）
- 直接注入到 EnhancedVisuals 的 tick 循环
- 更高效，性能更好
- 需要 Mixin 正确应用

### 方案 2: 事件系统（备用）
- 使用 Forge 的 ClientTickEvent
- 保证功能一定能工作
- 性能略低但更可靠

## 🔍 测试步骤

### 步骤 1: 重新启动游戏

确保使用最新构建的 JAR：
```
integratedindustrialcraft-1.0.0.jar (已自动复制到 mods 目录)
```

### 步骤 2: 检查日志

启动游戏后，搜索以下日志：

```bash
# 检查 Mixin 是否工作
grep "VisualManagerMixin" latest.log

# 检查事件系统是否工作
grep "PollutionVisualEvents" latest.log
```

**期望结果（至少一个）:**

**Mixin 工作:**
```
[VisualManagerMixin] Mixin is active! Tick: 100, Player: CharlesKo
[VisualManagerMixin] Mixin is active! Tick: 200, Player: CharlesKo
```

**事件系统工作:**
```
[PollutionVisualEvents] Event handler is active! Tick: 100
[PollutionVisualEvents] Event handler is active! Tick: 200
```

### 步骤 3: 测试效果

在游戏中执行：

```
/im pollution-visual debug
/im pollution-visual test light
```

然后搜索：

```bash
grep "EnhancedVisualsHelper" latest.log
```

**期望结果:**
```
[EnhancedVisualsHelper] Triggering light effects...
[EnhancedVisualsHelper] Created visual type: ...
[EnhancedVisualsHelper] Light effects triggered successfully
```

## 📊 诊断结果

### 情况 A: Mixin 工作 ✅
- 看到 `[VisualManagerMixin] Mixin is active!`
- 性能最优
- 一切正常

### 情况 B: 事件系统工作 ✅
- 看到 `[PollutionVisualEvents] Event handler is active!`
- 功能正常，性能略低
- Mixin 没有应用但有备用方案

### 情况 C: 两者都工作 ⚠️
- 效果会被触发两次
- 需要禁用其中一个
- 建议禁用事件系统，保留 Mixin

### 情况 D: 都不工作 ❌
- 检查 EnhancedVisuals 是否正确安装
- 检查是否有其他 mod 冲突
- 查看完整的错误日志

## 🔧 如果需要禁用某个系统

### 禁用 Mixin 系统

编辑 `integratedindustrialcraft.mixins.json`:

```json
{
  "client": [
    // "enhancedvisuals.VisualManagerMixin"  // ← 注释掉这行
  ]
}
```

### 禁用事件系统

删除或重命名文件：
```
cn/minerealms/iic/client/PollutionVisualEventHandler.class
```

或者移除 `@Mod.EventBusSubscriber` 注解。

## 📝 需要提供的信息

请执行以下命令并提供结果：

```bash
# 1. 检查 Mixin
grep "VisualManagerMixin" latest.log

# 2. 检查事件系统
grep "PollutionVisualEvents" latest.log

# 3. 检查效果触发
grep "EnhancedVisualsHelper" latest.log

# 4. 检查任何错误
grep -i "error\|exception" latest.log | grep -i "pollution\|enhanced\|visual"
```

## 🎯 预期结果

**最佳情况:**
- Mixin 工作 ✅
- 事件系统也工作 ✅
- EnhancedVisualsHelper 被调用 ✅
- 看到实际的视觉效果 ✅

**可接受情况:**
- 只有事件系统工作 ✅
- EnhancedVisualsHelper 被调用 ✅
- 看到实际的视觉效果 ✅

**需要进一步调试:**
- 有日志但没有视觉效果
- 可能是 EnhancedVisuals API 调用问题

## 🚀 下一步

如果一切正常，可以：

1. **调整污染阈值** - 修改 `PollutionVisualEffects.java` 中的阈值
2. **添加更多效果** - 在 `EnhancedVisualsHelper.java` 中添加新效果
3. **优化性能** - 如果两个系统都工作，禁用其中一个
4. **自定义粒子** - 使用不同的粒子类型和颜色

## 📚 相关文件

- **Mixin 实现:** `src/main/java/cn/minerealms/iic/mixin/enhancedvisuals/VisualManagerMixin.java`
- **事件实现:** `src/main/java/cn/minerealms/iic/client/PollutionVisualEventHandler.java`
- **效果管理:** `src/main/java/cn/minerealms/iic/industrial/PollutionVisualEffects.java`
- **API 调用:** `src/main/java/cn/minerealms/iic/mixin/enhancedvisuals/EnhancedVisualsHelper.java`
