# Minecraft 中使用 FBX 动画完整指南

## 快速开始

### 1. 准备 FBX 文件

将 FBX 文件放在 `models/` 目录：
```
models/
  └── character.fbx  (包含模型 + 动画)
```

**FBX 要求**：
- 包含骨骼层级（Armature/Skeleton）
- 包含一个或多个动画（idle, walk, run 等）
- 推荐使用纯关键帧动画（不要在 DCC 工具中使用 IK 约束）

### 2. 加载模型和动画

```java
// 在 Renderer 或 BlockEntity 中
AssimpModelLoader loader = new AssimpModelLoader();
AssimpScene scene = loader.loadModel(Path.of("models/character.fbx"), true);

// 提取所有动画
Map<String, AnimationPlayer> animations = new HashMap<>();
for (AssimpAnimation anim : scene.getAnimations()) {
    AnimationPlayer player = new AnimationPlayer(anim);
    player.setLooping(true);
    animations.put(anim.getName(), player);
}

// 播放默认动画
AnimationPlayer idleAnim = animations.get("idle");
idleAnim.play();
```

### 3. 每帧更新动画

```java
@Override
public void render(..., float partialTick, ...) {
    // 更新动画（Minecraft tick 转秒）
    idleAnim.update(partialTick / 20.0);
    
    // 渲染模型（使用更新后的骨骼变换）
    renderScene(scene, poseStack, bufferSource, packedLight);
}
```

## 动画状态机

### 根据实体状态切换动画

```java
public class MyEntityRenderer extends EntityRenderer<MyEntity> {
    private Map<String, AnimationPlayer> animations;
    private AnimationPlayer currentAnimation;
    
    private void updateAnimation(MyEntity entity) {
        String state = getAnimationState(entity);
        AnimationPlayer desired = animations.get(state);
        
        if (desired != currentAnimation) {
            if (currentAnimation != null) {
                currentAnimation.pause();
            }
            currentAnimation = desired;
            currentAnimation.play();
        }
    }
    
    private String getAnimationState(MyEntity entity) {
        if (entity.isInWater()) return "swim";
        if (entity.isSprinting()) return "run";
        if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6) return "walk";
        return "idle";
    }
}
```

## 添加运行时 IK

### 方式 1：使用预设（推荐）

```java
// 初始化 IK
idleAnim.initializeIK(rootBone);

// 使用人形预设
IKPresets.HumanoidIK humanoid = IKPresets.createHumanoid(
    idleAnim.getIKManager()
);

// 配置头部追踪
humanoid.head.setMaxAngle(70);
humanoid.head.setWeight(0.5f); // 50% IK + 50% 关键帧

// 配置脚部地面对齐
humanoid.feet.setGroundRaycast(pos -> raycastGround(pos));
humanoid.feet.enableLeftFoot();
humanoid.feet.enableRightFoot();

// 每帧更新
humanoid.head.setTarget(playerPosition);
humanoid.head.update(deltaTime);
humanoid.feet.update(deltaTime);
```

### 方式 2：手动创建控制器

```java
// 初始化 IK
idleAnim.initializeIK(rootBone);
IKManager ikManager = idleAnim.getIKManager();

// 创建头部控制器
HeadLookAtController head = new HeadLookAtController(
    ikManager, "Neck", "Head"
);
head.setTarget(targetPosition);
head.update(deltaTime);

// 创建手部控制器
HandIKController hands = new HandIKController(
    ikManager,
    "LeftShoulder", "LeftElbow", "LeftHand",
    "RightShoulder", "RightElbow", "RightHand"
);
hands.enableRightHand();
hands.setRightHandTarget(itemPosition, itemRotation);

// 创建脚部控制器
FootIKController feet = new FootIKController(
    ikManager,
    "LeftHip", "LeftKnee", "LeftAnkle",
    "RightHip", "RightKnee", "RightAnkle"
);
feet.setGroundRaycast(pos -> raycastGround(pos));
feet.enableLeftFoot();
feet.enableRightFoot();
feet.update(deltaTime);
```

## 常见场景

### 场景 1：NPC 头部追踪玩家

```java
// 在 render() 中
Player nearestPlayer = level.getNearestPlayer(entity, 10.0);
if (nearestPlayer != null) {
    Vec3 eyePos = nearestPlayer.getEyePosition(partialTick);
    headIK.setTarget(new Vector3f(
        (float) eyePos.x,
        (float) eyePos.y,
        (float) eyePos.z
    ));
    headIK.update(partialTick / 20.0f);
}
```

### 场景 2：角色在不平地面行走

```java
// 配置脚部 IK
footIK.setGroundRaycast(footPos -> {
    // 射线检测地面
    BlockHitResult hit = level.clip(new ClipContext(
        new Vec3(footPos.x, footPos.y + 2, footPos.z),
        new Vec3(footPos.x, footPos.y - 2, footPos.z),
        ClipContext.Block.COLLIDER,
        ClipContext.Fluid.NONE,
        entity
    ));
    
    if (hit.getType() == HitResult.Type.BLOCK) {
        Vec3 hitPos = hit.getLocation();
        return new Vector3f((float) hitPos.x, (float) hitPos.y, (float) hitPos.z);
    }
    
    return footPos; // 没有碰撞，保持原位
});

footIK.setFootOffset(0.05f); // 5cm 脚跟高度
footIK.setLeftFootWeight(0.8f); // 80% IK, 20% 关键帧
footIK.setRightFootWeight(0.8f);
footIK.enableLeftFoot();
footIK.enableRightFoot();

// 每帧更新
if (entity.onGround()) {
    footIK.update(deltaTime);
}
```

### 场景 3：角色抓取物品

```java
// 当拾取物品时
if (entity.isHoldingItem()) {
    handIK.enableRightHand();
    
    // 计算物品位置（实体前方）
    Vec3 itemPos = entity.position()
        .add(entity.getLookAngle().scale(0.5))
        .add(0, 1.0, 0);
    
    handIK.setRightHandTarget(
        new Vector3f((float) itemPos.x, (float) itemPos.y, (float) itemPos.z),
        null
    );
    
    // 平滑过渡到 IK（0.5 秒）
    handIK.smoothReachRight(deltaTime, 1.0f);
} else {
    // 松开物品，平滑回到关键帧
    handIK.smoothReachRight(deltaTime, 0.0f);
}
```

### 场景 4：混合多个动画

```java
// 上半身播放攻击动画，下半身播放行走动画
AnimationPlayer attackAnim = animations.get("attack");
AnimationPlayer walkAnim = animations.get("walk");

// 方案 A：使用 AnimationBlender（如果实现了）
AnimationBlender blender = new AnimationBlender();
blender.addLayer(walkAnim, 1.0f, "LeftHip", "RightHip"); // 下半身
blender.addLayer(attackAnim, 1.0f, "Spine", "LeftShoulder", "RightShoulder"); // 上半身
blender.update(deltaTime);

// 方案 B：手动控制 IK 权重
walkAnim.update(deltaTime);
attackAnim.update(deltaTime);
// 然后在渲染时选择性应用骨骼变换
```

## 性能优化

### 1. 按需启用 IK

```java
// 只在需要时启用 IK
if (entity.distanceTo(player) < 10.0) {
    headIK.setEnabled(true);
} else {
    headIK.setEnabled(false); // 远距离禁用，节省性能
}
```

### 2. 降低更新频率

```java
// 每 3 帧更新一次 IK（60fps → 20fps）
if (entity.tickCount % 3 == 0) {
    footIK.update(deltaTime * 3);
}
```

### 3. 使用低成本求解器

```java
// TwoBoneIK (~0.01ms) 比 FABRIK (~0.05-0.2ms) 快 5-20 倍
// 对于手臂/腿部，优先使用 TwoBoneIK
IKChain armChain = ikManager.createChain(
    "arm",
    Arrays.asList("Shoulder", "Elbow", "Hand"),
    IKSolver.Type.TWO_BONE  // 快速求解器
);
```

## 调试技巧

### 1. 检查动画是否加载

```java
System.out.println("Loaded animations:");
for (AssimpAnimation anim : scene.getAnimations()) {
    System.out.println("  - " + anim.getName() + 
        " (" + anim.getDuration() + "s, " + 
        anim.getTicksPerSecond() + " tps)");
}
```

### 2. 检查骨骼层级

```java
Skeleton skeleton = ikManager.getSkeleton();
System.out.println("Available bones:");
// 需要实现 getAllBones() 方法
```

### 3. 可视化 IK 目标

```java
// 在 render() 中绘制调试球体
if (Minecraft.getInstance().options.renderDebug) {
    Vector3f target = headIK.getChain().getTarget();
    renderDebugSphere(poseStack, target, 0.1f, 0xFF0000); // 红色球体
}
```

## 常见问题

### Q: 动画播放速度不对？
A: 检查 `ticksPerSecond` 和 `duration`：
```java
System.out.println("TPS: " + anim.getTicksPerSecond());
System.out.println("Duration: " + anim.getDuration());

// 调整播放速度
player.setSpeed(2.0f); // 2x 速度
```

### Q: IK 不生效？
A: 检查以下几点：
1. 是否调用了 `initializeIK(rootBone)`？
2. 骨骼名称是否正确？
3. IK 是否启用？`ikManager.isEnabled()`
4. blend weight 是否 > 0？`chain.getWeight()`

### Q: 骨骼名称不匹配？
A: 使用自定义配置：
```java
HumanoidConfig config = new HumanoidConfig();
config.head = "Head_Bone"; // 你的骨骼名
config.leftHand = "L_Hand";
config.rightHand = "R_Hand";
IKPresets.HumanoidIK humanoid = IKPresets.createHumanoid(ikManager, config);
```

### Q: FBX 已烘焙 IK，会冲突吗？
A: 不会冲突，但可能不自然。解决方案：
```java
// 降低运行时 IK 权重，保留更多关键帧
footIK.setLeftFootWeight(0.3f); // 30% IK, 70% 关键帧
```

## 完整示例

参考文件：
- `AnimatedEntityRenderer.java` - 完整的实体渲染器
- `TestAnimatedEntity.java` - 示例实体类
- `AssimpBlockEntityRenderer.java` - BlockEntity 渲染参考

## 总结

**基本流程**：
1. 加载 FBX → `AssimpModelLoader.loadModel()`
2. 提取动画 → `scene.getAnimations()`
3. 创建播放器 → `new AnimationPlayer(anim)`
4. 每帧更新 → `player.update(deltaTime)`
5. 渲染模型 → 使用更新后的骨骼变换

**可选 IK**：
1. 初始化 → `player.initializeIK(rootBone)`
2. 创建控制器 → `IKPresets.createHumanoid()` 或手动创建
3. 设置目标 → `controller.setTarget()`
4. 每帧更新 → `controller.update(deltaTime)`

**性能**：
- 纯关键帧：~0.1ms
- 关键帧 + IK：~0.15-0.3ms（取决于 IK 链数量）
