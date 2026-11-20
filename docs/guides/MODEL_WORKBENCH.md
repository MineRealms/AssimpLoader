# 游戏内模型工作台

更新日期：2026-11-19。本指南核对了当前源码中的界面与数据流程，不代表所有模型、窗口尺寸、光影和双客户端组合均已完成实际验收。

工作台把 FBX 等资产变成可编辑项目，在游戏里调整配置、预览已有动画并保存。编辑的是配置和独立预览姿态，不会回写源 FBX、修改网格顶点、骨骼权重或动画关键帧。

`/lookdev` 使用原生 Minecraft Screen，不依赖 ModernUI。第一行选择模组分区；AssimpLoader 的第二行包含 Models、Edit、Debug。旧 Nodes 空入口合并到 Edit / Tree。RigWorks 等模组注册自己的分区和功能页，公共接入方法见 [GUI 扩展](WORKSPACE_EXTENSIONS.md)。实际的 Photon 模型/材质导入集成继续保留。

## 打开与导入

1. 进入世界，执行 `/lookdev`，在顶部切到 **Edit**。命令不可用时检查客户端 `devLookdevEnabled` 设置。
2. 点击导入图标，在操作系统原生文件选择框中选择模型或工作台 ZIP。裸 FBX 等文件默认创建普通资产项目；从 **RigWorks > Characters > Import avatar** 等扩展入口导入，才初始化角色等专属配置。已有扩展标记的 ZIP 则恢复相应扩展的导入流程。模型有骨骼并不代表它一定是玩家模型。
3. 等待导入状态结束。左侧 **Files** 列出工作台项目和已经加载的模型；导入后进入 **Tree**。
4. 调整尺寸、材质和动画，按 **Save** 保存项目；需要在运行模型上使用配置时，再按 **Apply** 或进入对应模块扩展。

Import、Open 和材质贴图槽的文件浏览按钮均使用系统文件选择框，不要求输入地址。Windows 使用系统文件对话框，macOS 使用系统选择器，Linux 使用可用的桌面文件选择器；缺少图形后端时报告错误，不退回地址输入或控制台提示。文件选择在后台工作线程运行，并记住本次会话最近使用的目录。取消选择保留当前项目；选择期间离开该界面，返回的文件不会意外覆盖其他界面。

推荐直接选择原始 FBX，把贴图保留在旁边或被引用的子目录里。仍可把文件或文件夹拖入工作台，一次多个路径只处理第一个；直接拖入裸模型按普通资产导入。含多个无法唯一确定的模型的目录应改为选择其中一个文件。

工作台导出的 `.zip` 和 `.rig.zip` 可以直接 Import/Open，无需手动解压。包中已有的扩展配置会保留；没有安装对应模组时也不会丢弃未知扩展数据。这里的 ZIP 指工作台项目包，不是任意模型网站下载压缩包。

对带 `[RigWorks]` 标记的角色 ZIP，普通 Import、Open、拖入和 RigWorks 导入入口都会询问“创建独立副本 / 更新包中原副本”。取消会恢复之前的编辑项目并清理本次新解压的项目目录。打开已经保存的本地 `project.workbench.json` 则保留身份，不重复询问副本类型。

点击 **Export** 后可以选择要包含的配置组。模型本体、可读取的依赖和 PBR 贴图始终包含；模型 TRS、节点覆盖、材质/PBR 覆盖、Socket、动画预览以及扩展拥有的配置分别勾选。扩展可将一个配置面板拆成多个导出组。取消某组只影响导出的 ZIP，不改当前编辑会话；同一 ZIP 仍可再次导入为工作台项目。

导入使用动态模式，保留可用的节点、蒙皮和动画，并复制可识别的模型依赖，源目录保持不变。缺图、缺 UV 和失效引用会进入诊断；无法从源文件恢复的贴图不会自动生成。

从已加载的资源包模型创建项目时，还会复制 `.instance.json`、其 JSON 父定义、LabPBR `_n/_s` 贴图，以及安装的扩展所声明的依赖。生成的 `<模型名>.resources.json` 保留原资源 ID 到项目内文件的映射，避免转为本地文件后丢失命名空间引用。模型旁的 `.instance.json` 会实际参与本地文件模型的实例初始化；它不是只能随 ZIP 携带的无效附件。格式与限制见[项目格式](../reference/WORKBENCH_PROJECT_FORMAT.md#portable-resource-dependencies)。

## 项目位置

默认根目录在当前游戏目录下：

```text
config/assimploader/workbench/
  <模型名-哈希-项目后缀>/
    project.workbench.json
    assets/
      model.fbx
      textures/...
    extensions/               # 可选的模组附加文件
```

用 JVM 参数 `-Dassimploader.workbenchDirectory=<项目根目录>` 可覆盖默认值，建议填绝对路径。当前没有目录设置面板。

打开图标通过原生文件选择框选择 `project.workbench.json` 或工作台 ZIP，也可从 **Files** 选择项目。底层接口仍支持目录；拖入含 `project.workbench.json` 的目录会打开原项目，保留已有配置。ZIP 导入会在配置的项目根目录中新建独立副本，校验完成后才发布，不覆盖已有项目。保存仅允许写入该根目录；直接打开外部项目目录不会自动把它复制进来。

## 视口操作

| 操作 | 作用 |
| --- | --- |
| 空白处左键拖动 | 环绕相机 |
| 右键、中键或 Shift+拖动 | 平移 |
| 滚轮 | 推近、拉远 |
| Shift+滚轮 | 垂直调整取景 |
| `F` 或取景图标 | 按整个模型及根缩放重新取景 |
| 移动、旋转、缩放图标 | 切换 Gizmo 模式 |
| 左键拖动轴或旋转环 | 编辑当前选中变换 |
| 缩放模式的中心方块 | 等比缩放 |
| `Ctrl+Z` | 撤销 |
| `Ctrl+Y`、`Ctrl+Shift+Z` | 重做 |
| `Ctrl+S` | 保存 |

红、绿、蓝分别标识 XYZ，悬停会显示操作提示。拖动变换会暂停预览，一次连续拖动合为一条撤销记录。数值框用于精确调整，非法数值显示错误颜色。右侧 Inspector 内容较长时，在面板内滚轮滚动。

通用 **Edit** 是独立预览视口，不是正常世界画面的叠加编辑器。动画时钟独立于真实实体，不会为了预览动作而让玩家真的下蹲、跳跃或死亡；暂停预览不会暂停服务器。

## 层级与变换

| 标签 | 内容 |
| --- | --- |
| **Files** | 已保存项目与当前已加载模型 |
| **Tree** | 模型根变换、节点层级 |
| **Mats** | 材质；选中材质的网格有颜色高亮 |
| **Slots** | 通用 Socket |

项目启用扩展后，列表还会出现 **[模组名] 面板名**。例如 RigWorks 的 Rig、Actions、Equipment、Behavior；选中后仍使用同一个右侧 Inspector、预览视口和撤销历史，不再开启另一套 Rig Editor 预览窗口。

标签按可用文字宽度自动排布；GUI 缩放 2/3 下较窄的侧栏使用 2×2，并提供 Tooltip。Filter 和列表随标签行数下移，绘制与点击区域保持一致。时间轴的 Speed 使用行内标签，空间不足时隐藏 Speed、Loop 等次要控件；这不代表全部窗口和高 DPI 组合已验收。

上方 Filter 过滤当前列表。**Tree** 的 **Model transform** 调整整件资产：位置以米记录，通常按 1 米对应 1 格理解；旋转是 XYZ 欧拉角，单位为度；缩放可分轴调整。**Target height (blocks)** 根据导入包围盒计算等比缩放，不会修正源单位或重新建模。

节点 TRS 是叠加在导入或动画局部变换上的偏移。**Visible** 控制该节点及子层级，**Reset transform** 重置当前变换。旧静态/预变换缓存不一定保留原始层级；需要节点或骨骼编辑时，优先重新导入源文件。

**Skeleton / sockets** 显示节点位置点和 Socket 名称。当前没有通用骨骼点选、网格轮廓描边或顶点选择。底部显示三角形数、未保存状态和诊断数，悬停底栏可查看前若干条诊断。

## 材质与贴图

**Mats** 支持基础色 RGBA、Metallic、Roughness、HDR emission、Alpha cutoff、透明模式和双面开关。

| 模式 | 行为 |
| --- | --- |
| **OPAQUE** | 不透明，忽略基础色透明度 |
| **SOURCE** | 新导入默认；沿用源材质透明分组和纹理 Alpha，不强制填满头发片等裁切区域 |
| **MASK** | 根据 Alpha cutoff 裁切 |
| **BLEND** | 半透明混合；复杂交叠仍受透明排序限制 |

**Texture slots** 可填写 `albedo`、`normal`、`specular`、`roughness`、`metallic`、`occlusion`、`emissive`。清空字段移除对应覆盖。

项目贴图通常相对模型目录填写。例如模型是 `assets/model.fbx`，图片是 `assets/textures/metal.png`，引用为 `textures/metal.png`。在已有项目新填外部路径不会立即复制图片；本地 Save 仍保存该配置。Export 会把可读取的外部覆盖贴图复制到包内，并只改写包内文档的相对引用；找不到的配置贴图会阻止导出。为了让本地项目也便于移动，仍建议先把图片放进 `assets`，再填写相对路径。贴图约定见 [PBR 资产规范](PBR_ASSET_SPEC.md)。

预览器使用固定光照和简化 PBR，支持基础色、法线、LabPBR 输入和数值外观预览。各槽可保存，不保证所有工作流和通道语义均完整显示，也没有法线翻转或通道重排面板。它不执行 Oculus / Iris 光影包的完整世界管线；实际阴影、环境反射、Bloom、曝光和玻璃效果要在目标世界渲染路径检查。

运行时覆盖已消费双面开关，包括普通实体提交、静态 VBO、GPU 蒙皮及 Oculus 实体缓存；只有一种剔除状态的批量后端会将双面材质交回即时 VBO 路径。OPAQUE 在现有后台贴图准备任务中将基础色 Alpha 置为 255；MASK 将“纹理 Alpha × 材质 Alpha”与 cutoff 比较后生成二值 Alpha，顶点 Alpha 归一为 1，避免重复相乘。BLEND 保留连续 Alpha。缺图材质同样先使用白色贴图应用这些规则，原始贴图和法线/LabPBR 图不变；变体随对应材质缓存，不逐帧转换。Oculus 仍控制自己的实体着色器，不需要插入裁切 Uniform。与其他异步贴图一样，加载完成前可能短暂显示占位白色；具体光影照明和透明排序仍需目标世界验收。

normal/specular 由独立预览器创建和缓存，不依赖 Oculus 建立纹理。贴图来源改变时替换对应缓存，关闭预览、切换项目或资源重载时释放预览自有纹理；数值材质调整使用 Uniform，不逐帧重传这些图片。

## 动画

默认显示绑定姿态。底部上一段、下一段按钮选择已有 Clip，播放按钮开始或暂停，Loop 控制循环，Speed 控制速度。界面显示名称、时间和总时长；点击时间条跳到指定时刻并暂停。当前没有连续拖拽时间条、可折叠轨道或关键帧编辑。

工作台在 CPU 采样骨骼动画，顶点蒙皮在 GPU 预览着色器执行，使用驻留 VBO 和骨骼矩阵缓冲。拖动变换不会重新导入 FBX、重建全套几何或要求 F3+T。无动画模型仍可预览和编辑。

当前会采集 Morph 信息和动画权重，但预览 GPU 着色器尚未把 Morph 位移应用到几何。不要用这里的画面判断表情或 Blend Shape 已正确接入世界渲染。

## Socket

在 **Slots** 点 **+ New socket** 创建挂点，当前自动命名为 `socket_N`。**Bone** 填已有节点的结构路径或名称，再用 TRS/Gizmo 调整局部偏移；**Remove socket** 删除选中项。

撤销/重做会刷新列表；选中挂点若已不在当前草稿里，会清空失效选择，避免 Inspector 或 Gizmo 继续引用已删除挂点。挂点数据恢复后可在 Slots 重新选中。

项目会保存 Socket 数据并显示位置，但不会自动生成物品或把它挂到角色身上。物品、特效或炮塔部件仍需要消费者读取 Socket；当前没有 Socket 重命名和通用挂物品预览。

## 保存与应用

| 动作 | 当前行为 |
| --- | --- |
| 修改字段/Gizmo | 更新内存草稿和独立预览 |
| **Revert saved** | 清理恢复草稿成功后，回到会话最后成功保存的文档并清空历史 |
| 退出提示中的 **Discard** | 同样清理恢复草稿，成功丢弃编辑后退出 |
| **Apply** | 交给本地运行时或模块扩展，不代表服务器接受 |
| **Save** | 原子写入项目；首次保存缓存模型时先建立资产项目 |
| **Export** | 把当前草稿、资产和扩展附加文件打成可重新编辑的 ZIP；尚无项目时先建立项目 |

普通资产的 Apply 按来源标识生效，可影响本客户端使用同一资产的多个实例，不是单个实体编辑。消费者也可显式使用按实例隔离的覆盖；RigWorks 用这种方式区分不同玩家的节点和材质配置。Apply 不生成展示实体、不改碰撞箱、不注册新物品；重启后也不会仅因磁盘上存在项目，就自动应用到运行模型。

场景实例保留所属节点，因此多个节点复用同一网格时，可以分别隐藏，父节点隐藏也会作用于其子树。扩展若手动构造 `SceneTransforms.Instance`，需将所属 `AssimpNode` 作为第三个参数传入；兼容的两参数构造器没有节点身份，只能在该网格的所有节点都隐藏时整体隐藏。

Save/Apply/Export 先等待扩展的 `prepareAsync` 完成源文件校验和捕获，再通过同步 `prepare` 写入派生配置。Save 随后保存统一文档，成功后调用扩展的 `saved` 回调。项目包含 RigWorks 扩展时，这个回调会保存并应用角色，也可能触发已启用的自动分享。独立的 Apply 调用扩展的应用入口，不要求先写入项目 JSON；不要把本地应用成功理解为服务器已接受版本。

保存期间仍可编辑。首次建立资产项目会保留最新编辑和撤销历史；开始写 JSON 后的新编辑继续留在内存，底栏会提示 **newer edits remain**，需要再次保存。保存后的应用使用实际写入的那份快照，Revert saved 也回到该版本。

历史上限为 128 条。已建立项目的未保存修改约每 100 个客户端 tick 写入 `draft.workbench.json`；重开时仅恢复与当前正式版本及资产一致、且不早于正式文件的草稿，并提示 **Recovered unsaved draft**。旧版本、损坏或没有版本关联信息的历史草稿会跳过并保留原文件供检查，诊断区显示原因。这不是正式保存：检查后再 Save，或用 Revert saved 回到正式版本。尚未建立项目的缓存模型没有这份磁盘恢复文件。

Revert/Discard 在有界 IO 队列清理恢复文件，使排队或写入中的旧自动保存失效，避免重开又恢复已丢弃内容。清理期间暂停编辑；失败时保留当前编辑和窗口，并在底栏报告错误。其他保存/导入任务尚未结束时需先等待。Open 的路径输入与拖放也遵守未保存检查，不会直接替换脏项目。

导出位置是项目根目录的 `exports/<项目目录名>.zip`。其中包括 `manifest.json`、`project.workbench.json`、`assets/` 和可选的 `extensions/`。清单记录容器版本、每个文件的字节数和 SHA-256；导入拒绝损坏、遗漏、重复、越界路径和大小超限。旧版没有清单的工作台 ZIP 也可导入，但会提示缺少校验信息。具体限制见 [项目格式](../reference/WORKBENCH_PROJECT_FORMAT.md#portable-zip-packages)。校验不代表作者可信，AssimpLoader 不在导入时执行扩展脚本。

这是可移植的编辑项目，**不是 Minecraft 资源包**，不会自动生成 `pack.mcmeta`、`model.json`、`item.json` 或 `blockstate.json`。把它放进 `resourcepacks` 不等于完成游戏接入。

## RigWorks 扩展

安装 RigWorks 后，导入对话框可显式选择 **RigWorks**；Characters 页的导入入口也会转到这套流程。初始化要求模型有骨骼。重新打开已有 RigWorks 项目或 ZIP 会保留并读取 `extensions.rigworks`，无需再次转换。普通带骨骼资产仍保持普通资产，不会被自动接管。

| 共享编辑区 | 内容 |
| --- | --- |
| **Tree / Model transform** | 角色整体位置、旋转、比例；RigWorks 接管实际角色根变换，预览和应用使用同一份参数 |
| **[RigWorks] Rig** | 人体角色到骨骼的映射、参考/原版姿态校准旋转 |
| **[RigWorks] Actions** | 原版动作意图到已有 Clip 的映射与预览 |
| **[RigWorks] Equipment** | 打开持物校准，以及手部接触、物理、落地和特效开关 |
| **[RigWorks] Behavior** | 角色身份、行为源刷新及行为模板入口 |

骨架树、材质、Socket、动画时间轴、相机、撤销/重做、Save、Apply 和 Export 由 AssimpLoader 统一提供。RigWorks 只贡献角色语义和运行时接入；当前顶部的 RigWorks 分区保留 Characters 选择页，不再重复注册 Rig Editor 和 Equipment 页面。

导入的 FBX 与当前角色模型字节完全一致时，按 SHA-256 继承原角色的配置和身份，包括骨骼校准、动作映射、装备、Rig/物理和脚本所依赖的资源标识；项目副本不要求是同一个内存对象。没有匹配角色的首次导入才使用简单名称匹配和约 1.8 格的建议比例。自定义骨骼命名、坐标轴和手掌朝向仍需检查，缺少的动画也不会被自动创作。

旧版曾生成骨骼和动作映射全空的临时角色。如果打开该项目时，当前选择的原角色与其 FBX 哈希一致，会恢复原角色设置并保留项目中已保存的比例、前向 yaw、根 TRS 和视觉选项；状态栏会提示恢复。用户自行编写的空映射配置不会按此规则替换。

角色尺寸和朝向直接在 Tree 的 **Model transform** 调整，也可使用同一套 Gizmo。RigWorks 将这些编辑写入人物变换，避免再叠加一套通用根变换。Save/Apply/Export 前会将通用节点、可见性、材质和 Socket 配置写入角色的 `assetEdits`；运行时按玩家实例应用，编辑 A 不应污染使用相同 FBX 的 B。

统一工具栏 **Save** 持久化项目后保存、启用并选择角色；**Apply** 准备并应用当前草稿。装备面板的 **Calibrate held items** 等待项目保存成功后才进入校准。保存失败不会把内存草稿标成已保存。

重新打开已有项目应使用 **Open** 或 Files 列表中的项目项；再次导入裸 FBX 会创建独立项目，不会从文件名猜测应覆盖哪一份旧项目。导入包含 `project.workbench.json` 的项目目录会打开该项目。

手动修改项目 JSON 后，重新 Open 会读取磁盘。手动修改 `config/rigworks/characters/` 中的角色 JSON 后，使用角色页的 Reload 图标、`/assimploader character reload` 或 F3+T 清除旧配置和预览缓存并重新加载。重开角色配置未修改的项目时，较新的角色配置文件优先于较旧的项目快照；较新的项目文件则保留项目参数。尚未保存的角色编辑优先保留；只改通用材质等内容的草稿仍可合并较新的角色配置。

角色应用成功、加载就绪后，**Equipment transform** 进入正常世界中的双手校准。它冻结人物视觉姿态，显示真实持物和绑定点，使用世界及光影照明；这与通用 Edit 的独立视口不同。详见 [装备变换工作台](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/EQUIPMENT_TRANSFORM_WORKBENCH.md)。

## 共享边界

RigWorks 分享完整角色配置：比例/根变换、骨骼和动作映射、双手装备及规则、Rig/物理配置、视觉选项以及该角色的节点/可见性/材质/Socket 覆盖，并可携带专用 Avatar 包的 JSON/JS 行为源。服务器接受版本后，其他客户端按该玩家分别应用。上传自己的配置和显示别人的角色是独立开关。

目前不随之共享的内容包括：

- 普通资产项目的整个文档；角色只携带 RigWorks 准备的角色配置和 `assetEdits`，不分发通用工作台项目目录。
- 相机、选中项、撤销历史和未确认草稿。
- FBX、图片及模型资源文件；接收端仍需匹配资源和内容哈希。

本地预览、保存项目、下载网络包和服务器接受版本是不同阶段。开关、缓存与状态说明见 [角色配置同步](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/CHARACTER_CONFIGURATION_SYNC.md)。

## 当前边界

当前没有网格/UV/权重/关键帧创作、节点材质图、完整通用世界视口、资源包导出、面向所有普通资产的通用网络同步，以及高级 IK、次级运动和 LOD 面板。复杂资产继续在 Blender 等 DCC 软件制作，再到工作台配置。

详见 [项目格式](../reference/WORKBENCH_PROJECT_FORMAT.md) 和 [后续 TODO](../roadmap/IN_GAME_WORKBENCH_TODO.md)。

## 扩展 API 接入规范

本节面向基于 AssimpLoader 开发编辑器扩展的模组。扩展实现 `WorkbenchExtensions.Extension`，在客户端初始化时调用 `WorkbenchExtensions.register(extension)`。`id()` 必须是稳定的 `[a-z0-9_.-]` 标识，最长 64 字符；重复注册不同实现会报错。`supports(context)` 决定当前项目是否启用该扩展，建议检查项目自己的 `extensions.<id>` 标记，而不是仅检查有没有骨骼。

| 接口 | 扩展的职责 |
| --- | --- |
| `canImport()`、`initialize(context)` | 提供显式导入类型；异步完成预处理和扩展配置，完成前不宣告导入成功 |
| `panels(context)` | 注册自己的 `Panel`；通过共享 `Inspector` 构建控件，使用同一个预览、选择和撤销会话 |
| `ownsRootTransform()`、`rootTransform` getter/setter | 让 Tree、Gizmo 和预览编辑消费者的根变换，避免角色根变换再叠加一份通用根变换 |
| `prepareAsync(context)` | 在 Save/Apply/Export 取快照之前异步读取、校验并捕获外部作者文件 |
| `prepare(context)` | 在客户端线程把派生结果写入统一文档；保持短小，不执行文件扫描或阻塞等待 |
| `saved(savedContext)` | 正式保存成功后处理那份已落盘快照；此处不是当前仍可继续编辑的草稿 |
| `handlesApplication()`、`apply(context)` | 接管消费者自己的应用方式；接管后不会执行普通资产按来源生效的 `WorkbenchRuntime.apply` |
| `packageFiles(context)` | 返回已准备好的附加文件快照，交给公共 ZIP 导出流程 |
| `exportOptions(context)` | 声明公共 Export 对话框中的可勾选数据组 |
| `exportDocument(context, selected)` | 只改变输出文档，不能改写当前草稿 |
| `packageFiles(context, selected)` | 按勾选组提供 ZIP 附加文件 |
| `context.choose(...)` | 请求工作台显示扩展选项对话框，完成后异步返回选择 |

一个项目只应有一个根变换拥有者；当前公共界面选择首个可用的 `ownsRootTransform()` 扩展。`handlesApplication()` 只抑制通用来源级 Apply，公共界面仍会调用各可用扩展的 `apply`。扩展应在自己的命名空间内工作，不接管其他消费者的配置。

### 异步任务与错误

行为重载、重新分析等面板操作通过 `Context.runTask` 进入工作台的忙碌状态、会话检查和错误提示：

```java
inspector.action("Reload sources", () ->
        context.runTask("Reloading sources", () -> reloadSourcesAsync(context)));
```

这里的 `reloadSourcesAsync` 是消费者自己的方法。`runTask` 不会自动把方法体移到后台线程：磁盘读取和重预处理仍由扩展安排到自己的有界工作队列。修改 `context.session()`、刷新界面和操作 Minecraft 渲染对象必须回到客户端线程；`initialize`、`prepareAsync` 的完成也应在该线程交付。失败应让 Future 异常完成，不能吞掉错误后把旧数据伪装成新快照。

`runTask` 只接受仍属于该工作台的会话，并拒绝冲突的忙碌操作。不要在其忙碌回调里递归触发 Save/Apply；工具栏已负责统一执行 `prepareAsync → prepare → 快照/应用`。扩展直接请求保存时，在客户端线程调用 `context.saveProject()` 并等待其 Future；正式文件即使已写入，后续 `saved` 应用失败仍会作为失败报告，不能假定所有错误都意味着文件未落盘。

### 实例覆盖与挂点

消费者渲染各自的角色或实体时，应使用稳定的实例拥有者，不能以共享 `AssimpScene` 代替拥有者：

```java
try (var scope = WorkbenchRuntime.instance(owner, scene, document, textureSource)) {
    var attachment = WorkbenchRuntime.socket(scene, poseSnapshot, "right_item");
    // Render this owner's model and consume the optional attachment here.
}
```

`owner` 是该实例的独立状态对象；`document` 是已经接受的实例覆盖。`textureSource` 是用于解析相对贴图的实际模型文件 `Path`，不是贴图目录；传 `null` 时沿用场景来源。这样可让共享网格的两个实例使用不同项目贴图，而不修改场景来源。所有依赖该覆盖的模型、材质、姿态和挂点查询都应位于作用域内；`document == null` 仍建立拥有者作用域，并抑制外部来源级覆盖。实例卸载时调用 `WorkbenchRuntime.release(owner, scene)` 释放其缓存。

`socket(scene, snapshot, name)` 返回叠加节点编辑后的模型空间挂点矩阵，包含 Socket 局部变换；没有该挂点或节点时返回 `null`。消费者自己决定挂什么、如何提交渲染，以及角色/模型根变换和世界变换，并且只应用一次。通用 Socket 不自动注册装备、不驱动原版持物行为，也不替消费者建立网络同步。

### ZIP 附加目录与内嵌贴图

扩展若导出整个作者目录，应同时提供以 `/` 结尾、值为 `new byte[0]` 的目录快照标记，例如 `extensions/example/behavior/`。它会排除项目磁盘里该目录的旧内容，再加入本次提供的文件，避免已删除脚本重新进入 ZIP。目录已清空时仍保留标记；空 Map 只是没有新增文件，不会清除旧文件。标记不写入 ZIP，也不进入清单。只声明自己拥有的目录；它不会删除本地项目文件。

工作台从场景建立材质配置时，将已识别的内嵌贴图名称正规化为 `*N`，其中 `N` 是该源模型的纹理索引。导出时也会将能够匹配内嵌纹理、且没有实际外部文件的名称改为 `*N`。图片继续保留在原 FBX 等源文件中；不要把 `*0` 当文件路径，也不要让消费者把该索引错误地用于另一份模型。
