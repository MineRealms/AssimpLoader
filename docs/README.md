# 文档索引

最后整理：2026-11-13。项目说明、使用指南、资产规范、验收记录和历史报告统一位于 `docs/`。仓库根目录仅保留简短 README 导航与 LICENSE；工具要求的 `.claude/skills/mc-debug.md` 保持原位。

## 当前入口

| 文档 | 用途 |
| --- | --- |
| [项目说明](PROJECT_OVERVIEW.md) | 依赖、构建和当前模块状态 |
| [快速开始](QUICK_START.md) | 构建、安装与示例检查 |
| [审计报告](AUDIT_2026-11-13.md) | 本次发现、修复、剩余问题、测试与运行边界 |
| [修复与验收](FIXES_2026-11-13.md) | 首轮审计后的当前处理状态、统一 BUILD、实际截图和资源重载 |
| [LookDev 预览](LOOKDEV_PREVIEW.md) | 当前已加载模型列表与 GPU 预览操作 |
| [FBX 世界展示](MODEL_SHOWCASE.md) | CIWS、刀及有/无贴图测试件的 `/assimpdemo` 放置说明 |
| [测试资产说明](TEST_ASSETS.md) | `model-tests/` 中的 Blender 源文件、FBX/GLB、图片、尺寸和枢轴 |
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
| LookDev | [当前预览](LOOKDEV_PREVIEW.md)、[集成与验收](LOOKDEV_INTEGRATION.md) | 所有已加载场景，静止姿态 GPU 预览；节点页留空 |
| 节点/GLSL | [GLSL 设计](LOOKDEV_GLSL_INTEGRATION_GUIDE.md)、[Transformer 状态](GLSL_TRANSFORMER_INTEGRATION_STATUS.md) | 旧编辑器未启用，不能视为当前可用材质工作流 |
| Photon | [用户资料](archive/PHOTON_USER_GUIDE.md)、[网格桥接](archive/PHOTON_MESH_INTEGRATION_GUIDE.md) | 当前依赖是 Photon 1.1.17/LDLib，不能据历史文档认定路径追踪已实现 |
| Oculus | [当前使用](OCULUS_PBR_USAGE.md)、[实机验收](PBR_VALIDATION_2026-11-13.md)、[源码调查](OCULUS_PBR_RESEARCH_2026-11-13.md) | 自有动态 loader 已接入；资源、磁盘和嵌入贴图可用，已检查光影开关和资源重载 |
| Gaussian | [当前修复](GAUSSIAN_FIXES_2026-11-13.md)、[历史报告](archive/GAUSSIAN_SPLATTING_REPORT.md) | 布局/排序/LOD/TBO 已修；没有方块/实体入口 |
| 性能 | [优化索引](OPTIMIZATION_INDEX.md)、[分析器](archive/PERFORMANCE_PROFILER_GUIDE.md) | 未附可复现性能基准，比例数字不作为承诺 |

## 历史记录

- [旧项目状态](PROJECT_STATUS.md)：原日期 2024-04-28。
- [归档索引](archive/README.md)：原根目录阶段报告、旧使用指南及会话记录统一移入 `docs/archive/`，保留原文件名和历史内容。
- `IK_PHASE*.md`、`LOOKDEV_PHASE*.md`、`OPTIMIZATION*.md`：对应阶段的设计与实现说明。
- [旧快速参考](QUICK_REFERENCE.md)、[Circle 测试](CIRCLE_BLOCK_TEST_GUIDE.md)：旧注册 ID 已不作为当前使用入口；当前八组模型见[世界展示](MODEL_SHOWCASE.md)。

旧 CNC/CCB 示例的注册、代码和关联美术资源已删除，旧存档中的相关方块/方块实体将缺失。审计和截图报告提及它们时属于清理前记录。

历史记录保留原内容；其中的分支、机器路径、完成率和测试结论不作为当前验收证据。后续修复请更新审计报告的问题状态或另建有日期的报告。
