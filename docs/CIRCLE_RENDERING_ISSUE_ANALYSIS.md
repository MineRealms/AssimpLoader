# Circle 方块渲染问题分析与修复方案

## 项目信息
- **项目名称**: AssimpLoader - Minecraft 1.20.1 Forge Mod
- **功能**: 使用 Assimp 加载 FBX 等 3D 模型格式，支持骨骼动画
- **客户端目录**: `G:/MinecraftGames/CTNH-BaopuEdition/.minecraft/versions/Create New Horizon/`
- **项目目录**: `H:/MinecraftMods/AssimpLoader/`
- **构建工具**: Gradle
- **编译命令**: `./gradlew compileJava`
- **构建部署**: `build-and-run.ps1`
- **工作流程**: 每个 Phase 完成后编译、测试、提交 git

## 当前问题

### 症状
1. **GUI/物品栏渲染**: Circle 方块显示为严重拉伸的绿色圆柱体
2. **掉落物渲染**: 显示为压扁的绿色管子
3. **世界方块渲染**: 完全透明，什么都看不到

### 日志分析

#### 关键日志 1: 模型加载成功
```
[04:42:13] Loading model from resource pack: assimploader:models/block/circle.fbx with mode: dynamic
[04:42:13] Loading model from memory: models/block/circle.fbx, size: 70800 bytes, mode: DYNAMIC
[04:42:13] Successfully loaded resource pack model: circle in dynamic mode
[04:42:13] Building BakedModel for 'assimploader:block/circle': mode=DYNAMIC, 1 meshes, 1 materials
```
✅ 模型以 DYNAMIC 模式正确加载

#### 关键日志 2: AnimationState 有骨骼数据
```
[04:42:37] Initializing AnimationState for model: circle
[04:42:37] CircleBlock playing animation: 'Take 001' (total animations: 1)
[04:42:37] AnimationState update: animation='Take 001', meshes with bones=1.2000000178813934
```
✅ AnimationState 检测到骨骼数据（格式化错误但数据存在）

#### 关键日志 3: CircleBlockEntityRenderer 没有骨骼
```
[04:43:20] CircleBlockEntityRenderer: No meshes rendered (total meshes: 1, meshes with bones: 0)
```
❌ **核心问题**: Renderer 获取的 mesh 没有骨骼数据！

### 根本原因

**模型缓存冲突**: 存在两个不同的 `AssimpScene` 实例：

1. **AssimpBakedModel 使用的 Scene** (通过 `AssimpGeometryLoader` 加载)
   - 以 DYNAMIC 模式加载
   - 保留了骨骼数据
   - 但被转换为 BakedQuads，骨骼变换未应用 → GUI/物品栏变形

2. **AnimationState 使用的 Scene** (通过 `AssimpModelLoader.getCachedModel()` 加载)
   - 可能从缓存获取了 STATIC 模式的模型
   - 或者是不同的加载路径
   - 导致 `CircleBlockEntityRenderer` 获取的 mesh 没有骨骼 → 世界方块透明

### 缓存键冲突

`AssimpModelLoader` 的缓存使用简单的模型名称作为键（如 "circle"），但没有区分 STATIC 和 DYNAMIC 模式。这导致：
- 第一次加载（DYNAMIC 模式）缓存为 "circle"
- 第二次加载（STATIC 模式）直接返回缓存的 DYNAMIC 模型
- 或者反过来，导致模式不匹配

## 修复方案

### Phase 1: 修复缓存键以区分模式
**目标**: 让 STATIC 和 DYNAMIC 模式的模型使用不同的缓存键

**修改文件**: `AssimpModelLoader.java`

**步骤**:
1. 修改 `updateModelCache()` 方法，缓存键包含模式信息
2. 修改 `getCachedModel()` 方法，查找时考虑模式
3. 格式: `"modelName:STATIC"` 或 `"modelName:DYNAMIC"`

### Phase 2: 确保 AnimationState 使用正确的模型
**目标**: AnimationState 必须获取 DYNAMIC 模式的模型

**修改文件**: `AnimationState.java`, `CircleBlockEntity.java`

**步骤**:
1. AnimationState 初始化时，明确指定使用 DYNAMIC 模式
2. 或者直接从 AssimpBakedModel 获取 Scene 引用

### Phase 3: 禁用 DYNAMIC 模式的 BakedQuad 转换
**目标**: DYNAMIC 模式的模型不应该转换为 BakedQuads

**修改文件**: `AssimpBakedModel.java`

**步骤**:
1. 检查 Scene 的 importMode
2. 如果是 DYNAMIC 模式，返回空的 quads 列表
3. GUI/物品栏渲染应该使用 `AssimpItemRenderer`（已存在）

### Phase 4: 修复日志格式化
**目标**: 修复 AnimationState 日志中的格式化错误

**修改文件**: `AnimationState.java`

**步骤**:
1. 将 `{:.2f}` 改为 `{}`（Java 的 SLF4J 不支持 Python 风格格式化）

### Phase 5: 测试和验证
**步骤**:
1. 编译: `./gradlew compileJava`
2. 构建部署: `build-and-run.ps1`
3. 测试 Circle 方块在三种场景下的渲染
4. 检查日志确认骨骼数据正确加载
5. Git 提交

## 预期结果

修复后：
- ✅ GUI/物品栏: 使用 AssimpItemRenderer，正确渲染带动画的模型
- ✅ 掉落物: 使用 EntityRenderer，正确渲染
- ✅ 世界方块: 使用 CircleBlockEntityRenderer + SkinnedMeshRenderer，正确渲染动画
- ✅ 动画播放: 骨骼变换正确应用，模型动起来

## 技术细节

### 渲染路径对比

| 场景 | 渲染器 | 数据源 | 是否支持动画 |
|------|--------|--------|--------------|
| GUI/物品栏 | AssimpItemRenderer | BakedModel.getScene() | ✅ 应该支持 |
| 掉落物 | ItemEntityRenderer | BakedModel | ❌ 使用 BakedQuads |
| 世界方块 | CircleBlockEntityRenderer | AnimationState.getScene() | ✅ 应该支持 |

### 模型加载模式

| 模式 | Assimp 标志 | 骨骼数据 | 动画数据 | 用途 |
|------|-------------|----------|----------|------|
| STATIC | aiProcess_PreTransformVertices | ❌ 移除 | ❌ 移除 | 静态模型 |
| DYNAMIC | 不包含 PreTransformVertices | ✅ 保留 | ✅ 保留 | 动画模型 |

## 下一步行动

按照 Phase 1-5 的顺序执行修复，每个 Phase 完成后编译测试。
