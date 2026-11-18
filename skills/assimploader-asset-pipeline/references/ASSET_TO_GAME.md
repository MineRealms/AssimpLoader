# 从源场景到游戏应用

## 1. 先读资产，再动副本

读取源场景尺寸、单位、前向、层级/枢轴、Mesh/材质数量、UV0、蒙皮骨架和动画库存。保存独立 `.blend`、`.max` 或 `.ma/.mb` 副本后编辑，不将源场景不可逆地清理成运行时文件。

不同类型的处理重点：

| 资产 | 优先处理 |
| --- | --- |
| 静态物体 | 尺寸、地面原点、可见几何、材质数量和 UV |
| 机械 | 旋转子件、真实枢轴、父子继承、局部轴和动态导入 |
| 人物 | 绑定姿态、骨长、蒙皮、人体映射、前向与左右手 |
| PBR | UV0 图片、法线方向、粗糙度/金属度语义及透明材质分类 |

推荐修改和三种应用内的一键调用见 `../../../docs/guides/DCC_EXPORTERS.md`。不能统一对所有角色执行 Apply All、Reset XForm、冻结变换或合并网格。

## 2. 统一任务和产物

从 `../../../tools/dcc/export-job.example.json` 创建资产自己的 job。`output_dir` 相对 job 文件，或使用玩家提供的绝对路径；不要写项目作者的盘符。`roots` 指定资产根，也可使用当前选择。

只有原版动作需求时，无需制作或导出假动画：保持 `bake_animation=false`，在游戏里使用 VANILLA。已有动作时启用烘焙并填真实 Action/帧范围，导出命名片段。

在软件自己的 Python 环境执行对应 `export_job(job_path)`。它调用共享打包器生成：

```text
<output_dir>/
  pack.mcmeta
  assets/<namespace>/models/<asset_id>/
    model.fbx
    textures/...
    geometry.json
    model.instance.json
    profile.json             # character.enabled=true 时
    export-manifest.json
```

这是资源包目录及导出报告，不是 `.workbench.json` 项目。`export-manifest.json` 供 Agent 阅读，不是额外运行时材质格式。自动人体/动作匹配只是建议，FBX 重新导入后的名称和轴向才是最终依据。

默认拒绝覆盖 FBX；显式重导时设 `overwrite=true`。手工校准过的配置默认保留，需要有意重新生成时才设 `overwrite_configs=true`，或使用新输出目录。

## 3. 材质分支

- Blender：标准 Principled + 已保存 UV0 图片，普通法线 OpenGL Y+，独立 Roughness/Metallic。程序材质先烘焙。
- Maya/Max：先导出完整几何、骨架、动作和图片。针对 PBR，推荐在 Blender 的导出副本中检查并重接图片后重新导出，或按 PBR 规范交付预打包 LabPBR。
- 不能只把 Autodesk FBX 的 producer 字段改成 Blender 来启用转换，也不能把原始粗糙度直接改名为 `_s`。

Blender 规范化导入后，先读取新层级、骨名和 clip 名，再更新 job；不沿用已经失效的源名称。不要额外补偿一次单位或 90 度旋转。

## 4. 游戏导入与应用

1. 使用明确的 Minecraft 实例。资源包要放进该实例的 `resourcepacks/` 并启用，随后 `F3+T`，等待导入/重载结束。
2. `/lookdev` → `Edit`，导入具体 FBX，让工作台分析并创建项目。也可先通过资源包中的模型预览，但不能认为复制资源包自动生成了实体。
3. 普通资产检查树、材质、尺寸、动画和挂点；由现有消费者使用。需要新方块/物品/实体注册时，那是额外 Java/消费者任务。
4. 玩家资产进入 Character / RigWorks。已有角色优先打开其保存项目，保留原有映射、solver、装备和脚本；首次导入才使用名称推断作为起点。
5. 若使用打包器的资源 profile，确认游戏已启用该资源包，再用 `/assimploader character profile <namespace>:models/<asset_id>/profile.json` 选择，并启用角色；也可以直接从工作台校准、保存和 Apply。
6. 预览原版动作时用 VANILLA；导入片段自动选择用 CLIPS；需要组合逻辑时再使用 SCRIPTED。避免新导入后误选只有 idle 的控制器。
7. 保存，关闭编辑页后重开同一项目，确认变换及原角色动作继承；保留游戏客户端开启。

GUI 自动化使用环境实际提供的工具。没有 GUI 控制或游戏 API 时，不能用未存在的工作台 CLI、反射线程或渲染线程写操作代替；清楚记录已经生成的产物和等待实际导入的阶段。

## 5. 按要求验收

通常需要世界截图、PBR 开关对照、运动/特殊动作、左右手、保存重开和日志诊断。测试范围按任务选择，不为资产导出启动无关 benchmark。

用户要求不测试时，跳过导出运行/游戏验证并标注产物为未验证。本次工具交付遵循这个约定；未来用户让 Agent 实际处理资产时，再执行真实导出并根据错误适配软件版本。

多人应用需要服务器支持 RigWorks、上传开关开启、接收端允许他人模型且已有匹配资产。全局 `kubejs/client_scripts` 不自动上传；共享行为放在专用 `kubejs/rigworks/<avatar>/` 动作包。
