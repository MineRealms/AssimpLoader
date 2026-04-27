# Circle 方块测试指南

## 概述

Circle 方块是一个带有自动循环动画的示例方块，展示了完整的骨骼动画系统功能。

## 功能特性

- ✅ 自动检测 FBX 文件中的动画
- ✅ 自动播放第一个动画并循环
- ✅ 每个方块实例独立的动画状态
- ✅ GPU 加速的骨骼蒙皮渲染
- ✅ 实时动画更新（20 TPS）

## 测试步骤

### 1. 启动游戏

```bash
./gradlew runClient
```

### 2. 进入创造模式

按 `F3 + N` 切换到创造模式（如果不在创造模式）

### 3. 获取 Circle 方块

打开创造模式物品栏（按 `E`），在 "建筑方块" 标签页中找到 "Circle" 方块

### 4. 放置方块

在世界中放置 Circle 方块，动画会自动开始播放并循环

### 5. 测试多个方块

放置多个 Circle 方块，验证每个方块都有独立的动画状态：
- 每个方块的动画应该独立运行
- 不同方块的动画可能处于不同的时间点（取决于放置时间）

### 6. 查看调试信息

按 `F3` 打开调试界面，在日志中查看动画初始化信息：

```
[ANIMATION] Initializing CircleBlock animation at BlockPos{x=..., y=..., z=...}
[ANIMATION] CircleBlock playing animation: 'AnimationName' (total animations: 1)
```

## 预期行为

### 正常情况

1. **方块放置后**：
   - 动画立即开始播放
   - 模型在方块中心渲染
   - 骨骼动画平滑运行

2. **动画循环**：
   - 动画播放到结尾后自动重新开始
   - 无缝循环，没有停顿或跳跃

3. **多个方块**：
   - 每个方块独立动画
   - 互不干扰

4. **性能**：
   - GPU 蒙皮，CPU 占用低
   - 多个方块同时动画不卡顿

### 异常情况处理

如果动画没有播放，检查日志中的错误信息：

1. **模型加载失败**：
   ```
   [ERROR] CircleBlock: Failed to initialize animation state for model 'circle'
   ```
   - 检查 `circle.fbx` 文件是否存在于 `assets/assimploader/models/block/` 目录

2. **没有动画**：
   ```
   [WARN] CircleBlock: No animations found in model 'circle'
   ```
   - 使用 FBXInspector 检查 FBX 文件中的动画

3. **没有骨骼**：
   ```
   [WARN] Mesh circle has no bones, cannot render as skinned mesh
   ```
   - 确认 FBX 文件包含骨骼数据

## 调试工具

### FBXInspector

使用 FBXInspector 检查 FBX 文件中的动画信息：

```java
// 在代码中调用
FBXInspector.inspectFBX("assets/assimploader/models/block/circle.fbx");
```

输出示例：
```
=== Animations in circle.fbx ===
- Take 001 (duration: 2.5s)
- Idle (duration: 1.0s)
```

## 自定义配置

### 修改动画速度

在 `CircleBlockEntity.java` 的 `tick()` 方法中修改时间增量：

```java
// 默认速度（1x）
animationState.update(0.05f);

// 2倍速
animationState.update(0.1f);

// 0.5倍速
animationState.update(0.025f);
```

### 修改模型缩放

在 `CircleBlockEntityRenderer.java` 中取消注释缩放代码：

```java
// 缩小到 50%
poseStack.scale(0.5f, 0.5f, 0.5f);

// 放大到 200%
poseStack.scale(2.0f, 2.0f, 2.0f);
```

### 修改模型位置

在 `CircleBlockEntityRenderer.java` 中调整平移：

```java
// 默认：方块底部中心
poseStack.translate(0.5, 0.0, 0.5);

// 方块中心
poseStack.translate(0.5, 0.5, 0.5);

// 方块顶部中心
poseStack.translate(0.5, 1.0, 0.5);
```

### 切换动画

如果 FBX 文件包含多个动画，可以在 `CircleBlockEntity.java` 中选择：

```java
// 播放第一个动画（默认）
String firstAnimation = animations[0];
animationState.playAnimation(firstAnimation);

// 播放特定名称的动画
animationState.playAnimation("Idle");

// 播放第二个动画
if (animations.length > 1) {
    animationState.playAnimation(animations[1]);
}
```

## 性能测试

### 压力测试

1. 放置 10 个 Circle 方块，观察 FPS
2. 放置 50 个 Circle 方块，观察 FPS
3. 放置 100 个 Circle 方块，观察 FPS

按 `F3` 查看 FPS 和内存使用情况。

### 预期性能

- **10 个方块**：FPS 应该没有明显下降
- **50 个方块**：FPS 可能轻微下降（5-10%）
- **100 个方块**：FPS 下降取决于 GPU 性能

GPU 蒙皮比 CPU 蒙皮快 10-100 倍，所以即使大量方块也应该保持流畅。

## 常见问题

### Q: 方块是透明的/看不见

**A**: 检查以下几点：
1. 确认 FBX 文件包含网格数据
2. 确认纹理路径正确
3. 检查光照等级（`packedLight`）

### Q: 动画不流畅/卡顿

**A**: 可能原因：
1. FBX 文件关键帧太少
2. 帧率太低（检查 F3 调试信息）
3. CPU/GPU 性能不足

### Q: 动画速度不对

**A**: 调整 `tick()` 方法中的时间增量：
- 1 tick = 0.05 秒（20 TPS）
- 如果动画太快，减小增量
- 如果动画太慢，增大增量

### Q: 多个方块动画同步了

**A**: 这是正常的，如果它们在同一 tick 放置。要测试独立性：
1. 放置第一个方块
2. 等待几秒
3. 放置第二个方块
4. 观察它们的动画相位不同

## 扩展功能

基于 Circle 方块，你可以实现：

1. **动画控制**：
   - 右键方块切换动画
   - 红石信号控制播放/暂停
   - 方块状态控制动画速度

2. **交互动画**：
   - 玩家靠近时播放特定动画
   - 受到攻击时播放受击动画
   - 时间/天气相关的动画变化

3. **高级动画**：
   - 动画混合（两个动画平滑过渡）
   - 动画层（上半身和下半身独立动画）
   - IK 系统（看向玩家）

## 参考文档

- [动画系统使用指南](ANIMATION_USAGE_GUIDE.md)
- [骨骼动画分析](SKELETAL_ANIMATION_ANALYSIS.md)
- [动画系统完整功能](ANIMATION_SYSTEM_COMPLETE.md)

## 技术细节

### 动画更新流程

```
1. CircleBlock.getTicker() 注册 tick 方法
   ↓
2. CircleBlockEntity.tick() 每 tick 调用
   ↓
3. AnimationState.update(0.05f) 更新动画时间
   ↓
4. AnimationPlayer 插值关键帧
   ↓
5. BoneHierarchyBuilder 更新骨骼矩阵
   ↓
6. CircleBlockEntityRenderer.render() 渲染
   ↓
7. SkinnedMeshRenderer GPU 蒙皮
```

### 内存管理

- **模型缓存**：AssimpModelLoader 自动缓存加载的模型
- **VBO 缓存**：SkinnedMeshRenderer 缓存顶点缓冲对象
- **动画状态**：每个 BlockEntity 独立，不共享

### 渲染管线

```
CPU:
- AnimationPlayer 计算骨骼变换
- BoneHierarchyBuilder 构建骨骼矩阵
- 上传矩阵到 GPU (uniform)

GPU:
- 顶点着色器读取骨骼索引和权重
- 计算蒙皮变换：vertex = Σ(bone[i] * weight[i]) * vertex
- 输出变换后的顶点
```

## 总结

Circle 方块展示了完整的骨骼动画系统，包括：
- 自动动画检测和播放
- 独立的动画状态管理
- GPU 加速的蒙皮渲染
- 实时动画更新

这是一个可以直接使用的模板，你可以基于它创建更多带动画的方块和实体。
