# Oculus PBR 材质

本页描述当前实现。此前的接口和像素约定调查见 [源码调查](../history/OCULUS_PBR_RESEARCH_2026-11-13.md) 与 [FBX 材质映射](../history/FBX_PBR_PIPELINE_RESEARCH.md)。实际截图、测试和调试记录见[PBR 实机验收](../history/PBR_VALIDATION_2026-11-13.md)。不能仅根据纹理 ID 或构建成功推断画面正确。

美术制作、FBX 导出参数及建模数据保留要求见 [PBR 资产接入规范](PBR_ASSET_SPEC.md)。

## 支持范围

| 输入 | 处理 |
| --- | --- |
| Blender 导出的 FBX | 根据 Blender/FBX 源元数据读取底色、切线法线、粗糙度、金属度及支持的数值参数；支持嵌入图像 |
| glTF / GLB | 读取 metallic-roughness 工作流及 G/B 通道、normal scale、AO strength、emissive factor |
| 预打包 LabPBR | 按底色图的原始文件名查找 `_n` 和 `_s`；各通道独立优先于自动转换，保留原像素 |
| 普通 OBJ / Phong FBX | 显示底色并读取明确的法线；不把 Phong specular 或 shininess 自动解释为 LabPBR |
| Oculus 缺失或关闭 | 保留底色显示；不要求玩家安装 Oculus 才能加载模型 |

材质图像可以来自 FBX/GLB 内嵌图片、资源包或配置的模型目录。文件系统侧图像必须位于模型自己的目录内；符号链接检查也不能越界。

## Blender 资产

1. 为模型准备 UV0；将程序纹理和复杂节点烘焙成图像。
2. Principled BSDF 的 Base Color、Roughness、Metallic 使用图像节点；法线图通过 Normal Map 节点连接 Normal。
3. 底色使用 sRGB；粗糙度、金属度和法线使用 Non-Color。自动转换输入法线采用 OpenGL Y+。
4. 导出 FBX 时选择需要的对象、正确的轴和单位；Path Mode 选 Copy，按需要启用 Embed Textures。也可使用自包含 GLB。
5. 通过模型 JSON 加载到 AssimpLoader，再放置相应方块。Java 注册代码改变需要重启；已有模型/贴图资源修改可执行 F3+T。

Blender FBX 的 roughness 图由 Assimp 的 SHININESS 槽位传入，图像像素仍是原始粗糙度；标量则反算 Blender 的 `100 * (1 - roughness)^2`。只在确认 Blender 导出元数据时应用该约定。

仓库测试件：`model-tests/pbr/pbr_probe.fbx`、`pbr_probe.glb` 和 `textures/`。三个球共享同一底色，但分别使用磨砂介质、抛光介质、抛光金属材质；另含法线斜面。生成脚本为 `model-tests/scripts/build_pbr_probe.py`。

多部件传送门为 `portal_pbr`，材质包括金属、粗糙表面、玻璃和发光部件。玻璃须导出明确的材质 Alpha；glTF 也可使用 `alphaMode=BLEND` 搭配底色 alpha。当前玻璃表现为 alpha 混合和 PBR 高光，不转换 Blender 的 Transmission、IOR、厚度或真实折射。避免多层玻璃交叠。

## 预打包 LabPBR

下面的 `_n` / `_s` 会跳过自动转换，必须已按 LabPBR 打包。普通 OpenGL 法线应取 `body_normal_opengl.png` 等其他名称并接入材质；不能直接把它或粗糙度图片改成 `_n` / `_s`。

```text
assets/demo/models/example/model.fbx
assets/demo/models/example/body.png
assets/demo/models/example/body_n.png
assets/demo/models/example/body_s.png
```

| 图片 | LabPBR 1.3 通道 |
| --- | --- |
| `body.png` | RGB 底色，A 透明度 |
| `body_n.png` | RG 法线 XY，DirectX Y-；B AO；A 高度 |
| `body_s.png` | R 感知光滑度；G F0/金属代码；B 孔隙度/SSS；A 发光 |

`_s` 不是普通灰度 specular 图片。粗糙度转换为 `1 - roughness`；介质使用 F0，金属使用约定代码。自动转换将部分金属度以 0.5 为界近似，彩色/HDR 发光近似为调制底色的标量。`_s` alpha255 是“不发光”哨兵，自动发光值限制在 0..254。

## 光影设置

开发验证组合为 Oculus 1.8.0、Embeddium 0.3.31 和 Complementary Unbound r5.3。Oculus 与光影包均不打进发布 jar。

本机额外安装的 F3Tweaker 要求 Forge 47.4.10。包含它的开发启动使用 `-Pforge_version=47.4.10`；项目默认的 47.1.3 没有改动，单纯放入不满足版本要求的 jar 会在到达世界之前失败。另已修复 F3Tweaker 的 `mods.toml`：两个依赖项使用 Forge 要求的 `mandatory=true`，替换不被该版本接受的 `type="required"`，重新构建后安装到 `run/mods`。

启用光影包之后，还要在光影设置中选择 LabPBR 材质模式。该测试包对应 `RP_MODE=3`；`RP_MODE=1` 为光影内置材质，不能证明资产自己的 PBR 图正在被采样。

开发实例使用的本机文件：

```properties
# run/config/oculus.properties
shaderPack=ComplementaryUnbound_r5.3.zip
enableShaders=true
```

```properties
# run/shaderpacks/ComplementaryUnbound_r5.3.zip.txt
RP_MODE=3
BLOCK_REFLECT_QUALITY=3
NORMAL_MAP_STRENGTH=100
```

这些文件位于被 Git 忽略的 `run/`，不覆盖正式实例设置。全局 `minecraft:optifine/texture.properties` 的格式声明应由资源包作者决定，模组不强制覆盖它。

## 实现与诊断

`ModelTextureCache` 为每个 scene/material 创建独立 `AssimpPbrTexture`，共享底色也不会串用不同的 PBR 材质。图像解码和 LabPBR 转换由有界后台任务完成；首次显示可能短暂使用白色占位，上传完成后失效 Oculus 的旧 holder。

`OculusPBRSupport` 通过公开实现接口为这一种纹理类注册 loader；不覆盖所有 DynamicTexture，不修改 Oculus 私有 holder map。每次创建独立子纹理，交给 Oculus 管理释放。世界渲染使用标准 NEW_ENTITY 三角形路径以保留光影法线/切线处理。

`ModelTextureCache.status(scene, material)` 提供 ready/failed、normal/specular 来源和实际子纹理 ID。正 ID 仅证明图像关联，最终效果仍要检查世界截图。F3+T 会清理资源代际并重建贴图与 holder。

显式开发验证命令：

```powershell
.\gradlew.bat runClient -PwithOculus "-PclientPbrSmoke=New World" --console=plain --no-daemon --no-parallel
```

要求 `run/saves/New World` 已存在，且已安装并选择上面的光影。测试会临时放置探针、小平台、CIWS、刀和传送门，调整相机、天气和时间，完成后恢复世界及玩家状态并退出。检查 `[PbrSmoke] SUCCESS` 与 `run/screenshots/assimp-pbr-smoke-*.png`；Gradle 的 `BUILD SUCCESSFUL` 本身不是 smoke 成功标志。包含 PBR 开关、资源重载、光影开关以及传送门 PBR 开关/重载对照，共十张世界截图。

增加 `-PclientPbrKeepOpen` 会保留最终传送门展台、晴天和观察位置，恢复 HUD/相机选项后保持客户端打开。此模式明确保留展示内容，不执行默认的世界回滚。`tools/verification/start-pbr-client.ps1` 用于独立启动该模式；它接受世界、Forge 版本和可选 Rosetta 仓库参数，日志写入 `logs/`。

### Rosetta 读取调试

将自己的 Forge 1.20.1 Rosetta jar 放入本机 Maven 目录，用参数选择仓库和版本：

```powershell
.\gradlew.bat runClient '-PwithOculus' '-PclientPbrSmoke=New World' '-ProsettaRepo=mods_dev/maven' '-ProsettaVersion=1.0.0-20261113' '-ProsettaPort=48801' --console=plain --no-daemon --no-parallel
.\tools\verification\rosetta-readonly.ps1 -GameDirectory run -Port 48801
.\tools\verification\rosetta-readonly.ps1 -GameDirectory run -Port 48801 -ProbeFile tools/verification/pbr-snapshot.java
```

版本号应与自己的本地 Maven 坐标一致；没有传 `rosettaRepo` 时不会加载桥。使用 Maven 坐标让 ForgeGradle 正确 remap，不能直接以 `fg.deobf(files(...))` 代替。桥只绑定回环地址，token 从游戏目录读取，不写入代码或日志。脚本默认 ping，示例 Java 仅在服务端线程读取场景和材质元数据。禁止通过桥调用客户端渲染函数、GL、纹理上传或直接操纵渲染线程；截图和重载由客户端测试入口执行。

包含 F3Tweaker 并保留最终展示的开发启动：

```powershell
.\tools\verification\start-pbr-client.ps1 -ForgeVersion 47.4.10 -RosettaRepository mods_dev/maven -RosettaVersion 1.0.0-20261113 -Port 48801 -LogName pbr-final-client
```

## 当前限制

LookDev 的 studio shader 仍为底色预览，世界光影效果应在世界内检查。仅支持 UV0；其他 UV 集保留元数据并提示，不使用错误坐标采样。没有实现完整 Blender 节点图、任意 Phong 到 PBR 的推导、独立彩色 HDR emission、位移几何或通用透明排序。自动转换没有高度输入时使用默认高度；需要特定效果可提供预打包图像。
