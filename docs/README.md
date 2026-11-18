# 文档索引

最后整理：2026-11-18。项目说明、使用指南、资产规范、验收记录和历史报告统一位于 `docs/`。仓库根目录仅保留简短 README 导航与 LICENSE；工具要求的 `.claude/skills/mc-debug.md` 保持原位。

## 当前入口

| 文档 | 用途 |
| --- | --- |
| [项目说明](PROJECT_OVERVIEW.md) | 依赖、构建和当前模块状态 |
| [快速开始](QUICK_START.md) | 构建、安装与示例检查 |
| [开发客户端](DEVELOPMENT_CLIENT.md) | ModernUI、F3Tweaker、KubeJS 运行依赖及导入玩家模型验收 |
| [模型扩展实施报告](EXPANSION_IMPLEMENTATION_2026-11-16.md)、[架构评估](EXPANSION_ARCHITECTURE_ASSESSMENT_2026-11-16.md) | 实例、分层/IK、Morph、Spring、碰撞、挂点特效、CPU 基准和 GPU 验证边界 |
| [运行时性能模式](RUNTIME_PERFORMANCE_MODES.md)、[鹿展示与 Blender 工程](DEER_SHOWCASE.md) | 全局/资产预算、距离更新频率、四腿 IK 烘焙、Spring 和碰撞代理 |
| [KubeJS 集成](KUBEJS_INTEGRATION.md) | 可选客户端模型控制、性能参数、挂点/命中查询，以及服务端鹿状态同步；附脚本示例 |
| [GPU Batch Policy](GPU_BATCH_POLICY.md), [Validation](GPU_BATCH_VALIDATION_2026-11-16.md) | GPU instancing, MDI, current-frame Hi-Z, budgets and crowd acceptance |
| [Player Rendering Fixes](PLAYER_RENDERING_FIXES_2026-11-17.md) | Mixed materials, Oculus GPU deformation, CPU skinning reuse and local temporal shader fixes |
| [Custom Rig Calibration](CUSTOM_RIG_NATIVE_CALIBRATION.md) | Imported clips, native player state layers, hand sockets and Blender/IK responsibilities |
| [原版动作兼容](PLAYER_VANILLA_ACTIONS.md)、[装备与人物视图](RIGWORKS_EQUIPMENT_VIEWS.md) | 原版状态观测、动作分层与回退、命名挂点及物品规则、装备层、第一人称手臂和验证边界 |
| [Equipment Transform Workbench](EQUIPMENT_TRANSFORM_WORKBENCH.md) | In-world left/right hand calibration, orbit camera and persistent equipment JSON |
| [角色配置同步](CHARACTER_CONFIGURATION_SYNC.md)、[专用动作脚本](AVATAR_BEHAVIOR_RUNTIME.md)、[同步与工作台验收](RIGWORKS_SHARING_VALIDATION_2026-11-18.md) | 默认分享、版本冲突、JSON/JS 分发、按玩家隔离执行、压缩缓存及真实双客户端验证 |
| [游戏内模型工作台](MODEL_WORKBENCH.md)、[项目格式](WORKBENCH_PROJECT_FORMAT.md) | `/lookdev` → Edit；导入、GPU 骨骼预览、变换/材质/挂点、时间轴、保存和项目 ZIP；含 RigWorks 扩展与共享边界 |
| [工作台保存与角色继承修复](WORKBENCH_PERSISTENCE_FIXES_2026-11-18.md) | 保存后重开、外部 JSON Reload、原角色动作继承及实机验证 |
| [游戏内模型工作台 TODO](IN_GAME_WORKBENCH_TODO.md) | 按源码区分已实现与剩余工作；含资源包导出、通用世界视口、通用节点/材质同步等未完成项 |
| [渲染性能图表与评估](benchmarks/README.md) | 六组图表、历史实测、经验数据、内存/降频计算和管线技术评估 |
| [自定义玩家角色缺口评估](CUSTOM_PLAYER_GAP_ASSESSMENT_2026-11-17.md) | 对照 cleanroom 规格，区分 FBX/GLB 玩家换模与 PMX/VMD 完整角色系统 |
| [Client Visual Authority](CLIENT_VISUAL_AUTHORITY.md) | Local visual alignment and server collision authority |
| [审计报告](AUDIT_2026-11-13.md) | 本次发现、修复、剩余问题、测试与运行边界 |
| [修复与验收](FIXES_2026-11-13.md) | 首轮审计后的当前处理状态、统一 BUILD、实际截图和资源重载 |
| [LookDev 预览](LOOKDEV_PREVIEW.md) | 当前已加载模型列表与 GPU 预览操作 |
| [FBX 世界展示](MODEL_SHOWCASE.md) | CIWS、刀及有/无贴图测试件的 `/assimpdemo` 放置说明 |
| [火箭发动机展示](ROCKET_ENGINE_SHOWCASE.md) | 独立试车台、喷口坐标、原版示例和本地 PBR 发动机资源包 |
| [火箭尾焰](ROCKET_EXHAUST.md)、[光影源码接入](ROCKET_SHADER_RESEARCH.md)、[实机验收](ROCKET_VALIDATION_2026-11-14.md) | GPU 体积通道、物理输入与视觉近似、配置、Oculus 桥接与截图 |
| [质量流量模型](ROCKET_FLOW_MODEL.md)、[体积历史与 LOD](ROCKET_TEMPORAL.md) | 静止试车台的 kg/s 输入、喷口状态、壁温、烟气输运、半分辨率积分与历史拒绝 |
| [测试资产说明](TEST_ASSETS.md) | `model-tests/` 中的 Blender 源文件、FBX/GLB、图片、尺寸和枢轴 |
| [大型模型与巨物显示](LARGE_MODELS.md)、[客户端资产验收](TESTING.md) | 真实包围盒、可选强制显示、整组/部件交付和本地资源包截图 |
| [装甲车实体](VEHICLES.md) | 可驾驶实体、加减速、轮轴动画、第三人称相机和本地 PBR 车模接入 |
| [PBR 资产与 FBX 接入规范](PBR_ASSET_SPEC.md) | 美术必读：材质连接、图片命名/通道、玻璃、UV0、单位、层级与枢轴 |
| [Oculus PBR 使用](OCULUS_PBR_USAGE.md)、[PBR 实机验收](PBR_VALIDATION_2026-11-13.md) | 当前动态贴图 loader、Blender 材质约定、光影设置与实际截图 |
| [Blender/FBX PBR 源码调查](OCULUS_PBR_RESEARCH_2026-11-13.md)、[FBX 材质映射](FBX_PBR_PIPELINE_RESEARCH.md) | Oculus 动态 loader 扩展、Blender/Assimp 实际槽位、LabPBR 交付约定及前次 API 判断更正 |
| [第三方声明](THIRD_PARTY_NOTICES.md) | 内嵌源码、shader、打包依赖的归属与许可 |

功能是否可用应先查修复与验收记录。首轮审计保留历史快照。下面的技术资料包含目标设计、阶段记录与未接入代码，文件名中的 COMPLETE/完成不代表当前运行验证通过。

## 使用与技术资料

| 主题 | 资料 | 阅读提示 |
| --- | --- | --- |
| 配置 | [配置指南](CONFIG_USER_GUIDE.md)、[配置设计](CONFIG_SYSTEM_DESIGN.md) | 部分旧字段明确保留为预留，见修复记录 |
| 导入与动画 | [加载修复](LOADING_FIXES.md)、[动画指南](ANIMATION_USAGE_GUIDE.md)、[骨骼分析](SKELETAL_ANIMATION_ANALYSIS.md) | 已补完整父层级、多 root 和绑定姿态；复杂动画仍需样本实机检查 |
| IK | [集成计划](IK_SYSTEM_INTEGRATION_PLAN.md)、[控制器](IK_PHASE4_CONTROLLERS.md)、[预设](IK_PHASE5_PRESETS.md) | 已有 JUnit 覆盖，游戏内约束效果未在本次验收 |
| 热重载 | [加载生命周期](LOADING_FIXES.md)、[渲染资源](RENDERING_INTEGRATION.md) | 当前实现替换/删除恢复和 GPU 清理；旧设计保留历史资料 |
| LookDev / 工作台 | [当前预览](LOOKDEV_PREVIEW.md)、[模型工作台](MODEL_WORKBENCH.md)、[项目格式](WORKBENCH_PROJECT_FORMAT.md) | Models 浏览已加载场景，Edit 提供独立动画与配置编辑；旧 Nodes 页仍不是材质节点编辑器 |
| 节点/GLSL | [GLSL 设计](LOOKDEV_GLSL_INTEGRATION_GUIDE.md)、[Transformer 状态](GLSL_TRANSFORMER_INTEGRATION_STATUS.md) | 旧编辑器未启用，不能视为当前可用材质工作流 |
| Photon | [用户资料](archive/PHOTON_USER_GUIDE.md)、[网格桥接](archive/PHOTON_MESH_INTEGRATION_GUIDE.md) | 当前依赖是 Photon 1.1.17/LDLib，不能据历史文档认定路径追踪已实现 |
| Oculus | [当前使用](OCULUS_PBR_USAGE.md)、[实机验收](PBR_VALIDATION_2026-11-13.md)、[源码调查](OCULUS_PBR_RESEARCH_2026-11-13.md) | 自有动态 loader 已接入；资源、磁盘和嵌入贴图可用，已检查光影开关和资源重载 |
| Gaussian | [当前修复](GAUSSIAN_FIXES_2026-11-13.md)、[历史报告](archive/GAUSSIAN_SPLATTING_REPORT.md) | 布局/排序/LOD/TBO 已修；没有方块/实体入口 |
| 性能 | [扩展运行时 CPU 探针](EXPANSION_IMPLEMENTATION_2026-11-16.md)、[优化索引](OPTIMIZATION_INDEX.md)、[分析器](archive/PERFORMANCE_PROFILER_GUIDE.md) | 已有 64/256 骨、1/10/50 实例合成 CPU 基准；未覆盖实际世界帧率或 GPU 加速比，旧比例数字不作为承诺 |

## 历史记录

- [旧项目状态](PROJECT_STATUS.md)：原日期 2024-04-28。
- [归档索引](archive/README.md)：原根目录阶段报告、旧使用指南及会话记录统一移入 `docs/archive/`，保留原文件名和历史内容。
- `IK_PHASE*.md`、`LOOKDEV_PHASE*.md`、`OPTIMIZATION*.md`：对应阶段的设计与实现说明。
- [旧快速参考](QUICK_REFERENCE.md)、[Circle 测试](CIRCLE_BLOCK_TEST_GUIDE.md)：旧注册 ID 已不作为当前使用入口；当前八组模型见[世界展示](MODEL_SHOWCASE.md)。

旧 CNC/CCB 示例的注册、代码和关联美术资源已删除，旧存档中的相关方块/方块实体将缺失。审计和截图报告提及它们时属于清理前记录。

历史记录保留原内容；其中的分支、机器路径、完成率和测试结论不作为当前验收证据。后续修复请更新审计报告的问题状态或另建有日期的报告。
