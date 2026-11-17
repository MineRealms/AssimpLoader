# AssimpLoader / RigWorks / AssimpShowcase 拆分与数据驱动方案

## 1. 边界与依赖

三个独立 Mod，共用当前开发启动器。依赖只有 `RigWorks -> AssimpLoader` 和
`AssimpShowcase -> AssimpLoader`，RigWorks 不依赖 Showcase。跨三个模块的验收代码只进入开发运行目录。

| 模块 | Gradle 组织 | 职责 | 游戏服务端行为 |
| --- | --- | --- | --- |
| AssimpLoader | 根项目 main | 导入、资源生命周期、蒙皮、动画层、IK、Spring、通用刚体、GPU、材质、LookDev、通用脚本接口 | 不注册游戏内容、网络、世界模拟或服务端命令；服务端安装时为空入口 |
| RigWorks | `:rigworks` 子项目，独立 Mod/JAR | 玩家原版姿态适配、角色选择、装备挂载、动作策略、人形配置、KubeJS 角色预设 | 纯客户端视觉；不改变服务端碰撞、攻击或移动 |
| AssimpShowcase | 根项目 `showcase` source set，独立 Mod/JAR | 展示方块、鹿、载具、火箭内容与实验场景 | 方块/实体注册、载具网络与模拟、展示命令 |

通用代码允许在 AssimpLoader 中提供类型、解析器和数学求解器，不意味着本体自动执行服务端行为。
例如 Bullet 求解器由实例所有者显式创建；本体不创建世界物理实例，也不订阅服务器 tick 来执行它。
`net.minecraft.server.packs.resources` 是资源 API 包名，不能据此判断存在游戏服务端逻辑。

原先本体确实包含服务端内容：ShowcaseRegistry、DeerShowcaseRegistry、VehicleRegistry、
VehicleNetworking、RocketCommands 和 VehicleServerMovementGuard。拆分必须迁出这些入口和监听器，
不能只在构建时把同一批类再复制成另一个 JAR。

## 2. 构建与将来独立发布

`gradlew build` 应输出本体（含依赖的 all JAR）、Showcase JAR，以及 `rigworks/build/libs` 中的 RigWorks JAR。
`runClient` 同时挂载三个 source set/Mod；开发验收另外挂载 integration 源码，发行包不带验收程序。
`runServer` 可挂载同三个 Mod，但只有 Showcase 提供游戏服务端行为。

RigWorks 通过 Gradle `implementation project(':')` 引用本体输出，禁止引用根项目源码目录、
Showcase 输出或根项目测试。未来独立时把这一依赖替换为发布的映射依赖坐标，并补上自己的 wrapper/settings 即可。
Forge、Mixin 和 KubeJS 是平台/可选编译依赖，不应通过本体内容包偷偷获得。

已有世界中的 `assimploader:showcase_*`、鹿和载具注册 ID 暂保留，注册所有者变为 Showcase。
不同时改 ID 可避免无关的存档迁移。玩家资源迁到 `rigworks:`；旧脚本需迁到 `RigWorks` 绑定。

## 3. KubeJS + JSON 的职责

用户提出的方向适合当前工程：JSON 描述资源和约束，KubeJS 描述策略，Java 执行通用求解。
把物理积分、逐骨骼/逐顶点循环或 OpenGL 调用移到 JS 会增加调度和分配开销，不适合高性能目标。

| 所有者 | 内容 |
| --- | --- |
| JSON 资产 | 骨骼角色、参考姿态与坐标基、clip 别名、遮罩、混合曲线、IK 链/极向/关节范围、物理体与碰撞组、握点、LOD、着色参数类型与默认值 |
| KubeJS 策略 | 根据原版输入选择动画、层权重、clip 相位、IK 目标和开关、物理模式、物品分类、特效激活及参数 |
| Java 引擎 | 验证与编译定义、姿态缓存、空间转换、FK/IK、蒙皮、固定步长物理、线程边界、GPU 资源管理和错误恢复 |
| RigWorks 原生适配 | 采样 Minecraft 玩家状态/原版姿态，生成统一输入与目标；不把动作风格写成不可替换的核心逻辑 |

VANILLA / SCRIPTED / CLIPS 应逐步成为兼容预设，而不是互斥能力。Clip 是姿态来源，
Script 是调度策略，原版姿态是另一种来源，IK 是约束求解。这些应能组成同一个图。

### 当前基础与缺口

已有：clip 混合/遮罩、脚本 pose、Morph、IK 目标、Rig JSON、刚体 JSON、脚本物理控制、
资源重载和通用实例句柄。不能把“有接口”等同于完整的可组合引擎。

仍需补齐：统一能力清单、按通道的写入所有权、明确的局部/模型/世界空间目标、可验证的求解依赖图、
原版移动/物品使用/攻击分别启用、双手物品接触目标、定义的事务式重载与 LookDev 来源诊断。
现有 native `actions` 布尔开关同时关闭攻击与物品姿态，粒度过粗。

### 目标流程

1. 客户端输入快照和统一时间源。
2. 原版姿态与 baked clips 采样，按遮罩与权重混合。
3. FK 后计算视线、手持物握点、地面等约束目标。
4. 依依赖关系执行动画侧 IK，驱动运动学刚体。
5. 固定步长物理与动态骨骼反馈；显式后物理约束。
6. 发布一份插值姿态，模型、装备、鱼线、阴影与调试共用同一份。

以上是默认依赖示例；不能把所有约束强制固定在同一阶段。反馈环必须明确跨物理步，禁止同帧循环依赖。
变换的平移/旋转/缩放分别拥有通道，多写入者必须声明混合方式。Additive 必须指定参考姿态。

## 4. Blender / FBX 能力控制

FBX 不是 Blender 场景运行时。网格、权重、骨架、关键帧和部分 Morph 可以导入，
但原始 IK 控制器、约束、驱动器、刚体/关节语义不能假设经 FBX 和 Assimp 原样保留。
约束烘焙后的结果只是关键帧，禁用运行时 IK 无法反向去掉已经烘焙进 clip 的 IK 效果。
Blender 的 FBX 导出说明明确区分约束与烘焙结果，见
[官方 FBX 文档](https://docs.blender.org/manual/en/5.2/files/import_export/fbx_legacy.html)。

建议 Blender 导出包采用模型 + 版本化 JSON sidecar：模型带几何/蒙皮/烘焙动画，sidecar 带运行时控制链、
关节限位、物理、握点与能力来源。导入后展示 `available / baked / unsupported / absent` 状态。
脚本只能切换引擎实际解析并实现的能力。STATIC 导入已摊平的骨架不能用开关恢复。

运行时禁用某功能需要区分：停止 clip 轨道、IK 权重为零、物理切为 FOLLOW、清 Morph、
冻结姿态和禁用蒙皮。它们不是同一个 enabled 布尔值。共享不可变模型，实例分别持有开关与状态。

双手武器应从主手求物品根，再由物品 support socket 求副手 IK；弓弦目标单独处理。
不能让主手依赖物品、物品又依赖主手形成循环，也不宜给所有物品硬套同一肘部欧拉角。
这里的目标、极向提示和权重是可配置约束的基本组成，可参考
[Unity Two Bone IK 官方说明](https://docs.unity3d.com/Packages/com.unity.animation.rigging@1.2/manual/constraints/TwoBoneIKConstraint.html)。
游泳穿臂问题首先要求导入左右骨骼/前向基准验证，不能仅检查手臂“有移动”。

## 5. 着色器扩展

火箭燃烧、烟气、喷口和体积着色的语义属于 Showcase。
程序绑定通知、参数类型/边界验证、GPU 状态保存、资源失效、深度读取和通用 pass 调度属于本体。
Oculus 的兼容 hook 也应由本体提供；插件注册自己的参数上传器，本体不 import RocketExhausts。

本轮提供 JSON 声明 + 客户端命令提交的 uniform 桥：`Assimp.shaders()`。
脚本在 tick/reload 时提交值，绑定程序时由 Java 上传，不在 draw 回调调用 JS。
只影响显式声明并存在于程序中的 uniform，不代表可以启用任意 shader pack 内部 pass。
独立后处理 pass、输入纹理/深度/历史缓冲调度仍需后续通用 render graph，不能宣称已有。

着色器开关应由约定的 enabled uniform 驱动；关闭时的参数值必须有定义。
体积火箭默认 native 上传路径保留在 Showcase，避免为迁移而把大块数组每帧序列化到 JS。

## 6. 重载、性能与回退

- JSON 参数重载、图定义重编译、导入配置重建分开；只有导入变体变化才重建共享资产。
- 定义先解析/验证，成功后替换；失败保留旧定义。任意用户 JS 的外部副作用无法保证完整回滚。
- 模板随 Mod 发布但不能静默覆盖用户脚本；安装/升级使用新版本文件与迁移说明。
- 当前 KubeJS 角色重载先清控制器，尚无完整 last-known-good 事务，需要单列实现。
- 优先消除每 tick JSON 转换、无效重复求解和不必要的重置；复杂数学留 Java/Bullet。
- Rust/C++/SIMD/Compute 只在代表性 profiling 指向求解热点后接入，保持相同数据与回退契约。
- 客户端视觉碰撞只采已加载客户端数据；游戏命中箱、伤害和移动仍由服务器权威处理。

## 7. 任务与验收

本轮落地：三个 Mod 隔离、单向依赖、三者联合开发启动、独立打包、服务器行为迁出、
LookDev/诊断扩展接口、通用参数桥、资产/脚本迁移与架构检查。

后续引擎改造按以下顺序推进，不把草案 API 写成已经可用：

1. 关闭已报告的左右骨架、游泳、弓弩、鱼线缺陷，加入几何接触与方向验证。
2. 定义能力清单、骨架空间、通道所有权与版本化 JSON schema。
3. 编译 clip/native/IK/physics 依赖图，KubeJS 仅修改预编译目标与参数。
4. 把动作常量和曲线从 Java/JS 内联迁到 JSON 预设，实现失败保留旧定义。
5. Blender sidecar 导出与完整样例；LookDev 显示来源、覆盖者、目标、限制和性能预算。

验收必须覆盖：本体单独编译且无内容包引用；RigWorks 无 Showcase 引用；发行 JAR 不含跨模块源码；
开发启动发现三个 Mod；客户端可缺少 RigWorks/Showcase；本体无服务端注册与网络；
左右手/主副手与极向一致；脚本重载/禁用恢复原版；装备和鱼线使用相同渲染姿态；
每实例状态互不污染。编译通过不等于这些运行路径已验证。
客户端/服务端隔离必须同时考虑物理侧与逻辑侧；单人世界同样有逻辑服务器，见
[Forge Sides 官方说明](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/)。
