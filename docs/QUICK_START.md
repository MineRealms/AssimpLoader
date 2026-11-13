# AssimpLoader 快速开始

更新：2026-11-13。功能状态和测试边界见 [修复记录](FIXES_2026-11-13.md)。

## 构建

需要 JDK 17。PowerShell 在项目根目录执行：

```powershell
.\gradlew.bat test build --console=plain --no-daemon --no-parallel
```

Linux/macOS 使用 `./gradlew`。固定版本 Photon/LDLib 开发依赖及 ModernUI 从 Maven 获取。首次构建需要网络，无须预先创建 `mods_dev/`。

安装文件是 `build/libs/assimploader-1.0.0-all.jar`，内含 LWJGL Assimp 绑定和 Windows/Linux/macOS 的 x64、ARM64 natives。普通 jar 不能代替完整发布包；其他平台尚未实机验证。

## 安装与启动

1. 安装 Minecraft 1.20.1、Forge 47.1.3，以及 ModernUI Forge 1.20.1-3.11.0.1。
2. 将 `-all.jar` 放入该实例的 `mods/` 目录。
3. Photon/LDLib、Oculus 为可选兼容项；先验证基础组合，再逐项加入。
4. 开发环境可用以下任务启动客户端：

```powershell
.\gradlew.bat runClient --console=plain --no-daemon --no-parallel
```

日志在实例的 `logs/latest.log`，开发环境是 `run/logs/latest.log`。不要沿用历史文档里的其他机器游戏目录。

## 示例模型

Blender CIWS、刀、坐标测试件、PBR 探针和传送门已接入八个展示方块；在空地执行 `/assimpdemo` 可放置整套展台，详见 [FBX 世界展示](MODEL_SHOWCASE.md)。旧 CNC/CCB 示例及其资源已移除，旧存档中的对应注册项不再存在。

进入可使用命令的测试世界后：

```text
/give @s assimploader:showcase_portal_pbr
/give @s assimploader:showcase_ciws_textured
```

检查物品栏、手持、掉落物和放置后的模型。替换模型或测试动画时，同时检查缺面、纹理、坐标方向与枢轴。CIWS 使用 DYNAMIC 保留关节，其余展示 JSON 使用 STATIC；展示本身没有转动控制或烘焙动画片段。

## 接入自己的模型

先按 [PBR 资产与 FBX 接入规范](PBR_ASSET_SPEC.md) 保留建模数据并导出。Blender FBX/GLB 可自动转换受支持的 PBR 材质；普通法线不要误用预打包 LabPBR 的 `_n` 名称。启用 Oculus 光影还需选择光影的 LabPBR 材质模式，见 [PBR 使用说明](OCULUS_PBR_USAGE.md)。

资源包中的自包含 FBX/GLB 更适合作为首个测试样本。自定义 geometry JSON 示例：

```json
{
  "loader": "assimploader:assimp",
  "model": "your_namespace:models/block/your_model.glb",
  "mode": "dynamic"
}
```

STATIC 使用 `"mode": "static"`，会预变换顶点。DYNAMIC 保留场景树用于节点/骨骼动画。模型 JSON 仍需被对应 blockstate/item 引用，动态绘制还需绑定渲染器和动画状态；仅放置文件不会自动注册方块。

外置模型目录由 `rendering.externalModelDirectory` 配置，空值表示禁用。它优先于资源包，路径使用 `<root>/<namespace>/<resourcePath>`，如 `models/showcase/ciws_textured.fbx`。仅 `assimploader` namespace 兼容旧式 `<root>/<resourcePath>`。简单缓存名有歧义时不会任意选择场景。

资源包中的模型、贴图和 JSON display transforms 可用 `F3+T` 重新加载。外部覆盖目录使用 `hotreload.resourcePath`，结构同上；客户端命令 `/assimploader reload` 后台解析并原子替换，删除覆盖文件会恢复资源包版本。自动监听需启用 `hotreload.autoReloadEnabled`，详见 [加载与重载](LOADING_FIXES.md)。Java 代码修改需要重启客户端。

## LookDev

在世界中按默认键 `L` 或使用客户端命令 `/lookdev` 打开，可在按键设置中修改。列表包含已加载场景，支持筛选和切换；左键旋转、右键/中键拖动平移、滚轮缩放，`F` 适配、`R` 复位。详见 [LookDev 预览](LOOKDEV_PREVIEW.md)。

节点页按本轮要求留空。旧节点编辑、任意文件导入、材质编译与 shader 注入不在当前预览入口启用。Gaussian 仍是独立原型，没有自动世界入口。

## 验证

```powershell
.\gradlew.bat test --console=plain --no-daemon --no-parallel
git diff --check
```

报告位于 `build/reports/tests/test/index.html`。测试覆盖数据转换、动画、IK、顶点/矩阵打包和部分 GLSL 生成；不会验证实际 GPU 绘制或所有客户端模组组合。

更多资料见 [文档索引](README.md)。
