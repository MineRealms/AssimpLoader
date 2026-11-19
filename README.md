# AssimpLoader

![AssimpLoader](src/main/resources/icon.png)

**Minecraft 1.20.1 Forge / 1.21.1 NeoForge 的通用 3D 资产、动画与 GPU 渲染基础库。**

将 Blender 等工具导出的 FBX / glTF / GLB 接入 Minecraft，保留材质、节点层级、骨骼和动画，在游戏内完成预览与配置。玩家角色应用已独立为 [RigWorks](https://github.com/MineRealms/RigWorks)，通过 Git 子模块引用本项目。

## 能力

| 领域 | 已实现内容 |
| --- | --- |
| 模型导入 | Assimp 场景解析、静态与动态导入、嵌入/外部贴图、层级与枢轴、资源包及外置模型覆盖 |
| PBR 材质 | Base Color、Normal、Metallic/Roughness、AO、Emission、透明/裁切材质；Oculus / Iris 的 LabPBR 接口，1.21.1 光影路径仍在回归验证 |
| 动画运行时 | 骨骼片段、状态切换、分层混合、遮罩、程序化姿态、IK、Morph 数据、Spring 与 Bullet 物理服务 |
| GPU 管线 | 常驻顶点/索引缓冲、GPU 蒙皮、骨骼调色板、实例批处理；按能力与渲染条件启用 MDI、Compute 和 Hi-Z 路径 |
| 游戏内工作台 | 文件导入、独立 GPU 预览、层级、Transform、材质参数、挂点、时间轴、撤销/重做、项目保存与重开；可注册的模组分区和功能页 |
| 数据与脚本 | JSON 实例/动画处理器、可选 KubeJS API、热重载、挂点和空间查询、可扩展工作台接口 |
| 调试 | LookDev 已加载模型列表、材质诊断、骨架/包围盒/碰撞显示和性能 HUD |

### 性能设计

网格与索引保留在 GPU，动画求值和物理模拟按实例更新；主画面和阴影复用已经完成的姿态。Oculus 路径缓存扩展顶点和姿态数据，避免重复 CPU 蒙皮。批处理、更新间隔、距离预算和大型模型剔除均有独立策略。

这些路径有适用条件和回退方式，并非所有透明物、材质和光影组合都能合成一次 draw call。详见 [GPU 批处理策略](docs/reference/GPU_BATCH_POLICY.md)、[运行时性能模式](docs/reference/RUNTIME_PERFORMANCE_MODES.md)和[性能记录](docs/benchmarks/README.md)。

## 使用

需要 **Minecraft 1.20.1 + Forge 47.x + Java 17**，或 **Minecraft 1.21.1 + NeoForge 21.1.x + Java 21**（1.21.1 移植在 `multiversion` 分支进行中）。安装对应节点的产物，例如 `versions/1.20.1-forge/build/libs/assimploader-1.20.1-forge-1.0.0.jar`，其中包含 Assimp 绑定与原生库。普通薄 JAR 不能代替它。

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat :1.20.1-forge:runClient -PwithKubeJS --console=plain
```

构建使用 [Stonecutter](https://stonecutter.kikugie.dev/)：`versions/1.20.1-forge` 与 `versions/1.21.1-neoforge` 各是一个子项目，共享根 `build.gradle`；`gradlew build` 会同时构建两者，`buildAndCollect` 把两个节点的发布包汇总到 `build/distributions`。

开发客户端默认包含 Oculus / Embeddium（1.21.1 节点为 Iris / Sodium），可用 `-PwithoutOculus` 关闭整套光影开发依赖；KubeJS 是可选依赖。发布 JAR 不捆绑这些开发模组。两节点的版本、实现进度与待验项目见 [1.21.1 迁移状态](docs/roadmap/1.21.1_MIGRATION_PLAN.md)。

在世界内按 `L` 或执行 `/lookdev` 打开原生 Screen 工作区，进入 `Edit` 导入、编辑并保存项目；不需要 ModernUI。资源包模型和材质可用 `F3+T` 重载。完整流程见 [快速开始](docs/guides/QUICK_START.md)和[模型工作台](docs/guides/MODEL_WORKBENCH.md)。

## 开发与扩展

KubeJS 的具体操作见 **[动画、骨骼、混合、IK 与烘焙动画教程](docs/guides/KUBEJS_ANIMATION_GUIDE.md)**，附 [可运行脚本 Demo](examples/kubejs/README.md)，从实例取得到重载、清理和性能约束逐步说明。

用 Agent 整理并导入资产，见 **[Agent 自动化工作流](docs/guides/AGENT_ASSET_WORKFLOW.md)** 和 [可复用 Skill](skills/assimploader-asset-pipeline/SKILL.md)。配套 [Blender / Maya / 3ds Max 一键导出](docs/guides/DCC_EXPORTERS.md) 可交付 FBX、图片及初始配置；新脚本按需求未做运行验证。

本仓库维护通用引擎及独立的 AssimpShowcase 示例模组；`build` 在 `versions/<节点>/build/libs` 分别生成引擎 JAR 与 `assimpshowcase-<mc版本>-<加载器>.jar`。引擎不依赖 RigWorks，单独克隆即可构建。

[RigWorks](https://github.com/MineRealms/RigWorks) 负责玩家换模、原版动作重定向、装备编辑与角色配置同步。其他模组可以使用同一套引擎 API 和工作台扩展接口。

| 从这里开始 | 文档 |
| --- | --- |
| 美术资产与 FBX | [PBR 接入规范](docs/guides/PBR_ASSET_SPEC.md) · [大型模型](docs/guides/LARGE_MODELS.md) |
| Agent 与 DCC 导出 | [自动化指南](docs/guides/AGENT_ASSET_WORKFLOW.md) · [三种软件导出](docs/guides/DCC_EXPORTERS.md) · [Skill](skills/assimploader-asset-pipeline/SKILL.md) |
| 材质与光影 | [Oculus PBR](docs/guides/OCULUS_PBR_USAGE.md) · [渲染集成](docs/reference/RENDERING_INTEGRATION.md) |
| 动画与脚本 | [动画指南](docs/guides/ANIMATION_USAGE_GUIDE.md) · [处理器](docs/reference/ANIMATION_PROCESSORS.md) · [KubeJS](docs/guides/KUBEJS_INTEGRATION.md) |
| 编辑器扩展 | [工作台](docs/guides/MODEL_WORKBENCH.md) · [GUI 分区与页面注册](docs/guides/WORKSPACE_EXTENSIONS.md) · [项目格式](docs/reference/WORKBENCH_PROJECT_FORMAT.md) |
| 示例 | [模型展台与 CIWS](docs/showcase/MODEL_SHOWCASE.md) · [装甲车](docs/showcase/VEHICLES.md) · [火箭尾焰](docs/showcase/ROCKET_EXHAUST.md) |
| 全部资料 | [文档索引](docs/README.md) · [历史记录](docs/history/README.md) · [路线图](docs/roadmap/IN_GAME_WORKBENCH_TODO.md) |

工作台编辑资产配置，不修改原始 Mesh、UV、蒙皮权重或动画关键帧。AssimpLoader 分区包含 Models、Edit、Debug；节点层级在 Edit / Tree 中编辑。RigWorks 和其他模组通过公共接口注册自己的分区与页面。旧 ModernUI 节点编辑器已移除。

## 许可

[MIT](LICENSE)。依赖及第三方源码的许可见 [第三方声明](docs/THIRD_PARTY_NOTICES.md)。
