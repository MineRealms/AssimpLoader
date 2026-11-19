# 工作区导航与模组扩展

AssimpLoader 提供公共工作区外壳。顶部第一行选择模组分区，第二行选择该模组的功能页。注册机制、导航、溢出分页、资产项目上下文和世界编辑相机都属于引擎；角色规则、装备配置和分享界面由 RigWorks 实现。

默认分区：

| 分区 | 功能页 |
| --- | --- |
| AssimpLoader | Models、Edit、Debug |
| RigWorks（安装后） | Characters；Rig、Actions、Equipment、Behavior 注册到共享 Edit 的 Inspector |

Edit 内的 Tree 就是节点层级编辑。旧 `Page.NODES` 链接会进入这里，不再单独展示一个空 Nodes 页面。原来的 `L`、`/lookdev` 和调试命令仍可使用。

## 注册自己的分区

在模组的客户端 setup 中，通过 `enqueueWork` 在客户端线程注册。每个分区使用唯一模组 ID；页面 ID 在该分区内唯一。排序先比较 `order`，再比较 ID。重复注册会明确报错，避免一个扩展覆盖另一个扩展。

```java
var module = new WorkbenchPageRegistry.Module("examplemod", "Example Tools", 200);
var page = new WorkbenchPageRegistry.Key("examplemod", "vehicle");
LookDevNavigation.registerModule(module);
LookDevNavigation.registerPage(new WorkbenchPageRegistry.Page<>(
        page, "Vehicle", 0, VehicleEditorScreen::new));
```

注册时不会创建 Screen，也不查询世界状态。工厂在玩家打开页面时执行。工厂必须返回非 null 的新页面；默认在当前工作区内复用页面，切换分区不会丢失该页面的编辑会话。

需要活动实体或临时事务的页面使用完整构造器：

```java
LookDevNavigation.registerPage(new WorkbenchPageRegistry.Page<>(
        page, "Vehicle", 0, VehicleEditorScreen::new,
        VehicleEditorScreen::canOpen,
        () -> "Select a vehicle in a loaded world",
        false)); // 每次重新进入时新建，不能复用已关闭的事务
```

不可用页面会禁用，并显示给定 Tooltip。可用性在界面绘制时更新，必须保持轻量，不读文件、不导入模型、不创建 GPU 缓冲。工厂、按钮和谓词都在客户端线程调用。页面太多或 GUI 太窄时，每行可分别翻页，不把所有标题挤成不可点击的小块。

## 页面接入外壳

```java
@Override protected void init() {
    LookDevNavigation.install(this, this::addRenderableWidget, MY_PAGE);
    int contentTop = LookDevNavigation.HEADER_HEIGHT + 4;
    // 在 contentTop 之后布局本页控件。
}

@Override public void render(GuiGraphics graphics, int x, int y, float partial) {
    // 先画本页背景、视口，再画外壳和本页 widgets。
    LookDevNavigation.render(graphics, width);
    super.render(graphics, x, y, partial);
}

@Override public void onClose() { LookDevNavigation.close(null); }
```

外壳不包裹或重写消费者的 Screen，不介入其输入、存盘和相机事务。页面负责自己的 `removed()`：释放专属预览资源、取消未保存的临时事务、恢复冻结的姿态和相机。不要让世界拖拽逻辑吞掉 `y < HEADER_HEIGHT` 的导航点击。

独立预览页如果绘制不透明背景，在 1.21.1 应与现有页面一样禁用默认背景模糊，避免 GUI 和模型画面被再次模糊。世界叠加编辑器则保留原来的世界/Iris/Oculus 管线。

## 复用资产项目

`LookDevNavigation.modelContext()` 返回当前 Edit 页已打开的 `WorkbenchExtensions.Context`，没有模型或正在进行 I/O 时返回 null。上下文包含场景、编辑会话、项目持有者、保存入口和项目路径，不包含 RigWorks 类型。

消费者通过 `WorkbenchExtensions.Extension` 扩展导入初始化、Inspector 面板、打开时处理、保存和应用。`imported(context)` 用于显式文件导入（包括已有扩展元数据的 ZIP），默认委托 `initialize(context)`；本地项目重新打开则调用 `opened(context)`，扩展可据此区分新副本和原项目。基础预览、节点树、材质、Socket、Gizmo、动画时间轴、撤销与项目 I/O 由 AssimpLoader 维护，消费模组不需要再建立一套编辑视口。

扩展可以返回 `panels(context)`，每个面板提供唯一 `id()`、标题和 `build(Inspector)`。左侧以 `[模组名] 面板名` 显示；点击后在同一 Inspector 中构建控件。`Inspector` 提供 `label`、`text`、`number`、`toggle`、`action`、`select`，布局、裁剪和滚动由公共工作台负责。

```java
WorkbenchExtensions.register(new WorkbenchExtensions.Extension() {
    public String id() { return "examplemod"; }
    public String title() { return "Vehicle"; }
    public boolean supports(WorkbenchExtensions.Context context) {
        return context.session().document().extensions().has(id());
    }
    public Screen open(WorkbenchExtensions.Context context) { return context.parent(); }
    public List<WorkbenchExtensions.Panel> panels(WorkbenchExtensions.Context context) {
        return List.of(new WorkbenchExtensions.Panel() {
            public String id() { return "vehicle"; }
            public String title() { return "Vehicle"; }
            public void build(WorkbenchExtensions.Inspector ui) {
                var data = ui.context().session().document().extensions().getAsJsonObject("examplemod");
                float speed = data.has("speed") ? data.get("speed").getAsFloat() : 1;
                ui.number("Speed", speed, value -> ui.context().session().edit(document -> {
                    var next = document.extensions().getAsJsonObject("examplemod");
                    next.addProperty("speed", value);
                    return document.withExtension("examplemod", next);
                }));
            }
        });
    }
});
```

`WorkbenchDocument` 的扩展数据是防御性副本，修改 getter 返回的 JSON 不会直接改变项目；必须经 `withExtension` 和同一个 `WorkbenchSession.edit` 提交，才能获得统一撤销、脏状态、保存和恢复。需要因选择改变而重建 Inspector 时调用 `context.refresh()`；预览已有动画使用 `context.previewClip(name)`。

没有面板的旧扩展仍可用 `navigationPage()` 指向独立页面，或使用 `open(context)`。返回面板的扩展直接使用共享 Inspector，不再显示重复打开另一个编辑器的按钮。独立角色选择页、世界中的装备校准等不同任务仍可保留自己的 Screen。

### 显式导入类型

默认 **Import** 直接打开系统原生文件选择框，不激活任何消费模组，也不要求用户手动输入路径。扩展覆盖 `canImport()` 返回 true，并从自己的导入按钮调用 `ModelWorkbenchScreen.requestImport(id)`；玩家选中文件后，模型先由公共资产流程导入，再调用指定扩展的异步 `initialize(context)`。仅因为 FBX 有骨骼而自动启用玩家模型是不允许的。

初始化应在同一会话写入自己的扩展节。需要后台解析时自行安排工作线程，并把会话修改切回客户端线程；返回的 `CompletableFuture<Void>` 结束前工作台显示准备中。已有该扩展节的项目、ZIP 重新打开时不重复初始化，而是调用 `opened(context)`。RigWorks 的 Characters 导入入口使用 `ModelWorkbenchScreen.requestImport("rigworks")` 指定类型，同样直接打开原生文件选择框。Open 打开已有项目也复用引擎的原生对话框。

### 根变换与运行时隔离

默认根 TRS 就是 `document.transform()`。有自己根变换语义的扩展覆盖 `ownsRootTransform()`、`rootTransform(context)` 和对应 setter，让共享 Tree、Gizmo、取景和预览读取同一变换；不要在预览和世界应用时各乘一遍缩放。一个项目应只有一个扩展接管根变换，当前按注册顺序选择第一个。

普通 `WorkbenchRuntime.apply(scene, document)` 是按资产来源应用。多个实体共用同一 Assimp 场景、但需要不同节点/材质覆盖时，消费者在绘制期间使用 `try (var scope = WorkbenchRuntime.instance(owner, scene, document)) { ... }`，并在实例结束时 `release(owner, scene)`。空文档也建立隔离作用域，避免继承源级覆盖；嵌套作用域会恢复外层。节点和材质不应写回共享原始场景。

### 保存、应用与打包

| 回调/API | 职责 |
| --- | --- |
| `opened(context)` | 完成项目/恢复草稿选择后读取消费模组数据；修改仍属于未保存草稿 |
| `prepareAsync(context)` | Save、Apply、Export 前异步校验并捕获行为源等外部文件；完成后才进入同步准备 |
| `prepare(context)` | Save、Apply、Export 取快照前将派生配置写回同一会话 |
| `saved(context)` | 项目写入成功后处理实际保存快照；可以由模组保存并应用运行配置 |
| `apply(context)` | 应用当前准备好的草稿；不表示项目或服务器版本已保存 |
| `handlesApplication()` | 返回 true 时抑制普通资产的源级 Apply，由扩展负责运行时接入 |
| `packageFiles(context)` | 提供 `extensions/<模组>/...` 相对路径到 `byte[]` 的附加文件 |
| `exportOptions(context)` | 注册带稳定 `<extension-id>:<option-id>` 的可选 ZIP 数据组 |
| `exportDocument(context, selected)` | 根据当前勾选项返回过滤后的工作台文档，不应修改原编辑会话 |
| `packageFiles(context, selected)` | 按勾选项返回扩展附加文件；文件仍由公共 ZIP 校验和打包 |
| `context.choose(...)` | 在工作台内显示异步选项对话框，用于导入冲突等扩展流程 |
| `context.saveProject()` | 调用所属项目的统一异步 Save，成功返回确实写入的文档 |

生命周期为 `prepareAsync` 完成 → 客户端线程 `prepare` → 保存/应用/打包。文件读取和校验放在异步阶段，同步阶段只使用已完成的结果生成配置；不要在尚未结束的文件任务后立即宣布导出或应用成功。

保存期间出现的新编辑不会被错误标成已保存。`saved` 得到独立快照会话，不能拿它再次递归调用 `saveProject()`；如需应用角色，直接消费此快照。行为文件、项目配置和运行时应用分别由扩展负责，公共包导入本身不执行脚本。

普通与角色项目共享 ZIP 容器：版本化 `manifest.json`、项目 JSON、`assets/` 和可选的 `extensions/`。Import/Open 识别 `.zip`/`.rig.zip`，校验字节数和 SHA-256 后创建独立项目。外部覆盖贴图导出时复制为包内相对路径，未知扩展节和附加文件保留。数据格式、大小限制和旧包兼容见 [项目格式](../reference/WORKBENCH_PROJECT_FORMAT.md#portable-zip-packages)。ZIP 是本地全资产交换，不等同于多人配置同步或 Minecraft 资源包。

公共 Export 对话框始终包含模型、依赖和 PBR 贴图；核心配置（模型 TRS、节点、材质、Socket、动画预览）与扩展配置由 checkbox 分组选择。每个扩展的 `ExportOption` 默认可实现为一个整体文件选项，也可覆盖为更细的分组。导出时先对每个仍支持该项目的扩展依次运行 `exportDocument`，再调用其筛选版 `packageFiles`；临时文档按扩展顺序累积，不会写入当前编辑会话。若扩展拥有一整个脚本目录，筛选后的文件列表仍应配合目录快照标记，防止未选内容或过期脚本从项目磁盘重新混入 ZIP。

切换页面会提交当前变换事务并保留工作区中的草稿；关闭整个工作区时，未保存的 Edit 项目仍进入已有保存/丢弃提示。退出服务器会清除工作区引用，避免把上一个世界的编辑上下文带到下一个世界。

## 世界编辑相机

`WorldEditCamera` 是独立公共服务，可给车辆、挂点等模组复用：

```java
camera.open(partial -> visualFocusInWorld(partial));
camera.frame(viewportCenterX / width, viewportCenterY / height);
camera.fit(visualBoundingSphereRadius);
```

焦点使用双精度世界坐标；`frame` 使用整个游戏画面的归一化坐标。`fit` 在下一次世界相机更新取得当前 FOV 和宽高比后执行。模型的缩放、旋转和根位移应计入焦点和半径，不能直接用 Minecraft 实体原始身高代替。

默认 `Collision.FREE` 允许相机穿过已加载区域中的方块，不会发生原版第三人称相机被墙壁推近的问题。确实需要避障的消费者可以调用 `camera.collision(Collision.VISUAL_BLOCKS)`。两种模式都限制在已加载区块内。相机不改变玩家朝向、服务器位置或库存；页面移除时必须调用 `camera.close()`。

同一份共享源码经 Stonecutter 同时用于 Forge 1.20.1 和 NeoForge 1.21.1。修改公共导航或相机后，需要构建两个节点，并对实际鼠标输入与世界画面做客户端验收。
