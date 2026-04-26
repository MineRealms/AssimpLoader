# Photon Integration User Guide

**AssimpLoader ↔ Photon 集成功能使用指南**

版本: 1.0  
日期: 2026-04-26

---

## 概述

AssimpLoader 现在支持将 FBX/OBJ/GLTF 模型的 **Mesh** 和 **Material** 数据导入到 Photon 编辑器中，让你可以：

- ✅ 从 3D 模型表面发射粒子
- ✅ 使用模型的材质纹理和颜色
- ✅ 创建基于真实几何体的粒子特效

---

## 前置要求

### 必需模组

1. **AssimpLoader** - 本模组（提供 FBX 加载和转换功能）
2. **Photon** - 粒子编辑器模组
   - 版本: 1.1.17+ (1.20.1)
   - 下载: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/photon)
3. **LDLib** - Photon 的依赖库
   - 自动随 Photon 安装

### 可选但推荐

- **ModernUI** - 提供更好的 UI 体验（AssimpLoader 已包含）

---

## 功能 1: 导入 FBX Mesh

### 使用步骤

1. **启动 Minecraft** 并确保 Photon 和 AssimpLoader 都已加载
   - 检查日志：应该看到 `Photon integration enabled successfully`

2. **打开 Photon 编辑器**
   - 按键：默认 `P` 键（可在 Photon 设置中修改）
   - 或使用命令：`/photon editor`

3. **导航到 Mesh 资源面板**
   - 在编辑器左侧找到 "Resources" 面板
   - 点击 "Mesh" 标签

4. **点击 "Import FBX Mesh" 按钮**
   - 按钮位于 Mesh 配置面板底部
   - 灰色背景，圆角矩形

5. **选择 FBX 文件**
   - 支持格式：`.fbx`, `.obj`, `.gltf`, `.glb`
   - 浏览到你的模型文件位置
   - 点击选择

6. **等待导入完成**
   - 右上角会显示通知：`FBX Import Success: Imported X mesh(es)`
   - 导入的 Mesh 会出现在资源列表中
   - 命名格式：`filename_meshname`

7. **使用导入的 Mesh**
   - 创建新的 Particle Emitter
   - 在 Shape 设置中选择 "Mesh"
   - 从下拉列表中选择你导入的 Mesh
   - 选择发射模式：
     - **Vertex**: 从顶点发射
     - **Edge**: 从边发射
     - **Triangle**: 从三角形表面发射

### 示例：从立方体表面发射粒子

```
1. 导入 cube.fbx
2. 创建 ParticleEmitter
3. Shape → Mesh → cube_Cube
4. Type → Triangle
5. Emission → Rate Over Time: 10
6. 运行特效 → 粒子从立方体表面均匀发射
```

---

## 功能 2: 导入 FBX Materials

### 使用步骤

1. **打开 Photon 编辑器**（同上）

2. **导航到 Material 资源面板**
   - 在编辑器左侧找到 "Resources" 面板
   - 点击 "Material" 标签

3. **点击 "Import FBX Materials" 按钮**
   - 按钮位于 Material 配置面板底部

4. **选择 FBX 文件**
   - 支持格式：`.fbx`, `.obj`, `.gltf`, `.glb`
   - 选择包含材质的模型文件

5. **等待导入完成**
   - 通知：`FBX Material Import Success: Imported X material(s)`
   - 导入的 Material 会出现在资源列表中
   - 命名格式：`filename_materialname`

6. **使用导入的 Material**
   - 在 Particle Emitter 的 Material 设置中
   - 从下拉列表选择导入的材质
   - 粒子会使用该材质的纹理和颜色

### 材质类型说明

AssimpLoader 会自动选择最佳的 Photon 材质类型：

| FBX 材质特性 | Photon 材质类型 | 说明 |
|-------------|----------------|------|
| 基础纹理 + 颜色 | TextureMaterial | 标准粒子材质 |
| 自发光（Emissive） | CustomShaderMaterial | 支持发光效果 |

**支持的属性**:
- ✅ BaseColor Texture（基础纹理）
- ✅ BaseColor（颜色）
- ✅ Emissive Factor（自发光）
- ✅ Alpha Mode（透明度模式）

**不支持的属性**（Photon 限制）:
- ❌ Metallic/Roughness（金属度/粗糙度）
- ❌ Normal Map（法线贴图）
- ❌ Height Map（高度贴图）

---

## 完整工作流示例

### 示例：创建火焰粒子特效

**目标**: 从火炬模型发射火焰粒子

#### 步骤 1: 准备模型

在 Blender 中创建 `torch.fbx`:
- 火炬网格（Mesh）
- 火焰材质（Material）：橙红色，自发光

#### 步骤 2: 导入到 Photon

1. 打开 Photon 编辑器
2. 导入 Mesh:
   - Mesh 面板 → Import FBX Mesh → 选择 `torch.fbx`
   - 结果：`torch_Torch` 出现在列表中
3. 导入 Material:
   - Material 面板 → Import FBX Materials → 选择 `torch.fbx`
   - 结果：`torch_FlameMaterial` 出现在列表中

#### 步骤 3: 创建粒子特效

1. 创建新 FX 项目
2. 添加 Particle Emitter
3. 配置：
   ```
   Shape:
     - Type: Mesh
     - Mesh: torch_Torch
     - Mode: Triangle
   
   Material:
     - Type: CustomShaderMaterial
     - Material: torch_FlameMaterial
   
   Emission:
     - Rate Over Time: 20
   
   Start Lifetime: 1.5s
   Start Speed: 2.0
   Start Size: 0.3
   
   Color Over Lifetime:
     - 0.0: Orange (255, 100, 0)
     - 1.0: Transparent (0, 0, 0, 0)
   ```

4. 预览特效 → 火焰粒子从火炬表面发射

---

## 故障排除

### 问题 1: 看不到 "Import FBX" 按钮

**原因**: Photon 未正确加载或版本不兼容

**解决方案**:
1. 检查 Photon 是否安装：`/mods` 命令查看
2. 检查版本：需要 Photon 1.1.17+ (1.20.1)
3. 查看日志：搜索 `Photon integration`
   - 成功：`Photon integration enabled successfully`
   - 失败：`Photon not detected` 或 `required classes not available`

### 问题 2: 导入失败 "Error: ..."

**原因**: FBX 文件格式问题或路径错误

**解决方案**:
1. 确认文件格式：`.fbx`, `.obj`, `.gltf`, `.glb`
2. 确认文件完整性：在 Blender 中重新打开测试
3. 查看日志：搜索 `Failed to import FBX`
4. 尝试简化模型：减少顶点数、材质数

### 问题 3: 导入的 Mesh 看起来不对

**原因**: 坐标系转换问题

**解决方案**:
1. AssimpLoader 会自动转换坐标系（FBX → Minecraft）
2. 如果模型方向错误，在 Blender 中调整：
   - 应用所有变换（Ctrl+A → All Transforms）
   - 确保 Up Axis = Z, Forward Axis = -Y
3. 重新导出 FBX

### 问题 4: 材质没有纹理

**原因**: 纹理路径丢失或嵌入纹理问题

**解决方案**:
1. 在 Blender 导出时选择 "Embed Textures"
2. 或确保纹理文件与 FBX 在同一目录
3. 检查材质是否有 BaseColor Texture
4. 查看日志：搜索 `Set texture:`

### 问题 5: 自发光不工作

**原因**: 材质未设置 Emissive Factor

**解决方案**:
1. 在 Blender 中设置材质的 Emission 属性
2. Emission Strength > 0
3. Emission Color 设置为非黑色
4. 重新导出并导入

---

## 性能建议

### Mesh 优化

- **顶点数**: 建议 < 1000 顶点/Mesh
- **三角形数**: 建议 < 2000 三角形/Mesh
- **原因**: 粒子发射器需要遍历所有顶点/三角形

### Material 优化

- **纹理大小**: 建议 512x512 或 1024x1024
- **纹理格式**: PNG（带透明度）或 JPG（不透明）
- **避免**: 4K 纹理（过大，影响性能）

### 粒子数量

- **发射速率**: 建议 < 50 粒子/秒
- **最大粒子数**: 建议 < 500 同时存在
- **原因**: 过多粒子会影响帧率

---

## 高级技巧

### 技巧 1: 多层粒子特效

组合多个发射器创建复杂特效：

```
FX: MagicSpell
├─ Emitter 1: Core (Mesh: sphere, Material: glow)
├─ Emitter 2: Particles (Mesh: sphere, Material: sparkle)
└─ Emitter 3: Trail (Shape: cone, Material: smoke)
```

### 技巧 2: 动画网格

虽然 Photon 不直接支持骨骼动画，但可以：
1. 导出动画的关键帧为单独的 FBX
2. 在 Photon 中切换不同的 Mesh
3. 创建"伪动画"效果

### 技巧 3: 材质混合

使用 Photon 的 BlendMode 创建特殊效果：
- `ADDITIVE_ALPHA`: 发光、火焰
- `ALPHA`: 烟雾、云
- `CLOSE_CIRCUITS`: 实体粒子

---

## 限制和已知问题

### 当前限制

1. **不支持骨骼动画**: Mesh 是静态的
2. **不支持完整 PBR**: Metallic/Roughness 会丢失
3. **不支持法线贴图**: 需要自定义 Shader
4. **不支持多 UV 通道**: 仅使用第一个 UV 通道

### 计划中的功能（Phase 6 - TODO）

- ⏳ **Emitter 数据提取**: 从 FBX 场景图提取发射器节点
- ⏳ **预设配置**: Fire, Smoke, Magic, Spark 预设
- ⏳ **批量导入**: 一次导入整个文件夹

---

## 技术信息

### 数据转换流程

```
FBX File
  ↓ (Assimp)
AssimpScene (Mesh, Material)
  ↓ (AssimpToPhotonMeshConverter)
Photon MeshData (Vertices, Triangles, Edges)
  ↓ (Photon Editor)
Particle Emitter Shape
```

### 支持的文件格式

| 格式 | 扩展名 | Mesh | Material | 推荐 |
|------|--------|------|----------|------|
| FBX | .fbx | ✅ | ✅ | ⭐⭐⭐ |
| OBJ | .obj | ✅ | ⚠️ 部分 | ⭐⭐ |
| GLTF | .gltf, .glb | ✅ | ✅ | ⭐⭐⭐ |

**推荐**: FBX 或 GLTF（完整支持材质和纹理）

---

## 常见问题 (FAQ)

**Q: 需要安装额外的库吗？**  
A: 不需要。AssimpLoader 已内置 LWJGL Assimp，Photon 集成是可选的。

**Q: 可以在服务器上使用吗？**  
A: Photon 编辑器是客户端功能，但导出的特效可以在服务器上使用。

**Q: 支持 Blender 以外的 3D 软件吗？**  
A: 支持！任何能导出 FBX/OBJ/GLTF 的软件都可以（Maya, 3ds Max, C4D 等）。

**Q: 导入的资源保存在哪里？**  
A: Photon 项目文件（`.photon`），位于 Photon 工作空间目录。

**Q: 可以导入动画吗？**  
A: 当前不支持。Mesh 是静态的，但可以通过切换 Mesh 模拟动画。

---

## 反馈和支持

### 报告问题

如果遇到问题，请提供以下信息：
1. Minecraft 版本
2. AssimpLoader 版本
3. Photon 版本
4. 完整日志（`logs/latest.log`）
5. 问题描述和复现步骤

### 贡献

欢迎贡献代码、文档或示例！

---

## 更新日志

### v1.0.0 (2026-04-26)

**Phase 1-5 完成**:
- ✅ 基础设施搭建
- ✅ Mesh 数据转换器
- ✅ Mesh 导入 UI
- ✅ Material 数据转换器
- ✅ Material 导入 UI

**Phase 6 延后**:
- ⏳ Emitter 数据提取（计划中）

---

**祝你创作出精彩的粒子特效！** ✨
