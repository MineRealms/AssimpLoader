# AssimpLoader 文档

当前使用说明、技术参考与历史过程分开维护。玩家角色模块已迁往独立的 [RigWorks 仓库](https://github.com/MineRealms/RigWorks)。

## 使用指南

| 入口 | 内容 |
| --- | --- |
| [快速开始](guides/QUICK_START.md) | 构建、安装、模型接入与热重载 |
| [开发客户端](guides/DEVELOPMENT_CLIENT.md) | 可选模组、启动参数与运行目录 |
| [PBR 资产规范](guides/PBR_ASSET_SPEC.md) | FBX/GLB、UV、法线、金属/粗糙度、玻璃、单位与枢轴 |
| [Oculus PBR](guides/OCULUS_PBR_USAGE.md) | 动态材质、LabPBR 与光影设置 |
| [模型工作台](guides/MODEL_WORKBENCH.md) | 导入、编辑、预览、保存、恢复与扩展 |
| [LookDev](guides/LOOKDEV_PREVIEW.md) | 已加载模型列表与 GPU 预览 |
| [动画](guides/ANIMATION_USAGE_GUIDE.md) · [KubeJS](guides/KUBEJS_INTEGRATION.md) | 运行时控制与脚本 API |
| [KubeJS 动画操作报告](guides/KUBEJS_ANIMATION_GUIDE.md) · [Demo](../examples/kubejs/README.md) | 片段、骨骼、混合、IK、烘焙及实际安装步骤 |
| [配置](guides/CONFIG_USER_GUIDE.md) · [大型模型](guides/LARGE_MODELS.md) | 配置文件、显示范围与剔除策略 |
| [测试](guides/TESTING.md) · [测试资产](guides/TEST_ASSETS.md) | 验证入口、样本来源与约束 |

## 技术参考

- [GPU 批处理](reference/GPU_BATCH_POLICY.md)、[运行时性能模式](reference/RUNTIME_PERFORMANCE_MODES.md)、[Compute 蒙皮](reference/COMPUTE_SKINNING.md)、[性能记录](benchmarks/README.md)。
- [渲染接入](reference/RENDERING_INTEGRATION.md)、[Oculus 姿态缓存](reference/OCULUS_POSE_BUFFER_CACHE.md)、[渲染姿态插值](reference/RENDER_POSE_INTERPOLATION.md)。
- [动画处理器](reference/ANIMATION_PROCESSORS.md)、[求值计划](reference/POSE_EVALUATION_PLAN.md)、[Morph](reference/MORPH_TARGETS.md)、[Spring/碰撞](reference/SPRING_AND_COLLISION_RUNTIME.md)。
- [项目格式](reference/WORKBENCH_PROJECT_FORMAT.md)、[加载生命周期](reference/LOADING_FIXES.md)、[KubeJS 重载](reference/KUBEJS_RELOAD_LIFECYCLE.md)、[Shader 参数桥](reference/SHADER_PARAMETER_BRIDGE.md)。
- [模型能力清单](reference/MODEL_CAPABILITIES_AND_CLIP_MORPHS.md)、[世界实例](reference/WORLD_INSTANCE_RUNTIME.md)、[碰撞代理](reference/PCA_OBB_COLLISION.md)。

## 示例与归档

[CIWS / 模型展台](showcase/MODEL_SHOWCASE.md)、[鹿](showcase/DEER_SHOWCASE.md)、[装甲车](showcase/VEHICLES.md)、[火箭发动机](showcase/ROCKET_ENGINE_SHOWCASE.md)、[体积尾焰](showcase/ROCKET_EXHAUST.md)、[质量流量模型](showcase/ROCKET_FLOW_MODEL.md)、[体积历史](showcase/ROCKET_TEMPORAL.md)。示例属于单独的 AssimpShowcase JAR。

待办见[工作台路线图](roadmap/IN_GAME_WORKBENCH_TODO.md)。审计、阶段设计、调查和验收过程放在 [history/](history/README.md)，更早资料保存在 [archive/](archive/README.md)。历史报告中的机器路径、旧模块结构和完成率不代表当前状态。

许可与打包归属见[第三方声明](THIRD_PARTY_NOTICES.md)。
