# KubeJS 动画、骨骼、混合与 IK 操作报告

适用：Minecraft Forge 1.20.1；当前开发依赖为 KubeJS `2001.6.5-build.26`、Rhino `2001.2.2-build.17`、Architectury `9.1.12`。以下接口按仓库实际源码核对。

## 1. 各层负责什么

| 层 | 职责 |
| --- | --- |
| Blender / Maya | 网格、UV、PBR、骨架、权重、绑定姿态、关键帧；需要时烘焙约束/IK 结果 |
| AssimpLoader | 导入场景和片段、实例时钟、混合、IK、物理、GPU 蒙皮与绘制 |
| KubeJS 的 `Assimp` | 在客户端 tick 发出播放、权重、姿态、IK 目标等命令 |
| RigWorks 的 `RigWorks` | 玩家输入、人体语义映射、原版动作、装备与角色脚本策略 |

JS 不逐顶点变形，不推进物理子步，不自行调用渲染。引擎每次更新统一求值。没有 RigWorks 时仍可控制通用动态模型。

## 2. 安装和最小可见 Demo

1. 安装引擎发布 JAR（如 `versions/1.20.1-forge/build/libs/assimploader-1.20.1-forge-1.0.0.jar`）、KubeJS、Rhino 和 Architectury。
2. 本文可见样例还需要 `assimpshowcase-1.20.1-forge-1.0.0.jar`；开发客户端默认已加载 Showcase。
3. 将 [assimp_animation_tutorial.js](../../examples/kubejs/client_scripts/assimp_animation_tutorial.js) 放入实例的 `kubejs/client_scripts/`。
4. 使用 `/kubejs reload client_scripts`，进入测试世界执行下面命令并手持物品。

```mcfunction
/give @s assimploader:showcase_highbone_probe
```

模型播放 `Wave`，下蹲会叠加最后一个骨骼的旋转、局部动画层和 Morph。换走物品时清理 Demo 自己的控制。不要同时安装多个控制同一实例的示例。

开发启动：

```powershell
.\gradlew.bat :1.20.1-forge:runClient -PwithKubeJS --console=plain
```

脚本日志在实例 `logs/kubejs/client.log`，引擎诊断另见 `logs/latest.log`。

## 3. 取得实例并检查资产

```javascript
ClientEvents.tick(function (event) {
  if (!event.player || event.player.mainHandItem.isEmpty()) return
  var model = Assimp.instance(event.player.mainHandItem, 'highbone_probe')
  if (!model) return
  // 对成功取得的 handle 使用后面的 API。
})
```

`Assimp.instance(owner, modelId)` 的 owner 必须是当前客户端世界的 Entity、BlockEntity 或非空 ItemStack。模型必须已经由资源加载器/渲染器以 **DYNAMIC** 导入；未准备好会返回 `null`。资源 ID 或明确缓存名可用，同名歧义不会随机选取。

这个调用不会注册方块、生成实体、下载 FBX，也不会给任意 owner 自动挂渲染器。脚本控制的 owner 与实际渲染器使用的 owner 必须一致。

| 查询 | 返回值/含义 |
| --- | --- |
| `Assimp.models()` | 已缓存动态模型名 |
| `model.isValid()` | 此代动画实例是否仍然有效 |
| `model.sameInstance(other)` | 两个 handle 是否指向同一个动画实例 |
| `model.animations()` | Java 字符串数组；用 `.length` / `[i]` 访问，片段名区分大小写 |
| `model.bones()` | 场景全部节点名，包含非加权节点，并不只列 deform bones |
| `model.meshes()` | 网格、顶点、骨骼等元数据 |
| `model.capabilities()` | 导入能力/资产事实；不代表当前光影或运行时开关 |
| `model.schedule()` | 实际姿态求值阶段与依赖 |
| `model.node('Hand.R')` | 当前节点的模型空间位置 |
| `model.bounds()` / `attachment(name)` | 动画包围盒 / 已定义挂点位置 |

返回的 Java Map 用 `.get('key')`；Java List 用 `.size()` / `.get(i)`。不要假设所有对象都等同普通 JS 对象。模型重载后重新取得 handle，不能继续使用已关闭的一代。

## 4. 播放烘焙动画

```javascript
if (!model.play('Walk', 0.15)) {
  console.warn('模型没有 Walk，请检查 model.animations()')
} else {
  model.playback(1.0, true)
}
```

`play(clip, blendSeconds)` 切换片段，第二个参数单位是秒；`playback(speed, looping)` 调速和循环。**仅在动作发生变化时调用 `play`**，不要每 tick 重播片段。

```javascript
model.pause()
model.resume()
var clock = model.playback()
console.info(clock.get('time') + ' / ' + clock.get('duration'))
model.phase(0.5)       // 锁定归一化进度，适合编辑器或已接受的攻击阶段
model.releasePhase()  // 恢复正常时钟
```

`time`、`duration` 是秒，`phase` 范围 `[0,1]`。锁定阶段后，不解除就不会自行继续播放。

有 `.instance.json` 状态定义时也可用 `model.states()`、`model.currentState()`、`model.state('walk')`。状态名与 FBX 片段名是两套标识；没有状态定义时应直接播放真实片段。

## 5. 动画混合和遮罩

```javascript
model.layer('pack:upper', 'Aim', 0.7, false, {
  'Spine': 0.5, 'Chest': 1, 'UpperArm.R': 1, 'Forearm.R': 1
})
model.layerWeight('pack:upper', 0.4)
model.layerPlayback('pack:upper', 1, true)
model.layerPhase('pack:upper', 0.25)
model.layerReleasePhase('pack:upper')
model.removeLayer('pack:upper')
```

参数依次是层 ID、片段、权重、是否 additive、节点遮罩。`false` 为按权重覆盖混合；`true` 为相对于绑定姿态的增量。非空遮罩只影响列出的节点，不会因为写了 Chest 就自动覆盖所有后代。`model.layerPlayback(id)` 可查询独立层时钟。

通常使用“下半身移动 + 上半身持械/攻击”，特殊全身动作时降低覆盖层权重。Additive 动画应围绕一致参考姿态制作，否则会重复旋转或夸大位移。

## 6. 程序化骨骼姿态

```javascript
model.pose('pack:guard', {
  additive: true, weight: 0.5, space: 'model',
  bones: {
    'UpperArm.R': {rotation: [-20, 0, -5]},
    'Forearm.R': {rotation: [-30, 0, 0], position: [0, 0, 0], scale: [1, 1, 1]}
  }
})
model.removePose('pack:guard')
```

Euler XYZ 旋转单位是 **度**，姿态相对于恢复的绑定姿态。`local` 使用骨骼局部轴；`model` 通过绑定方向解释模型轴，适合 roll 各异的导入骨架。位置仍采用模型导入单位，不是屏幕坐标；不要把“模型轴旋转”理解成世界坐标绝对变换。

`pose(...)` 会创建/替换一个姿态对象。不断变化的权重优先放进下面的缓存 processor，不要每 tick 编译整份定义。

## 7. 缓存 Processor：片段 + 姿态 + IK

可在初始化时 `model.processorDefinition(id, JS对象)`，或由资源文件加载：

```javascript
model.processor('pack:hybrid', 'pack:animation_processors/hybrid.json')
model.processorWeight('pack:hybrid', 'walk_layer', 0.8)
model.processorEnabled('pack:hybrid', 'reach', true)
model.processorTarget('pack:hybrid', 'reach', 0.3, 1.2, 0.4)
```

`processor(id, 同一资源)` 在此实例上重复调用会复用已编译内容；修改文件后需要 `reloadProcessor(id, resource)`。内联 `processorDefinition` 每次调用都会编译，因此只在初始化或编辑提交时调用。

下面是需要按目标骨架命名修改的完整资源结构；三个节点必须是按顺序的祖先/后代链，不能填同级骨骼。

```json
{
  "version": 1,
  "clips": {
    "walk_layer": {"clip": "Walk", "weight": 0, "loop": true, "mask": {"Thigh.L": 1, "Shin.L": 1}}
  },
  "poses": {
    "guard": {"weight": 0, "additive": true, "space": "model", "bones": {"Forearm.R": {"rotation": [-20, 0, 0]}}}
  },
  "ik": {
    "reach": {
      "chain": ["UpperArm.L", "Forearm.L", "Hand.L"],
      "target": [0.3, 1.2, 0.4], "enabled": false,
      "weight": 0.7, "iterations": 12, "tolerance": 0.002
    }
  }
}
```

IK 目标位于**模型空间**，且使用导入后的单位。世界空间目标需先逆变换到模型空间，包含根平移、朝向、各轴缩放；不能直接塞世界 XYZ。通用 `node()` 查询可作为模型空间调试起点。

通用 processor 使用有迭代上限的 CCD 链求解，不等于完整 Blender 约束图，也不是自动手指抓握。RigWorks 另有已命名 IK、人体映射、手部接触策略。可直接运行的玩家混合/IK 示例见 [RigWorks 教程](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/KUBEJS_ANIMATION_GUIDE.md)。Highbone 示例的 256 个骨骼大部分是同级节点，不能拿连续编号假装成 IK 链。

| Processor 控制 | 用途 |
| --- | --- |
| `processorEnabled(id, boolean)` | 整个处理器开关 |
| `processorEnabled(id, control, boolean)` | 指定 clip / pose / IK 开关 |
| `processorWeight(id, control, weight)` | `[0,1]` 权重 |
| `processorPhase` / `processorReleasePhase` | 片段进度锁定与解除 |
| `processorTarget` | 更新 IK 目标 |
| `processorControls(id)` | 可控制名字 |
| `removeProcessor(id)` | 同时移除组合阶段和配套 IK 阶段 |

引擎按类别安排求值；processor 的片段与姿态在组合阶段，配套 IK 在 IK 阶段。可以用 `after` 声明同阶段合法依赖，循环和跨越不允许阶段的依赖会被拒绝。不要靠 JS 调用排列去推断最终求值顺序，使用 `schedule()` 检查。详细 schema 见 [动画处理器](../reference/ANIMATION_PROCESSORS.md)。

## 8. Morph、物理与求解能力的边界

`model.morph(meshIndex, weights)` 设置某网格的 Morph 权重，`resetMorphs()` 复位；目标必须存在。导入片段也可以包含 Morph 通道。不同 GPU/预览路径是否实际应用 delta 应结合[能力说明](../reference/MODEL_CAPABILITIES_AND_CLIP_MORPHS.md)和当前渲染路径验证。

`model.target(modifierId,x,y,z)` 仅修改已有受支持 IK / LookAt modifier，不会凭空创建 IK。物理 body、弹簧、碰撞、接地和挂点需要先有实例/rig/physics 定义；不要把脚本 API 的存在当作资产已经配置完毕。

## 9. Blender 烘焙与运行时 IK 怎么选

| 内容 | 推荐做法 |
| --- | --- |
| 行走、奔跑、攻击、演出 | Blender 关键帧/动作，必要时 Bake Action 后导出片段 |
| 眼睛/头看目标、地形脚底、双手握武器 | 运行时目标和有限 IK，作为片段之后的修正 |
| 布料、头发、装饰摆动 | 骨骼 + Spring/Bullet 定义，不用 JS 逐帧模拟顶点 |
| 已知约束只需固定演出 | 烘焙结果，不要求游戏重建建模软件约束 |
| 游戏内编辑变换/配置 | 工作台保存项目；不会反向把关键帧写回 FBX |

保留骨架和蒙皮权重、稳定骨骼名、正确绑定姿态和单位。Blender 控制器、自定义驱动、约束图和 IK 控制骨不会自动变为游戏里的求解器。需要最终运动时烘焙变形骨结果；导出时核对实际 clip 名称、帧范围、采样率及 Root Motion。当前脚本播放模型局部动画，不会自动让服务器实体跟随根运动移动。

## 10. 重载、性能和同步

- 脚本：`/kubejs reload client_scripts`；资源/FBX/贴图：`F3+T`；processor JSON：重新加载资源后显式 `reloadProcessor` 或重建实例。
- 动作选择发生变化才 `play/state`；processor 定义只编译一次，tick 只调权重/目标/阶段。
- 不在 render、每骨骼或每顶点循环执行 JS；不在 tick 中读磁盘或解析 JSON。
- `clearScriptControls()` 会清理该实例全部脚本层、pose、processor、阶段锁和 Morph；多个脚本共用实例时优先只删除自己的 ID。
- `Assimp.release(owner)` 释放 owner 的实例，不是“移除一个动画层”。由正式渲染器管理的实例不要随意释放。
- 本地全局 `client_scripts` 不会自动上传。多人自定义配置/脚本包由 RigWorks 的明确分发协议负责，且只同步配置和动作包，不传大模型资产或每帧骨骼矩阵。

源码入口：[AssimpScriptApi](../../src/main/java/cn/minerealms/assimploader/compat/kubejs/AssimpScriptApi.java)、[AssimpModelHandle](../../src/main/java/cn/minerealms/assimploader/compat/kubejs/AssimpModelHandle.java)、[ModelAnimationProcessor](../../src/main/java/cn/minerealms/assimploader/animation/ModelAnimationProcessor.java)。

本报告的 Demo 进行了语法/数据和现有源码契约检查；独立拆分后的客户端实机画面不以本文代替验收。
