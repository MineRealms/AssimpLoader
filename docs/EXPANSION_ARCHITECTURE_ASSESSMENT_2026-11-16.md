# AssimpLoader 扩展架构评估

后续实施记录：[第一阶段动画基础与验证](ANIMATION_FOUNDATION_2026-11-16.md)。本评估正文保留实施前的架构基线。

日期：2026-11-16。范围：在现有 AssimpLoader 基础上评估模型实例、动画、IK/次级物理、Morph、模型碰撞、特效和渲染扩展。参考 CustomSteve cleanroom 文档中的公开架构建议；不移植其实现，不把自定义 Steve、本地玩家替换、PMX/VMD、动作数据库、展示摄像机/BGM 或通用光影兼容纳入本项目范围。本报告是静态代码和文档分析，没有在本轮运行游戏或构建。

## 结论

扩展可行，无需先用 Rust/C++ 重写核心，也不应先铺开一个完整渲染引擎。AssimpLoader 已有可复用的资源导入/缓存、场景节点、每顶点四权重蒙皮、TBO 与 CPU 回退、GL 资源代际清理、Oculus 专用接入，以及实体/方块展示入口。当前最大结构性缺口是**姿态只有分散的可变状态，没有统一、可发布的模型实例帧**。状态机/混合、IK/次级物理、挂点、碰撞和绘制若各自直接改骨骼，就会产生顺序、线程、跨帧数据和生命周期问题。

建议先新增一个 Java 模型实例运行层和不可变姿态快照，复用现有导入、渲染、资源重载设施。首个交付目标应是“一个测试资产的状态切换/crossfade/挂点，同一姿态稳定用于普通绘制、阴影及包围盒”，不包含新物理库或新原生依赖。拿到可信基线后，再决定具体的 GPU/Natives 优化。

## 可吸收的设计原则

1. **共享静态资产，状态按实例隔离。** `AssimpScene` 和其网格/材质为共享导入资产；播放时间、混合参数、骨骼姿态、Morph 权重、挂点和物理状态属于单个运行实例。改一个实例不能影响另一个实例。
2. **tick 与绘制分离。** 游戏事件在游戏线程被转换为不可变输入；计算阶段生成一个带序号的最终姿态；世界、阴影、预览、装备和特效消费同一已完成帧。绘制回调不推进动画或物理。
3. **一个姿态求值所有者。** 以明确的可扩展阶段组合片段、混合、IK、可选次级物理，最后发布节点/骨骼矩阵、Morph、挂点和动态边界。IK 与物理不是每个模型都必须启用，也不应假定所有资产用同一处理顺序。
4. **先有可比对的 Java 参考路径。** 新后端必须声明能力及回退原因；同一测试资产用 CPU 标量路径作为结果基准，再比较 GPU/Natives。GPU 或 SIMD 不自动意味着更快，不能跳过游戏实机和 GPU 压力验证。
5. **显式所有权和代际。** 导入缓存负责共享场景；每个实例负责自己的动画/物理状态；GL 资源只在渲染线程创建、使用、释放。资源重载废弃旧模型代际，防止旧帧或旧后台任务回写新状态。

## 当前接入点与实际缺口

| 子系统 | 现有接入点 | 扩展前应处理的问题 |
|---|---|---|
| 资源 | `AssimpModelLoader`、`ModelRepository`、`AssimpScene`、`ModelCacheLifecycle` | 场景已将原生 Assimp 数据复制为 Java 数据并释放；可承载更多不可变目标/碰撞描述，但须在释放前复制。实例可引用共享资产，但不得将可变实例状态写入共享场景。 |
| 姿态 | `AnimationPlayer`、`AnimationState`、`BoneHierarchyBuilder`、`ClientAnimationManager` | `AnimationState` 当前为每个蒙皮网格建一份层级并维护一个 `currentAnimation`；渲染从当前播放器直接取矩阵。需统一跨网格的实例姿态，再建立每网格 palette。物品管理器当前按模型共享状态，需在有状态播放的用途支持实例隔离。 |
| 混合 | `AnimationStateMachine`、`AnimationBlender` | 两类 API 已存在，但状态机没有把 crossfade 姿态交给现有 `AnimationState`/renderer；`AnimationBlender.createTransition` 只搭好两个 layer，`transitionDuration` 未驱动权重。属于待接线路径，不是完成的动画混合系统。 |
| IK | `animation/ik` 求解器和控制器、`AnimationPlayer` | 有 Java 求解器和数据测试；实体地面探测、通用资产映射与最终姿态阶段尚无证据。加入后应从最终实例姿态读取/发布。 |
| 蒙皮/Morph | `SkinnedMeshProcessor`、`SkinnedMeshRenderer`、`SkinnedMeshShader`、`CpuSkinning` | 现有 GPU 路径静态 VBO + 顶点着色器蒙皮，上传骨骼 TBO；低能力/光影等情况退回 CPU。Morph 需先在 `AssimpMesh`/`AssimpScene` 复制基准顶点目标，避免跨帧累加，再定义 Morph 与蒙皮顺序。 |
| 渲染 | `BaseAssimpRenderer`、`CachedMeshRenderer`、`OculusEntityBufferCache`、`SceneTransforms` | 当前通过逐网格 draw/cache 工作；Oculus 缓存明确排除骨架场景。可增加后端/批队列，但必须尊重游戏和光影管线的状态与材质约束。 |
| 碰撞/物理 | `ArmoredCarPhysics`、载具边界盒、`ExhaustOcclusion`、`ShowcaseVisibility` | 有载具专用碰撞运动和为尾焰照明使用的区块占据采样，未形成通用模型几何 BVH、骨骼碰撞或角色物理服务。Minecraft 世界形状必须在游戏线程或通过受控快照采集，不可让 native 后台直接访问 Level/Entity。 |
| 重载/释放 | `ClientRenderResources`、`ModelRepository` | 已集中清理若干 GPU、贴图、变形缓存和动画缓存。新增实例快照、物理句柄、Morph 缓冲应挂在模型/上下文代际上清理。 |

## 建议的分步实施

| 阶段 | 范围 | 可见结果与出口 |
|---|---|---|
| 0：运行时契约 | Java `ModelInstanceState`、独立实例时钟、单写者姿态求值、不可变 `PoseSnapshot`/frame ID、实例注册/释放 | 两个相同模型实例可分别播放；同一 frame ID 的普通绘制、阴影、挂点和边界读同一姿态；资源重载后旧帧不会重新发布。以一份新建小型测试骨架和一条简单动画验收。 |
| 1：动画闭环 | 接通状态机、crossfade、中断策略、骨骼遮罩与基础混合；为已有实例提供按用途请求状态的 API | Idle/动作切换与 crossfade 实机运行；停止/换模可恢复 bind pose；没有缺帧残留。先 Java 标量/对象实现，沿用既有插值和绑定姿态数据。 |
| 2：模型附属能力 | Morph Target 导入与零权重恢复；命名挂点、骨骼定位特效/挂载；通用动态包围盒 | 少量目标资产完成 0→1→0 Morph、独立实例表情、挂点/拖尾采样和边界更新。UV/材质动画优先参数化，不复制顶点数据。 |
| 3：受限物理与碰撞 | 固定步长 Spring Bone/次级摆动；粗 AABB→胶囊/OBB→静态网格 BVH 查询；骨骼碰撞代理 | 先一个弹簧链、一个命中查询资产；展示暂停、传送、重载、失去 chunk 等重置/降级。不要在本阶段一起上全身布娃娃。 |
| 4：测量后加速 | Java 数据布局、缓存/分配、跨实例并行评估；必要时 GPU Morph/Compute、跨对象实例批次 | 由代表性资产基准证明 CPU 占用、GPU 变体或 draw submission 成本是实际瓶颈后再选具体优化。每条加速路径保留能力检测、回退和独立对比报告。 |
| 5：选做的高成本扩展 | 多关节 Ragdoll、PCA OBB 自动生成、网格连续碰撞、MDI、Hi-Z、更多游戏效果 | 每项独立立项，不作为前述阶段的完成前置条件。仅当具体目标和测得负载能证明价值时进入开发。 |

这条路线不引入本地玩家替换、VMD/Motion Matching、动作展示模式或通用节点编辑器。实体和方块继续通过现有 Forge 注册/渲染扩展点接入；GUI 复用 LookDev，但其预览播放状态应独立于世界实例。粒子、拖尾、装备挂点只依赖快照提供的节点变换/事件，不各自求解骨骼。

## 计算后端评估

| 工作负载 | 首选方案 | 何时考虑替代实现 |
|---|---|---|
| 动作采样、过渡、普通骨架 FK | Java 17 + JOML，先修复求值语义与分配 | 通常非主要成本；仅在大量实例的 profiler 数据证明是热点时优化。跨实例可在实例彼此独立后并行。 |
| 小规模 IK / Spring Bone | Java 固定步模拟，明确活动/距离/骨骼上限 | 单链计算通常不足以证明 JNI 成本合理；测到很多链/实例占用后再评估批处理 SIMD 或物理库。 |
| 顶点蒙皮 | 当前 GPU vertex skinning 优先；已有 TBO 能容纳超过 uniform 的骨骼 palette，并有 CPU/原版回退 | 在支持 GPU skinning 的驱动上先验证 256+ 骨骼的容量与画面；若需要 CPU 变形数据用于碰撞、截图或影子导出，再测 CPU SIMD。 |
| Morph + 蒙皮 | 先实现正确的 CPU 参考数据和 GPU vertex 目标混合；顶点量大且每帧变化多时评估 Compute Shader | Compute 仅走实际支持的 OpenGL 能力路径，检查写后 barrier、fence/输出槽所有权和正常 draw 消费；驱动不支持时退回顶点 shader/CPU。现有即时 GPU 路径以 GL 3.3 为能力门槛，Compute/SSBO 不是可无条件假定的能力。 |
| 可重复大量 CPU 顶点运算 | 先连续数组、避免临时分配；标量结果对照后评估 JDK 17 Vector API incubator 或 C++/Rust batch kernel | 每顶点 JNI 调用和 Java/native 高频往返不可接受。若 Native，按模型/实例一次批次传版本化 direct buffer，显式容量、地址/缓冲有效期、线程与 close；现有 Assimp natives 的打包/加载不自动管理第二个自建库。 |
| 网格碰撞查询 | Java 粗筛与自有 BVH 基线 | 规模和查询频次有证据后，再比较成熟刚体/连续碰撞库。OBB/PCA 盒和 BVH 面片测试不同，不能用“精确碰撞”合并描述。物理第三方 JNI 原生库需实际支持的 OS/CPU 组合、许可证和 Minecraft 客户端打包调查。 |
| 绘制提交 | 当前缓存 GPU 网格基线，先量每帧 draw/material/CPU submit 数 | 同网格多实例较多时先 GL instancing。MDI 需要 OpenGL 4.3 或相应 ARB 扩展组合，不作为所有 GL 3.3 机器的默认路径；分组和普通 draw fallback 保留。Hi-Z 还需自有或适配可用深度阶段，当前没有通用场景深度所有权。 |
| IBL/SSAO/Bloom/SSR/TAA/CSM 全场景效果 | 复用当前 Minecraft/Oculus 可接入通道，先做目标明确的局部效果 | 不建议现在自建完整 G-buffer/postprocess 栈。那会与第三方 shader pack 的深度、阴影和后处理竞争，并显著扩大适配矩阵；先确定单一普通/光影目标再评估局部通道。 |

**Native 选择建议：** 本阶段不选 Rust 或 C++，也不增加 JNI。Assimp 已通过 LWJGL native 依赖获得格式解析和多平台打包；这不表示动画、IK、碰撞需要 native 才能承担。现有逐顶点蒙皮主要已有 GPU 路径，当前可确认的代码缺口集中在实例状态/帧一致性而非算术吞吐。以后如证明CPU逐顶点路径超预算，Rust 与 C++ 都可以做批 kernel；评估对象应是“端到端 CPU 时间、JNI/复制开销、各平台分发维护成本”，而不是单独的循环微基准。若是图形工作负载，优先评估当前 OpenGL 上的 Compute Shader，并保留已有 shader/CPU 路径。

**SIMD 选择建议：** 不把 Java 17 下的 incubator Vector API 当成第一期基线 API，也不因“SIMD”直接改写 IK/FK。先让数据连续、处理每个顶点/实例的工作可批量执行，量出实际热点。CPU 权重蒙皮、Morph 和 BVH 节点查询较可能受益；单骨链 IK 和文件解码通常应先保持简单 Java。

## 性能候选与测量依据

以下为源码确认的成本来源，**不是本轮测得的性能瓶颈**。

| 位置 | 可确认的行为 | 优先验证/处理 |
|---|---|---|
| `AnimationState.initialize/applyCurrentTransforms` | 每个有权重的 mesh 建层级，更新时向每份层级应用同一播放器结果 | 共享一份实例节点姿态，按 mesh 的骨骼映射/offset 生成 palette；保留每个网格的绑定偏移，不能把所有 palette 直接共用。先测试重复求值减少量。 |
| `AnimationState.update`、`ClientAnimationManager.tick` | 只有当前片段时才更新，物品状态使用墙钟增量并按模型缓存 | 无片段但有 IK/次级物理/Morph 的实例也应能求值；使用明确的暂停/时钟策略，不能依赖播放片段才能运行所有扩展。 |
| `CpuSkinning.deform` | palette 改变时为位置和法线分配新数组，逐顶点混合矩阵并构建法线逆转置；结果缓存按 palette 对象标识判断 | 先建立姿态版本、复用受生命周期保护的输出槽并测分配；不能简单原地复用 palette 而保留旧缓存判断。 |
| `SkinnedMeshVBO` 与 `CpuSkinning` | GPU 保留最强四权重；CPU 读取导入的全部配置权重（最多八权重输入） | 比较性能前先统一或明确权重策略，否则“CPU/GPU 数学一致”可能根本不是相同算法；同样固定法线变换与归一化规则。 |
| `BaseAssimpRenderer`、`OculusEntityBufferCache` | 光影启用时避开自有蒙皮 shader；Oculus 持久缓存排除骨架场景；回退逐三角形向 VertexConsumer 提交 | 对有动画模型分别测 deformation、顶点展开、切线生成、上传与 draw；CPU SIMD 仅能加速其中的运算，不能自动解决提交成本。 |
| `BoneHierarchyBuilder.getFinalTransforms`、`BoneMatrixTBO.upload` | 最终矩阵按调用构造输出数组；绘制时 pack 并通过 glBufferData 上传 | 按姿态版本复用 palette/打包结果；只在版本变化时更新对应实例槽，前提是不覆盖 GPU 正在消费的数据。 |
| `PerformanceProfiler` | 用 System.nanoTime 记录 CPU 调用区间，现有统计主要为 min/max/avg/total | 这些计时不等于 GPU 完成时间；补 JFR/分配采样、非阻塞 GPU timer query 和每帧成本分布。诊断开销也须纳入比较。 |

一个量级示例：256 个 4×4 float 骨骼矩阵约 16 KiB；10 万个唯一顶点仅位置+法线就有 2.4 MB。一秒 60 次更新分别约为 0.98 MB 和 144 MB 数据量，后者尚未包含索引展开、UV/切线/光照、复制及缓存流量。这只是按字段大小的理论字节数，不是实测带宽或 FPS。它说明优先常驻静态几何、上传小量姿态数据有价值，也说明 256 骨矩阵求值与几十万顶点反复展开是不同问题。

GPU vertex skinning 已是有效的基础路线。Compute 的具体价值是将 Morph/蒙皮结果生成一次，供主视图、阴影等多个 pass 重用；需要把一次 dispatch、输出内存流量和多次 vertex shader 计算的成本进行比较。小网格/单 pass 不保证 Compute 更快。当前 Oculus 格式还需要扩展 ENTITY 顶点、切线、实体 ID、光照和阴影协议；Compute 输出必须能被该路径直接消费，若为了接入又每帧读回 CPU，则应把读回和同步开销全部计入，不能宣称获得 GPU 常驻收益。

当前同一 GL 上下文内可按 dispatch → 与下一消费者匹配的 memory barrier → draw 顺序使用结果，无需在每次绘制前等待 CPU fence。Fence 主要用于避免跨帧输出槽在 GPU 尚未读完时被 CPU/后续任务覆盖；CPU 可用的姿态快照与 GPU 命令完成也应分别表示。重载、窗口尺寸变化和光影切换使依赖失效时，要重新建立有效缓冲和历史，不能把 barrier 当作完成或生命周期证明。

参考文档的坐标约定不直接照搬：它建议角色前方为 -Z，本项目载具约定为 +Z。继续沿用现有 Assimp 导入转换、每个资产明示的朝向和绑定偏移；新公共接口明确 model/local/world/camera-relative 空间，远世界位置采用 double 锚点和相对 float 数据。先通过非对称轴向资产核对，避免改变现有模型方向、尺寸、保存 ID 或资源身份。

## 对 Native 的具体取舍

1. **无需替换 Assimp。** 保留 LWJGL 导入及缓存资源身份，不新写格式解析器，也不新增一套由 native 持有的完整模型资源运行时。先允许窄的可选计算后端读取本项目已验证的数组/缓冲。
2. **新增刚体库的理由可以是成熟功能。** 后续 Ragdoll 引入成熟的约束/碰撞求解器，不必先证明 Java 在数值上“算不动”；应比较库的功能、Forge/Java 17 接入、线程/原生库打包和维护成本。已有 Java 绑定可减少自建 JNI 工作；平台发布声明须验证依赖实际覆盖范围。
3. **自建计算内核只选一条维护路径。** 需要直接集成 Bullet 等 C++ 库时优先现成绑定或 C++ 薄封装；若仅实现独立数据算法且有 Rust 维护能力，Rust 可作为候选。Rust 不自动加速也不消除 FFI、unsafe SIMD 或原生崩溃边界，C++ 也不自动适配现有 LWJGL 的包内加载。
4. **跨语言接口以批次为单位。** 候选是整批 CPU deformation 或整批 collision query，不是 getBone/setBone/getVertex 等每元素 JNI 调用。静态输入长期驻留、动态输出按需复用；明确 ABI/schema、句柄代际、容量/溢出检查、错误结果、所有权、取消和重载顺序。异常不跨 FFI 边界。
5. **SIMD 保留通用路径。** 先标量参考，再使用运行时检测选择 AVX2/SSE/NEON 等实现，报告真正启用的内核；不能把构建机器的 CPU 特性直接作为发布要求。不额外引入 JDK 17 incubator 启动要求作为默认安装前置条件。
6. **服务端碰撞保持 CPU 可用。** AABB/BONE/OBB 粗细查询使用服务端定义和权威姿态；需要 MESH 精确命中时使用 CPU BVH/碰撞库或按候选局部变形。不得为了命中判定每 tick 同步读回整模 GPU 顶点。游戏线程批量提交世界碰撞形状与增删事件，native 仅消费数据快照。

新增 JSON 先定位为少量带版本的模型实例定义：引用现有模型资源、命名动画、过渡、挂点与可选约束；保持已有 geometry JSON、Forge 注册项和缓存 identity 兼容。后续支持继承时明确合并规则和循环检查。客户端表现资源与服务端玩法碰撞定义分开；不在首期搭建任意玩法脚本、动态创建所有注册项或全套资源分发协议。

## 首步规模与推进门槛

先完成 Java 实例帧、两实例隔离、最小 crossfade 和一个挂点；初期在现有主线程/渲染线程边界上工作，**不先增加常驻计算线程池、全功能 Native Runtime 或可视化图系统**。让现有 `AnimationState` API 能逐步委托新求值层，渲染器逐步切换读取最终快照，保留其他模型的原路径。已有只读缓存封装需要防止数组和矩阵被外部修改，不能仅用 unmodifiable Map 当作深层不可变。

性能样例按同一批资产分别测试 1/10/50 个独立实例、64/256 骨、不同顶点量和 Morph 活动目标数，包含无光影及明确的 Oculus 组合；这些是建议测试档位，不是承诺支持上限。记录 warmup、分辨率、驱动、视角、阴影 pass 数和可见实例。冷导入/重载与持续帧成本分开，遇到劣化只扩大针对该风险的测试。

只有测量指出具体阶段超出商定预算后才切换后端：分配高先复用/布局优化；Java 数值热点考虑 SIMD/Native；重复几何运算考虑 vertex/Compute；提交热点考虑缓存/instancing；GPU 片元成本高先减少采样、覆盖面积或分辨率。完整延迟渲染/全套后处理、MDI、Hi-Z 和布娃娃继续保留为后续扩展目标，分阶段评估，不因本轮收敛而视为已完成或永久取消。

## 验证清单

- **CPU 正确性：** 非对称 2 段骨架、非对易父子旋转、绑定姿势、缺省动画通道、两个独立实例、crossfade 开始/中点/结束、中断/重入。
- **Morph/蒙皮：** 权重 0→1→0 无累积；目标与法线数量校验；CPU 参考、vertex shader 与 Compute 对同输入的数值容差和画面一致；高骨数 TBO 和 uniform/CPU 回退。
- **世界集成：** 通用实体/方块样例、静止/移动、视锥/远距、阴影一致、粒子挂点、资源重载/替换/删除、光影开关与退出世界。
- **碰撞/物理：** 具有已知几何答案的 AABB/OBB/BVH/胶囊/ray 夹具；固定步同总时长；暂停、长帧、传送、维度/区块变化和模型换代。
- **性能：** 相同机器/资产/实例数测试 scalar Java、所选 JVM/GPU 路径；报告 CPU 时间、GC/分配、GPU 时间、draw call、上传字节、回退次数和高百分位帧时。现有历史测试数和缓存上传计数不等同于这些性能基准。
- **边界：** 明确通用模型运行层不替换任意原版玩家/生物；不宣称支持未实测的光影组合、原生平台或 Assimp 子格式。

现存测试报告在 2026-11-14 记录 171 项测试、0 失败/错误/跳过。它说明现有数据层和部分渲染路径有回归覆盖，不覆盖本报告拟议的新实例帧、Morph、通用物理/碰撞或新后端。此处没有重跑测试。

## 主要资料

代码依据（以下均为当前 AssimpLoader 源码，性能表是静态成本分析）：

- [实例动画状态](../src/main/java/cn/minerealms/assimploader/animation/AnimationState.java)、[物品动画管理](../src/main/java/cn/minerealms/assimploader/animation/ClientAnimationManager.java)、[层级和 palette](../src/main/java/cn/minerealms/assimploader/animation/BoneHierarchyBuilder.java)。
- [混合器](../src/main/java/cn/minerealms/assimploader/animation/AnimationBlender.java)、[状态机](../src/main/java/cn/minerealms/assimploader/animation/AnimationStateMachine.java)、[共享绘制分派](../src/main/java/cn/minerealms/assimploader/render/base/BaseAssimpRenderer.java)。
- [CPU 变形](../src/main/java/cn/minerealms/assimploader/rendering/CpuSkinning.java)、[GPU 权重上传](../src/main/java/cn/minerealms/assimploader/rendering/SkinnedMeshVBO.java)、[骨骼 GPU 路径](../src/main/java/cn/minerealms/assimploader/rendering/SkinnedMeshRenderer.java)、[TBO 上传](../src/main/java/cn/minerealms/assimploader/render/BoneMatrixTBO.java)。
- [Oculus 持久几何缓存](../src/main/java/cn/minerealms/assimploader/rendering/OculusEntityBufferCache.java)、[GPU 能力检测](../src/main/java/cn/minerealms/assimploader/render/GLCapabilityDetector.java)、[CPU 计时器](../src/main/java/cn/minerealms/assimploader/debug/PerformanceProfiler.java)、[图形资源释放](../src/main/java/cn/minerealms/assimploader/render/ClientRenderResources.java)。

- [AssimpLoader 当前架构概况](PROJECT_OVERVIEW.md)、[当前渲染合同](RENDERING_INTEGRATION.md)、[载具物理边界](VEHICLES.md)、[尾焰实机验收与边界](ROCKET_VALIDATION_2026-11-14.md)。
- CustomSteve 参考：[清理文档入口](../../Reverse/customsteve-1.21.11-rc-hf4-analysis/docs/cleanroom/README.md)、[Native 合同](../../Reverse/customsteve-1.21.11-rc-hf4-analysis/docs/cleanroom/02-native-contracts.md)、[架构路线](../../Reverse/customsteve-1.21.11-rc-hf4-analysis/docs/cleanroom/03-architecture-and-roadmap.md)。
- [Assimp morph mesh 输入](https://github.com/assimp/assimp/blob/master/include/assimp/mesh.h)（只说明数据结构；实际文件格式和绑定版本仍要逐项验证）。
- [OpenGL multi draw indirect 扩展](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_multi_draw_indirect.txt)；批量提交能力需对比当前上下文，不代表驱动/Shader 能自动兼容。
- [Libbulletjme 实现与平台 native 边界](https://stephengold.github.io/Libbulletjme/lbj-en/English/implementation.html)；作为候选类别，不是本项目已选依赖。
- [Java 17 JNI 直接缓冲区规范](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html#nio-support)、[JEP 414 Vector API](https://openjdk.org/jeps/414)。
- [OpenGL memory barrier](https://registry.khronos.org/OpenGL-Refpages/gl4/html/glMemoryBarrier.xhtml)、[OpenGL 4.6 核心规范](https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf)、[Rust SIMD 架构接口与检测](https://doc.rust-lang.org/std/arch/index.html)。
