# AssimpLoader

Minecraft Forge 1.20.1 的 Assimp 模型加载、动画渲染和 LookDev 模型预览工具。

[快速开始](../guides/QUICK_START.md) | [文档索引](../README.md) | [修复与验收](FIXES_2026-11-13.md) | [首轮审计](AUDIT_2026-11-13.md)

## 当前状态

当前版本包含基于 `feat/oculus-injector` / `ab9318e` 的审计修复、LookDev 预览与 Oculus PBR 接入。历史阶段文档中的完成率、功能清单和机器路径不代表当前验收结果。

| 模块 | 当前结论 |
| --- | --- |
| Assimp 导入 | 文件、内存与资源包 IO；支持相对 `.bin`/`.mtl` 侧文件；统一完整资源标识和导入模式缓存 |
| STATIC/DYNAMIC | STATIC 预变换顶点，DYNAMIC 保留节点；修复完整父子变换和 FBX 单位缩放 |
| 方块/物品渲染 | 静态 VBO/EBO 缓存、GPU 蒙皮及原版/CPU 回退；物品默认适配尺寸，支持 JSON display transforms |
| 骨骼/IK | 保留辅助父节点、多 root、绑定姿态及缺省动画通道；GPU 使用最强四个影响；IK 有数据测试 |
| LookDev | `L` / `/lookdev` 浏览全部已加载模型；GPU 预览、筛选、旋转、平移、缩放；节点页留空 |
| 节点/GLSL | 旧编辑器保留为未启用代码，不执行节点材质或 shader 注入 |
| Photon/Oculus | 可选依赖与 mixin 缺失保护；支持 Blender FBX/GLB 嵌入 PBR 图像与 LabPBR `_n`/`_s`，见[材质说明](../guides/OCULUS_PBR_USAGE.md)与[实机验收](PBR_VALIDATION_2026-11-13.md) |
| Gaussian Splatting | 修复布局、排序、LOD 和真实 TBO 回退；仍为独立原型，没有世界入口 |
| 热重载/缓存 | 版本化缓存、后台原子替换、删除恢复、F3+T 失效及客户端 GPU/贴图资源清理 |

修复位置与实际验证范围见 [修复记录](FIXES_2026-11-13.md)。通过构建和数据测试不等于所有格式、驱动或整合包的画面已通过验证。

## 环境与依赖

| 依赖 | 版本/来源 |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.1.3 |
| Java / Gradle | JDK 17 / wrapper 8.8 |
| LWJGL Assimp | 3.3.1，与 Minecraft 的 LWJGL 版本匹配 |
| ModernUI Forge | 1.20.1-3.11.0.1，客户端硬依赖 |
| ModernUI Core | 3.11.0，与当前 Forge 适配器配套，由开发构建解析 |
| Photon / LDLib | 1.1.17 / 1.0.48；编译兼容层需要，玩家运行时可选 |

Assimp 支持 FBX、GLTF/GLB、OBJ、DAE 等格式。资源包导入通过只读 IO 回调读取主文件及同 namespace 内的相对侧文件；禁止绝对路径和越界路径。首个测试样本可使用仓库提供的自包含 FBX/GLB。

## 构建

PowerShell：

```powershell
.\gradlew.bat test build --console=plain --no-daemon --no-parallel
```

Linux/macOS 使用 `./gradlew`。ModernUI 和固定版本的 Photon/LDLib 开发依赖从 Maven 解析，首次构建需要网络。`downloadDevDeps` 仅供单独下载并校验开发归档，不是编译前置步骤。可选模组不会打进发布包。

发布文件是 `build/libs/assimploader-1.0.0-all.jar`，包含 Assimp 绑定及 Windows/Linux/macOS 的 x64、ARM64 natives。普通 jar 不能代替完整发布包。当前机器仅能验证 Windows x64；打包不代表其他平台已实机通过。

本地代理、JDK 路径等配置应放在用户的 `~/.gradle/gradle.properties`，不要写入仓库。

## 安装与开发启动

1. 安装 Minecraft 1.20.1、Forge 47.1.3 和对应 ModernUI Forge。
2. 将 `-all.jar` 放入目标实例的 `mods/` 目录。
3. 先验证基础组合，再逐项加入可选 Photon/LDLib、Oculus。

开发客户端：

```powershell
.\gradlew.bat runClient --console=plain --no-daemon --no-parallel
```

在有命令权限的测试世界执行 `/assimpdemo` 可放置八组展示模型，包括 CIWS、刀、PBR 探针和传送门。单独获取传送门可用 `/give @s assimploader:showcase_portal_pbr`，详见 [世界展示](../showcase/MODEL_SHOWCASE.md)。

制作新资产时先阅读 [PBR 资产与 FBX 接入规范](../guides/PBR_ASSET_SPEC.md)，其中列明材质节点、贴图命名、UV、透明度、单位、层级和枢轴要求。光影设置及截图见 [PBR 使用](../guides/OCULUS_PBR_USAGE.md)与[实机验收](PBR_VALIDATION_2026-11-13.md)。

旧 CNC/CCB 示例代码、注册项、模型与关联美术资源已移除。旧存档中的 `assimploader:cnc` 方块及其方块实体会成为缺失注册项；升级前备份需要保留这些内容的存档。

LookDev 默认在世界中按 `L` 或执行 `/lookdev` 打开，操作见 [预览说明](../guides/LOOKDEV_PREVIEW.md)。开发日志位于 `run/logs/latest.log`，其他游戏实例使用其自己的 `logs/latest.log`。

## 项目结构

```text
src/main/java/cn/minerealms/assimploader/
  core/                       AssimpModelLoader、导入模式、后台解码
  model/                      Java 场景、节点、网格、材质和纹理
  animation/                  动画、骨骼层级、IK
  render/                     Forge geometry、方块/物品渲染、骨骼 TBO
  rendering/                  GPU 蒙皮、静态网格、Gaussian 原型
  lookdev/                    已加载模型 GPU 预览；保留未启用的旧节点编辑代码
  compat/                     Photon 资源桥接、Oculus 检测/PBR
  hotreload/                  资源重载
  example/                    可复用方块物品与客户端事件
  showcase/                   八组展示模型、方块注册与 /assimpdemo
src/main/java/au/edu/federation/  内嵌 IK 数学/Caliko 源码
src/main/resources/             模组元数据、shader、示例模型
src/test/java/                  数据和回归测试
```

主要流程是 geometry JSON → Assimp 解码 → Java scene/cache → BakedModel 或方块/物品渲染器。LookDev 使用同一缓存和材质贴图，不重新解析文件。

## 验证与文档

```powershell
.\gradlew.bat test --console=plain --no-daemon --no-parallel
git diff --check
```

测试报告：`build/reports/tests/test/index.html`。测试覆盖动画、IK、坐标转换、真实原生导入、顶点/矩阵打包、资源路径和部分 GLSL 生成，不覆盖实际 OpenGL 绘制与全部模组组合。

| 资料 | 内容 |
| --- | --- |
| [快速开始](../guides/QUICK_START.md) | 安装、构建、示例和自定义资源 |
| [文档索引](../README.md) | 按主题组织使用资料、设计和历史报告 |
| [修复记录](FIXES_2026-11-13.md) | 审计条目处理状态、构建与运行证据、剩余边界 |
| [首轮审计](AUDIT_2026-11-13.md) | 修复前问题、Git 线索和第一阶段验证快照 |
| [LookDev 预览](../guides/LOOKDEV_PREVIEW.md) | 当前界面的模型列表、视角操作和绘制方式 |
| [PBR 资产规范](../guides/PBR_ASSET_SPEC.md) | FBX/GLB 制作、贴图通道、导出与美术交付 |
| [PBR 实机验收](PBR_VALIDATION_2026-11-13.md) | 法线、金属、玻璃、光影开关和重载截图 |
| [动画资料](../guides/ANIMATION_USAGE_GUIDE.md) | 动画 API 和使用设计 |
| [IK 集成](IK_SYSTEM_INTEGRATION_PLAN.md) | IK 系统设计和阶段说明 |

## 来源与许可

项目许可见 [LICENSE](../../LICENSE)，内嵌源码、shader 及打包依赖的来源和许可材料见 [第三方声明](../THIRD_PARTY_NOTICES.md) 与仓库根目录的 `licenses/`；这些文件也随发布包分发。Caliko 的原始导入 revision 未被历史记录保存，因此不声称精确上游版本。

主要使用 Assimp、ModernUI、JOML、Gson 和内嵌 Caliko 代码。项目开发者包括 CARIERX，历史开发记录保留在仓库文档和 Git 中。
