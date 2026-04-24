# Scene Graph Transformation Fix

## 问题描述

修复前的 AssimpLoader 存在两个严重问题：

### 问题 1：物体变换不生效
在 3D 软件（Blender/Maya/3ds Max）中对物体进行旋转、移动、缩放操作后，导出的 FBX 文件在 Minecraft 中显示时**完全忽略这些变换**，物体始终显示在原点位置和默认旋转。

**原因**：渲染器直接遍历 `scene.getMeshes()` 列表，完全忽略了场景图（Scene Graph）的节点层级结构和每个节点的变换矩阵（`mTransformation`）。

### 问题 2：多个独立物体只显示一个
如果 3D 场景中有多个独立的物体（未 Combine/Join），只有第一个物体会在 Minecraft 中显示，其他物体完全消失。必须在 3D 软件中手动 Combine 所有物体才能完整显示。

**原因**：同样是因为忽略了场景图结构。FBX 文件的场景图是树形结构：
```
RootNode
├── Object1 (transformation matrix)
│   └── Mesh[0]
├── Object2 (transformation matrix)
│   └── Mesh[1]
└── Object3 (transformation matrix)
    └── Mesh[2]
```

旧代码直接遍历 mesh 数组，没有遍历节点树，导致只渲染了根节点直接关联的 mesh。

## 解决方案

### 核心改动

实现了完整的场景图递归遍历，正确处理节点层级和变换矩阵累积：

```java
private void renderNode(AssimpScene scene, AssimpNode node,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay,
                       Matrix4f parentTransform, String modelName, boolean isFirstRender) {

    // 1. 累积变换：parent * current
    Matrix4f nodeTransform = new Matrix4f(parentTransform);
    nodeTransform.mul(node.getTransformation());

    // 2. 渲染当前节点的所有 mesh
    int[] meshIndices = node.getMeshIndices();
    for (int meshIndex : meshIndices) {
        if (meshIndex >= 0 && meshIndex < scene.getMeshes().size()) {
            AssimpMesh mesh = scene.getMeshes().get(meshIndex);

            // 应用节点变换
            poseStack.pushPose();
            poseStack.last().pose().mul(nodeTransform);
            poseStack.last().normal().mul(new Matrix3f(nodeTransform));

            renderMeshWithMaterial(scene, mesh, meshIndex, poseStack, bufferSource,
                                  packedLight, packedOverlay, modelName, isFirstRender);

            poseStack.popPose();
        }
    }

    // 3. 递归渲染子节点
    for (AssimpNode child : node.getChildren()) {
        renderNode(scene, child, poseStack, bufferSource, packedLight, packedOverlay,
                  nodeTransform, modelName, isFirstRender);
    }
}
```

### 修改的文件

1. **AssimpBlockEntityRenderer.java**
   - 添加 `renderNode()` 方法：递归遍历场景图
   - 添加 `renderMeshWithMaterial()` 方法：提取原有的 mesh 渲染逻辑
   - 修改 `render()` 方法：优先使用场景图渲染，fallback 到旧逻辑

2. **AssimpItemRenderer.java**
   - 同样的改动，确保物品渲染也支持场景图变换

### 变换矩阵处理

**关键点**：
- **累积变换**：子节点的最终变换 = 父节点变换 × 当前节点变换
- **同时应用到 pose 和 normal**：
  - `poseStack.last().pose().mul(nodeTransform)` - 顶点位置变换
  - `poseStack.last().normal().mul(new Matrix3f(nodeTransform))` - 法线变换

**为什么需要累积**：
```
RootNode (identity)
└── ParentObject (translate Y+5)
    └── ChildObject (rotate 45°)
        └── Mesh
```
ChildObject 的最终变换 = translate(Y+5) × rotate(45°)，而不是单独的 rotate(45°)。

## 测试场景

修复后应该支持以下场景：

### 场景 1：单个物体的变换
```
Blender:
1. 创建 Cube
2. 旋转 45° (R Z 45)
3. 移动到 (2, 3, 0) (G X 2, G Z 3)
4. 导出 FBX

结果：Minecraft 中应该显示在正确的位置和旋转
```

### 场景 2：多个独立物体
```
Blender:
1. 创建 Cube1 at (0, 0, 0)
2. 创建 Cube2 at (2, 0, 0)
3. 创建 Sphere at (0, 2, 0)
4. 不要 Join，直接导出 FBX

结果：Minecraft 中应该显示所有 3 个物体，位置正确
```

### 场景 3：父子层级
```
Blender:
1. 创建 Parent (Empty) at (0, 5, 0)
2. 创建 Child (Cube) at (2, 0, 0)
3. 设置 Child 的 parent 为 Parent
4. 导出 FBX

结果：Cube 应该显示在 (2, 5, 0)（父子变换累积）
```

## 技术细节

### Assimp 场景图结构

```c
struct aiNode {
    aiString mName;              // 节点名称
    aiMatrix4x4 mTransformation; // 变换矩阵（相对于父节点）
    aiNode* mParent;             // 父节点
    unsigned int mNumChildren;   // 子节点数量
    aiNode** mChildren;          // 子节点数组
    unsigned int mNumMeshes;     // 关联的 mesh 数量
    unsigned int* mMeshes;       // mesh 索引数组
};
```

### 变换矩阵格式

Assimp 使用 4x4 矩阵（行主序）：
```
| a1 a2 a3 a4 |   | Xx Xy Xz Tx |
| b1 b2 b3 b4 | = | Yx Yy Yz Ty |
| c1 c2 c3 c4 |   | Zx Zy Zz Tz |
| d1 d2 d3 d4 |   | 0  0  0  1  |
```

- 左上 3x3：旋转和缩放
- 右侧列（a4, b4, c4）：平移
- 底部行：透视（通常是 0 0 0 1）

### 坐标系转换

FBX 文件可能使用不同的坐标系：
- **Blender/OpenGL**: Y-up, right-handed
- **3ds Max**: Z-up, right-handed
- **Maya**: Y-up, right-handed
- **Minecraft**: Y-up, right-handed

`CoordinateSystemConverter` 会自动处理坐标系转换，但变换矩阵需要在转换后的坐标系中应用。

## 向后兼容

代码保留了 fallback 逻辑：
```java
if (scene.getRootNode() != null) {
    renderNode(scene, scene.getRootNode(), ...);
} else {
    // 旧逻辑：直接遍历 mesh 数组
    for (int meshIndex = 0; meshIndex < scene.getMeshes().size(); meshIndex++) {
        renderMeshWithMaterial(...);
    }
}
```

如果场景没有根节点（理论上不应该发生），会使用旧的渲染方式。

## 性能影响

- **递归开销**：场景图通常不深（< 10 层），递归开销可忽略
- **矩阵乘法**：每个节点一次 4x4 矩阵乘法，现代 CPU 可以高效处理
- **内存**：每个递归层级需要一个 Matrix4f（64 字节），总开销很小

## 相关文件

- `src/main/java/cn/minerealms/assimploader/example/AssimpBlockEntityRenderer.java`
- `src/main/java/cn/minerealms/assimploader/render/AssimpItemRenderer.java`
- `src/main/java/cn/minerealms/assimploader/model/AssimpNode.java`
- `src/main/java/cn/minerealms/assimploader/model/AssimpScene.java`

## 参考资料

- [Assimp Scene Graph Documentation](http://assimp.sourceforge.net/lib_html/data.html)
- [FBX SDK Scene Graph](https://help.autodesk.com/view/FBX/2020/ENU/?guid=FBX_Developer_Help_nodes_and_scene_graph_fbx_nodes_html)
- [OpenGL Transformation Matrices](https://learnopengl.com/Getting-started/Transformations)
