# 激光Bloom效果 - 完整集成指南

## ✅ 已完成集成

激光渲染器已经完全集成Shimmer bloom效果，无需额外配置即可使用！

## 🎨 效果说明

### Bloom渲染层次

```
┌─────────────────────────────────┐
│  Bloom层 (0.25半径, 60%透明)   │  ← 最外层，强烈发光
│  ┌───────────────────────────┐  │
│  │ 光晕层 (0.15半径, 50%透明)│  │  ← 中间层，柔和过渡
│  │  ┌─────────────────────┐  │  │
│  │  │ 核心 (0.08半径, 实心)│  │  │  ← 核心光束，高亮
│  │  └─────────────────────┘  │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
```

### 颜色方案

- **BASIC**: 红色 (255, 50, 50) - 基础激光
- **ADVANCED**: 橙色 (255, 165, 0) - 高级激光
- **ELITE**: 蓝色 (50, 150, 255) - 精英激光
- **ULTIMATE**: 紫色 (200, 50, 255) - 终极激光

### 动画效果

- 能量波动：沿激光方向的脉冲效果
- 频率：`sin(time + position * 10)`
- 幅度：30%半径变化

## 📦 依赖配置

### build.gradle

Shimmer已经添加为依赖（第126行）：

```gradle
implementation fg.deobf("com.lowdragmc.shimmer:Shimmer-forge:1.20:0.2.4")
```

### 配置文件

Shimmer配置已创建：`src/main/resources/config/shimmer/iic_turrets.json`

```json
{
  "blooms": [
    {
      "comment": "IIC激光炮塔 - 激光实体bloom效果",
      "particle": "integratedindustrialcraft:laser_spark"
    }
  ]
}
```

## 🔧 实现细节

### LaserRenderer.java

**关键特性**：
1. **自动检测Shimmer**: 启动时检查Shimmer是否可用
2. **优雅降级**: Shimmer不可用时自动使用普通渲染
3. **异常处理**: Bloom渲染出错时自动降级
4. **三层渲染**: Bloom层 + 光晕层 + 核心层

**代码结构**：
```java
// 检查Shimmer
private static void checkShimmer() {
    try {
        Class.forName("com.lowdragmc.shimmer.client.postprocessing.PostProcessing");
        shimmerAvailable = true;
    } catch (ClassNotFoundException e) {
        shimmerAvailable = false;
    }
}

// 渲染入口
public void render(...) {
    if (shimmerAvailable) {
        renderWithBloom(...);  // 使用bloom
    } else {
        renderNormal(...);     // 降级渲染
    }
}

// Bloom渲染
private void renderWithBloom(...) {
    PostProcessing bloom = PostProcessing.getBlockBloom();
    bloom.postEntity(bufferSource -> {
        // 三层渲染
        renderBeam(..., BLOOM_RADIUS, 0.6F, ...);  // Bloom层
        renderBeam(..., GLOW_RADIUS, 0.5F, ...);   // 光晕层
        renderBeam(..., CORE_RADIUS, 1.0F, ...);   // 核心层
    });
}
```

## 🎮 使用方法

### 玩家端

1. **安装Shimmer**: 确保Shimmer mod已安装
2. **启动游戏**: 激光会自动显示bloom效果
3. **配置bloom**: 在Shimmer配置中可调整bloom强度

### 开发端

无需额外操作，bloom效果已完全集成到`LaserRenderer`中。

## ⚡ 性能优化

### 自动优化

- **批处理**: 所有bloom实体在同一帧批量处理
- **降级机制**: Shimmer不可用时自动降级
- **异常恢复**: 渲染出错时自动切换到普通渲染

### 性能影响

- **轻微GPU负载**: Bloom是后处理效果，主要消耗GPU
- **可配置**: 玩家可在Shimmer配置中禁用bloom
- **优化建议**: 限制同时存在的激光数量（<10个）

## 🐛 调试

### 检查Shimmer状态

查看日志输出：
```
[IIC-Turrets] Shimmer detected, bloom effects enabled for lasers
```
或
```
[IIC-Turrets] Shimmer not found, using standard laser rendering
```

### 常见问题

**Q: 激光没有bloom效果？**

A: 检查：
1. Shimmer是否正确安装
2. 查看日志确认Shimmer检测状态
3. 检查Shimmer配置中bloom是否启用

**Q: 激光渲染出错？**

A: 系统会自动降级到普通渲染，查看日志：
```
[IIC-Turrets] Error rendering laser with bloom, falling back to normal rendering
```

**Q: 性能下降？**

A: 尝试：
1. 在Shimmer配置中降低bloom质量
2. 减少同时存在的激光数量
3. 禁用Shimmer bloom效果

## 📊 效果对比

### 无Shimmer（降级渲染）
```
✓ 双层渲染（光晕 + 核心）
✓ 基础发光效果
✓ 能量波动动画
✗ 无bloom光晕扩散
```

### 有Shimmer（完整效果）
```
✓ 三层渲染（bloom + 光晕 + 核心）
✓ 强烈bloom发光效果
✓ 柔和的光晕扩散
✓ 能量波动动画
✓ 真实的高能激光视觉效果
```

## 🔬 技术原理

### Shimmer PostProcessing工作流程

1. **提交渲染**: `bloom.postEntity(consumer -> {...})`
2. **MRT渲染**: 渲染到Multiple Render Target
3. **Bloom提取**: 提取高亮区域
4. **高斯模糊**: 对bloom区域进行模糊
5. **合成**: 将bloom效果合成回主渲染目标

### 渲染管线

```
主渲染循环
    ↓
LaserRenderer.render()
    ↓
renderWithBloom()
    ↓
PostProcessing.postEntity()  ← 提交到bloom队列
    ↓
[下一帧]
    ↓
Shimmer渲染bloom队列
    ↓
应用bloom后处理
    ↓
合成到屏幕
```

## 🎯 扩展功能

### 自定义Bloom强度

可以根据炮塔等级调整bloom强度：

```java
private float getBloomIntensity(LaserTurretTier tier) {
    return switch (tier) {
        case BASIC -> 0.5F;
        case ADVANCED -> 0.6F;
        case ELITE -> 0.7F;
        case ULTIMATE -> 0.8F;
    };
}

// 在renderWithBloom中使用
float intensity = getBloomIntensity(tier);
renderBeam(..., BLOOM_RADIUS, intensity, ...);
```

### 距离LOD优化

远距离激光可以降级渲染：

```java
double distSqr = pEntity.distanceToSqr(mc.player);
if (distSqr > 1024.0) {  // 超过32格
    renderNormal(...);   // 使用普通渲染
} else {
    renderWithBloom(...); // 使用bloom渲染
}
```

### 添加粒子效果

结合Shimmer的粒子bloom：

```java
// 在激光路径上生成带bloom的粒子
if (shimmerAvailable) {
    PostProcessing bloom = PostProcessing.getBlockBloom();
    bloom.postParticle(
        ModParticles.LASER_SPARK.get(),
        laserPos.x, laserPos.y, laserPos.z,
        0, 0, 0
    );
}
```

## 📚 参考资料

- **Shimmer GitHub**: https://github.com/Low-Drag-MC/Shimmer
- **Shimmer Wiki**: 查看bloom配置和API文档
- **PostProcessing源码**: `H:\MinecraftMods\Shimmer\Common\src\main\java\com\lowdragmc\shimmer\client\postprocessing\PostProcessing.java`

## ✨ 总结

激光bloom效果已完全集成，特点：

✅ **即插即用**: 无需额外配置
✅ **自动降级**: Shimmer不可用时自动切换
✅ **异常安全**: 渲染出错时自动恢复
✅ **性能优化**: 批处理和智能降级
✅ **视觉震撼**: 三层bloom效果，真实的高能激光

享受炫酷的激光效果吧！🔥⚡

