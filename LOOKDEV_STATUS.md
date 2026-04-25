# LookDev Viewer 项目状态文档

## 📋 项目概述

**目标**: 为 AssimpLoader 创建游戏内 LookDev 查看器（类似 Substance Previewer），使用 ModernUI-MC 框架实现实时材质参数调整和预览。

**当前状态**: Phase 1-7 开发完成，正在进行自动化测试和 Bug 修复阶段。

---

## 🏗️ 架构设计

### ModernUI-MC 集成

**依赖配置** (`build.gradle`):
```gradle
dependencies {
    library "icyllis.modernui:ModernUI-Core:3.12.0"
    library "icyllis.modernui:ModernUI-Forge:3.12.0"
}
```

**关键 API**:
- `Fragment`: UI 组件基类，类似 Android Fragment
- `FragmentManager`: 管理子 Fragment 生命周期
- `MuiModApi.openScreen(fragment)`: 打开 UI 界面
- 控件: `SwitchButton`, `SeekBar`, `TextView`, `LinearLayout`, `ScrollView`

**重要**: 
- ModernUI 使用 `SwitchButton` 而不是 `Switch`
- 子 Fragment 必须通过 `FragmentManager.beginTransaction()` 管理，不能直接调用 `onCreateView()`
- ModernUI-Forge jar 已包含 ModernUI-Core（通过 shadow 打包）

### 项目结构

```
src/main/java/cn/minerealms/assimploader/
├── lookdev/                          # LookDev 查看器核心
│   ├── ui/
│   │   ├── LookDevScreen.java        # 主界面（3面板布局）
│   │   ├── ModelTreeFragment.java    # 左侧：材质列表
│   │   ├── PreviewFragment.java      # 中间：3D 预览
│   │   └── ParameterPanelFragment.java # 右侧：参数面板
│   ├── material/
│   │   ├── MaterialOverride.java     # 材质覆盖数据类
│   │   └── MaterialOverrideManager.java # 材质管理器
│   ├── comparison/
│   │   ├── ComparisonSnapshot.java   # 对比快照
│   │   └── ComparisonManager.java    # 对比管理器
│   ├── preset/
│   │   ├── MaterialPreset.java       # 材质预设
│   │   └── PresetManager.java        # 预设管理器
│   ├── camera/
│   │   └── OrbitCamera.java          # 轨道相机控制器
│   ├── IModelProvider.java           # 模型提供者接口
│   ├── IMaterialProvider.java        # 材质提供者接口
│   ├── ViewMode.java                 # 视图模式枚举
│   └── LookDevCommand.java           # /lookdev 命令
└── debug/
    ├── DebugManager.java             # 统一日志系统
    └── AutoTestManager.java          # 自动测试管理器 ⭐
```

---

## 🎯 已完成功能 (Phase 1-7)

### Phase 1: 框架搭建 ✅
- ModernUI-MC 依赖集成
- 三面板布局（左 20% | 中 60% | 右 20%）
- `/lookdev` 命令打开查看器

### Phase 2: 材质覆盖系统 ✅
- 非破坏性材质覆盖（深拷贝）
- 7 个通道开关（BaseColor/Normal/Metallic/Roughness/AO/Emissive/Alpha）
- 参数覆盖（roughness/metallic/emission/alphaCutoff/normalStrength/aoFactor）

### Phase 3: 参数面板 UI ✅
- 7 个通道开关（SwitchButton）
- 6 个参数滑块（SeekBar）
- 2 个颜色选择器（RGB 滑块）
- Reset 按钮

### Phase 4: 3D 预览渲染 ✅
- OrbitCamera 相机控制器（旋转/缩放/平移）
- 模型信息显示
- 参数变化实时通知

### Phase 5: 模型树视图 ✅
- 材质列表显示
- 材质选择功能
- 从 AssimpScene 提取材质

### Phase 6: 预设系统 ✅
- MaterialPreset 数据类
- PresetManager（JSON 保存/加载）
- 预设 UI（保存/加载/删除按钮）

### Phase 7: 高级功能 ✅
- 性能优化（渲染节流 30fps + 材质缓存）
- 快捷键（R/Space/1-7/ESC）
- Before/After 对比功能

---

## 🤖 自动化测试系统

### 核心组件

#### 1. AutoTestClient（事件驱动自动测试）
**位置**: `src/main/java/cn/minerealms/assimploader/client/AutoTestClient.java`

**功能**: 
- 标题画面自动加载世界
- 进入世界后自动执行 `/lookdev` 命令
- 状态机管理测试流程
- 看门狗超时保护

**JVM 参数**:
```
-Dautotest.enabled=true          # 启用自动测试
-Dautotest.world="世界名称"       # 指定要加载的世界
```

**状态流程**: WAIT_TITLE → WAIT_OPEN_WORLD → WAIT_IN_WORLD → WAIT_CMD → DONE

#### 2. auto-test-loop.ps1（集成测试循环）
**位置**: `H:\MinecraftMods\ImprovedMobs\auto-test-loop.ps1`

**功能**:
1. 构建 mod（内联 Gradle 构建）
2. 部署 JAR 到游戏 mods 目录
3. 启动游戏（调用 testgame.ps1）
4. 监控崩溃报告
5. 写入状态到 `test-status.txt`
6. 失败时自动重试（最多 10 次）

**使用**:
```powershell
cd H:\MinecraftMods\ImprovedMobs
.\auto-test-loop.ps1
```

**注意**: 已整合所有逻辑，不再调用 build-and-run.ps1 避免重复构建

#### 3. monitor-test.ps1（状态监控）
**位置**: `H:\MinecraftMods\ImprovedMobs\monitor-test.ps1`

**功能**: 快速查看当前测试状态和最近错误

**使用**:
```powershell
cd H:\MinecraftMods\ImprovedMobs
.\monitor-test.ps1
```

### 状态文件

#### test-status.txt（测试状态标记）
**位置**: `H:\MinecraftMods\ImprovedMobs\test-status.txt`

**格式**:
```
STATUS: BUILDING | BUILD_FAILED | GAME_LAUNCHING | CRASHED | SUCCESS
ITERATION: 1 / 10
TIMESTAMP: 2024-01-01 12:00:00
PHASE: 当前阶段描述
ACTION: 需要采取的行动
```

**状态说明**:
- `BUILDING`: 正在编译
- `BUILD_FAILED`: 编译失败
- `GAME_LAUNCHING`: 游戏启动中
- `CRASHED`: 游戏崩溃
- `SUCCESS`: 测试通过

#### last-crash.txt（崩溃详情）
**位置**: `H:\MinecraftMods\ImprovedMobs\last-crash.txt`

**内容**: 完整的崩溃报告，包括堆栈跟踪

---

## 🔍 Claude 监控流程

### 1. 定期检查状态（每 30-60 秒）

```bash
# 快速检查状态
cat H:\MinecraftMods\ImprovedMobs\test-status.txt

# 或使用监控脚本
cd H:/MinecraftMods/ImprovedMobs && powershell.exe -ExecutionPolicy Bypass -File ./monitor-test.ps1
```

### 2. 根据状态采取行动

#### 状态: CRASHED
```bash
# 1. 读取崩溃报告
cat H:\MinecraftMods\ImprovedMobs\last-crash.txt

# 2. 分析错误原因
# 3. 修复代码
# 4. 重新启动测试
cd H:/MinecraftMods/ImprovedMobs && powershell.exe -ExecutionPolicy Bypass -File ./auto-test-loop.ps1
```

#### 状态: BUILD_FAILED
```bash
# 1. 查看编译错误
cd H:/MinecraftMods/ImprovedMobs && ./gradlew compileJava --console=plain

# 2. 修复编译错误
# 3. 重新启动测试
```

#### 状态: SUCCESS
```bash
# 1. 检查游戏日志是否有错误
cat "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log" | grep -i error

# 2. 如果无错误，测试完成
```

### 3. 读取游戏日志

```bash
# 查看最新日志（最后 100 行）
tail -100 "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log"

# 搜索错误
grep -i "error\|fatal\|exception" "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log" | tail -50

# 搜索 AssimpLoader 相关日志
grep "\[ASSIMPLOADER\]" "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log" | tail -50
```

---

## 🐛 当前已知问题

### 1. Fragment 生命周期错误（已修复）
**问题**: `Fragment not attached to a context`

**原因**: 直接调用子 Fragment 的 `onCreateView()`

**修复**: 使用 `FragmentManager.beginTransaction()` 管理子 Fragment

### 2. 日志格式化占位符错误（已修复 2026-04-25）
**问题**: 日志输出显示 `%s` 和 `%d` 未替换

**原因**: DebugManager 使用 SLF4J，应使用 `{}` 而非 `%s`/`%d`

**修复文件**:
- `PresetManager.java:42` - "Presets directory: {}"
- `LookDevScreen.java:371` - "Material selected: {} (index: {})"

### 3. LookDev 界面只显示灰色背景（调查中）
**问题**: 进入 LookDev 后只有灰色背景，没有控件显示

**调查步骤**:
- 已添加 Fragment 生命周期调试日志
- 检查 ModelTreeFragment/PreviewFragment/ParameterPanelFragment 的 onCreateView 是否被调用
- 验证 Fragment 容器是否正确接收子视图

**待验证**: 运行测试查看日志输出

**提交**: `c157f66`

### 2. Switch 类名错误（已修复）
**问题**: `NoClassDefFoundError: icyllis/modernui/widget/Switch`

**原因**: ModernUI 使用 `SwitchButton` 而不是 `Switch`

**修复**: 批量替换所有 `Switch` 为 `SwitchButton`

**提交**: `d638d78`

---

## 📝 开发规范

### 日志系统

**使用 DebugManager 而不是直接使用 Logger**:

```java
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

// 8 个调试类别
DebugCategory.CORE        // 核心功能
DebugCategory.RENDER      // 渲染相关
DebugCategory.MATERIAL    // 材质相关
DebugCategory.ANIMATION   // 动画相关
DebugCategory.LOOKDEV     // LookDev 查看器
DebugCategory.MODERNUI    // ModernUI 集成
DebugCategory.CACHE       // 缓存系统
DebugCategory.LIGHT       // 光照系统

// 4 个日志级别
DebugManager.debug(DebugCategory.LOOKDEV, "Debug message: %s", value);
DebugManager.info(DebugCategory.LOOKDEV, "Info message: %s", value);
DebugManager.warn(DebugCategory.LOOKDEV, "Warning message: %s", value);
DebugManager.error(DebugCategory.LOOKDEV, "Error message", exception);
```

**统一前缀**: 所有日志带 `[ASSIMPLOADER]` 前缀

### Git 提交规范

```bash
git commit -m "feat(lookdev): 功能描述

详细说明

Co-Authored-By: Claude Sonnet 4.6 (1M context) <noreply@anthropic.com>"
```

---

## 🚀 下一步计划

### 当前任务: 自动化测试和 Bug 修复

1. ✅ 修复 Fragment 生命周期问题
2. ✅ 修复 Switch 类名错误
3. ✅ 创建自动化测试系统
4. 🔄 **运行自动化测试循环**
5. ⏳ 修复运行时崩溃（如果有）
6. ⏳ 游戏内功能测试
7. ⏳ 性能优化和稳定性改进

### 测试清单

- [ ] `/lookdev` 命令能正常打开界面
- [ ] 三面板布局正确显示
- [ ] 材质列表能正常显示
- [ ] 参数滑块能正常调整
- [ ] 通道开关能正常切换
- [ ] 颜色选择器能正常工作
- [ ] 预设保存/加载功能正常
- [ ] Before/After 对比功能正常
- [ ] 快捷键响应正常
- [ ] 无内存泄漏
- [ ] 无崩溃

---

## 📚 参考资源

### ModernUI-MC 文档
- GitHub: https://github.com/BloCamLimb/ModernUI-MC
- Wiki: https://github.com/BloCamLimb/ModernUI-MC/wiki
- 示例代码: `H:\MinecraftMods\ModernUI-MC\common\src\main\java\icyllis\modernui\mc\ui\`

### 关键示例文件
- `CenterFragment2.java`: 子 Fragment 管理示例
- `ThemeFragment.java`: UI 控件使用示例

### 项目文档
- 设计文档: `LOOKDEV_VIEWER_DESIGN.md`
- 记忆文件: `memory/project_lookdev_phase*.md`

---

## 🔧 常用命令

### 编译和测试
```bash
# 编译
cd H:/MinecraftMods/ImprovedMobs && ./gradlew compileJava

# 完整构建
cd H:/MinecraftMods/ImprovedMobs && ./gradlew build

# 构建并运行游戏
cd H:/MinecraftMods/ImprovedMobs && powershell.exe -ExecutionPolicy Bypass -File ./build-and-run.ps1

# 自动化测试循环
cd H:/MinecraftMods/ImprovedMobs && powershell.exe -ExecutionPolicy Bypass -File ./auto-test-loop.ps1

# 监控测试状态
cd H:/MinecraftMods/ImprovedMobs && powershell.exe -ExecutionPolicy Bypass -File ./monitor-test.ps1
```

### Git 操作
```bash
cd H:/MinecraftMods/ImprovedMobs

# 查看状态
git status

# 提交更改
git add -A
git commit -m "feat: 描述"

# 查看日志
git log --oneline -10
```

---

## 💡 重要提示

1. **ModernUI 控件**: 使用 `SwitchButton` 而不是 `Switch`
2. **Fragment 管理**: 必须使用 `FragmentManager`，不能直接调用 `onCreateView()`
3. **日志系统**: 使用 `DebugManager` 而不是 `LOGGER`
4. **自动测试**: 修改 `AutoTestManager.AUTO_TEST_ENABLED = true` 启用
5. **状态监控**: 定期检查 `test-status.txt` 了解测试进度
6. **崩溃分析**: 崩溃时读取 `last-crash.txt` 获取详细信息

---

**最后更新**: 2024-01-01
**当前分支**: NodeWorkflow
**最新提交**: 13d6d3a (feat: Add monitoring system for auto-test loop)
