# AssimpLoader 快速参考

## 当前状态 (2024-04-28)

### ✅ 已完成
- 骨骼动画系统 (SkinnedMeshRenderer)
- 节点动画系统 (SimpleMeshRenderer + 场景图映射)
- 自动识别动画类型 (hasBones() 判断)

### ⚠️ 待修复
- 世界渲染: Circle 方块显示为混凝土材质
- 物品渲染: 拉长的模型，无动画

---

## 核心文件速查

### 模型加载
- `AssimpScene.java` - 场景管理，网格到节点映射
- `AssimpNode.java` - 节点层级，存储 meshIndices
- `AssimpMesh.java` - 网格数据，hasBones() 判断

### 动画系统
- `AnimationState.java` - 动画状态管理
- `AnimationPlayer.java` - 节点动画插值 (mChannels)
- `BoneHierarchyBuilder.java` - 骨骼层级 (仅骨骼动画)

### 渲染器
- `SkinnedMeshRenderer.java` - 骨骼动画 (hasBones=true)
- `SimpleMeshRenderer.java` - 节点动画 (hasBones=false)
- `CircleBlockEntityRenderer.java` - 示例渲染器

---

## 动画类型判断

```java
if (mesh.hasBones()) {
    // 骨骼蒙皮动画
    // 使用 SkinnedMeshRenderer
    // 数据: mChannels + mBones
} else if (nodeTransforms != null) {
    // 节点动画
    // 使用 SimpleMeshRenderer + nodeTransforms
    // 数据: mChannels
    // 示例: circle.fbx
} else {
    // 静态网格
    // 使用 SimpleMeshRenderer (无变换)
}
```

---

## 场景图映射 (最新修复)

### 问题
网格名称 ("Geometry::") ≠ 节点名称 ("pCylinder1")

### 解决方案
```java
// AssimpScene 构造时
buildMeshToNodeMapping(rootNode);  // 遍历场景图
meshIndexToNodeName.put(0, "pCylinder1");  // 建立映射

// SimpleMeshRenderer 渲染时
String nodeName = scene.getNodeNameForMesh(mesh);  // 查找节点
Matrix4f transform = nodeTransforms.get(nodeName);  // 获取变换
poseStack.last().pose().mul(transform);  // 应用变换
```

---

## 工具链

### 构建
```bash
./gradlew build
# 输出: build/libs/assimploader-1.0.0-all.jar
```

### 部署
```bash
powershell.exe -ExecutionPolicy Bypass -File ./build-and-run.ps1
```

### 日志
```bash
tail -100 run/logs/latest.log
grep -i "assimploader\|animation" run/logs/latest.log
```

### Git 提交
```bash
git add <files>
git commit -m "feat: <description>

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## 测试模型

### circle.fbx
- 格式: FBX 7400 ASCII
- 网格: pCylinder1 (空名称)
- 节点: pCylinder1
- 动画: "Take 001" (100s)
- 类型: 节点动画 (channels=1, meshChannels=0)

---

## 下一步

1. 修复世界渲染 (纹理 + 变换)
2. 修复物品渲染 (自定义 ItemRenderer)
3. 测试并提交 git commit

---

*快速参考 v1.0 - 2024-04-28*
