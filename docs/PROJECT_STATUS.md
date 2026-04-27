# AssimpLoader 项目状态文档

## 项目概述

AssimpLoader 是一个 Minecraft Forge 1.20.1 模组，用于加载和渲染 FBX/OBJ 等 3D 模型格式，支持骨骼动画和节点动画。

## 当前状态 (2024-04-28)

### ✅ 已完成的功能

1. **骨骼动画系统 (Skeletal Animation)**
   - 完整的骨骼层级管理 (BoneHierarchyBuilder)
   - 蒙皮网格渲染 (SkinnedMeshRenderer)
   - 动画插值和播放 (AnimationPlayer)
   - 支持多个动画通道

2. **节点动画系统 (Node Animation)** ✅ 已修复
   - 场景图遍历和映射 (AssimpScene)
   - 节点变换应用 (SimpleMeshRenderer)
   - 自动识别网格所属节点
   - 支持 Translation/Rotation/Scaling 动画
   - 材质颜色正确渲染 (从 PhongE 材质获取 diffuseColor)
   - 顶点顺序修复 (使用退化四边形: i2, i1, i0, i0)

3. **模型加载系统**
   - 支持 STATIC 和 DYNAMIC 两种导入模式
   - 坐标系转换 (Y-up/Z-up)
   - 嵌入纹理支持
   - BakedModel 转换 (用于物品渲染)

4. **调试系统**
   - DebugManager 分类日志
   - 动画通道信息输出
   - 渲染路径追踪
   - 日志级别优化 (减少刷屏)

### ✅ 已解决的问题

1. **世界渲染 (BlockEntityRenderer) 问题** - 已修复
   - ✅ Circle 方块材质颜色正确显示 (绿色 PhongE 材质)
   - ✅ 三角面完整渲染 (通过反转顶点顺序)
   - ✅ 节点动画正确播放 (通过场景图映射)
   - ✅ 网格到节点的自动映射 (解决名称不匹配问题)

2. **日志刷屏问题** - 已优化
   - ✅ 大部分日志改为 debug 级别
   - ✅ 减少重复日志输出

### ⚠️ 已知限制

1. **物品渲染 (ItemRenderer) 限制**
   - BakedModel 是静态的，无法显示动画
   - 物品渲染使用第一帧的静态快照
   - 这是 Minecraft BakedModel 系统的固有限制

### 🔧 待优化任务

- [ ] 为 DYNAMIC 模式实现自定义物品渲染器 (可选)
- [ ] 测试多节点动画场景
- [ ] 支持顶点动画 (mMeshChannels)
- [ ] 支持变形目标动画 (mMorphMeshChannels)

---

## 核心文件索引

### 模型加载 (model/)

#### AssimpScene.java
- **路径**: `src/main/java/cn/minerealms/assimploader/model/AssimpScene.java`
- **功能**: 
  - 包装 Assimp AIScene
  - 管理网格、材质、动画、纹理
  - 场景图遍历和映射
- **关键方法**:
  - `buildMeshToNodeMapping()`: 遍历场景图建立网格索引到节点名称的映射
  - `getNodeNameForMesh(AssimpMesh)`: 查询网格所属的节点名称
  - `findNodeByName(String)`: 按名称查找节点
- **最近修改**: 添加了网格到节点的自动映射功能

#### AssimpNode.java
- **路径**: `src/main/java/cn/minerealms/assimploader/model/AssimpNode.java`
- **功能**: 
  - 包装 Assimp AINode
  - 存储节点变换矩阵
  - 存储网格索引数组
  - 管理父子关系
- **关键字段**:
  - `meshIndices`: 该节点包含的网格索引
  - `transformation`: 节点的局部变换矩阵
  - `children`: 子节点列表

#### AssimpMesh.java
- **路径**: `src/main/java/cn/minerealms/assimploader/model/AssimpMesh.java`
- **功能**: 
  - 包装 Assimp AIMesh
  - 存储顶点、法线、UV、索引
  - 存储骨骼和权重数据
- **关键方法**:
  - `hasBones()`: 判断是否有骨骼 (用于区分骨骼动画和节点动画)
  - `getIndex()`: 获取网格索引 (用于场景图映射)

#### AssimpAnimation.java
- **路径**: `src/main/java/cn/minerealms/assimploader/model/AssimpAnimation.java`
- **功能**: 
  - 包装 Assimp AIAnimation
  - 存储动画通道数据
- **关键字段**:
  - `mChannels`: 节点动画通道 (Node Animation)
  - `mMeshChannels`: 网格动画通道 (Vertex Animation)
  - `mMorphMeshChannels`: 变形目标动画 (Morph Target)

### 动画系统 (animation/)

#### AnimationState.java
- **路径**: `src/main/java/cn/minerealms/assimploader/animation/AnimationState.java`
- **功能**: 
  - 管理动画播放状态
  - 连接 AnimationPlayer 和 BoneHierarchyBuilder
  - 更新骨骼变换
- **关键方法**:
  - `update(float deltaTime)`: 更新动画时间和变换
  - `getCurrentAnimation()`: 获取当前 AnimationPlayer
  - `getBoneHierarchy(AssimpMesh)`: 获取网格的骨骼层级
- **最近修改**: 添加了动画通道信息调试日志

#### AnimationPlayer.java
- **路径**: `src/main/java/cn/minerealms/assimploader/animation/AnimationPlayer.java`
- **功能**: 
  - 处理动画播放和插值
  - 从 mChannels 读取节点动画数据
  - 插值位置、旋转、缩放
- **关键方法**:
  - `updateBoneTransforms()`: 更新节点变换
  - `interpolatePosition/Rotation/Scale()`: 插值动画关键帧
  - `getBoneTransforms()`: 获取所有节点变换 (Map<String, Matrix4f>)
- **支持的动画类型**: 
  - ✅ 节点动画 (mChannels)
  - ❌ 顶点动画 (mMeshChannels) - 未实现
  - ❌ 变形目标 (mMorphMeshChannels) - 未实现

#### BoneHierarchyBuilder.java
- **路径**: `src/main/java/cn/minerealms/assimploader/animation/BoneHierarchyBuilder.java`
- **功能**: 
  - 管理骨骼层级
  - 计算最终骨骼变换矩阵
  - 应用偏移矩阵
- **使用场景**: 仅用于骨骼蒙皮动画 (hasBones() == true)

### 渲染系统 (rendering/)

#### SkinnedMeshRenderer.java
- **路径**: `src/main/java/cn/minerealms/assimploader/rendering/SkinnedMeshRenderer.java`
- **功能**: 
  - 渲染骨骼蒙皮网格
  - 使用 OpenGL shader 应用骨骼变换
  - 支持最多 100 个骨骼
- **Shader**: `assets/assimploader/shaders/skinned_mesh.vert/frag`
- **使用场景**: 骨骼动画 (mesh.hasBones() == true)

#### SimpleMeshRenderer.java
- **路径**: `src/main/java/cn/minerealms/assimploader/rendering/SimpleMeshRenderer.java`
- **功能**: 
  - 渲染非蒙皮网格 (静态或节点动画)
  - 使用 Minecraft 的 VertexConsumer 系统
  - 应用节点变换到 PoseStack
  - 从材质获取漫反射颜色
  - 使用退化四边形渲染 (与 AssimpBlockEntityRenderer 一致)
- **关键方法**:
  - `render(mesh, poseStack, bufferSource, texture, light, overlay, scene, nodeTransforms)`
  - 通过 `scene.getNodeNameForMesh(mesh)` 查找节点名称
  - 从 `nodeTransforms` 中获取变换矩阵并应用
  - 从 `material.getDiffuseColor()` 获取颜色并应用到顶点
- **渲染细节**:
  - 顶点顺序: `i2, i1, i0, i0` (退化四边形，与 AssimpBlockEntityRenderer 一致)
  - 颜色来源: 材质的 diffuseColor (RGBA)
  - RenderType: entitySolid (不透明网格)
- **使用场景**: 
  - 静态网格 (无动画)
  - 节点动画 (mesh.hasBones() == false && 有 nodeTransforms)
- **最近修改**: 
  - 添加了场景图映射支持，自动查找网格所属节点
  - 修复了材质颜色渲染 (从硬编码白色改为材质 diffuseColor)
  - 修复了顶点顺序 (使用退化四边形避免面剔除问题)

### 示例实现 (example/)

#### CircleBlockEntityRenderer.java
- **路径**: `src/main/java/cn/minerealms/assimploader/example/CircleBlockEntityRenderer.java`
- **功能**: 
  - Circle 方块的渲染器
  - 区分骨骼动画和节点动画
  - 传递场景和节点变换到渲染器
- **渲染逻辑**:
  ```java
  for (AssimpMesh mesh : scene.getMeshes()) {
      if (mesh.hasBones()) {
          // 骨骼动画 -> SkinnedMeshRenderer
          skinnedRenderer.render(mesh, boneHierarchy, ...);
      } else {
          // 节点动画 -> SimpleMeshRenderer
          Map<String, Matrix4f> nodeTransforms = animationState.getCurrentAnimation().getBoneTransforms();
          SimpleMeshRenderer.render(mesh, poseStack, bufferSource, texture, light, overlay, scene, nodeTransforms);
      }
  }
  ```
- **最近修改**: 传递 `scene` 参数给 SimpleMeshRenderer

#### CircleBlockEntity.java
- **路径**: `src/main/java/cn/minerealms/assimploader/example/CircleBlockEntity.java`
- **功能**: 
  - Circle 方块实体
  - 管理 AnimationState
  - 每 tick 更新动画 (0.05s)

#### ExampleRegistry.java
- **路径**: `src/main/java/cn/minerealms/assimploader/example/ExampleRegistry.java`
- **功能**: 
  - 注册 Circle 方块、方块实体、物品
  - 配置 DYNAMIC 模式加载 circle.fbx

### BakedModel 系统 (render/)

#### AssimpBakedModel.java
- **路径**: `src/main/java/cn/minerealms/assimploader/render/AssimpBakedModel.java`
- **功能**: 
  - 将 Assimp 网格转换为 Minecraft BakedQuads
  - 用于物品和 GUI 渲染
- **问题**: 
  - BakedQuads 是静态的，无法显示动画
  - DYNAMIC 模式也会生成 BakedQuads (用于物品渲染)
  - 导致物品显示拉长且无动画

#### MinecraftMeshConverter.java
- **路径**: `src/main/java/cn/minerealms/assimploader/render/MinecraftMeshConverter.java`
- **功能**: 
  - 将 Assimp 网格转换为 BakedQuads
  - 使用静态顶点数据 (mesh.getVertices())
- **警告**: 蒙皮网格转换为 BakedQuads 不会应用骨骼变换

---

## 测试模型

### circle.fbx
- **路径**: `src/main/resources/assets/assimploader/models/block/circle.fbx`
- **格式**: FBX 7400 ASCII
- **内容**:
  - 网格: pCylinder1 (圆柱体)
  - 网格名称: "Geometry::" (空字符串)
  - 节点名称: "pCylinder1"
  - 动画: "Take 001" (100 秒)
  - 动画类型: 节点动画 (channels=1, meshChannels=0, morphChannels=0)
  - 初始变换: Translation(0, 0.155, 0), Rotation(-90, 0, 0), Scaling(0.157, 0.472, 0.157)
- **问题**: 网格名称和节点名称不匹配
- **解决方案**: 通过场景图映射自动查找 (已实现)

---

## 动画类型识别

### 如何判断动画类型

1. **骨骼蒙皮动画 (Skeletal Animation)**
   - 条件: `mesh.hasBones() == true`
   - 特征: 有骨骼和蒙皮权重数据
   - 渲染器: SkinnedMeshRenderer
   - 动画数据: mChannels (节点动画) + mBones (骨骼绑定)

2. **节点动画 (Node Animation)**
   - 条件: `mesh.hasBones() == false && animation.mChannels() != null`
   - 特征: 无骨骼，但有节点变换动画
   - 渲染器: SimpleMeshRenderer (应用节点变换)
   - 动画数据: mChannels (节点动画)
   - 示例: circle.fbx

3. **顶点动画 (Vertex Animation)**
   - 条件: `animation.mMeshChannels() != null`
   - 特征: 直接记录每帧的顶点位置
   - 渲染器: 未实现
   - 动画数据: mMeshChannels

4. **变形目标动画 (Morph Target)**
   - 条件: `animation.mMorphMeshChannels() != null`
   - 特征: 通过混合多个网格形状实现动画
   - 渲染器: 未实现
   - 动画数据: mMorphMeshChannels

5. **静态网格 (Static Mesh)**
   - 条件: `mesh.hasBones() == false && 无动画`
   - 特征: 无骨骼，无动画
   - 渲染器: SimpleMeshRenderer (无变换)

---

## 开发工具链

### 构建系统

#### Gradle 构建
```bash
cd H:/MinecraftMods/AssimpLoader
./gradlew build --console=plain
```
- 输出: `build/libs/assimploader-1.0.0-all.jar`
- 时间: ~2-3 分钟

#### 部署到游戏
```bash
powershell.exe -ExecutionPolicy Bypass -File ./build-and-run.ps1
```
- 功能: 
  1. 复制 JAR 到 `run/mods/`
  2. 启动 Minecraft 客户端
- 脚本路径: `H:/MinecraftMods/AssimpLoader/build-and-run.ps1`

### Git 工作流

#### 提交流程
```bash
# 1. 查看状态
git status

# 2. 添加文件
git add <files>

# 3. 提交
git commit -m "feat: <description>

<detailed description>

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"

# 4. 推送 (可选)
git push origin main
```

#### 提交消息格式
- `feat:` - 新功能
- `fix:` - 修复 bug
- `docs:` - 文档更新
- `refactor:` - 重构代码
- `test:` - 测试相关

### 日志查看

#### 游戏日志
```bash
# 最新日志
ls -lt H:/MinecraftMods/AssimpLoader/run/logs/*.log | head -1

# 查看日志
tail -100 H:/MinecraftMods/AssimpLoader/run/logs/latest.log

# 搜索 AssimpLoader 日志
grep -i "assimploader\|animation\|circle" H:/MinecraftMods/AssimpLoader/run/logs/latest.log
```

#### 调试类别
- `CORE`: 核心加载和初始化
- `ANIMATION`: 动画系统
- `RENDER`: 渲染系统
- `SHADER`: Shader 编译和使用

---

## 修复方法参考

### Circle 方块渲染修复 (2024-04-28) ✅ 已完成

**问题描述**:
1. 世界渲染: 材质颜色丢失 (显示白色而非绿色)，缺少约 1/4 的三角面
2. 物品渲染: 模型被拉伸成长条
3. 节点动画变换未正确应用

**根本原因**:
1. SimpleMeshRenderer 硬编码白色 (255, 255, 255)，未使用材质颜色
2. 顶点顺序错误导致面剔除问题
3. 网格名称 (空字符串) 和节点名称 ("pCylinder1") 不匹配

**解决方案**:

1. **材质颜色修复**:
   ```java
   // SimpleMeshRenderer.java
   // 从材质获取漫反射颜色
   int r = 255, g = 255, b = 255, a = 255;
   if (scene != null && mesh.getMaterialIndex() >= 0) {
       AssimpMaterial material = scene.getMaterials().get(mesh.getMaterialIndex());
       float[] diffuseColor = material.getDiffuseColor();
       r = (int) (diffuseColor[0] * 255);
       g = (int) (diffuseColor[1] * 255);
       b = (int) (diffuseColor[2] * 255);
       a = (int) (diffuseColor[3] * 255);
   }
   
   // 应用到顶点
   consumer.vertex(pose, x, y, z)
       .color(r, g, b, a)  // 使用材质颜色
       .uv(u, v)
       .overlayCoords(packedOverlay)
       .uv2(packedLight)
       .normal(normal, nx, ny, nz)
       .endVertex();
   ```

2. **顶点顺序修复**:
   ```java
   // SimpleMeshRenderer.java
   // 使用退化四边形: i2, i1, i0, i0 (与 AssimpBlockEntityRenderer 一致)
   for (int i = 0; i < indices.length; i += 3) {
       int i0 = indices[i];
       int i1 = indices[i + 1];
       int i2 = indices[i + 2];
       
       // 反转顶点顺序
       addVertex(consumer, pose, normal, vertices, normals, texCoords,
           i2, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);
       addVertex(consumer, pose, normal, vertices, normals, texCoords,
           i1, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);
       addVertex(consumer, pose, normal, vertices, normals, texCoords,
           i0, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);
       addVertex(consumer, pose, normal, vertices, normals, texCoords,
           i0, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);
   }
   ```

3. **场景图映射** (已在之前完成):
   ```java
   // AssimpScene.java
   private void buildMeshToNodeMapping() {
       if (rootNode != null) {
           buildMeshToNodeMappingRecursive(rootNode);
       }
   }
   
   private void buildMeshToNodeMappingRecursive(AssimpNode node) {
       int[] meshIndices = node.getMeshIndices();
       for (int meshIndex : meshIndices) {
           meshIndexToNodeName.put(meshIndex, node.getName());
       }
       for (AssimpNode child : node.getChildren()) {
           buildMeshToNodeMappingRecursive(child);
       }
   }
   ```

**相关文件**:
- `SimpleMeshRenderer.java`: 材质颜色和顶点顺序修复
- `AssimpScene.java`: 场景图映射 (已完成)
- `CircleBlockEntityRenderer.java`: 传递 scene 参数 (已完成)

**测试结果**: ✅ 通过
- 世界渲染: 正确的绿色材质，完整的三角面，动画正常播放
- 物品渲染: 静态快照 (BakedModel 限制)

**提交消息**:
```
fix(render): Fix Circle block rendering issues

- Fix material color rendering in SimpleMeshRenderer
  * Get diffuseColor from AssimpMaterial instead of hardcoded white
  * Apply RGBA color to each vertex
  
- Fix face culling issue with reversed vertex order
  * Use degenerate quad: i2, i1, i0, i0
  * Consistent with AssimpBlockEntityRenderer
  
- Reduce debug log spam
  * Change most logs from info to debug level
  
Fixes:
- World rendering: correct green material color
- World rendering: complete triangle faces (no missing faces)
- Node animation: correctly applied via scene graph mapping

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

### 场景图映射修复 (已完成)

**问题**: 网格名称和节点名称不匹配，导致无法找到节点变换

**解决方案**:
1. 在 `AssimpScene` 构造函数中遍历场景图
2. 建立 `meshIndexToNodeName` 映射表
3. 在 `SimpleMeshRenderer` 中使用 `scene.getNodeNameForMesh(mesh)` 查找节点名称
4. 从 `nodeTransforms` 中获取变换矩阵

**相关文件**:
- `AssimpScene.java`: 添加 `buildMeshToNodeMapping()` 和 `getNodeNameForMesh()`
- `SimpleMeshRenderer.java`: 修改 `render()` 方法，添加 `scene` 参数
- `CircleBlockEntityRenderer.java`: 传递 `scene` 参数

**提交消息**:
```
feat(animation): Add scene graph mapping for node animations

- Add mesh-to-node mapping in AssimpScene
- Traverse scene graph to build meshIndex -> nodeName map
- Update SimpleMeshRenderer to use scene graph for node lookup
- Fix node animation transform application

This fixes the issue where mesh names (empty string) don't match
node names (e.g., "pCylinder1"), preventing node transforms from
being applied correctly.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
```

### 动画通道调试 (已完成)

**问题**: 不清楚模型使用的动画类型

**解决方案**:
1. 在 `AnimationState` 构造函数中添加调试日志
2. 输出 `channels`, `meshChannels`, `morphChannels` 数量
3. 在 `AnimationPlayer` 中输出节点动画关键帧信息

**相关文件**:
- `AnimationState.java`: 添加动画通道信息日志
- `AnimationPlayer.java`: 添加节点动画关键帧日志

---

## 下一步计划

### 优先级 1: 修复世界渲染
- [ ] 检查游戏日志，确认 SimpleMeshRenderer 是否被调用
- [ ] 验证节点变换是否正确应用
- [ ] 检查纹理加载和绑定
- [ ] 测试 circle.fbx 在世界中的渲染效果

### 优先级 2: 修复物品渲染
- [ ] 研究 Minecraft 的 ItemRenderer 和 ItemOverrides 系统
- [ ] 为 DYNAMIC 模式实现自定义物品渲染器
- [ ] 或者：为物品渲染生成静态快照 (第一帧)

### 优先级 3: 支持更多动画类型
- [ ] 实现顶点动画 (mMeshChannels) 支持
- [ ] 实现变形目标动画 (mMorphMeshChannels) 支持

### 优先级 4: 优化和清理
- [ ] 减少调试日志输出
- [ ] 优化性能 (缓存、批处理)
- [ ] 添加单元测试

---

## 已知限制

1. **BakedModel 系统**
   - BakedQuads 是静态的，无法显示动画
   - 物品渲染使用 BakedModel，导致无法显示动画效果

2. **动画类型支持**
   - ✅ 骨骼蒙皮动画 (Skeletal Animation)
   - ✅ 节点动画 (Node Animation)
   - ❌ 顶点动画 (Vertex Animation)
   - ❌ 变形目标动画 (Morph Target)

3. **渲染限制**
   - 骨骼数量限制: 最多 100 个骨骼
   - 渲染距离: 64 方块

---

## 参考资料

### Assimp 文档
- [Assimp Documentation](http://assimp.sourceforge.net/lib_html/index.html)
- [AIScene Structure](http://assimp.sourceforge.net/lib_html/structai_scene.html)
- [AIAnimation Structure](http://assimp.sourceforge.net/lib_html/structai_animation.html)

### Minecraft Forge 文档
- [Block Entity Renderer](https://docs.minecraftforge.net/en/latest/rendering/modelloaders/bakedmodel/)
- [Custom Model Loaders](https://docs.minecraftforge.net/en/latest/rendering/modelloaders/)

### FBX 格式
- [FBX SDK Documentation](https://help.autodesk.com/view/FBX/2020/ENU/)
- FBX 7400 格式支持 ASCII 和 Binary

---

## 联系和支持

- **项目路径**: `H:/MinecraftMods/AssimpLoader`
- **Minecraft 版本**: 1.20.1
- **Forge 版本**: 47.3.0
- **Assimp 版本**: 通过 LWJGL 3.x 绑定

---

*最后更新: 2024-04-28*
*文档版本: 1.0*
