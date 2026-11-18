# Blender、Maya、3ds Max 一键导出工具

本页面向美术人员和负责自动处理资产的 Agent。它说明如何把已打开的场景导出为 AssimpLoader 可导入的 FBX，并交给统一打包器生成配置和资产清单。玩家动作的来源、人体骨架映射和动画混合请先阅读 [RigWorks 动画教程](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/KUBEJS_ANIMATION_GUIDE.md)。

**交付状态：2026-11-18，按本次需求编写，未执行脚本，未进行语法检查、真实 DCC 导出或游戏画面验收。** API 参考已查阅，但不同软件、FBX 插件和材质版本仍可能需要适配。不要把本文中的步骤当成已经完成的实机验收。

## 1. 这些脚本做什么

| 工具 | 运行位置 | 作用 |
| --- | --- | --- |
| [blender_export.py](../../tools/dcc/blender_export.py) | Blender Python | 选择资产依赖、临时整理图片路径、导出 FBX；支持 Action/NLA 片段 |
| [maya_export.py](../../tools/dcc/maya_export.py) | Maya Python，需 `fbxmaya` | 导出选中层级、Skin、Blend Shape、烘焙动画和命名 takes |
| [max_export.py](../../tools/dcc/max_export.py) | 3ds Max Python，使用 `pymxs` | 导出选中层级、Skin、Morph 和烘焙 takes |
| [assimploader_bundle.py](../../tools/dcc/assimploader_bundle.py) | 由上述脚本调用 | 统一管理输出目录、纹理复制、资源包元数据、几何 JSON、可选角色 profile 和导出清单 |

三个入口统一为 `export_job(job_path)`。它们不是独立的 FBX 转换器：必须在对应软件内部运行，普通系统 Python 没有 `bpy`、`maya.cmds` 或 `pymxs`。软件本身和 FBX 插件也必须已经可用。

导出器保留原场景，不会保存或覆盖 `.blend`、`.ma/.mb`、`.max`，不会执行减面、合并对象、Reset XForm 或冻结骨架变换。需要这些编辑时，Agent 应先在副本中根据资产类型处理，再调用导出入口。

## 2. 先确定资产的用途

| 用途 | 推荐整理 |
| --- | --- |
| 普通静态摆件 | 移除测试副本和摄影棚，整理尺寸、UV0、材质和地面原点；可按材质合并不需要独立运动的部件 |
| 炮塔、车轮、机械关节 | 保留独立部件、父子关系和真实关节原点，明确局部旋转轴；按动态模式接入 |
| 玩家角色 | 保留骨架、蒙皮、绑定姿态及动作片段；检查人体角色到真实骨骼的映射，校准手掌和模型前向 |
| 头发、裙摆、装饰 | 保留有意义的辅助骨；需要运行时摆动时另外配置 Spring/Bullet，不能指望 FBX 带入完整布料求解器 |

尺寸以最终游戏需求为准。不要把“把所有物体缩放到 1”当成通用修复：已经蒙皮的模型、父级缩放和动画根节点可能相互依赖。应使用单位元数据、已知尺寸参照和最终导入结果判断。

## 3. 一份任务配置驱动三种软件

`job_path` 指向统一打包器读取的 JSON。从 [完整任务模板](../../tools/dcc/export-job.example.json) 开始，改成自己的资源名称和输出目录。以下字段由三个导出器共同使用，实际实现见 [打包器](../../tools/dcc/assimploader_bundle.py)。

| 字段 | 含义 |
| --- | --- |
| `version` | 固定为 `1` |
| `namespace`、`asset_id` | 小写资源命名空间及资产名；例如 `myassets`、`my_character` |
| `output_dir` | 资源包输出目录；相对路径以 job 文件所在目录为基准，不以 DCC 当前目录为基准 |
| `roots` | 需要导出的对象根名称数组。省略或为空时使用当前选择；无选择就报错，不默认导出整个场景 |
| `bake_animation` | 默认 `false`。需要动画时设为 `true` |
| `clips` | 片段数组，基础字段为 `name`、`start`、`end`，帧单位为当前软件帧率 |
| `frame_step` | 采样步长，默认 1 帧；跨三种软件使用时采用正整数 |
| `triangulate` | 默认 `true`，让导出三角形划分明确；法线贴图应按相同划分烘焙 |
| `embed_textures` | 默认 `true`。同时保留输出树内的外部图片，方便诊断和后续规范化 |
| `overwrite` | 默认不覆盖已有导出；重新生成时显式允许覆盖，避免写坏原有交付物 |
| `overwrite_configs` | 默认 `false`，重导 FBX 时仍保留已编辑的 JSON；仅明确重建配置时开启 |
| `character` | `enabled` 控制是否生成 RigWorks profile；`humanoid`、`animations` 提供显式映射，`auto_humanoid` 默认尝试唯一名称匹配 |

原版动作角色不需要空造动画。模板默认 `bake_animation=false`、`clips=[]`，适合先导入骨架后用 VANILLA。静态或机械资产将 `character.enabled` 设为 `false`。

导出器专用字段示例：

```json
{
  "roots": ["CharacterRoot"],
  "bake_animation": true,
  "frame_step": 1,
  "triangulate": true,
  "embed_textures": true,
  "clips": [
    {"name": "idle", "start": 1, "end": 60},
    {"name": "walk", "start": 61, "end": 90}
  ]
}
```

这段只展示导出字段，不是可独立执行的完整任务。命名 takes 不等于自动创作动作；范围内必须已经有正确的动画或能被导出器烘焙的求值结果。

## 4. Blender

### 推荐修改

- 使用可导出的 Principled BSDF 和图片纹理；程序节点、Generated 坐标、UDIM 等先烘焙为 UV0 图片。
- 普通法线使用 OpenGL Y+，通过 Normal Map 节点接入；Metallic、Roughness 使用独立灰度图片。
- 已蒙皮模型保留 Armature 和权重。脚本在存在骨架时不启用通用网格修改器应用，必要的非骨架修改器应先在导出副本上人工/自动烘焙。
- 检查源图已经保存，骨骼名、Action 名和 NLA strip 名明确；退出 Edit/Pose 和 NLA Tweak 模式。

脚本采用 `-Z` 前向、`Y` 向上、Scale=1 和单位换算，不增加 leaf bones。它保留全部骨骼，避免误删 Socket、辅助骨或人体映射目标。是否需要清理控制骨应在资产副本中决定。嵌入图片使用 Copy；关闭嵌入时使用相对图片路径。

### 一键入口

把以下调用放入 Blender 的 Text Editor、按钮回调或已连接 MCP 的 Python 执行工具。将目录和任务路径换成当前环境的实际值：

```python
import sys
sys.path.insert(0, r"C:/your/AssimpLoader/tools/dcc")
import blender_export
result = blender_export.export_job(r"C:/your/jobs/character.export.json")
print(result)
```

后台入口也已提供：

```powershell
& $blenderExe $sourceBlend --background --python $exportScript -- $exportJob
```

### 动画片段规则

填写 `clips` 时，默认将片段 `name` 视为同名 Blender Action。脚本临时创建 NLA strip，按指定范围导出，随后恢复原 Action、槽位和 NLA mute/solo 状态。多个动画所有者不能猜测共享 Action，应指定：

```json
{
  "name": "walk",
  "start": 1,
  "end": 30,
  "object": "CharacterArmature",
  "action": "WalkForward"
}
```

也可以使用 `"actions": {"CharacterArmature": "WalkForward", "PropRoot": "PropWalk"}` 指定多个所有者。使用多槽 Action 时，`slot` 填实际 Action Slot identifier；无法唯一确定就报错，不向所有对象广播。

不填 `clips` 而开启动画时，脚本保留现有 Action/NLA 导出方式，不开启“导出所有 Action”。清单中的名字来自源场景，FBX 导入后可能附带 `Armature|` 前缀；应在游戏工作台确认最终名称再绑定动作。

## 5. Maya

### 推荐修改

- 将最终 Mesh、Joint 和挂点放在明确的资产根下。脚本补齐 Skin influence 和必要父级，不冻结变换、不改场景单位。
- 对复杂 HumanIK、约束、驱动控制器和非标准变形，先在导出副本中烘焙最终变形骨/可支持 Blend Shape；FBX 不是 Maya 场景求解器。
- Arnold、Redshift、V-Ray、层叠材质、UDIM 和程序纹理先烘焙到 UV0 图片。脚本只复制普通 `file` 纹理节点。
- 需要不同片段时先在时间轴规划帧范围，任务 `clips` 定义命名 takes。没有填写片段时使用当前播放范围。

```python
import sys
sys.path.insert(0, r"C:/your/AssimpLoader/tools/dcc")
import maya_export
result = maya_export.export_job(r"C:/your/jobs/character.export.json")
print(result)
```

脚本加载 `fbxmaya`，使用二进制 FBX、Skin/Shape、Y-up 和原始单位元数据。选择范围由脚本显式整理，关闭额外输入连接和子级自动扩张，避免选中父级时顺带导出摄影机或其他资产。FBX 配置、选择、当前时间和临时图片路径在结束时恢复。

**材质限制：当前 AssimpLoader 不会把任意 Autodesk FBX 的旧式高光字段直接解释成 Blender Metallic/Roughness。** 推荐接着用 Blender 导入、检查并重接图片后规范化导出，或者交付已经正确打包的 LabPBR。仅运行这份脚本不能保证 Arnold 材质在游戏内完整还原。

## 6. 3ds Max

### 推荐修改

- 指定资产根，保留 Dummy/Point 等必要父级和关节枢轴；轮子、炮管、俯仰/方位部件不要整体 Attach。
- 角色使用 Skin；脚本补齐 Skin bone 依赖。Physique 当前会报错，应在副本转换到 Skin 后导出。
- Biped、CAT、约束和自定义控制器应烘焙为最终骨骼变换。需要时由 Agent 先在副本内处理，不使用 Reset XForm 去强行修复已绑定角色。
- 标准 Bitmaptexture 可以复制；Physical Material、OSL 和渲染器专有图仍需确认导出映射或烘焙。图片路径无法解析时会报错。

```python
import sys
sys.path.insert(0, r"C:/your/AssimpLoader/tools/dcc")
import max_export
result = max_export.export_job(r"C:/your/jobs/character.export.json")
print(result)
```

脚本通过 `pymxs` 调用 FBX Exporter，启用 Skin、Shape、明确三角形划分和可选动画烘焙，导出选中资产及必要依赖。它恢复选择、时间、临时 Bitmap 路径和 FBX 导出设置。

Max FBX 的 `SplitAnimationIntoTakes` 通常保留额外的 `Take001`。不要把清单里声明的片段列表当成引擎实际导入列表，应该在工作台核对、预览并绑定真正需要的片段。材质同样推荐 Blender 规范化或预打包 LabPBR。

## 7. PBR、清单与运行时数据的区别

导出清单记录软件版本、源文件、单位、节点、骨骼、片段和警告，供 Agent 继续处理。**清单不是新的材质 sidecar，Minecraft 不会读取它来执行材质图或控制骨骼。**

生成的几何 JSON 只告诉 AssimpLoader 从哪里读取模型、是否动态导入。角色 `profile.json` 描述人体映射和动作映射；应使用真实骨骼/片段名称，不能为缺失的骨骼或动画填上虚构名字。自动推断后仍要预览校准。

预打包 LabPBR 的 `_n`、`_s` 必须与底色保持共同基名和相邻目录。打包器会一起复制同扩展名的伴随图，并按组保留命名；采用这条路线时设置 `embed_textures=false`，保留明确的外部底色引用。不同扩展名、仅存在于打包图片中的伴随图，以及 FBX 插件额外生成的 `.fbm` 路径需要单独整理。不要给普通法线或粗糙度简单改后缀。完整通道规范见 [PBR 资产规范](PBR_ASSET_SPEC.md)。

### 生成的目录

```text
<output_dir>/
  pack.mcmeta                         # Minecraft 1.20.1 资源包格式 15
  assets/<namespace>/models/<asset_id>/
    model.fbx
    textures/...
    geometry.json                     # AssimpLoader geometry loader，dynamic
    model.instance.json               # 已导出片段的命名状态，不自动驱动玩家
    profile.json                      # 可选 RigWorks 草稿
    export-manifest.json              # Agent 清单，不是运行时材质配置
```

`character.humanoid` 的键是 `right_hand` 等人体语义，值必须是实际导出的骨名。自动匹配仅识别少量明确名称，不确定或重复名称留空并报告；不会猜测任意自定义 Rig。`character.animations` 使用 `walk` 等动作意图，值为本次导出的片段名，仍需在游戏确认最终名称。`scale` 和 `model_yaw_degrees` 应按预览结果调整。

不要把导出 profile 覆盖到已有角色上丢掉其装备、脚本、solver 和校准。已有角色用工作台保存项目继续编辑。打包器不生成虚构 IK、物理或装备数据，不修改已存在的工作台项目。

## 8. 接入游戏

1. 获取返回的 FBX 和配置路径；先区分产物是资源包还是工作台项目。
2. 资源包安装到玩家指定的游戏目录，启用后执行 `F3+T`；工作台可以直接导入导出的具体 FBX。
3. `/lookdev` → `Edit` 预览层级、材质、尺寸和真实片段；RigWorks 在 Character 扩展内校准骨架、动作和装备。
4. 保存配置、Apply，并重新打开已保存项目确认继承结果；游戏内动作和目标光影需要各自验收。

只放入 FBX 或几何 JSON不会自动注册新的方块、物品或实体。普通资产必须由现有消费者/Showcase/自定义渲染器使用；玩家模型通过 RigWorks 应用。现有 MC 模型替换也需要匹配已注册资源 ID，不能由脚本凭空创造注册项。

MCP 负责让 Agent 操作软件，不是 FBX 插件。连接成功后先读取软件版本、场景名和当前选择，再调用适配器。实际连接/启动参数应使用所选 MCP 项目的当前文档，不要假定这三个导出模块自己提供网络服务。

## API 参考

- [Blender FBX Export Operator](https://docs.blender.org/api/current/bpy.ops.export_scene.html)：选择、轴向、蒙皮、NLA、图片嵌入和采样参数。
- [Maya 2024 FBX Export MEL commands](https://help.autodesk.com/cloudhelp/2024/ENU/Maya-Interoperability/files/GUID-6CCE943A-2ED4-4CEE-96D4-9CB19C28F4E0.htm)：烘焙、Skin/Shape、take 和选择范围。
- [Maya FBX settings commands](https://help.autodesk.com/cloudhelp/2016/ENU/Maya-Tech-Docs/PyMel/generated/pymel.core.other.html)：`FBXPushSettings` / `FBXPopSettings`。
- [3ds Max FBX Export Dialog Access](https://help.autodesk.com/cloudhelp/2020/ENU/3DSMax-MAXScript/files/GUID-54B1F140-B304-48A2-9829-C68B345E2044.htm)：`FBXExporterSetParam`、设置栈和无交互导出。
- [3ds Max Skin Modifier API](https://help.autodesk.com/cloudhelp/2019/ENU/3DSMax-MAXScript/files/GUID-0820AA26-920F-434D-A6BC-E8B6B57F54AC.htm)：Skin influence 节点查询。
