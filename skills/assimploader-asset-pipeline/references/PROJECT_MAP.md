# Agent 项目地图

先读当前 checkout 的 README、AGENTS/CLAUDE 指令和 Git 状态。以下是导航，不替代现场源码；不同分支可能存在变动。

## 项目职责

| 项目 | 应在这里处理 |
| --- | --- |
| AssimpLoader | 模型解析、材质、节点、动画求值、GPU 渲染、通用编辑器和项目保存 |
| RigWorks | 玩家换模、人体映射、原版动作重定向、装备、角色工作台扩展和配置/动作包同步 |
| 消费者 Mod / AssimpShowcase | 具体实体/方块注册、车辆行为、机械关节和特效控制 |
| DCC | 几何、UV、图片烘焙、蒙皮、关键帧和源文件 |

RigWorks 的 `AssimpLoader/` 是 Git 子模块，独立克隆的 AssimpLoader 也可以使用本 skill。修改引擎时先提交引擎，再更新 RigWorks 子模块指针；不把子模块历史复制成普通目录。资产任务通常无需改 Java。

## AssimpLoader 导航

相对 AssimpLoader 根目录：

| 入口 | 用途 |
| --- | --- |
| `docs/guides/PBR_ASSET_SPEC.md` | UV0、普通 PBR、LabPBR、FBX 来源识别、单位与枢轴 |
| `docs/guides/DCC_EXPORTERS.md` / `tools/dcc/` | 三种 DCC 的统一任务和导出工具 |
| `docs/guides/MODEL_WORKBENCH.md` | 游戏内导入、编辑、预览、保存与 Apply |
| `docs/reference/WORKBENCH_PROJECT_FORMAT.md` | 项目格式、相对路径、内容哈希和 extensions |
| `docs/guides/KUBEJS_ANIMATION_GUIDE.md` | clip、层、pose、processor 和 IK API |
| `docs/reference/POSE_EVALUATION_PLAN.md` | 有序动画求值，避免多控制器抢同一骨骼 |
| `src/main/java/cn/minerealms/assimploader/animation/ModelInstanceDefinition.java` | `.instance.json` 真正接受的字段 |
| `src/main/java/cn/minerealms/assimploader/workbench/io/WorkbenchProjectStore.java` | 导入依赖、保存位置和原子写 |

## RigWorks 导航

相对独立 RigWorks 根目录：

| 入口 | 用途 |
| --- | --- |
| `docs/guides/ANIMATION_BEGINNER_GUIDE.md` | 原版、FBX、脚本、IK 的概念和选择 |
| `docs/guides/CUSTOM_RIG_NATIVE_CALIBRATION.md` | 绑定轴、参考姿态与原版姿态校准 |
| `docs/guides/KUBEJS_ANIMATION_GUIDE.md` | 本地控制器实际用法及 Demo |
| `docs/guides/CHARACTER_CONFIGURATION_SYNC.md` | 配置上传、服务器 revision、缓存与接收 |
| `docs/guides/AVATAR_BEHAVIOR_RUNTIME.md` | 可分发 `kubejs/rigworks/<avatar>/` 的受限 API |
| `src/main/java/cn/minerealms/rigworks/character/core/CharacterProfile.java` | profile 字段、动作意图、四元数校准 |
| `src/main/java/cn/minerealms/rigworks/character/client/LocalPlayerCharacter.java` | VANILLA / CLIPS / SCRIPTED 的真实执行入口 |

## 性能和保存约定

- 动画和物理按实例更新，渲染读取已完成姿态。不要在 render、每骨骼或每顶点循环运行 JS，也不要每 tick 解析 JSON/重建 processor。
- clip 变化时才切换播放；权重、目标和阶段使用现有缓存接口更新。
- `Apply` 与 `Save` 不同；保存后再打开同一个工作台项目，确认已有角色的动作继承。
- 初次导入通过预处理和工作台生成草稿；自动猜骨名只提供起点，不能还原任意 DCC 约束或替代轴向校准。
- 配置同步不传大资产、不逐帧上传骨骼矩阵；客户端各自根据观测到的玩家状态求值。
