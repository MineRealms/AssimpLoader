# AssimpLoader 快速开始

## 构建和安装

需要 Minecraft 1.20.1、Forge 47.x 和 JDK 17。在项目根目录执行：

```powershell
.\gradlew.bat build --console=plain
```

安装 `build/libs/assimploader-1.0.0-all.jar`。其中包含 Assimp 绑定、Windows/Linux/macOS 的 x64 和 ARM64 原生库；仅打包不代表所有平台均已实机验证。普通薄 JAR 不能代替 `-all.jar`。

另一个产物 `assimpshowcase-1.0.0.jar` 是模型展台、装甲车、鹿和火箭等示例内容。引擎本身不注册这些游戏内容；使用示例命令时需一并安装 Showcase。

## 游戏内预览和编辑

世界内按默认键 `L` 或执行 `/lookdev` 打开工作区。Models 浏览已加载场景，`Edit` 可以导入外部 FBX/GLB，调整 Transform、材质参数、挂点和片段预览，然后保存为可重新打开的项目。

完整操作见[模型工作台](MODEL_WORKBENCH.md)。它修改配置和项目，不写回原 FBX 的网格、UV、蒙皮权重或关键帧。旧 Nodes 页尚不是可用的材质节点图编辑器。

玩家换模由独立 [RigWorks](https://github.com/MineRealms/RigWorks) 提供。只安装引擎时不会自动获得玩家角色应用。

## 接入资产

导出前按 [PBR/FBX 规范](PBR_ASSET_SPEC.md) 保留骨架、UV、材质贴图、稳定节点名和正确单位。STATIC 会预变换顶点；DYNAMIC 保留节点/骨骼动画结构。

已有方块/物品渲染入口可以通过 geometry JSON 使用模型：

```json
{
  "loader": "assimploader:assimp",
  "model": "your_namespace:models/block/your_model.glb",
  "mode": "dynamic"
}
```

对应的 blockstate/item 仍需引用模型 JSON；仅放一个 FBX 不会自动注册 Minecraft 方块或实体。自定义动态实体还需要渲染器和动画实例。大型模型的范围/强制渲染可按[配置](LARGE_MODELS.md)调整。

## KubeJS 和动画

[详细 KubeJS 动画教程](KUBEJS_ANIMATION_GUIDE.md)提供实例取得、骨骼、烘焙片段、混合、程序化姿态、IK 和重载的具体 API，以及[可运行 Demo](../../examples/kubejs/README.md)。

```powershell
.\gradlew.bat runClient -PwithKubeJS --console=plain
```

KubeJS 可选，动画时钟和 GPU 计算始终由引擎负责。脚本给出状态、权重和目标，不逐顶点求解。

## 资源重载与光影

资源包模型、贴图和配置用 `F3+T` 重载。外置目录通过配置指定，客户端 `/assimploader reload` 后台解析并替换；删除覆盖会恢复资源包版本。见[加载生命周期](../reference/LOADING_FIXES.md)。

Oculus 开发依赖默认开启，`-PwithoutOculus` 可关闭。实际 PBR 显示还需要支持并启用对应材质模式的 shader pack，见 [Oculus PBR](OCULUS_PBR_USAGE.md)。Java/Mixin 变化需要重启，不能用 F3+T 热更。

## 开发与验证

```powershell
.\gradlew.bat test integrationTestClasses --console=plain
git diff --check
```

单元测试报告在 `build/reports/tests/test/index.html`；编译通过不等于实际 GPU 图像验收。更多启动参数见[开发客户端](DEVELOPMENT_CLIENT.md)，过程记录在 [history](../history/README.md)。
