# 工作区导航与模组扩展

AssimpLoader 提供公共工作区外壳。顶部第一行选择模组分区，第二行选择该模组的功能页。注册机制、导航、溢出分页、资产项目上下文和世界编辑相机都属于引擎；角色规则、装备配置和分享界面由 RigWorks 实现。

默认分区：

| 分区 | 功能页 |
| --- | --- |
| AssimpLoader | Models、Edit、Debug |
| RigWorks（安装后） | Characters、Rig Editor、Equipment |

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

消费者仍通过 `WorkbenchExtensions.Extension` 扩展资产检查、打开时处理和应用逻辑。若扩展已经注册功能页，可覆盖 `navigationPage()` 返回自己的页面 Key；Edit 的扩展按钮便通过公共导航打开该页。没有注册页面的旧扩展继续使用 `open(context)`。

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
