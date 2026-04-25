# AssimpLoader LookDev - Quick Start Guide

## 项目概述

这是一个 Minecraft Forge 1.20.1 模组，用于加载和渲染 Assimp 3D 模型（FBX、OBJ 等格式）。

**核心功能：**
- 使用 Assimp 库加载 3D 模型
- 支持 PBR 材质系统（BaseColor、Roughness、Metallic、Normal 等）
- 与 Oculus/Iris 着色器系统集成
- LookDev 预览工具：实时调整材质参数并预览效果

## 当前状态

**工作分支：** `NodeWorkflow`

**最近修复的问题：**
- ✅ 显存溢出问题（嵌入纹理重复加载）
- ✅ 预览界面拖动流畅度优化（30 FPS 渲染节流）

**正在修复的问题：**
- 🔧 Base Color RGB 参数调整不生效（只有 Alpha 有效）
- 🔧 滚轮缩放功能不工作
- 🔧 材质通道信息（BaseColor、NormalMap 等）没有显示内容

**根本原因：**
- `PreviewFragment` 和 `ParameterPanelFragment` 使用了不同的 `MaterialOverrideManager` 实例
- 参数面板修改的是自己的 manager，但预览渲染使用的是另一个 manager
- 需要让两个 Fragment 共享同一个 `MaterialOverrideManager` 实例

**最新修复方案：**
1. 在 `PreviewFragment.setMaterial()` 中创建 `MaterialOverrideManager`
2. 通过 `PreviewFragment.getOverrideManager()` 暴露给外部
3. `LookDevScreen` 将 preview 的 manager 传递给 parameter panel
4. `PreviewFragment.updateOffscreenRenderer()` 使用 `overrideManager.getEffectiveMaterial()` 而不是原始材质

## 构建和运行

### 自动构建部署（推荐）

```powershell
# 在项目根目录执行
powershell.exe -ExecutionPolicy Bypass -File build-and-run.ps1
```

**脚本功能：**
1. 使用 Gradle 构建 mod
2. 自动部署到游戏目录：`G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\mods\`
3. 启动游戏客户端

### 手动构建

```bash
# 构建
./gradlew build --no-daemon

# 部署
cp build/libs/assimploader-*.jar "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\mods\"

# 启动游戏
cd "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft"
java -jar forge-*.jar
```

## 测试 LookDev 功能

1. 启动游戏后按 `L` 键打开 LookDev 界面
2. 左侧：模型树和材质列表
3. 中间：3D 预览窗口（支持鼠标拖动旋转）
4. 右侧：材质参数面板

**测试要点：**
- 拖动鼠标旋转模型是否流畅
- 调整 Base Color RGB 滑块，预览是否实时更新
- 调整 Roughness/Metallic 滑块，预览是否生效
- 滚轮缩放是否工作
- 分辨率切换按钮是否正常

## 日志位置

```
G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log
```

**关键日志标记：**
- `[LOOKDEV]` - LookDev 模块日志
- `[ParameterPanel]` - 参数面板日志
- `[PreviewFragment]` - 预览窗口日志
- `[OffscreenRenderer]` - 离屏渲染器日志

## 关键文件

### UI 层
- `LookDevScreen.java` - 主界面，协调各个 Fragment
- `PreviewFragment.java` - 3D 预览窗口，管理相机和渲染
- `ParameterPanelFragment.java` - 参数面板，材质参数调整
- `ModelTreeFragment.java` - 模型树和材质列表

### 渲染层
- `OffscreenRenderer.java` - 离屏渲染器，FBO 渲染到纹理
- `OrbitCamera.java` - 轨道相机，支持旋转和缩放

### 材质系统
- `MaterialOverrideManager.java` - 非破坏性材质参数覆盖管理器
- `MaterialOverride.java` - 材质覆盖容器
- `PBRMaterial.java` - PBR 材质数据结构

### Oculus 集成
- `OculusCompat.java` - Oculus 兼容层
- `OculusPBRSupport.java` - PBR 纹理注册（_n.png, _s.png, _e.png）

## 已知问题

1. **Base Color RGB 不生效**
   - 原因：PreviewFragment 和 ParameterPanelFragment 使用不同的 MaterialOverrideManager
   - 修复：已实现共享 manager 机制，待测试

2. **滚轮缩放不工作**
   - 原因：ACTION_SCROLL 事件可能没有触发
   - 待调查：添加日志确认事件是否触发

3. **材质通道信息不显示**
   - 原因：updateUIFromMaterial() 只更新了滑块值，没有显示纹理路径
   - 待实现：显示各个通道的纹理路径和参数值

## Git 工作流

```bash
# 查看当前状态
git status

# 提交修改
git add .
git commit -m "feat(lookdev): description"

# 查看最近提交
git log --oneline -10

# 回滚到某个 commit（如果需要）
git checkout <commit-hash>
git checkout NodeWorkflow  # 回到工作分支
```

## 下一步工作

1. 测试 Base Color RGB 修复是否生效
2. 调试滚轮缩放功能
3. 实现材质通道信息显示
4. 添加分辨率切换按钮（256×256 ↔ 512×512）
5. 优化渲染性能，监控显存使用

## 联系方式

如有问题，请查看：
- 项目文档：`LOOKDEV_PROGRESS.md`
- 调试日志：游戏日志文件
- Git 历史：`git log --oneline --all`
