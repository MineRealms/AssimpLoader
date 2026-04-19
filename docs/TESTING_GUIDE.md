# EnhancedVisuals 集成调试 - 测试指南

## ✅ 已完成的修改

### 1. 添加了详细的调试日志

**MixinPlugin.java:**
- ✅ 记录 Mixin 加载过程
- ✅ 记录 EnhancedVisuals 类检测结果
- ✅ 记录 Mixin 应用过程

**EnhancedVisualsHelper.java:**
- ✅ 记录每次效果触发
- ✅ 记录 API 调用参数
- ✅ 记录异常信息

### 2. 重新构建并部署

```bash
./gradlew build
✓ JAR 已复制到游戏 mods 目录
```

## 🔍 测试步骤

### 步骤 1: 启动游戏

启动游戏后，查看日志文件：
```
G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log
```

### 步骤 2: 搜索 MixinPlugin 日志

在日志中搜索以下关键词：

```bash
# 搜索 MixinPlugin 日志
grep -i "IIC-MixinPlugin" latest.log
```

**期望看到:**
```
[IIC-MixinPlugin] Loading mixin plugin for package: cn.minerealms.iic.mixin
[IIC-MixinPlugin] Class found: team.creative.enhancedvisuals.client.VisualManager
[IIC-MixinPlugin] Checking EnhancedVisuals mixin: ... -> Present: true
[IIC-MixinPlugin] Applying EnhancedVisuals mixin: ...
[IIC-MixinPlugin] Successfully applied EnhancedVisuals mixin: ...
```

**如果看到 "Class not found":**
- EnhancedVisuals 没有被检测到
- 可能是版本不兼容或包名错误

### 步骤 3: 测试效果触发

在游戏中执行命令：

```
/im pollution-visual debug
/im pollution-visual test light
```

### 步骤 4: 检查效果日志

在日志中搜索：

```bash
# 搜索 EnhancedVisualsHelper 日志
grep -i "EnhancedVisualsHelper" latest.log
```

**期望看到:**
```
[EnhancedVisualsHelper] Triggering light effects...
[EnhancedVisualsHelper] Created visual type: ..., color: ..., duration: ...
[EnhancedVisualsHelper] Light effects triggered successfully
```

**如果看到错误:**
```
[EnhancedVisualsHelper] Error triggering light effects
```
说明 API 调用失败，需要检查具体的异常信息。

## 📊 诊断结果

### 情况 1: 没有 MixinPlugin 日志

**问题:** Mixin 配置没有加载

**解决方案:**
1. 检查 `integratedindustrialcraft.mixins.json` 是否正确
2. 确认 JAR 文件中包含 mixin 配置
3. 检查 Forge 版本兼容性

### 情况 2: "Class not found" 日志

**问题:** EnhancedVisuals 没有被检测到

**解决方案:**
1. 确认 EnhancedVisuals 在 mods 目录
2. 检查版本是否为 v1.8.2
3. 确认包名是否为 `team.creative.enhancedvisuals`

### 情况 3: Mixin 加载但没有 EnhancedVisualsHelper 日志

**问题:** 效果没有被触发或在错误的线程执行

**可能原因:**
- Packet 处理不在客户端主线程
- `@OnlyIn(Dist.CLIENT)` 注解导致类加载失败
- 方法调用被优化掉

### 情况 4: EnhancedVisualsHelper 日志存在但没有视觉效果

**问题:** EnhancedVisuals API 调用失败

**可能原因:**
- API 参数不正确
- VisualHandler 配置问题
- EnhancedVisuals 自身配置禁用了效果

## 🔧 下一步调试

### 如果 Mixin 没有加载

1. 检查 mixin 配置文件
2. 确认 MixinPlugin 类路径正确
3. 查看完整的启动日志

### 如果 API 调用失败

1. 测试 EnhancedVisuals 自己的效果是否工作
   - 受伤时是否有血液效果
   - 水下是否有模糊效果

2. 检查 EnhancedVisuals 配置
   ```
   .minecraft/config/enhancedvisuals-client.toml
   ```

3. 尝试简化 API 调用
   - 使用最简单的参数
   - 测试单个粒子

### 如果一切日志正常但没有效果

可能是 EnhancedVisuals v1.8.2 的 API 与我们的使用方式不兼容。

**备用方案:**
1. 查看 EnhancedVisuals v1.8.2 的示例代码
2. 联系 EnhancedVisuals 作者询问正确用法
3. 考虑使用事件系统而不是 Mixin

## 📝 收集信息

请提供以下信息以便进一步诊断：

1. **MixinPlugin 日志:**
   ```bash
   grep -i "IIC-MixinPlugin" latest.log
   ```

2. **EnhancedVisualsHelper 日志:**
   ```bash
   grep -i "EnhancedVisualsHelper" latest.log
   ```

3. **PollutionVisual 日志:**
   ```bash
   grep -i "PollutionVisual" latest.log
   ```

4. **EnhancedVisuals 加载日志:**
   ```bash
   grep -i "EnhancedVisuals" latest.log | head -20
   ```

5. **任何错误或异常:**
   ```bash
   grep -i "error\|exception" latest.log | grep -i "pollution\|enhanced"
   ```

## 🎯 测试清单

- [ ] 游戏启动成功
- [ ] 在日志中找到 MixinPlugin 加载信息
- [ ] 在日志中找到 "Class found: team.creative.enhancedvisuals.client.VisualManager"
- [ ] 在日志中找到 "Applying EnhancedVisuals mixin"
- [ ] 执行 `/im pollution-visual debug` 成功
- [ ] 执行 `/im pollution-visual test light` 成功
- [ ] 在日志中找到 EnhancedVisualsHelper 日志
- [ ] 看到实际的视觉效果（粒子、模糊等）

## 📚 相关文档

- **调试指南:** `docs/ENHANCED_VISUALS_DEBUG.md`
- **实现文档:** `docs/ENHANCED_VISUALS_FINAL.md`
- **设置指南:** `docs/ENHANCED_VISUALS_SETUP.md`
