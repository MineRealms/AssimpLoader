# Blender / FBX 到 Oculus PBR 的源码调查

> 本文记录实现前的调查状态。后续已接入动态 loader，当前使用方法见 [PBR 材质](../guides/OCULUS_PBR_USAGE.md)，构建和实际光影截图见[实机验收](PBR_VALIDATION_2026-11-13.md)。下文的“当前未实现”指调查时的状态。

日期：2026-11-13。目标是让 Blender 资产通过 AssimpLoader 在 Minecraft Java 1.20.1 中获得光影包的法线、反射和材质表现。本次为源码调查和文档更正，没有实现新的动态 PBR loader，也没有启用 Oculus 进行画面验收。

## 结论

FBX 可以继续作为几何、层级和 UV 的载体。光影包需要另外约定材质图像的编码；建议以 LabPBR 1.3 为目标。Blender 的节点材质、FBX 的 Phong 参数和 LabPBR 不是可直接互换的材质格式。

**更正上一轮结论：Oculus 默认没有 DynamicTexture loader，但公开了自定义 loader 的注册入口。** AssimpLoader 可以给自己的纹理类型注册 loader，将嵌入 FBX 或磁盘中的 PBR 图像交给 Oculus，无需修改它的私有 holder map。此前把“当前未实现”写成“公共 API 不支持”不准确。

| 输入方式 | 当前实现 | 后续接入方式 |
| --- | --- | --- |
| FBX + 资源包中的外置 PNG | 底色保留资源路径；Oculus 的 SimplePBRLoader 可按同路径 `_n`/`_s` 找图 | 使用预先打包好的 LabPBR 贴图，验证光影实体阶段实际采样 |
| FBX 内嵌 PNG | 已验证底色；转为普通 DynamicTexture，当前没有配套 PBR loader | 自有动态纹理类型 + public registry loader |
| 文件系统中的外置图像 | 底色转为普通 DynamicTexture，当前没有动态 PBR | 同上，保留可以重建图像的来源描述 |
| Blender Roughness / Metallic / Emission 图 | 当前 AssimpMaterial 没有完整读取和转换 | 明确导出布局，读取原始通道，再生成 LabPBR 图像 |
| Blender 程序纹理和复杂节点图 | 不随 FBX 自动变成可用的游戏材质 | 先烘焙到 UV 图像 |

前一轮 CIWS、刀和测试件只证明底色图集和几何能显示。它们没有法线/粗糙度/金属度图像，不能作为 PBR 验收样本。

## 核对版本与证据

- 本地源码：`H:\MinecraftMods\Oculus`，HEAD `b3b278134f719afe32ba8b6b5d3a93f052175afc`，`gradle.properties` 声明 Oculus 1.8.0 / Minecraft 1.20.1。
- 本地实际依赖：`mods_dev/oculus-mc1.20.1-1.8.0.jar.disabled`。使用 `javap -p -c` 交叉检查了 loader registry、consumer 和 holder 方法；关键 PBR 行为与该源码一致。jar 没有 revision 元数据，因此不声称整包与该 HEAD 完全一致。
- 正式贴图约定：[Iris PBR 文档](https://shaders.properties/current/how-to/pbr_standards/)；[LabPBR 材质规范](https://shaderlabs.org/wiki/LabPBR_Material_Standard)。光影包负责解码和着色，Oculus 负责加载、关联和绑定。

## Oculus 实际调用链

```text
RenderType 绑定 albedo 到纹理单元 0
  -> TextureTracker.onSetShaderTexture
  -> IrisRenderingPipeline.onSetShaderTexture
  -> PBRTextureManager.getOrLoadHolder(albedo GL id)
  -> TextureTracker 找到 AbstractTexture 对象
  -> registry.getLoader(texture.getClass())
  -> loader.load(base, ResourceManager, consumer)
  -> consumer 接收 normal / specular AbstractTexture
  -> normals / specular shader sampler
```

`IrisRenderingPipeline.java:829` 还要求正在绘制世界、且管线发现 PBR samplers。给 holder 填入图像不保证当前光影的实体阶段使用它。GUI 物品和当前 LookDev 自有 studio shader 不等同于世界 PBR 光影预览。

| Oculus 源码 | 核对内容 |
| --- | --- |
| [PBRTextureLoaderRegistry.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/texture/pbr/loader/PBRTextureLoaderRegistry.java) | 第 21 行 public `register(Class, loader)`；第 27 行按精确 class 查找；默认只注册 SimpleTexture 和 TextureAtlas |
| [PBRTextureLoader.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/texture/pbr/loader/PBRTextureLoader.java) | loader 和 consumer 为公开接口；接受任意 AbstractTexture；契约只允许改变当前 GL_TEXTURE_2D binding |
| [SimplePBRLoader.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/texture/pbr/loader/SimplePBRLoader.java) | 使用 base 的 ResourceLocation，查同路径 `_n`、`_s` 资源；不读取 Assimp 材质路径 |
| [PBRTextureManager.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/texture/pbr/PBRTextureManager.java) | 第 96 行按 albedo id 缓存 holder；第 153/168 行清理和关闭附属纹理；默认 holder 也有两张纹理 |
| [IrisRenderingPipeline.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/pipeline/IrisRenderingPipeline.java) | 第 829 行关联 base id 和 PBR holder，更新当前 normal/specular id |
| [IrisSamplers.java](https://github.com/Asek3/Oculus/blob/b3b278134f719afe32ba8b6b5d3a93f052175afc/src/main/java/net/irisshaders/iris/samplers/IrisSamplers.java) | 第 202 行动态绑定 `normals`、`specular`，无需硬编码两个纹理单元 |

这些是 Oculus 1.8.0 中公开可调用的实现接口，并非 Iris v0 对外稳定 API；应探测签名并保留版本不兼容时的底色回退。

## 贴图交付约定

建议几何继续交付 FBX，另交付相同 UV 布局的 `body.png`、`body_n.png`、`body_s.png`。先使用已打包的 LabPBR 图像，避免把 FBX Phong 的 specular 误认为 LabPBR specular。

| 图像 | 通道含义（LabPBR 1.3） |
| --- | --- |
| `body.png` | RGB 底色，A 为表面透明度 |
| `body_n.png` | R/G 为切线法线 X/Y，采用 DirectX Y-；B 为 AO；A 为高度 |
| `body_s.png` | R 为感知光滑度；G 为 F0/金属代码；B 为孔隙度/SSS；A 为自发光 |

关键转换：OpenGL Y+ 法线需按目标约定反转 G；普通 XYZ 法线的 B 不能直接沿用为 AO。感知 roughness 可取 `smoothness = 1 - roughness`；输入若是线性微表面 roughness，则按规范取 `1 - sqrt(roughness)`。G 不是连续 metallic 的原样复制：0..229 表示 F0，230..255 表示金属。`_s` 的 alpha 255 表示忽略发光，发光范围是 0..254；独立彩色 emission 无法无损表达为该标量。数值图使用 Non-Color，底色按 sRGB 工作流处理。完整取值以规范为准。

资源包方式可使用如下布局；FBX 的底色引用需解析到这里的 `body.png`，且不能优先命中 FBX 中的嵌入图像：

```text
pack.mcmeta
assets/demo/models/ciws/ciws.fbx
assets/demo/models/ciws/body.png
assets/demo/models/ciws/body_n.png
assets/demo/models/ciws/body_s.png
assets/minecraft/optifine/texture.properties
```

最后一个文件可声明 `format=lab-pbr/1.3`。在核对版本中，它的完整资源位置来自 `TextureFormatLoader.LOCATION = minecraft:optifine/texture.properties`。这是一项资源包整体格式声明，不是单材质选项；不应由模组无条件添加去覆盖用户资源包设置。光影也需要选择对应材质模式，文件改名或格式声明不会转换像素数据。

## AssimpLoader 尚缺的实现

并行核对了本机 Blender 5.3 导出器和本项目实际 Assimp native revision：Roughness 图通过 `ShininessExponent` 进入 Assimp SHININESS，Metallic 图通过 `ReflectionFactor` 进入 METALNESS；当前 Java snapshot 均未保存。Blender 的 scalar shininess 采用 `100 * (1 - roughness)^2`，但图像仍是原图，不能套用 scalar 换算到整张图片。完整映射和源码位置见 [FBX 材质链路调查](FBX_PBR_PIPELINE_RESEARCH.md)。

1. **完整材质输入**：[AssimpMaterial](../../src/main/java/cn/minerealms/assimploader/model/AssimpMaterial.java) 当前读取 DIFFUSE / NORMALS / SPECULAR。没有完整保存 baseColor、metallic、roughness、AO、emissive、工作流和 UV 集信息。FBX specular 与 LabPBR `_s` 不能因名称相似而直接绑定。对不能从 FBX 无歧义恢复的参数，使用明确的 sidecar 或预打包贴图约定。
2. **材质级纹理身份**：[ModelTextureCache](../../src/main/java/cn/minerealms/assimploader/render/ModelTextureCache.java) 当前按 scene + diffuse path 缓存。相同底色、不同 PBR 图像/参数需要不同 base texture 对象，因为 Oculus 按 base GL id 关联 holder。
3. **动态 loader**：[OculusPBRSupport](../../src/main/java/cn/minerealms/assimploader/compat/OculusPBRSupport.java) 当前只查询现成 loader。应创建自有 `AssimpPbrTexture extends DynamicTexture`，仅注册该精确 class；可用反射 Proxy 实现接口以保持软依赖，不能覆盖全局 DynamicTexture loader。
4. **真实状态**：现有 `prepareTexture()` 用 holder 两字段非 null 判成功，默认 1x1 纹理也会通过。`OculusCompat.isPBRSupported()` 的两个绑定 id 也不证明当前材质找到两张图。应分别报告 loader 可用、各通道找到/转换成功、光影是否实际采样，不能合成一个“PBR 成功”。
5. **配置含义**：关闭当前 PBR 开关只会停止主动 warm-up；Oculus 仍可自动处理资源 SimpleTexture 的 `_n/_s`。要实现模组材质自身的禁用，需要在自有 loader 中明确退回默认通道；不能声称关闭所有资源包 PBR。
6. **法线与切线**：当前 [BaseAssimpRenderer](../../src/main/java/cn/minerealms/assimploader/render/base/BaseAssimpRenderer.java) 把三角形提交为四顶点退化 QUADS。Oculus `mixin/vertices/MixinBufferBuilder.java:216` 的 TRIANGLES 分支保留每顶点平滑法线，而第 228 行 QUADS 分支重写面法线。接入时应评估标准 NEW_ENTITY + TRIANGLES RenderType，验证方向、镜像 UV、平滑面和切线符号。

`render/PBRShaderIntegration.java` 是无调用者的旧占位结构，不能作为已经实现上述链路的依据。普通 OpenGL 自有 shader 不会因绑定了几张图而自动获得 Oculus 世界光影效果。

## 动态 loader 的生命周期

- 在 base 首次绑定之前注册 loader。Oculus 会缓存默认 holder，晚注册不会自动重试。
- base 记录不可变的图像来源/转换参数；每次 loader 调用创建新的附属纹理，重载后可以重建，不能复用已关闭对象。
- consumer 接受后由 Oculus 拥有附属纹理；不要把 TextureManager 的共享纹理或同一实例交给多个 holder。删除 base 和 F3+T 会触发 Oculus 清理。
- 准备全部通道后再移交；未移交资源在失败时关闭。不要在只 accept 一半后抛异常，避免遗留资源。
- loader 不切 framebuffer、program 或 active texture unit，遵守它的 GL 状态契约；解码/通道打包尽量在 worker，纹理上传在正常客户端渲染线程安排，不经 Rosetta 直接调用渲染。
- 同一材质一次解码/转换，按资源代际失效；不在每帧查文件、打包图片或重新上传。

## 后续验收

先做一个有 UV 的平滑球和一块斜面：固定相同底色，分开测试高/低粗糙度、金属/电介质、带方向标记的法线，再比较资源贴图、FBX 内嵌图和磁盘图。分别记录 normal/specular 的实际来源与 holder id，不能只看模组是否加载。

需要覆盖世界方块、手持、光影开关/切换、F3+T、相同底色但不同材质、缺单通道和完全缺 PBR 图。LookDev 的当前 studio shader 保持底色预览，除非单独实现 PBR 预览和光照环境。正式功能完成前，生产包仍只具有上一轮已验收的底色/资源纹理路径。
