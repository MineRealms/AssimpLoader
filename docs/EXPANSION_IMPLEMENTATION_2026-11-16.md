# AssimpLoader 扩展实施报告

日期：2026-11-16。本文记录[架构评估](EXPANSION_ARCHITECTURE_ASSESSMENT_2026-11-16.md)之后的实现及验证。所有能力继续建立在现有 Assimp 导入、共享场景、Minecraft 渲染和资源重载机制上。

## 当前结论

实例姿态、动画混合、分层、基础 IK、Spring Bone、Morph、命名挂点、模型命中查询及基础挂点特效已经形成一条可组合的运行链路。物品、展示方块和供扩展模组继承的实体渲染器都有接入入口。这已经超过最初的动画基础增量，但不等于实现了对比模组的全部功能。

没有引入新的 Rust/C++、JNI 或 SIMD 分发依赖。新增默认关闭的 OpenGL 4.3 Compute 蒙皮后端，继续使用现有 Assimp 场景、姿态和材质绘制；GPU Morph 同时保留原有顶点着色器/TBO 路径。CPU 合成基准不提供 GPU 耗时；独立高骨数实机记录提供单夹具提交与 GPU timestamp 诊断，不泛化为通用加速比。

| 提交 | 交付内容 |
|---|---|
| `70aa6b3` | 实例隔离、不可变姿态快照、crossfade/中断、Blender FBX 和 CPU/GPU 对照 |
| `d2dcd1d` | JSON 实例定义、分层、IK、Morph、Spring Bone、碰撞基础和特效基础 |
| `55dd630` | 物品/方块/实体入口、当前姿态网格查询、地面采样、粒子适配、Blender GLB 和性能探针 |
| `7a26713` | GPU Morph delta TBO、先 Morph 后蒙皮、权重语义共用、GL 资源校验/恢复及容量回退 |
| `3eb09d8` | 无效实例定义按场景记录一次错误并回退基础播放，清理/重载后允许重试 |
| `0cbad9a` | 强制活动 Morph GPU 对照、纹理绑定恢复及重载后活动目标重建验证 |

GPU Morph 是独立批次；最终测试数和客户端结果见本文的 GPU 验证补记。

## 能力与边界

| 能力 | 实际实现 | 边界 |
|---|---|---|
| 实例姿态 | `AnimationState` 单独拥有时间、片段、层、modifier 和 Morph 权重；`PoseSnapshot` 发布节点、网格实例、palette、权重和包围盒 | 共享导入资产不承载实例状态；快照返回防御性副本 |
| 状态切换 | 绑定的 `AnimationStateMachine`、秒制 crossfade、中断后冻结当前基础混合结果、停止恢复 bind pose | 状态请求由宿主逻辑发起；不是通用玩法脚本或自动动作数据库 |
| 分层 | 按名称添加/删除层、层权重、节点遮罩、覆盖或 additive；Morph 层可按网格/承载节点遮罩 | additive 相对 bind pose；导入动画通道仍按节点名称匹配 |
| IK | `LookAtModifier` 和有迭代上限的 CCD `ChainIkModifier` 接入最终姿态；处理父空间转换 | 目标为模型空间；不是完整人形重定向系统 |
| 脚贴地 | JSON `groundBody` / `footPlacement`、有限躯干校正、CCD 和 Minecraft 地面采样 | 四蹄高低地面数值验收完成；还不是步态接触规划或坡面脚掌旋转 |
| Spring Bone | 固定步长、最大子步数、重置/传送阈值、球/胶囊接触、骨长约束 | 次级摆动；无全身刚体、关节约束、布料或布娃娃 |
| Morph | Assimp 目标/动画键复制、实例默认/显式权重、片段混合、分层、CPU/GPU 变形和动态边界 | GPU 路径限可用的非透明骨骼网格；位置/法线；不公开 Morph UV/颜色/切线；Assimp 键接口未保留源曲线插值类型 |
| 挂点 | 节点和局部 TRS 偏移；`attachmentTransform`、`drawAttachment` 共用快照 | 宿主负责实际注册装备/物品/子实体 |
| 材质动画 | UV 滚动、RGBA、脉冲，按实例模拟时钟采样；保持普通顶点提交协议 | 参数动画走 CPU/vanilla 提交；不是材质节点图或新后处理链 |
| 拖尾 | 两个挂点、固定容量历史、寿命渐隐、世界轨迹、传送清理 | 移动、传送、重载已通过世界数值断言；未绑定世界时保留模型空间入口 |
| 粒子 | 有上限的挂点发射器及 Minecraft 适配器；每 tick 刷新锚点、已加载区块检查 | 仅 `SimpleParticleType`；世界发射计数已验收；参数化粒子不支持 |
| 碰撞查询 | AABB、按需 PCA OBB、胶囊、骨骼代理及 BVH 射线查询；静态/姿态缓存与预算 | 显式查询 API；没有自动替换 Minecraft 实体物理或服务端权威命中流程 |
| 渲染性能 | 沿用缓存网格、GPU 骨骼 TBO、能力检测和 CPU 回退 | 此批未完成 MDI、Hi-Z、Compute 输出复用及新的完整延迟/后处理栈 |

## 运行链路与所有权

```text
共享 AssimpScene / 网格 / 动画 / Morph 目标
  -> 每实例 AnimationState：片段与过渡 -> 层 -> PoseModifier
  -> 不可变 PoseSnapshot：节点 + 每网格 palette + Morph + bounds
  -> 绘制 / 挂点 / 命中查询
  -> ModelInstance：材质参数、拖尾历史、粒子发射
```

托管实例在客户端 tick END 推进固定 `0.05` 秒；暂停时不推进。绘制只消费已发布姿态，不推进动画或物理。目前是 20 Hz 姿态采样，未加入渲染插值。直接创建的 `AnimationState` 由调用方执行 `initialize/update/close`，不得再交给第二个更新循环。

`ClientAnimationManager` 以 owner 的对象身份区分实例，并以弱引用持有 owner。使用稳定的 `ItemStack`、方块实体或实体对象；每帧新建 owner 会重启动画。生命周期已知时调用 `release(owner)`。资源重载/退出清除实例、定义缓存、变形缓存和 GL 资源；场景替换关闭旧实例。外部持有的旧状态不能继续用于新场景。

修改状态、世界采样和 GL 操作仍分别遵循客户端线程/渲染线程约束。快照不可变不代表旧播放器、共享资产或整个 API 自动线程安全。所有 modifier 每实例创建，禁止在实例之间共享有状态的 Spring。

## 配置接入

动态模型 `namespace:models/example.glb` 的旁置定义是 `namespace:models/example.instance.json`。解析 `version: 1`，`model` 必须匹配原模型资源。缺少定义时保留原有动画实例入口。模型必须以 `DYNAMIC` 导入，才能使用实例定义。

`parent` 引用另一份完整资源 ID。对象递归合并，数组和标量整体替换；检测循环和最多 32 层的继承链。解析器检查未知字段、节点/片段/材质名称和有限数值。片段可用完整名称，或唯一的 `|名称` 后缀匹配 Blender FBX 导出前缀。

直接解析/实例化会报告具体校验错误。客户端托管入口遇到资源解析或资产绑定错误时，按场景记录一次错误并回退基础播放，其他 owner 不会每帧重复解析失败配置；清理/重载后重试。

已提交的最小可用配置：

```json
{
  "version": 1,
  "model": "assimploader:models/validation/animation_probe_morph.glb",
  "initial": "idle",
  "states": {
    "idle": {"clip": "Idle", "loop": true, "transition": 0.3},
    "reach": {"clip": "Reach", "loop": false, "transition": 0.4},
    "pulse": {"clip": "MorphPulse", "loop": false, "transition": 0.2}
  },
  "attachments": {
    "tool": {"node": "ToolSocket", "translation": [0.15, 0, 0]}
  }
}
```

顶层配置入口如下；具体参数由对应类型校验：

| 字段 | 用途 |
|---|---|
| `states` / `initial` / `transitions` | 命名状态、片段、速度、循环及过渡时长 |
| `layers` | 片段、权重、additive、节点/网格 mask、速度和循环 |
| `attachments` | 节点、translation、rotation、scale；旋转为弧度 |
| `lookAt` / `ik` | 前向轴/目标/角度，或有序节点链/目标/迭代/容差 |
| `morphs` | 唯一网格名称到完整权重数组 |
| `springs` / `colliders` | 驱动节点、局部 tail、积分参数、球/胶囊；声明的 collider 提供给该定义的每个 spring |
| `hitboxes` | 当前节点的局部胶囊代理；不与 Spring 接触体混淆 |
| `materials` | 材质名或 `*` 的 UV/RGBA/脉冲参数 |
| `trails` | 起止挂点、容量、寿命和 RGBA |
| `particles` | 挂点、粒子注册 ID、速率和速度 |

完整资产配置见 [GLB sidecar](../src/main/resources/assets/assimploader/models/validation/animation_probe_morph.instance.json)。Spring 参数见[运行时说明](SPRING_AND_COLLISION_RUNTIME.md)。没有 JSON 地面采样器或自动实体行为注册；这些需要调用方按实际世界和资产接入 Java API。

## 宿主扩展入口

```java
// 客户端线程；scene 为当前 DYNAMIC 场景，owner 在展示期间保持身份稳定。
var manager = ClientAnimationManager.getInstance();
var state = manager.getOrCreateAnimationState(owner, modelName, scene);
if (state != null && state.getModelInstance() != null) {
    var instance = state.getModelInstance();
    instance.requestState("reach");
    var pose = state.getPoseSnapshot();
    var mount = instance.attachmentTransform("tool", modelToWorld);
    var hit = instance.collision().raycast(pose, modelRay, maxModelDistance,
            ModelCollision.Mode.BONE);
}
// owner 销毁时：manager.release(owner);
```

`AssimpItemRenderer` 按实际 `ItemStack` 取状态；`ShowcaseRenderer` 按方块实体取状态并更新瞬态视觉边界。`ConfiguredEntityRenderer<T>` 供扩展模组为自己的已注册实体继承，实现 `modelName` 并按需要覆盖 `modelTransform`。它不替换原版玩家或任意生物，也不替宿主调整实体的服务端碰撞箱。

调用 `addModifier/getModifier/removeModifier` 可以更新 LookAt/IK 目标；`setLayer/setLayerWeight/removeLayer` 控制层；`setMorphWeights/resetMorphWeights` 覆盖或恢复动画权重。传送世界锚点、换维度或其他不连续运动后应调用 `resetDynamics()`，因为模型空间骨原点不一定因实体传送而变化。粒子适配按渲染时复制的世界锚点发射，过期锚点停止使用，不强持有实体 owner。

碰撞 `BOUNDS` 使用保守动态 AABB；`BONE` 测试配置胶囊，不会被视觉 AABB 提前排除；`MESH` 先粗筛，再对当前 Morph/蒙皮结果建立快照级 BVH，静态几何复用 BVH。同一快照的重复查询复用结构；跨快照重建可能昂贵，应优先用粗筛/骨代理。输入 ray 和距离均为模型空间；调用方负责世界变换及权威时钟。它是瞬时表面查询，不是连续碰撞检测或物理求解。

## 验证记录

### 自动测试与构建

`55dd630` 批次执行：

```powershell
.\gradlew.bat test build -PruntimeBenchmark --console=plain --no-daemon --no-parallel
.\gradlew.bat test build runClient -PclientAnimationSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

237 项测试通过，零失败/错误/跳过，构建成功。性能测试为显式开关；普通测试不启用时跳过这一项。测试包含真实 Assimp glTF/GLB/FBX 导入、原生释放后的目标数据、独立实例、过渡中断、层/IK、Morph 0→1→0、父变换、Spring 固定步/重置、碰撞几何及配置继承校验。

GPU 与错误回退批次的最终普通测试为 240 项：239 项通过，1 项可选性能探针跳过，零失败/错误。新增覆盖真实 Morph delta TBO 打包、容量/权重校验，以及资源解析/资产绑定失败后独立 owner 继续基础播放、清理后重试。此前性能数据保留，不为这一错误处理改动重复运行基准。

### 客户端基线

Minecraft 1.20.1 / Forge 47.1.3 / Java 17.0.18，RTX 4060 / OpenGL 4.6 / NVIDIA 572.83。此次排除可选 Photon/LDLib 与 Oculus，保留 ModernUI；`run/mods` 中已移除 F3Tweaker。

客户端运行 `1794823348622` 完成 13 阶段：独立实例、crossfade、中断、停止、重载、遮罩层、LookAt、Spring、Morph 零/一/恢复、材质/拖尾、Morph 重载。每次模型切换或重载后至少等待 10 秒截图。两行实例共 26 对 CPU/加速列轮廓比较，错配率全部为零。Morph 改变 1182 个轮廓像素，归零后逐像素恢复。

该基线中的活动 Morph 和材质动画使用 CPU 回退，所以不能用它证明 GPU Morph。轮廓一致也不等同于法线、全部纹理和任意资产的数值一致。Blender 自制 FBX/GLB、混合权重桥接网格、嵌入棋盘贴图和 shape key 已提交，生成脚本与 `.blend` 在 `model-tests`。保留原 FBX，新增 GLB；未覆盖用户的 Blender 会话。

截图：`run/screenshots/assimp-animation-1794823348622-*.png`。重点检查 `morph-one`、`morph-restored`、`trail`。更早动画基础验证见[FBX 验证报告](ANIMATION_FBX_VALIDATION_2026-11-16.md)。

### CPU 性能探针

每实例 4095 顶点、四权重，64/256 骨骼的合成 glTF；共享场景、独立状态，骨链为 identity 且无片段。每档 100 次预热、120 个样本，顺序更新。表格为整档全部实例一次更新的中位数/p95 毫秒；后两列包括姿态和 CPU 蒙皮，不是纯蒙皮差值。

| 骨骼 | 实例 | 姿态 median | 姿态 p95 | 姿态+CPU 蒙皮 median | 姿态+CPU 蒙皮 p95 |
|---:|---:|---:|---:|---:|---:|
| 64 | 1 | 0.0953 | 0.1653 | 0.4152 | 0.4851 |
| 64 | 10 | 0.4070 | 0.5719 | 3.8424 | 5.8021 |
| 64 | 50 | 1.2867 | 1.8315 | 18.4164 | 32.9536 |
| 256 | 1 | 0.0960 | 0.1214 | 0.4499 | 0.5103 |
| 256 | 10 | 1.0118 | 1.1252 | 4.4368 | 11.4453 |
| 256 | 50 | 5.1465 | 5.5903 | 22.7511 | 45.9178 |

环境：Windows 11、Java 17.0.18、Xeon E5-2697 v2 @ 2.70 GHz、24 逻辑处理器。原始输出由测试生成到 `build/reports/runtime-benchmark.json`。该探针不包括渲染提交、GPU、世界、真实动画片段和光影，不是 FPS 或硬件通用结论。结果支持优先处理大量 CPU 顶点变形；不能据此宣布 native 比 Java 更快，或估计 GPU 加速倍数。

### GPU Morph 验证补记

`7a26713` 使用共享只读目标 delta TBO，仅每实例权重变化；顶点着色器先混合位置/法线，再蒙皮。能力上限为每网格 32 个目标、128 MiB，并受驱动 `GL_MAX_TEXTURE_BUFFER_SIZE` 限制。无骨 Morph、超限、透明/特定材质及光影路径保留 CPU 回退；GL 资源随重载释放，修改的纹理单元绑定恢复。分配后校验缓冲实际容量及纹理关联；失败通过原有机制禁用 GPU 蒙皮并回退。

运行 `1794824500519` 在新增分配校验后完成 13 阶段。活动 Morph 明确由 GPU delta TBO 执行，加速列禁止用 CPU Morph 回退通过；26 对轮廓错配率全部为 0，变化 1182 像素，归零后恢复。每个 GPU draw 检查纹理单元 4 的 TBO 绑定恢复，捕获前检查 GL 错误。自动测试共 239 项，238 项通过，1 项可选性能测试未启用，零失败/错误。最终回归另增重载后活动 Morph 重建检查，结果见下方补记。

最终运行 `1794824774109`（验证代码 `0cbad9a`）完成 14 阶段，在原 13 阶段之后增加 `morph-active-reloaded`。28 对轮廓错配率全部为 0，14 张截图，每次均等待至少 10 秒。活动 Morph 重载后新建 delta TBO，其 GPU 轮廓与重载前逐像素一致；零权重恢复检查、独立实例检查和 GL 错误检查均通过。`test build runClient` 的 Gradle 日志以 `BUILD SUCCESSFUL` 结束，客户端正常退出。记录位于 `run/logs/latest.log`、`build/animation-final-validation.log`；截图为 `run/screenshots/assimp-animation-1794824774109-*.png`，已人工查看 `morph-active-reloaded`。

这些是形态轮廓与资源生命周期证据，不是全部法线/纹理像素一致或 GPU 计时证据。材质 UV/tint 阶段仍明确使用 vanilla CPU 提交；本次没有以该阶段证明材质动画 GPU 化，也没有测得 GPU Morph 加速倍数。

## 后续缺口与推进顺序

1. **世界进一步覆盖**：地形、移动/传送、世界特效、重载、NBT、磁盘重开和实体移除已通过；后续补更远距离/视锥边缘的受控比较，以及服务器权威伤害/投射物接入。
2. **光影进一步覆盖**：256 骨和 Morph 已有真实 GPU 与当前 Oculus pack 的主/阴影证据；不同目标 pack、光影切换和大规模实体仍需单独验收。
3. **稳定帧成本**：已完成 JFR、单夹具提交/GPU timestamp 和上传统计，并按测量实现 Oculus 姿态缓冲跨 pass 复用。仍需渲染插值、可比较的大规模压力结果，再决定 Compute/批量提交等后端。
4. **更重物理**：全身 Ragdoll、布料、约束刚体和连续网格碰撞尚未实现。进入这些目标时优先评估成熟引擎及 Java 绑定，核对平台、打包和生命周期，再决定 C++/Rust 作用范围。
5. **更大渲染扩展**：MDI、Hi-Z 和自有 SSAO/Bloom/SSR/SSS/TAA/CSM 全套通道尚未交付。现有 PBR/Oculus 接入不代表自有延迟渲染和后处理栈完成；PCA OBB 自动拟合已交付。

完整 CustomSteve、玩家模型替换、PMX/VMD 运行时、Motion Matching、动作数据库、展示摄像机/BGM 和通用节点编辑器继续在本目标之外。新增 Native 或 SIMD 应针对已测得的批量计算成本，并保留同输入 Java 参考与回退；不引入逐顶点/逐骨 JNI 调用。

## 后续增量：性能策略与鹿资产

新增 [运行时性能模式](RUNTIME_PERFORMANCE_MODES.md)：`QUALITY/BALANCED/PERFORMANCE/CUSTOM`、远距离/历史不可见实例分频、错峰求值、累计播放时间、资产级开关、实例粒子总预算和拖尾容量限制。`/assimploader performance` 提供只读计数。当前没有渲染插值，分频可能产生可见步进；从未收到绘制观察的实例保守保持近距离频率。

新增 [鹿展示](DEER_SHOWCASE.md) 和用户 Blender 工程的转换产物：174 部件合成一个蒙皮网格、35 骨、原有 10 动作加 `ik_probe`，含四腿目标/极向控制、独立可编辑预览和无约束的导出骨架。GLB 内嵌贴图，另提供烘焙 FBX。运行时 JSON 定义 6 处 Spring、躯干碰撞体、14 个骨骼命中代理及挂点；并注册独立 `assimploader:deer_showcase` 生物。

数值验证包含 GLB/FBX 各 33 组 Blender 参考姿态，覆盖全部 11 个动作的节点位置及变形几何范围，误差阈值 0.3 毫米；真实鹿模型运行时 IK 抬蹄、骨骼/网格射线查询、Spring 有限值和独立状态均有测试。鹿原始动作转换最大矩阵误差约 `7.2e-7`，Blender IK 最大目标误差约 `0.058 mm`，IK 烘焙矩阵误差约 `1.2e-6`。这不代表任意模型或复杂地面足部解算已经验收。

客户端鹿验证运行 `1794828136485` 完成 7 阶段、14 对 CPU/GPU 轮廓比较，全部像素错配为 0；世界运行 `1794828136511` 完成独立生物的 idle/walk/gallop/ik_probe 和资源重载共 5 张截图，每阶段至少等待 10 秒。已人工查看待机、奔跑和 IK 图像。日志 `build/deer-validation.log` 以 `BUILD SUCCESSFUL` 结束。运行使用 `-PwithoutOptionalMods`，不包含 Oculus 动画验收。

此用户提供资产未附作者/再分发许可；本地验证结果不构成公开发布授权。脚贴地与世界粒子仍需单独场景验收，鹿骨骼命中代理尚未接管服务器伤害逻辑。未新增 Native/Compute：此鹿合批后的几何规模和 35 骨尚不构成引入额外后端的证据。

## 后续增量：数值世界验收与高骨数

`4d1191a` 新增 [PCA OBB](PCA_OBB_COLLISION.md)：有界协方差采样、全顶点保守投影、静态/姿态缓存和超预算整体拒绝。7 项针对退化、离群点、射线及缓存的测试通过。

`5485f43` 新增 [256 骨骼 Blender 样本与验证](../model-tests/highbone_validation.md)：256 个实际带权骨、6144 导入顶点和活动 Morph。运行 `1794830936198` 四轮 CPU/GPU 掩码差异均为零，归零和重载严格恢复，确认使用 TBO 并禁止 fallback。该夹具的平均 CPU 提交约 2.32-2.57 ms、GPU 提交约 0.318-0.393 ms；单次预热 GPU timestamp 区间约 15.36-18.432 微秒，不作为游戏 FPS 或通用加速比。

[世界实例运行说明](WORLD_INSTANCE_RUNTIME.md) 记录 JSON 脚贴地、有限躯干校正、tick 锚点、世界拖尾、实体卸载清理和动态视觉边界。运行 `1794831654168` 完成 9 阶段，8 张延时截图；每次检查全部 4176 个变形顶点及模型边缘坐标。高低地面四蹄最大误差 7.162 mm，受控移动 2.587 格、寻路实际移动 1.046 格并自动选择 walk，7 格传送、重载、NBT 重建及移除后旧实例关闭均通过。274 项测试中 273 项通过，1 项可选基准跳过，零失败/错误。

后续 [Oculus 世界与 JFR 验证](OCULUS_ANIMATION_VALIDATION_2026-11-16.md) 完成运行 `1794832541673` 的 10 阶段和 9 张延时截图：同一存档完整保存/退出/重开、旧状态释放，以及 256 骨骼 Showcase 的 Morph 0→1→0 主/阴影通道均通过。最终 275 项测试，274 项通过，1 项可选基准跳过。稳定 22 秒 JFR 切片中，548 个含模组栈的 Java 样本有 496 个归因到顶点提交入口及其 Minecraft/Iris 后代调用，CPU 蒙皮为 14 个；优先处理重复提交而不是直接新增 Native 蒙皮后端。

以上结果更新前文历史缺口：PCA OBB、高骨数 GPU、世界地形/移动/特效/卸载、磁盘重开和这套 Oculus 光影的动画/Morph 主/阴影通道已经有实测证据。仍未覆盖渲染插值、Compute/MDI/Hi-Z、全身刚体/布料以及自有完整后处理栈，也不泛化为任意光影兼容证明。

进一步新增 [Oculus 姿态缓冲缓存](OCULUS_POSE_BUFFER_CACHE.md)，复用现有 ENTITY draw bridge 和同一姿态的 CPU 变形数据；主/阴影绘制复用 VBO，新姿态仅更新已有动态缓冲。可配置总 MiB/实例数、LRU 淘汰、超预算预检回退、关闭/重载释放。运行 `1794833212718` 完成 11 阶段与 10 张延时截图：重开后的一个区间为 4221 次缓存绘制、626 次上传、3595 次复用；1 MiB/1 实例测试实际保留 516096 字节，删除后旧实例 GPU 缓冲已释放。这不是固定 FPS 提升或 Native/Compute 已完成的声明。

## 后续增量：渲染插值和 Compute

`2086f25` 新增 [只读渲染插值](RENDER_POSE_INTERPOLATION.md)。资产通过 `performance.renderInterpolation` 显式开启；默认关闭，`PERFORMANCE` 模式绕过。管理器每个 tick 更新视觉时钟，包括降频跳过的 tick；实体、Showcase、普通绘制和 Oculus 姿态缓存消费同一相位快照。局部 TRS、Morph 和中间姿态边界重新计算，碰撞与特效继续使用仿真快照。鹿 sidecar 已开启，普通资产保持默认行为。

新增 [可选 Compute 蒙皮](COMPUTE_SKINNING.md)：GL 4.3 SSBO、最多八权重、Morph 先于蒙皮，结果作为 VBO 留在 GPU。按 owner/mesh 与快照身份缓存，支持总显存/实例数配置、LRU、关闭/重载释放和失败后回退。Oculus 保留原有兼容路径。本次不是 MDI、Hi-Z 或通用 GPU 场景调度的交付。

初次 Compute 实机运行 `1794835563300` 的四阶段均通过：6144 顶点、256 个实际带权骨骼，位置分量最大误差 `4.7683716e-7`、法线分量 `1.1920929e-7`；CPU/GPU 轮廓差异均为零，Morph 归零和资源重建恢复一致。最终运行 `1794836019101` 再次通过四阶段，并通过四个非零 SSBO 范围绑定恢复、1 MiB 预算缩减及实例关闭释放检查；日志 `build/compute-final-validation.log`。该夹具不证明八个同时权重或持续变化大规模场景的性能收益。

插值世界运行 `1794836199048` 在 Oculus 下通过全部 11 阶段、10 张延迟截图；每次检查插值快照的全部变形顶点均位于自身 AABB，渲染取样不改变模拟时间或效果发射数。主/阴影绘制、地形、移动、传送、重载、NBT、AI、磁盘重开、缓存预算和卸载均通过。日志 `build/interpolation-world-validation.log`；最终 `test build` 为 286 项、285 通过、1 项可选基准跳过，零失败/错误。

此前“未实现插值/Compute”的段落是历史批次记录，以本节为准。剩余主要目标仍为：可比较的多实例压力与更多光影覆盖、服务器权威命中/投射物、成熟引擎的 Ragdoll/布料接入、MDI/Hi-Z，以及自有完整后处理通道；不能据此宣称对比图全部完成。

## 后续增量：KubeJS 与蹄底支撑

`c215d3e` 新增 [KubeJS 集成](KUBEJS_INTEGRATION.md)。依赖为可选安装，未打包
KubeJS/Rhino/Architectury。客户端 `Assimp` 支持复用实际 owner 的状态、动画混合、
层、Morph、已有 IK/LookAt 目标、性能参数、挂点和模型空间射线查询；服务端
`AssimpServer` 操作展示鹿现有的同步状态、贴地和特效字段。模型别名复用同一播放实例，
句柄弱引用且重载后失效。附客户端手持物 Morph 和服务端实体事件示例。

真实 Rhino `client_scripts` 断言通过，日志 `build/kubejs-client-validation.log`。
真实 `EntityEvents.spawned` 的状态/贴地/特效断言通过。没有宣称任意生物替换、
服务器端网格伤害或服务器动画模拟已经接入。

`21f41c2` 修复 [行走蹄底支撑](WORLD_INSTANCE_RUNTIME.md)：原世界测试将禁重力鹿
抬高 0.12 m，切换到未启用待机 IK 的行走状态后仍保留高度。现在进入行走时恢复重力，
并以 JSON 的 `groundBody.supportStates` 和 `contacts` 支撑点修正视觉身体高度。
鹿每 tick 变换 24 个蹄底局部点，进行四次地面采样；运行时不扫描蒙皮顶点或回读 GPU。
待机保留原四腿 CCD，奔跑和跳跃保留原动作的腾空阶段。这不是完整步态规划器。

120 帧真实资产离线测量：旧行走最低蹄底间隙约 0.04964–0.14912 m；修正后为
0.004999735–0.005000301 m，对应配置的 5 mm 余量。世界模拟快照为 4.990 mm，
两次插值快照为 4.608/4.379 mm。Rosetta 通过可选 Maven 坐标重映射启动后，在客户端线程
只读采样得到支撑蹄 5.005 mm；另外三蹄处于抬腿阶段，间隙约 20–46 mm。
探针为 `tools/verification/deer-ground-contact.java`，数值结果在
`logs/rosetta-deer-ground-contact-20261116.json`。以真实蹄底到方块碰撞面的距离验收，
不再仅依赖蹄骨到 IK 目标的距离。诊断脚本中选取旧实体和覆盖最低坐标的问题已经修正，
较早未保存的探针输出不作为结论。

本批完整 `test build` 为 291 项：290 通过、1 项可选基准跳过、零失败/错误。
世界运行 `1794840112096` 在 KubeJS 与 Rosetta 同时安装时通过全部 11 阶段、10 张延迟截图，
包括地形、特效、移动、传送、重载、NBT、AI、磁盘重开、缓存预算和移除释放；日志
`build/deer-contact-world-validation.log`。已查看行走支撑截图
`run/screenshots/assimp-world-runtime-1794840112096-2-world-effects.png`。
本次地面修正验收使用普通渲染路径，没有将它称为新增 Oculus 光影兼容验收。
不安装 KubeJS/Rosetta 的 `clientStartupSmoke` 也通过，日志
`build/kubejs-optional-absence-validation.log` 含启动与初始资源烘焙 SUCCESS，构建成功。

## 代码入口

- [实例与配置](../src/main/java/cn/minerealms/assimploader/animation/ModelInstance.java)、[定义解析](../src/main/java/cn/minerealms/assimploader/animation/ModelInstanceDefinition.java)、[资源继承](../src/main/java/cn/minerealms/assimploader/animation/InstanceDefinitionResources.java)
- [状态与姿态](../src/main/java/cn/minerealms/assimploader/animation/AnimationState.java)、[快照](../src/main/java/cn/minerealms/assimploader/animation/PoseSnapshot.java)、[客户端所有者](../src/main/java/cn/minerealms/assimploader/animation/ClientAnimationManager.java)
- [IK](../src/main/java/cn/minerealms/assimploader/animation/ik/ChainIkModifier.java)、[脚贴地](../src/main/java/cn/minerealms/assimploader/animation/ik/FootPlacementModifier.java)、[Spring](../src/main/java/cn/minerealms/assimploader/animation/physics/SpringBoneModifier.java)、[模型查询](../src/main/java/cn/minerealms/assimploader/collision/ModelCollision.java)
- [共享绘制](../src/main/java/cn/minerealms/assimploader/render/base/BaseAssimpRenderer.java)、[实体扩展](../src/main/java/cn/minerealms/assimploader/render/base/ConfiguredEntityRenderer.java)、[世界粒子适配](../src/main/java/cn/minerealms/assimploader/client/MinecraftAttachmentParticles.java)
- [Morph 语义](MORPH_TARGETS.md)、[受控客户端验证](../src/main/java/cn/minerealms/assimploader/client/AnimationSmokeTest.java)、[性能探针](../src/test/java/cn/minerealms/assimploader/animation/RuntimePerformanceProbeTest.java)
