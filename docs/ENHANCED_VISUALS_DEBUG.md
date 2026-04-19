# EnhancedVisuals Integration 调试指南

## 当前状态

### 版本信息
- **游戏中的 EnhancedVisuals:** v1.8.2 (`EnhancedVisuals_FORGE_v1.8.2_mc1.20.1.jar`)
- **参考版本:** v1.8.2 (H:\MinecraftMods\EnhancedVisuals)
- **包名:** `team.creative.enhancedvisuals.client.VisualManager` ✅ 正确

### 问题现象
- 命令执行成功，显示 "Effect triggered successfully"
- 但是**没有实际的视觉效果**（粒子、模糊等）
- 只有原版的 Nausea 和 Blindness 效果生效

### 可能的原因

1. **Mixin 没有被应用**
   - MixinPlugin 可能没有检测到 EnhancedVisuals
   - Mixin 配置可能有问题
   - 目标方法签名不匹配

2. **EnhancedVisualsHelper 调用失败**
   - API 调用方式不正确
   - 参数传递有问题
   - 客户端/服务端执行问题

3. **EnhancedVisuals API 版本差异**
   - v1.8.2 的 API 可能与我们的代码不兼容

## 调试步骤

### 步骤 1: 检查 Mixin 是否加载

重新启动游戏，查看日志中是否有以下信息：

```
[IIC-MixinPlugin] Loading mixin plugin for package: cn.minerealms.iic.mixin
[IIC-MixinPlugin] Class found: team.creative.enhancedvisuals.client.VisualManager
[IIC-MixinPlugin] Checking EnhancedVisuals mixin: ... -> Present: true
[IIC-MixinPlugin] Applying EnhancedVisuals mixin: ...
[IIC-MixinPlugin] Successfully applied EnhancedVisuals mixin: ...
```

**如果没有这些日志:**
- Mixin 没有被加载
- 检查 `integratedindustrialcraft.mixins.json` 配置

**如果有 "Class not found" 日志:**
- EnhancedVisuals 没有被检测到
- 检查 EnhancedVisuals 是否在 mods 目录

### 步骤 2: 检查 EnhancedVisualsHelper 是否被调用

在 `PollutionVisualEffects.java` 中，我们调用了：
```java
cn.minerealms.iic.mixin.enhancedvisuals.EnhancedVisualsHelper.triggerLightEffects();
```

**问题:** 这个调用可能在服务端执行，而 EnhancedVisuals 是客户端 only！

### 步骤 3: 检查命令执行位置

当前命令流程：
```
Server: /im pollution-visual test light
  └─> PollutionVisualCommand.testEffect() [服务端]
       └─> PacketHandler.sendToClient(TriggerPollutionEffectPacket) [发送到客户端]
            └─> TriggerPollutionEffectPacket.handle() [客户端接收]
                 └─> PollutionVisualEffects.triggerTestEffect() [客户端执行]
                      └─> EnhancedVisualsHelper.triggerLightEffects() [客户端执行]
```

**可能的问题:** Packet 处理可能不在正确的线程

## 修复方案

### 方案 1: 确保在客户端主线程执行

修改 `TriggerPollutionEffectPacket.java`:

```java
public void handle(Supplier<NetworkEvent.Context> ctx) {
    ctx.get().enqueueWork(() -> {
        // 确保在客户端主线程执行
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                // 添加调试日志
                System.out.println("[DEBUG] Triggering pollution effect on client");
                PollutionVisualEffects.triggerTestEffect(player, effectType);
            }
        });
    });
    ctx.get().setPacketHandled(true);
}
```

### 方案 2: 直接测试 EnhancedVisuals API

创建一个简单的测试命令，直接调用 EnhancedVisuals API：

```java
// 在客户端执行
Color color = new Color(0.2f, 0.8f, 0.2f, 0.5f);
VisualType visualType = new VisualTypeParticleColored("blob", color);
IntMinMax duration = new IntMinMax(100, 200);
VisualHandler handler = new VisualHandler();

VisualManager.addParticlesFadeOut(visualType, handler, 3, duration, true, color);
```

### 方案 3: 检查 EnhancedVisuals 配置

EnhancedVisuals 可能有配置文件禁用了某些效果：

```
.minecraft/config/enhancedvisuals-client.toml
```

检查是否有禁用粒子效果的选项。

## 测试命令

### 测试 1: 检查 Mixin 日志

```bash
# 启动游戏后，搜索日志
grep -i "IIC-MixinPlugin" latest.log
```

### 测试 2: 检查效果触发

```bash
# 在游戏中执行
/im pollution-visual debug
/im pollution-visual test light

# 查看日志
grep -i "pollution\|enhanced" latest.log | tail -20
```

### 测试 3: 检查 EnhancedVisuals 是否工作

在游戏中测试 EnhancedVisuals 自己的效果：
- 受到伤害时应该有血液效果
- 水下应该有模糊效果
- 如果这些都不工作，说明 EnhancedVisuals 本身有问题

## 下一步

1. **重新构建并部署** (已完成)
   ```bash
   ./gradlew build
   ```

2. **启动游戏并测试**
   - 检查启动日志中的 MixinPlugin 信息
   - 执行测试命令
   - 观察是否有视觉效果

3. **收集日志**
   - 复制 `latest.log` 中所有包含 "IIC-MixinPlugin" 的行
   - 复制所有包含 "PollutionVisual" 的行
   - 复制所有包含 "EnhancedVisuals" 的行

4. **分析问题**
   - 如果 Mixin 没有加载 → 配置问题
   - 如果 Mixin 加载但效果不触发 → API 调用问题
   - 如果 EnhancedVisuals 自己的效果也不工作 → EnhancedVisuals 配置问题

## 临时解决方案

如果 Mixin 方式不工作，可以尝试：

1. **使用事件系统**
   - 监听客户端 tick 事件
   - 直接调用 EnhancedVisuals API

2. **使用反射（备用方案）**
   - 虽然不推荐，但可以作为临时解决方案

3. **联系 EnhancedVisuals 作者**
   - 询问正确的 API 使用方式
   - 确认 v1.8.2 的 API 文档
