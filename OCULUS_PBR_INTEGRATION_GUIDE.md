# Oculus/Iris PBR 集成指南

## 渲染管线兼容性分析

### 当前集成状态 ✅

**AssimpLoader → Oculus 渲染管线已完全打通**

1. **FBX 模型加载** (AssimpModelLoader)
   - 支持嵌入式纹理和外部纹理
   - 完整的材质属性解析（漫反射、法线、高光、透明度）
   - 坐标系转换（FBX → OpenGL）

2. **Oculus 兼容性检测** (OculusCompat)
   - 运行时检测 Oculus/Iris 是否加载
   - 检测着色器是否激活
   - 自动切换渲染类型（Vanilla vs Shader）

3. **PBR 纹理支持** (OculusPBRSupport)
   - 反射式访问 Oculus PBR 系统
   - 自动注册法线贴图和高光贴图
   - 遵循 Oculus 命名约定（`_n`, `_s` 后缀）

4. **渲染类型优化** (AssimpBlockEntityRenderer)
   - 着色器激活时：`entityTranslucentCull` (透明) / `entityCutout` (不透明)
   - Vanilla 模式：`entityTranslucent` / `entityCutout`
   - 自动混合模式管理

---

## Oculus PBR 渲染管线详解

### 1. 纹理拦截流程

```
RenderSystem._setShaderTexture(unit, textureId)
    ↓ (MixinRenderSystem 拦截)
TextureTracker.onSetShaderTexture(unit, textureId)
    ↓ (仅 unit 0 触发)
IrisRenderingPipeline.onSetShaderTexture(textureId)
    ↓
PBRTextureManager.getOrLoadHolder(textureId)
    ↓
SimplePBRLoader.load() / AtlasPBRLoader.load()
    ↓ (查找 _n 和 _s 后缀文件)
绑定到着色器采样器: "normals", "specular"
```

### 2. PBR 纹理类型

Oculus 支持 **2 种 PBR 纹理**（定义在 `PBRType.java`）：

| 类型 | 后缀 | 默认值 | 用途 |
|------|------|--------|------|
| **NORMAL** | `_n` | `0x7F7FFFFF` | 切线空间法线贴图（RGB = XYZ） |
| **SPECULAR** | `_s` | `0x00000000` | 高光/金属度/粗糙度（通道编码） |

**命名约定**：
- 基础纹理：`my_texture.png`
- 法线贴图：`my_texture_n.png`
- 高光贴图：`my_texture_s.png`

### 3. Specular 贴图通道编码

Oculus 使用 **LabPBR 格式**（`LabPBRTextureFormat.java`）：

| 通道 | 内容 | 范围 | 说明 |
|------|------|------|------|
| **R** | Smoothness | 0-255 | 光滑度（255 = 镜面，0 = 粗糙） |
| **G** | Metallic | 0-255 | 金属度（255 = 金属，0 = 电介质） |
| **B** | Emissive | 0-255 | 自发光强度 |
| **A** | Porosity/SSS | 0-255 | 孔隙度/次表面散射（可选） |

**重要特性**：
- 法线贴图使用线性插值（mipmap）
- 高光贴图使用离散混合（防止金属度插值错误）

### 4. 着色器采样器绑定

Oculus 为 PBR 纹理预留了专用采样器（`IrisSamplers.java`）：

```glsl
uniform sampler2D gtexture;   // 纹理单元 0：基础纹理（漫反射/Albedo）
uniform sampler2D lightmap;   // 纹理单元 2：光照贴图
uniform sampler2D normals;    // 动态绑定：法线贴图
uniform sampler2D specular;   // 动态绑定：高光贴图
```

**绑定时机**：
- 每次 `RenderSystem.setShaderTexture(0, id)` 调用时
- 自动查找对应的 `_n` 和 `_s` 纹理
- 如果找不到，使用默认值（法线 = 向上，高光 = 黑色）

### 5. 实体渲染集成点

Oculus 通过 Mixin 拦截实体渲染（`MixinBlockEntityRenderDispatcher.java`）：

```java
// 渲染前设置上下文
CapturedRenderingState.INSTANCE.setCurrentBlockEntity(intId);
GbufferPrograms.beginBlockEntities();

// 你的渲染代码
renderMesh(...);

// 渲染后清理
GbufferPrograms.endBlockEntities();
CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
```

**当前实现**：
- AssimpBlockEntityRenderer 已自动兼容
- 使用 `entityTranslucentCull` / `entityCutout` 渲染类型
- Oculus 自动包装 RenderType 为 `OuterWrappedRenderType`

---

## 3D 建模软件 PBR 材质设置指南

### 推荐材质类型：**Phong E** (FBX 标准)

Phong E 是 FBX 格式的标准材质，被 Assimp 完美支持，无需特殊渲染器。

### 材质属性映射表

| Phong E 属性 | Assimp 常量 | Oculus 用途 | 建议值 |
|--------------|-------------|-------------|--------|
| **Diffuse Color** | `AI_MATKEY_COLOR_DIFFUSE` | Albedo（基础色） | RGB 颜色 |
| **Diffuse Texture** | `aiTextureType_DIFFUSE` | 基础纹理 | PNG/TGA |
| **Normal Map** | `aiTextureType_NORMALS` | 法线贴图 | `_n.png` |
| **Specular Map** | `aiTextureType_SPECULAR` | 高光贴图 | `_s.png` |
| **Opacity** | `AI_MATKEY_OPACITY` | 透明度 | 0.0-1.0 |
| **Transparency Factor** | `AI_MATKEY_TRANSPARENCYFACTOR` | 透明因子 | 0.0-1.0 |
| **Specular Color** | `AI_MATKEY_COLOR_SPECULAR` | 高光颜色 | RGB 颜色 |
| **Shininess** | `AI_MATKEY_SHININESS` | 光泽度 | 1-128 |

### 必需纹理贴图（PBR 工作流）

#### 1. **Diffuse/Albedo 贴图** (必需)
- **文件名**：`my_model.png`
- **格式**：PNG/TGA，RGB 或 RGBA
- **内容**：物体的基础颜色（无光照信息）
- **注意**：
  - 不要包含阴影或高光
  - 如果有透明部分，使用 Alpha 通道
  - 建议使用 sRGB 色彩空间

#### 2. **Normal Map 法线贴图** (强烈推荐)
- **文件名**：`my_model_n.png`
- **格式**：PNG，RGB（无 Alpha）
- **内容**：切线空间法线（Tangent Space Normal）
- **颜色编码**：
  - R 通道 = X 轴（左右）
  - G 通道 = Y 轴（上下）
  - B 通道 = Z 轴（深度）
  - 默认法线 = `(128, 128, 255)` 或 `(0.5, 0.5, 1.0)`
- **注意**：
  - 必须是切线空间，不是世界空间或对象空间
  - OpenGL 格式（Y+ 向上），不是 DirectX 格式
  - 如果使用 DirectX 法线，需要反转 G 通道

#### 3. **Specular Map 高光贴图** (推荐)
- **文件名**：`my_model_s.png`
- **格式**：PNG，RGBA
- **通道编码**（LabPBR 格式）：
  - **R 通道**：Smoothness（光滑度）
    - 255 = 完全光滑（镜面反射）
    - 128 = 中等光滑
    - 0 = 完全粗糙（漫反射）
  - **G 通道**：Metallic（金属度）
    - 255 = 纯金属（金、银、铜）
    - 0 = 电介质（塑料、木头、石头）
    - 中间值通常不使用（物理上不真实）
  - **B 通道**：Emissive（自发光）
    - 255 = 最强发光
    - 0 = 不发光
  - **A 通道**：Porosity（孔隙度，可选）
    - 255 = 高孔隙度（海绵、布料）
    - 0 = 无孔隙（金属、玻璃）

**常见材质的 Specular 值**：

| 材质类型 | R (Smoothness) | G (Metallic) | B (Emissive) | 示例 |
|----------|----------------|--------------|--------------|------|
| 抛光金属 | 240 | 255 | 0 | 钢铁、铝 |
| 粗糙金属 | 80 | 255 | 0 | 生锈铁 |
| 光滑塑料 | 200 | 0 | 0 | 塑料外壳 |
| 粗糙石头 | 30 | 0 | 0 | 混凝土 |
| 发光材质 | 100 | 0 | 255 | LED、荧光 |
| 木材 | 60 | 0 | 0 | 木板 |
| 玻璃 | 255 | 0 | 0 | 透明玻璃 |

### 3D 软件具体设置步骤

#### Blender (推荐)

1. **创建材质**：
   - 选择对象 → 材质属性 → 新建材质
   - 着色器类型：**Principled BSDF**（自动映射到 Phong E）

2. **设置纹理**：
   - **Base Color**：连接 Diffuse 贴图
   - **Normal**：添加 Normal Map 节点 → 连接法线贴图
   - **Roughness**：连接 Specular 贴图的 R 通道（需反转：1 - R）
   - **Metallic**：连接 Specular 贴图的 G 通道
   - **Emission**：连接 Specular 贴图的 B 通道

3. **导出 FBX**：
   - 文件 → 导出 → FBX (.fbx)
   - 路径模式：**复制**（Copy）
   - 嵌入纹理：**勾选**（Embed Textures）
   - 应用变换：**勾选**（Apply Transform）
   - 前向轴：**-Z Forward**
   - 上轴：**Y Up**

#### Maya

1. **创建材质**：
   - 材质类型：**Phong E** 或 **Standard Surface**
   - 不要使用 Arnold 材质（aiStandardSurface）

2. **设置纹理**：
   - **Color**：连接 Diffuse 贴图（File 节点）
   - **Bump Mapping**：添加 Bump2d 节点 → 连接法线贴图
     - Bump2d 设置：Use As = Tangent Space Normals
   - **Specular Color**：连接 Specular 贴图

3. **导出 FBX**：
   - 文件 → 导出选择 → FBX Export
   - 嵌入媒体：**勾选**（Embed Media）
   - 平滑组：**勾选**（Smoothing Groups）
   - 切线和副法线：**勾选**（Tangents and Binormals）

#### 3ds Max

1. **创建材质**：
   - 材质编辑器 → 标准材质（Standard）
   - 不要使用 Physical Material（不兼容 FBX）

2. **设置纹理**：
   - **Diffuse Color**：添加 Bitmap → 选择 Diffuse 贴图
   - **Bump**：添加 Normal Bump → 选择法线贴图
     - Normal Bump 设置：Method = Tangent
   - **Specular Level**：添加 Bitmap → 选择 Specular 贴图

3. **导出 FBX**：
   - 文件 → 导出 → FBX
   - 嵌入媒体：**勾选**（Embed Media）
   - 切线和副法线：**勾选**（Tangents and Binormals）

---

## 纹理制作工具推荐

### 法线贴图生成

1. **从高模烘焙**（最佳质量）：
   - Blender：Cycles 渲染器 → Bake → Normal
   - Substance Painter：Bake Mesh Maps
   - xNormal：专业烘焙工具

2. **从漫反射生成**（快速方案）：
   - GIMP + NormalMap 插件
   - Photoshop + NVIDIA Normal Map Filter
   - CrazyBump（商业软件）

### Specular 贴图制作

1. **手动绘制**（精确控制）：
   - Photoshop/GIMP：分别绘制 R/G/B 通道
   - Substance Painter：使用 LabPBR 模板

2. **从材质转换**：
   - Substance Designer：PBR → LabPBR 转换
   - Material Converter 工具

---

## 完整工作流示例

### 场景：制作一个金属机器方块

1. **在 Blender 中建模**：
   - 创建立方体 → 添加细节
   - UV 展开 → 导出 UV 布局

2. **制作纹理**（1024x1024）：
   - **Diffuse** (`machine.png`)：
     - 基础金属颜色（深灰色 #3A3A3A）
     - 添加污渍和划痕（颜色变化）
   - **Normal** (`machine_n.png`)：
     - 从高模烘焙螺丝和凹槽
     - 或使用 CrazyBump 从 Diffuse 生成
   - **Specular** (`machine_s.png`)：
     - R 通道：光滑金属 = 220，划痕 = 100
     - G 通道：金属区域 = 255，橡胶垫 = 0
     - B 通道：LED 指示灯 = 255，其他 = 0

3. **在 Blender 中应用材质**：
   - Principled BSDF
   - Base Color → `machine.png`
   - Normal → `machine_n.png`（通过 Normal Map 节点）
   - Roughness → `machine_s.png` R 通道（反转）
   - Metallic → `machine_s.png` G 通道
   - Emission → `machine_s.png` B 通道

4. **导出 FBX**：
   - 嵌入纹理
   - 应用变换
   - -Z Forward, Y Up

5. **放入 Minecraft**：
   - 将 FBX 放入 `assets/improvedmobs/models/block/`
   - 纹理自动嵌入，无需额外文件
   - AssimpLoader 自动加载
   - OculusPBRSupport 自动注册法线和高光贴图

6. **测试**：
   - 启动游戏，加载 Oculus 着色器包（如 Kappa, BSL, Complementary）
   - 放置方块，观察：
     - 法线贴图：表面凹凸细节
     - 金属度：金属区域的反射
     - 粗糙度：光滑/粗糙区域的高光
     - 自发光：LED 在黑暗中发光

---

## 常见问题排查

### 问题 1：法线贴图不生效

**可能原因**：
- 法线贴图格式错误（DirectX 格式，需要 OpenGL 格式）
- 文件命名不符合约定（缺少 `_n` 后缀）
- 模型缺少切线数据（导出时未勾选 Tangents）

**解决方案**：
```bash
# 检查法线贴图是否加载
[AssimpLoader] DEBUG: Bound normal map machine_n.png to machine.png

# 如果没有此日志，检查文件是否存在
# 如果是 DirectX 法线，使用 GIMP 反转 G 通道
```

### 问题 2：金属材质看起来像塑料

**可能原因**：
- Specular 贴图的 G 通道（Metallic）值太低
- Specular 贴图的 R 通道（Smoothness）值太低

**解决方案**：
- 金属区域：R = 220-255, G = 255
- 检查 Specular 贴图是否正确加载

### 问题 3：透明材质渲染错误

**可能原因**：
- 材质透明度属性未正确设置
- 渲染类型选择错误

**解决方案**：
- 在 Blender 中设置 Alpha 模式为 Blend
- 确保 Diffuse 贴图有 Alpha 通道
- 检查日志：`Material X has transparency: opacity=0.5`

### 问题 4：自发光不发光

**可能原因**：
- Specular 贴图的 B 通道（Emissive）值为 0
- 着色器包不支持自发光

**解决方案**：
- 确保 Specular 贴图 B 通道 = 255（发光区域）
- 测试不同着色器包（推荐 BSL, Complementary）

---

## 性能优化建议

### 纹理分辨率

| 模型大小 | Diffuse | Normal | Specular |
|----------|---------|--------|----------|
| 小型道具 | 512x512 | 512x512 | 512x512 |
| 方块/实体 | 1024x1024 | 1024x1024 | 1024x1024 |
| 大型结构 | 2048x2048 | 2048x2048 | 1024x1024 |

### 纹理压缩

- **不要**在 FBX 中使用 JPEG（有损压缩会破坏法线贴图）
- **推荐**使用 PNG（无损压缩）
- **高级**：使用 DDS + BC5 压缩（法线贴图）

### Mipmap 优化

- Oculus 自动生成 Mipmap
- 法线贴图：线性插值
- 高光贴图：离散混合（防止金属度插值）

---

## 总结

### ✅ 已实现的功能

1. **完整的 FBX → Oculus 渲染管线**
2. **自动 PBR 纹理注册**（法线、高光）
3. **着色器兼容性检测**（自动切换渲染类型）
4. **透明材质支持**（混合模式管理）
5. **嵌入式纹理支持**（无需外部文件）

### 🎯 推荐工作流

1. **建模软件**：Blender（免费，Phong E 兼容）
2. **材质类型**：Principled BSDF（自动映射到 Phong E）
3. **纹理格式**：PNG（Diffuse + Normal + Specular）
4. **命名约定**：`model.png`, `model_n.png`, `model_s.png`
5. **导出格式**：FBX 2020，嵌入纹理，-Z Forward, Y Up

### 📚 参考资源

- **LabPBR 规范**：https://shaderlabs.org/wiki/LabPBR_Material_Standard
- **Oculus 文档**：https://github.com/IrisShaders/Iris
- **Assimp 文档**：https://assimp-docs.readthedocs.io/
- **Blender FBX 导出**：https://docs.blender.org/manual/en/latest/addons/import_export/scene_fbx.html

---

**最后更新**：2026-04-24  
**作者**：CARIERX  
**项目**：ImprovedMobs - AssimpLoader
