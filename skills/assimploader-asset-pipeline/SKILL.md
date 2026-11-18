---
name: assimploader-asset-pipeline
description: Prepare Blender, 3ds Max or Maya assets and import them into Minecraft AssimpLoader or RigWorks. Use for agent operated modeling, PBR preparation, skeletal animation export and in-game application using these projects.
---

# AssimpLoader / RigWorks Asset Pipeline

将玩家的 3D 资产在 DCC 副本中整理、导出、导入 Minecraft 并应用。先读项目事实，再根据已安装的软件执行；交付应包括可继续编辑的源副本、运行时资产和保存的游戏配置。

本 skill 随 AssimpLoader 的 `skills/assimploader-asset-pipeline/` 分发。在仓库内使用时，仓库根是相对本文件的 `../../`；安装到用户 skills 目录后，从当前工作区定位具有 `tools/dcc/assimploader_bundle.py` 的 AssimpLoader checkout，或 RigWorks 的 `AssimpLoader/` 子模块，找不到才询问仓库位置。下面工具/项目文档路径均以已定位仓库为准，不按安装目录推算。RigWorks 是独立仓库。不要将 `run/`、盘符、端口、客户端 PID 或工作目录写死。

## 开始时确定环境

先检查会话已有信息、仓库和已提供的工具。只对仍然缺失的信息向玩家发起一次简短询问：

> 你使用 Blender、3ds Max 还是 Maya，具体版本和源文件在哪里？要作为玩家模型、机械实体还是静态展示？Minecraft 的游戏/开发目录在哪里，期望游戏内尺寸是多少？

从已有上下文能确定的字段不重复询问。未知尺寸可以先使用源单位预览，标注尚未确认；源文件、目标软件和游戏实例不明确时不能对任意窗口执行修改。

1. 阅读 [项目地图](references/PROJECT_MAP.md)，识别 AssimpLoader、RigWorks 和消费者的边界。
2. 按 [MCP 连接](references/MCP_CONNECTIONS.md) 复用已有连接，或为所选软件配置桥接。
3. 读取应用版本、场景路径、选择、层级、材质、尺寸和动画库存，确认目标实例。
4. 根据 [资产到游戏](references/ASSET_TO_GAME.md) 执行副本编辑、导出、游戏导入和保存。

MCP 项目的名字和工具参数可能变化，实际工具发现和所安装版本的文档优先。没有目标软件或 FBX 插件时明确记录缺项；MCP 不能替代软件本身，也不能让 Blender 原生读取 `.max`。

## 执行约定

- 已授权处理资产后，连续完成合理的编辑、导出、导入和预览步骤；正常阶段转换不再逐项询问。
- 先保存独立源副本。保留角色蒙皮、绑定姿态和稳定名称；机械部件保留枢轴及层级。按玩家要求决定减面和合并，不自行把会动的部件合成一块。
- 最终材质使用 UV0 图片。Blender FBX/标准 GLB 的 PBR 路径和其他 DCC FBX 不同，详见 PBR 规范；不能将“有底色”当作完整 PBR 验收。
- 使用仓库的 `tools/dcc/*_export.py` 和统一 job，避免每次重新编写整套导出器。完整设置与入口见 `../../docs/guides/DCC_EXPORTERS.md`。
- 任务配置、导出清单、Minecraft 运行时配置、工作台保存项目是不同格式。不要把清单当作引擎可执行配置，也不要伪造材质 sidecar 或骨骼/片段名。
- 优先让游戏工作台导入、分析和保存项目。角色数据通过 RigWorks 扩展应用，保留原角色动作、装备及专用脚本身份；不以空白 profile 覆盖已校准角色。
- 原版动画模式、导入片段与脚本的区别见 RigWorks `docs/guides/ANIMATION_BEGINNER_GUIDE.md`。推荐原版动作基线加高质量片段和有限 IK；完整控制器不是必需的第一步。
- 角色视觉配置不会修改服务端玩家碰撞箱或伤害；FBX 根运动也不会自动移动实体。普通模型不会凭导出 JSON 自动注册物品、方块或实体。
- 在客户端线程调度适当的操作，不从远程调试线程直接调用 OpenGL 或修改渲染线程对象。Rosetta 的读取用途、入口和限制以目标环境的 USAGE 文档为准。
- 重载、导入和脚本运行依赖各自真实完成信号。保持客户端开启，让玩家接着查看。

## 完成条件

用户要求实机验收时，检查模型尺寸/方向、缺图诊断、实际 clip 名称、原版动作和手持挂点，保存并重新打开，留下截图和具体路径。需要 PBR 时检查目标光影中的法线、粗糙度、金属和透明度。

多人只分发配置和专用动作包，接收端需要已有相同资产；不要声称上传 profile 就上传了 FBX。通用 `client_scripts` 不自动分享。

用户明确省略某项验证时照做，并在交付中标出未验证项。这一版附带的三个导出器及公共打包器按用户要求仅编写，未执行、未做语法检查或 DCC/游戏验收；后续真正使用时读取实际结果。

## 交付摘要

说明使用的软件与版本、源副本、FBX/贴图/配置位置、游戏项目路径、已应用的角色或消费者、当前验证范围和剩余问题。记录本次环境值到资产交付清单，不把它们升级为项目固定路径。
