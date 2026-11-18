# PBR 资产制作与 FBX 接入规范

更新：2026-11-13。本文描述本仓库当前实现，适用于导出给 AssimpLoader 的美术资产。游戏内光影设置见 [Oculus PBR 使用](OCULUS_PBR_USAGE.md)，实际画面见 [PBR 验收](../history/PBR_VALIDATION_2026-11-13.md)。

需要 Agent 操作建模软件时，使用 [Agent 工作流](AGENT_ASSET_WORKFLOW.md) 和 [三种 DCC 导出工具](DCC_EXPORTERS.md)。导出器生成基础交付数据，PBR 识别约定仍以本文为准。

## 1. 推荐交付方式

优先使用 **Blender 导出的二进制 FBX，正确连接并嵌入 PNG 贴图**，或自包含 GLB。模型能显示不代表 PBR 数据已接入；底色、法线、粗糙度、金属度必须分别检查。

| 来源 | 当前支持方式 | 制作要求 |
| --- | --- | --- |
| Blender FBX | 自动读取材质槽，转换为 LabPBR | 保留 Blender 导出元数据；连接普通 PBR 图片；可嵌入 |
| glTF / GLB | 自动读取标准 metallic/roughness 材质 | 保留标准材质、纹理及因子；GLB 可自包含 |
| Maya、3ds Max 等其他来源 FBX | 不自动猜测旧式高光值的 PBR 含义 | 用 Blender 导入后重新检查、导出，或改交 GLB；也可交付预打包 LabPBR |
| 预打包 LabPBR | 读取底色旁的 `_n` / `_s` 图片 | 图片必须已经符合 LabPBR 通道规范 |
| 任意程序材质、完整节点图 | 不执行 DCC 的材质程序 | 烘焙成使用 UV0 的图片后交付 |

Blender FBX 的自动粗糙度/金属度转换要求 Assimp 场景元数据 `SourceAsset_Format` 包含 `fbx`，且 `SourceAsset_Generator` 包含 `blender`。仅改文件扩展名或写一个自定义 JSON 不能启用该约定；当前没有额外的材质 sidecar 配置格式。

## 2. 在建模软件中应保留的数据

| 数据 | 要求及原因 |
| --- | --- |
| 单位与尺寸 | 保留单位元数据；用 1 米测试物确认约对应 1 个 Minecraft 方块。不要再无条件乘 100 或额外旋转 90 度 |
| 最终几何 | 删除隐藏的测试副本、摄影棚地板等；保留最终模型、必要空物体及骨架 |
| 三角形划分 | 法线烘焙和导出的三角形保持一致，避免非平面四边形重新切分后出现接缝 |
| 顶点法线、硬边 | 保留作者法线与平滑/硬边。缺失时自动补平滑法线不能还原原始硬边设计 |
| UV0 | 所有参与渲染的图片使用第一套 UV；UV1+、UDIM、Mapping 节点变换不在当前采样范围内 |
| 材质槽和面分配 | 多部件可共享材质，但每个面的分配必须正确；不同粗糙度/法线的材质可以共用底色 |
| 图片文件与连接 | 图片保存到实际文件，并接到可导出的材质插槽；不要只留下图片名或未连接的节点 |
| 材质标量 | 保留底色、Alpha、粗糙度、金属度和发光强度；没有图片时也可使用受支持标量 |
| 局部原点及父子关系 | 单独旋转的部件必须保留独立节点、局部枢轴与父子关系，使用 DYNAMIC 导入 |
| 骨骼和蒙皮 | 有动画时保留骨架层级、绑定姿态、权重和动画片段。GPU 使用最强四个影响，建议每顶点不超过四个并归一化 |

静态物件可在烘焙前整理缩放和变换；已绑定或带关节的模型不能一律 Apply All Transforms、合并对象，否则可能破坏枢轴和动画。位于地面的摆件建议将根原点设在底面中央。

当前实体 PBR 路径根据几何、UV 和法线计算切线，不要求导出切线属性；需要避免退化 UV。不要预先再翻一次 UV，导入流程已有 UV 翻转。顶点色不能代替当前底色图片，应将依赖顶点色的材质效果烘焙到图片。

### 独立旋转部件

例如 CIWS：固定底座为根，Yaw 节点绕局部 Y 轴，Pitch 子节点绕局部 X 轴，Rotor 再继承前两者。对象的顶点和局部变换应围绕各自关节组织，不能只把整个模型的原点移到世界零点。

`STATIC` 会预变换顶点并可能按材质合并网格，适合固定展示。`DYNAMIC` 保留节点树，适合方位角、俯仰角或骨骼动画；仍需 Mod 为实例提供控制或动画状态，导入本身不会让关节自动转动。CIWS 实际枢轴数据见 [资产说明](TEST_ASSETS.md#ciws-pivots-and-hierarchy)。

## 3. 普通 PBR 图片与 Blender 节点

建议每种材质交付下列图片。这里的后缀是供美术阅读的命名建议，读取依据是导出材质连接，不会仅凭 `_roughness` 等文件名自动发现图片。

| 图片示例 | 色彩空间 | Blender Principled 连接 | 当前读取方式 |
| --- | --- | --- | --- |
| `body_basecolor.png` | sRGB | Image Color → Base Color | RGBA 底色，结合材质底色/透明度 |
| `body_normal_opengl.png` | Non-Color | Image → Normal Map，Tangent → Normal | OpenGL Y+ 切线法线，自动转 LabPBR |
| `body_roughness.png` | Non-Color | Image Color → Roughness | 灰度 R，0 光滑、1 粗糙 |
| `body_metallic.png` | Non-Color | Image Color → Metallic | 灰度 R，0 介质、1 金属 |
| `body_emission.png`，可选 | sRGB | Image Color → Emission Color | 结合发光因子，近似为调制底色的发光强度 |
| AO，可选 | Non-Color | 需格式提供明确 AO 槽位 | 灰度 R；普通 Blender FBX 的任意 AO 节点不保证被导出 |

要求：

- 所有图片对齐同一套 UV0。Generated/Object 坐标、复杂 Mix/Mapping、噪声、渐变等先烘焙。
- 普通粗糙度和金属度使用独立灰度图。FBX 不要直接把 ORM 彩色打包图同时接到这两个插槽；当前 FBX 路径读取各自的 R。
- glTF/GLB 的标准组合贴图可使用 G=粗糙度、B=金属度；AO 通过标准 occlusion 槽读取 R。
- 普通切线法线使用 OpenGL Y+，平面约为 `(128,128,255)`，Normal Map Strength 建议 1。不要在自动转换前手动再翻绿色通道。
- 单张图片每边不超过 4096，像素总量不超过 16M。小型 Minecraft 摆件通常用 512 或 1024 即可。
- 同一材质的图片建议相同尺寸及长宽比；当前合成会按输入最大尺寸重采样，尺寸不一致可能产生细节偏差。
- 完整彩色底图通常配中性白色 Base Color；额外色值会参与着色。Blender 已连接粗糙度/金属度图片时按图片值读取，没有图片才从导出标量恢复；glTF 按标准乘材质因子。

**保留 `_n`、`_s` 给已经打包完成的 LabPBR。** 例如底色 `body.png` 旁出现 `body_n.png` 时，它将优先于自动法线转换直接读取；普通 OpenGL 法线误用这个名称会跳过所需转换。普通粗糙度也不能直接改名成 `body_s.png`。

## 4. Blender FBX 导出

本项目使用 Blender 5.3 的二进制 FBX 实际验证过，不要求所谓“FBX 2020 以上”。建议：

1. 确认模型实际尺寸、原点、UV0、材质分配及图片节点连接。
2. 保存图片；需要单文件交付时启用 `Path Mode: Copy` 和 `Embed Textures`。
3. 仅选择要交付的 Mesh、必要 Empty；有骨骼才加入 Armature。
4. Scale=1，启用单位换算，Forward=-Z、Up=Y；不要额外烘焙一次空间旋转。
5. 使用最终修改器和一致的三角形划分。没有动画时关闭动画导出及多余叶骨。
6. 重新导入检查，之后到 Minecraft 世界验证 PBR。Blender 预览正确不是最终验收。

项目静态传送门使用的 Python 参数可作参考：

```python
bpy.ops.export_scene.fbx(
    filepath=output_fbx,
    use_selection=True,
    object_types={'MESH', 'EMPTY'},
    axis_forward='-Z',
    axis_up='Y',
    apply_unit_scale=True,
    apply_scale_options='FBX_SCALE_NONE',
    bake_space_transform=False,
    use_mesh_modifiers=True,
    mesh_smooth_type='FACE',
    use_triangles=True,
    add_leaf_bones=False,
    bake_anim=False,
    path_mode='COPY',
    embed_textures=True,
)
```

这是静态资产参数。骨骼动画需包含 `ARMATURE` 并导出所需动画片段；不要直接沿用 `bake_anim=False`。保留 `.blend` 中的原始骨架、约束及未破坏的枢轴，再交付运行时副本。

仅在 `.blend` 中 Pack Resources 不等于 FBX 已嵌入图片；实际 FBX 必须带图片数据。Blender 也不会自动嵌入旁边未连接到材质的任意图片。自行准备的 LabPBR 伴随文件宜采用外部资源交付。

## 5. 玻璃、发光及转换限制

### 玻璃

FBX 需要显式材质 Alpha/Opacity 小于 0.99，才能进入透明绘制通道。仅底图含透明像素而材质 Alpha=1，不能保证 FBX 被归为半透明。glTF/GLB 可使用明确的 `alphaMode=BLEND`。

传送门玻璃使用材质 Alpha=0.24，能量膜使用 0.18，底图 Alpha=1。若还需要空间变化的透明度，再设计图片 Alpha。部分光影会裁掉接近 0.1 的值，需在实际光影中检查。薄片应检查双面、背面和层叠遮挡，避免大量相交透明面。

当前玻璃表现为 Alpha 混合及 PBR 高光，没有导入 Blender Transmission、IOR、体积、厚度或物理折射。光影支持 LabPBR 不意味着它实现了这些 Blender 材质参数。

### 发光和金属

自动转换把有色/HDR 发光近似为调制底色的标量，发光颜色主要依赖底色；不保证还原独立颜色、HDR 亮度或照亮附近物体。需要彩色灯带时，底色也应具有目标颜色。

金属度在 0.5 处分为介质与通用金属，部分金属过渡有损。当前不自动转换各向异性、清漆、绒布、孔隙度、SSS 或几何位移；没有高度输入时使用默认高度。

## 6. 已打包 LabPBR 交付

这条路径适合其他 DCC 或需要直接控制 LabPBR 通道的资产。基准名字取自材质实际引用的底色图片，而不是 FBX 文件名：

```text
assets/demo/models/portal/model.fbx
assets/demo/models/portal/body.png
assets/demo/models/portal/body_n.png
assets/demo/models/portal/body_s.png
```

| 图片 | 通道含义 |
| --- | --- |
| `body.png` | RGB 底色；A 透明度 |
| `body_n.png` | RG 切线法线 XY，DirectX Y-；B=AO；A=高度 |
| `body_s.png` | R=感知光滑度；G=介质 F0 或 LabPBR 金属代码；B=孔隙度/SSS；A=发光 |

粗糙度转换为 `1 - roughness`；普通反射灰度、ORM 或原始金属度不能直接充当 `_s`。LabPBR `_s.A=255` 是不发光哨兵，自动生成的发光限制为 0..254。自动法线转换将 OpenGL Y+ 变为 LabPBR Y-，AO 写入 B，高度默认 A=255；预打包图片则必须已经符合目标约定。

伴随图独立覆盖对应的自动转换结果。只交 `_n` 时，`_s` 仍可自动生成；两者都有则直接使用。命名与路径必须与底色对应；匿名嵌入引用 `*0` 若没有可恢复文件名，不能据此猜测兄弟图片。

## 7. 资源目录和游戏接入

将资产放在资源包或 Mod 的 `assets/<namespace>/models/...`，图片使用相对路径。资源位置建议全小写 ASCII、下划线和 `/`。文件系统加载的外部图片必须位于模型所在目录及其子目录，不支持绝对盘符、`../` 越界或符号链接逃逸；资源包侧文件需在允许的同 namespace 路径内。

几何 JSON 示例：

```json
{
  "loader": "assimploader:assimp",
  "model": "demo:models/portal/model.fbx",
  "mode": "static"
}
```

需要独立旋转或动画时改为 `"mode": "dynamic"`。JSON 还需被方块 blockstate 或物品引用，Mod 也需注册相应内容与渲染器；仅复制 FBX 不会自动增加方块。接入示例见 [快速开始](QUICK_START.md) 和 [世界展示](../showcase/MODEL_SHOWCASE.md)。

资源包模型、图片和 display transforms 修改可按 `F3+T`。外部覆盖目录及监听由配置决定，见 [加载与重载](../reference/LOADING_FIXES.md)；Java 注册、材质解析或渲染代码修改需要重启客户端。

## 8. 美术交付清单

- 可编辑源文件，如 `.blend`；保留图片来源和资产许可。
- 运行时 `.fbx` 或 `.glb`；外部图片及 glTF 的 `.bin` 等侧文件齐全。
- 部件、材质名称；单位、整体尺寸、朝向、根原点和独立枢轴坐标。
- 普通 PBR 或预打包 LabPBR 的明确说明；法线方向、颜色空间、贴图通道。
- 有动画时附片段名称、绑定姿态及控制节点；静态资产说明没有动画。
- 至少一张中性预览，以及世界内 PBR 开/关对照；透明资产增加背后有可见物体的视角。

可参考本仓库 `model-tests/portal/portal_manifest.json` 的交付说明，但它是资产清单，运行时不会读取它来配置材质。

## 9. 验收和排错

| 现象 | 优先检查 |
| --- | --- |
| 有底色，没有法线起伏 | Normal Map 是否连接；UV0；图片是否嵌入；普通法线是否误叫 `_n` |
| 金属像塑料，粗糙度无区别 | 是否为可识别的 Blender FBX/GLB；材质连接；光影是否选 LabPBR 模式 |
| 法线凹凸反向 | 普通输入是否 OpenGL Y+；是否手动和自动重复翻绿；预打包是否已经是 Y- |
| 玻璃不透明或消失 | FBX 材质 Alpha；是否低于光影裁切阈值；背面与交叠透明面 |
| 模型太大或躺倒 | 单位、导出轴和根变换；是否重复乘 100、重复旋转；区分世界尺寸与物品 display transforms |
| 关节转动带歪整个模型 | 是否 STATIC；是否保留 Empty/父子层级；局部轴和枢轴是否正确 |
| Blender 正常，游戏黑紫或缺图 | 实际图片文件、相对路径和嵌入数据；检查 `logs/latest.log` |
| LookDev 有底色但看不出 PBR | 当前 LookDev 是底色 studio 预览，应在启用对应光影的世界中验收 |

世界验收需安装兼容 Oculus 的光影，启用该光影的 LabPBR/资源包材质模式。本次验证使用 Complementary Unbound r5.3 的 `RP_MODE=3`；别的光影选项名称可能不同。底图正常、texture ID 存在、编译成功都不能单独证明法线和金属采样已生效。

## 实现依据

- [`AssimpMaterial`](../../src/main/java/cn/minerealms/assimploader/model/AssimpMaterial.java)：来源识别、材质槽、标量与 Alpha。
- [`AssimpScene`](../../src/main/java/cn/minerealms/assimploader/model/AssimpScene.java)、[`AssimpMesh`](../../src/main/java/cn/minerealms/assimploader/model/AssimpMesh.java)：元数据、节点、UV 与几何。
- [`PbrTexturePreparation`](../../src/main/java/cn/minerealms/assimploader/render/PbrTexturePreparation.java)、[`LabPbrPacker`](../../src/main/java/cn/minerealms/assimploader/material/LabPbrPacker.java)：伴随图优先级和通道转换。
- [`ModelTextureCache`](../../src/main/java/cn/minerealms/assimploader/render/ModelTextureCache.java)：输入路径、图片限制和资源生命周期。
- [`build_portal_pbr.py`](../../model-tests/scripts/build_portal_pbr.py)：本次实际验证的 Blender 制作与导出脚本。
