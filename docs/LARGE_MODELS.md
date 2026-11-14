# 大型模型与巨物显示

适用于当前 Showcase 世界展示方块。材质规范见 [PBR 资产规范](PBR_ASSET_SPEC.md)，自动截图入口见 [客户端资产验收](TESTING.md)。

## 剔除配置

编辑游戏目录的 `config/assimploader-client.toml`，在已有的 `[rendering]` 中配置：

```toml
[rendering]
frustumCullingEnabled = true
alwaysRenderLargeModels = true
largeModelThreshold = 16.0
modelViewDistance = 128
```

`alwaysRenderLargeModels` 默认关闭。开启后，最大轴尺寸达到 `largeModelThreshold` 的 Showcase 同时跳过视锥剔除和渲染器的距离限制。阈值按导入并应用节点变换后的实际方块尺寸计算，范围为 1–4096。`modelViewDistance` 的范围为 16–4096，普通模式按相机到模型包围盒最近点的距离计算，不按锚点距离计算。

Showcase 使用真实、有限的几何包围盒，并进入引擎的全局方块实体列表。因此锚点区段离开视野时，仍会按整个模型的边界判断可见性。普通模式保留正常视锥检查；显式强制显示和关闭视锥设置通过本模组专用包围盒标记处理，同时兼容原版/Forge 和 Embeddium 的独立视锥入口。其他模组的包围盒不受影响。

模型锚点区块仍必须加载。这个开关不加载区块，也不突破相机远裁剪面、光影裁剪或深度缓冲。大范围摆放多个巨型模型会增加 GPU 工作量及纹理显存占用。

## 资产整理

以 1 米对应 1 格检查实际尺寸，整组和独立部件均建议使用底部中心原点。静态摆件可以将世界变换烘焙到几何；带关节、动画和旋转枢轴的模型应保留局部层级并使用 DYNAMIC 导入。

清除未被任何面引用的顶点，否则它们可能扩大包围盒并增加内存占用。UV 必须按多边形的面角保存；仅顶点坐标和三角形索引相同，不能证明材质分配及 UV 正确。纹理使用 UV0；平铺 UV 可以超过 0–1，不应直接截断。贴图必须连接到底色、法线、粗糙度和金属度对应插槽。

FBX 可以嵌入图片。外置图片应使用小写相对路径，位于模型所在目录及子目录；不要依赖建模电脑上的绝对盘符路径。多个独立部件可放在同一目录，并共享 `textures/` 子目录。源文件和贴图应另行保留。

## 本地资源包预览

无需修改模组注册，可用资源包覆盖已有测试槽：

```text
Scaffold_Local_PBR/
  pack.mcmeta
  assets/assimploader/models/showcase/probe_textured.fbx
  assets/assimploader/models/showcase/textures/...
```

嵌入了全部图片的 FBX 无需外置 `textures/`。`pack.mcmeta` 的 `pack_format` 为 15，适用于 Minecraft 1.20.1。启用资源包后，放置 `assimploader:showcase_probe_textured`，或使用 [AssetSmoke](TESTING.md) 自动放置、取景、截图及执行 F3+T 等效重载。

这个测试槽的全部实例共享同一个资源模型。需要同时展示多个不同部件时，应为它们接入各自的模型和方块注册，不能仅给同一个文件改名字。

第三方资产及其本地预览资源包不应随模组源码公开发布，除非已经确认具有相应再分发许可。
