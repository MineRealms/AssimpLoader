# AssimpLoader KubeJS Demo

详细操作报告：[动画、骨骼、混合、IK 与烘焙](../../docs/guides/KUBEJS_ANIMATION_GUIDE.md)。这些脚本不是自动安装到玩家实例的生产策略。

## 可见的动画 Demo

1. 安装 AssimpLoader 发布 JAR（如 `versions/1.20.1-forge/build/libs/assimploader-1.20.1-forge-1.0.0.jar`）、AssimpShowcase、KubeJS / Rhino / Architectury。
2. 将 [assimp_animation_tutorial.js](client_scripts/assimp_animation_tutorial.js) 放入实例 `kubejs/client_scripts/`，执行 `/kubejs reload client_scripts`。
3. `/give @s assimploader:showcase_highbone_probe`，手持后观察 `Wave`；下蹲切换局部片段混合、最后一个节点的增量姿态和 Morph。

模型是仓库自带测试件，不需要外部付费资产。脚本不注册新物品、不创建渲染器、不操作服务端。

| 脚本 | 内容与前提 |
| --- | --- |
| [动画教程](client_scripts/assimp_animation_tutorial.js) | 同一实例上的烘焙片段、遮罩层、缓存 pose 和 Morph；需要 Showcase highbone 物品 |
| [最小 Morph](client_scripts/assimp_held_morph.js) | 独立的蹲伏 Morph 示例；不要与上面同时控制同一物品 |
| [Shader 参数](client_scripts/assimp_shader_parameters.js) | 安装匹配的 `assets/` 定义，目标光影须主动消费 uniform；不自动注入 shader pass |
| [服务端鹿状态](server_scripts/assimp_deer.js) | Showcase 游戏实体的服务端策略，与客户端骨骼 API 分开 |

IK 和自定义玩家混合的可见样例在 [RigWorks 示例](https://github.com/MineRealms/RigWorks/blob/main/examples/kubejs/README.md)。通用 IK 不要求 RigWorks，但必须给实际三维骨架提供有祖先关系的 chain，Highbone 的同级节点不适合作为 IK 样本。
