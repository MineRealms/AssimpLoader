# 动画系统使用指南

## 概述

本页保留动画集成的示例设计，代码片段不是可直接复制的完整 Mod。当前实现与验证边界见 [加载修复](LOADING_FIXES.md) 和 [修复与验收](FIXES_2026-11-13.md)；美术导出的单位、UV、PBR、层级和枢轴要求见 [资产接入规范](PBR_ASSET_SPEC.md)。每个方块/物品应使用独立动画状态，不修改共享缓存场景。

## 系统架构

```
FBX 文件 (带骨骼和动画)
    ↓
AssimpModelLoader (加载模型)
    ↓
AssimpScene (包含网格、骨骼、动画)
    ↓
AnimationState (每个实例独立)
    ↓
AnimationPlayer (播放动画，插值关键帧)
    ↓
BoneHierarchyBuilder (计算骨骼变换矩阵)
    ↓
SkinnedMeshRenderer (GPU 蒙皮渲染)
```

## 如何使用

### 1. 准备 FBX 文件

你的 FBX 文件需要包含：
- **网格 (Mesh)**: 3D 模型的几何体
- **骨骼 (Bones)**: 骨骼层次结构
- **蒙皮权重 (Skin Weights)**: 每个顶点受哪些骨骼影响
- **动画 (Animations)**: 一个或多个动画片段

**推荐设置**:
- 导出格式: Assimp 可读的二进制 FBX；当前 Blender 导出的 FBX 7400 已验证，无需所谓 FBX 2020 以上
- 包含: Mesh, Skeleton, Animation, Skin Weights
- 坐标系: Y-up (会自动转换到 Minecraft 坐标系)

### 2. 放置 FBX 文件

将 FBX 文件放到以下位置之一：

#### 方式 A: 资源包 (Resource Pack)
```
assets/assimploader/models/your_model.fbx
```

#### 方式 B: 配置的外部覆盖目录

设置 `hotreload.resourcePath`，目录结构为 `<root>/<namespace>/<resourcePath>`。例如：

```
<root>/assimploader/models/your_model.fbx
```

客户端执行 `/assimploader reload`，或配置 `hotreload.autoReloadEnabled` 开启监听。资源包修改可按 F3+T；注册代码仍需重启。详见 [加载与重载](LOADING_FIXES.md)。有动画的 geometry JSON 必须使用 `"mode": "dynamic"`。

### 3. 创建方块 (Block)

方块定义示意：

```java
public class MyAnimatedBlock extends BaseEntityBlock {
    
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // 重要！
    }
    
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MyAnimatedBlockEntity(pos, state);
    }
    
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 只在客户端 tick 动画
        return level.isClientSide ? createTickerHelper(type, YOUR_BLOCK_ENTITY_TYPE.get(),
                (lvl, pos, st, blockEntity) -> blockEntity.tick()) : null;
    }
}
```

### 4. 创建 BlockEntity

方块实体定义示意：

```java
public class MyAnimatedBlockEntity extends BlockEntity {
    
    private AnimationState animationState;
    private boolean initialized = false;
    
    public MyAnimatedBlockEntity(BlockPos pos, BlockState state) {
        super(YOUR_BLOCK_ENTITY_TYPE.get(), pos, state);
    }
    
    private void initializeAnimation() {
        if (!initialized) {
            // 模型名称 (不带扩展名)
            String modelName = "your_model";
            
            animationState = new AnimationState(modelName);
            animationState.initialize();
            
            // 可选: 播放特定动画
            // animationState.playAnimation("idle");
            
            initialized = true;
        }
    }
    
    public void tick() {
        if (!initialized) {
            initializeAnimation();
        }
        
        if (animationState != null && animationState.isInitialized()) {
            // 更新动画 (1 tick = 0.05 秒，20 TPS)
            animationState.update(0.05f);
        }
    }
    
    public AnimationState getAnimationState() {
        if (!initialized) {
            initializeAnimation();
        }
        return animationState;
    }
}
```

### 5. 创建 Renderer

参考 `TestModelBlockEntityRenderer.java`:

```java
public class MyAnimatedBlockEntityRenderer extends BlockEntityRenderer<MyAnimatedBlockEntity> {
    
    private SkinnedMeshRenderer skinnedRenderer;
    private boolean skinnedRendererInitialized = false;
    
    public MyAnimatedBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public void render(MyAnimatedBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        
        AnimationState animationState = blockEntity.getAnimationState();
        
        if (animationState == null || !animationState.isInitialized()) {
            return;
        }
        
        // 初始化渲染器
        if (!skinnedRendererInitialized) {
            skinnedRenderer = new SkinnedMeshRenderer();
            try {
                skinnedRenderer.initialize();
                skinnedRendererInitialized = true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize skinned mesh renderer", e);
            }
        }
        
        AssimpScene scene = animationState.getScene();
        if (scene == null) {
            return;
        }
        
        poseStack.pushPose();
        
        // 应用变换 (位置、旋转、缩放)
        poseStack.translate(0.5, 0, 0.5); // 居中
        
        // 计算光照
        float lightLevel = (packedLight & 0xFFFF) / 240.0f;
        
        // 渲染每个蒙皮网格
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
                if (boneHierarchy != null) {
                    Matrix4f mvp = new Matrix4f(poseStack.last().pose());
                    Matrix4f mv = new Matrix4f(poseStack.last().pose());
                    
                    // 纹理
                    ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
                    
                    // 渲染
                    skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                            Minecraft.getInstance().getTextureManager(), lightLevel);
                }
            }
        }
        
        poseStack.popPose();
    }
}
```

### 6. 注册

```java
public class ExampleRegistry {
    
    // Block
    public static final RegistryObject<Block> MY_ANIMATED_BLOCK = BLOCKS.register("my_animated_block",
            () -> new MyAnimatedBlock(BlockBehaviour.Properties.of()));
    
    // BlockEntity
    public static final RegistryObject<BlockEntityType<MyAnimatedBlockEntity>> MY_ANIMATED_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("my_animated_block_entity",
                    () -> BlockEntityType.Builder.of(MyAnimatedBlockEntity::new, MY_ANIMATED_BLOCK.get()).build(null));
    
    // Renderer (客户端)
    @OnlyIn(Dist.CLIENT)
    public static void registerRenderers() {
        BlockEntityRenderers.register(MY_ANIMATED_BLOCK_ENTITY.get(), MyAnimatedBlockEntityRenderer::new);
    }
}
```

## 动画控制

### 播放特定动画

```java
// 在 BlockEntity 中
animationState.playAnimation("walk");
animationState.playAnimation("idle");
animationState.playAnimation("attack");
```

### 获取可用动画列表

```java
String[] animations = animationState.getAvailableAnimations();
for (String anim : animations) {
    System.out.println("Available animation: " + anim);
}
```

### 控制播放速度

```java
AnimationPlayer player = animationState.getCurrentAnimation();
if (player != null) {
    player.setSpeed(2.0f); // 2倍速
    player.setSpeed(0.5f); // 0.5倍速
}
```

### 循环/单次播放

```java
AnimationPlayer player = animationState.getCurrentAnimation();
if (player != null) {
    player.setLooping(true);  // 循环播放
    player.setLooping(false); // 播放一次后停止
}
```

## 多个动画示例

如果你的 FBX 文件包含多个动画（例如 idle, walk, run），你可以根据条件切换：

```java
public void tick() {
    if (!initialized) {
        initializeAnimation();
    }
    
    if (animationState != null && animationState.isInitialized()) {
        // 根据条件切换动画
        if (isPlayerNearby()) {
            animationState.playAnimation("wave");
        } else {
            animationState.playAnimation("idle");
        }
        
        animationState.update(0.05f);
    }
}
```

## 独立动画状态

**重要**: 每个 BlockEntity 都有自己独立的 AnimationState，这意味着：

- ✅ 放置多个相同方块，每个都可以播放不同的动画
- ✅ 每个方块的动画进度独立
- ✅ 可以同时播放不同的动画片段

例如：
```
方块 A: 播放 "idle" 动画，进度 50%
方块 B: 播放 "walk" 动画，进度 20%
方块 C: 播放 "idle" 动画，进度 80%
```

## 性能优化

### 1. VBO 缓存
SkinnedMeshRenderer 会自动缓存每个网格的 VBO，相同模型的多个实例共享 VBO。

### 2. 动画更新频率
通常每 tick 更新一次 (20 TPS)。`partialTick` 是当前 tick 内的插值位置，不是每帧经过的时间；不要每帧再累计 `0.05 * partialTick`，否则动画速度随帧率变化。需要平滑显示时在独立采样状态插值，或使用真实帧时间，并避免与 tick 重复推进同一播放器。

### 3. 按需初始化
AnimationState 使用懒加载，只在第一次访问时初始化，节省内存。

## 调试

### 启用调试日志

```java
DebugManager.setEnabled(DebugCategory.ANIMATION, true);
DebugManager.setEnabled(DebugCategory.RENDER, true);
```

### 查看骨骼层次结构

```java
BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
if (boneHierarchy != null) {
    boneHierarchy.printHierarchy();
}
```

### 查看可用动画

```java
String[] animations = animationState.getAvailableAnimations();
System.out.println("Available animations: " + Arrays.toString(animations));
```

## 常见问题

### Q: 动画不播放？
A: 检查：
1. FBX 文件是否包含动画数据
2. AnimationState 是否已初始化 (`animationState.initialize()`)
3. BlockEntity 是否注册了 ticker
4. 是否在客户端调用 `tick()`

### Q: 模型显示但不动？
A: 检查：
1. 是否调用了 `animationState.update(deltaTime)`
2. 动画是否正在播放 (`player.isPlaying()`)
3. 骨骼层次结构是否正确构建

### Q: 模型变形错误？
A: 检查：
1. FBX 导出设置是否正确
2. 蒙皮权重是否正确绑定
3. 骨骼偏移矩阵是否正确

### Q: 性能问题？
A: 优化建议：
1. 减少骨骼数量（< 100 个骨骼）
2. 减少顶点数量
3. 使用 LOD (Level of Detail)
4. 限制同时渲染的动画实例数量

## 示例项目

当前展示注册与资源链参考 `showcase/ShowcaseRegistry.java`、`ShowcaseBlockEntity.java` 和 `ShowcaseBlockEntityRenderer.java`。它们用于静止展示，并非完整骨骼动画示例；原有 TestModel / CNC / CCB 注册类不再存在。动画实例接入应参考 `AnimationState` 与 `render/base/AnimatedBlockEntityRenderer` 的实际 API。

## 历史规划

以下为早期规划，不代表当前缺失清单。动画混合、IK 等已有实现和数据测试，实际可用范围以修复记录为准。

- [ ] 添加动画混合 (Animation Blending)
- [ ] 添加动画过渡 (Animation Transition)
- [ ] 添加动画事件系统 (Animation Events)
- [ ] 添加 IK (Inverse Kinematics) 支持
- [ ] 添加动画层 (Animation Layers)
- [ ] 添加动画状态机 (Animation State Machine)

## 技术文档

更多技术细节请参考：
- `SKELETAL_ANIMATION_ANALYSIS.md` - 骨骼动画技术分析
- `MINECRAFT_ANIMATION_GUIDE.md` - Minecraft 动画集成指南
- `IK_FBX_BAKING_ANALYSIS.md` - IK 和 FBX 烘焙分析
