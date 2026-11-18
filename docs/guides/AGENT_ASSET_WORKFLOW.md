# 用 Agent 完成 3D 资产到 Minecraft 的流程

这套流程让玩家描述目标后，由 Agent 操作建模软件、整理材质与骨架、导出文件，再在 AssimpLoader / RigWorks 工作台中导入、预览、应用和保存。游戏工作台仍是编辑和保存配置的中心。

## 给 Agent 的入口

仓库提供可直接交给 Agent 阅读的 [SKILL.md](../../skills/assimploader-asset-pipeline/SKILL.md)，以及按需读取的项目地图、MCP 连接和游戏导入参考。它是工具无关的操作指南，支持已经暴露 DCC MCP 的 Agent。

可以把下面这段作为任务开头，并附自己的源文件路径：

```text
使用 AssimpLoader 仓库 skills/assimploader-asset-pipeline/SKILL.md。
先读取项目现状和我的已有环境信息；仍然缺失时，一次询问我使用的
Blender / 3ds Max / Maya 版本、源文件、游戏目录、资产用途和目标尺寸。
推荐并连接对应 MCP，操作源副本，整理后使用 tools/dcc 导出。
随后进入 Minecraft 工作台导入、预览、保存和 Apply，保留客户端开启。
已有角色要继承原来的动作、装备及脚本配置。
```

也可将整个 `skills/assimploader-asset-pipeline` 文件夹安装到所用 Agent 的 skills 目录，随仓库工具更新。移动 skill 时需要同时保留仓库链接或按新位置修改工具路径；只复制一个 SKILL.md 会丢失参考文件。

## 玩家需要提供什么

| 信息 | 例子或作用 |
| --- | --- |
| 软件与版本 | Blender、3ds Max 或 Maya；影响 Python、FBX 插件和 MCP |
| 源资产 | 场景文件、贴图目录或已打开的正确场景 |
| 用途 | 玩家模型、静态展示、带关节机械或具体实体 |
| 游戏环境 | 实际游戏目录/开发仓库、已装 AssimpLoader/RigWorks、目标世界 |
| 尺寸和效果 | 例如身高 1.8 格、轮子可转、保留透明/PBR、不减面 |

已有上下文能确定的信息，Agent 应直接使用。三种软件不需要都安装；选择一种制作路线即可。MCP 只提供控制接口，`.max` 仍需 3ds Max 打开。

## 三种软件怎么选

| 软件 | 本项目建议 |
| --- | --- |
| Blender | 当前 PBR FBX 来源识别最直接；Principled + 图片、保留骨架/NLA、使用统一导出器 |
| Maya | 保留 Joint/Skin，烘焙需要的控制器结果；原生 FBX 后推荐经 Blender 检查 PBR 或提供 LabPBR |
| 3ds Max | 保留 Skin、Dummy 和关节枢轴；CAT/Biped 动作烘焙为骨骼结果，PBR 路线同上 |

MCP 推荐和自动连接步骤见 [连接参考](../../skills/assimploader-asset-pipeline/references/MCP_CONNECTIONS.md)。现有连接优先，不重复安装。新安装若需要重启 Agent 或商业软件授权，必须如实报告当前阶段；指南不会凭空获得不存在的工具。

## 一次导出会得到什么

三种软件共享 [导出任务模板](../../tools/dcc/export-job.example.json)，都调用应用内 Python 的 `export_job(job_path)`。完整参数、各软件推荐修改及调用命令见 [DCC 一键导出](DCC_EXPORTERS.md)。

输出包括 FBX、实际图片、可启用的资源包元数据、geometry JSON、片段状态、可选 RigWorks profile 和交付清单。名称推断是初始草稿，轴向、手持和最终片段名仍由游戏预览确认。

导出资源包、工作台项目和多人配置包是三个不同东西：

| 内容 | 作用 |
| --- | --- |
| 导出资源包 | 给游戏提供 FBX、图片和资源 ID；不自动注册新实体/方块 |
| 工作台项目 | 保存导入后编辑的变换、材质、挂点及 RigWorks 扩展 |
| 多人配置/动作包 | 分享角色配置与专用 JS；不包含大模型资产 |

## 自动化完成到哪一步

具备 DCC MCP 和游戏 GUI 控制时，Agent 按 skill 连续完成编辑、导出和工作台操作，不需要玩家逐步传话。遇到真实缺失的工具、软件或目标世界时，报告准确的阻塞阶段，保留已经生成的产物。

普通模型由工作台或已有消费者使用；玩家模型通过 RigWorks 的 Character 扩展应用。要新增一个可骑乘实体或有游戏逻辑的方块，还需要该消费者的注册和逻辑，不是多写一个模型 JSON 就能完成。

本次新增导出脚本按需求仅编写，**没有运行、语法检查或实机验收**。这不影响后续 Agent 按指南实际执行导出并根据软件版本修正；不能把这次文档交付描述成三个 DCC 都已经自动接入成功。

理解玩家动作的基础概念，可先阅读 [RigWorks 动画入门](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/ANIMATION_BEGINNER_GUIDE.md)，再看 [KubeJS 操作指南](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/KUBEJS_ANIMATION_GUIDE.md)。
