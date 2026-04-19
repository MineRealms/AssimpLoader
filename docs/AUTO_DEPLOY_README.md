# 自动部署配置 - 快速参考

## ✅ 配置完成

已在 `build.gradle` 中添加自动部署功能，支持构建后自动复制 JAR 到游戏目录并可选启动游戏。

## 使用方法

### 🔨 方式 1: 构建并自动复制（推荐）

```bash
./gradlew build
```

**执行内容:**
- ✅ 编译代码
- ✅ 构建 JAR
- ✅ 自动复制到游戏 mods 目录
- ❌ 不会自动启动游戏

**输出示例:**
```
> Task :build
> Task :copyToGameMods
✓ JAR 已复制到游戏 mods 目录: G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods

BUILD SUCCESSFUL in 1m 29s
```

### 🚀 方式 2: 构建、复制并启动游戏

```bash
./gradlew buildAndDeploy
```

**执行内容:**
- ✅ 编译代码
- ✅ 构建 JAR
- ✅ 自动复制到游戏 mods 目录
- ⏳ 等待 1 秒
- ✅ 执行 `testgame.ps1` 启动游戏

**输出示例:**
```
> Task :build
> Task :copyToGameMods
✓ JAR 已复制到游戏 mods 目录: ...
> Task :launchTestGame
⏳ 等待 1 秒后启动游戏...
🚀 正在启动游戏测试脚本...
✓ 游戏启动脚本已执行

BUILD SUCCESSFUL in 1m 32s
```

### 📦 方式 3: 仅复制（不重新构建）

```bash
./gradlew copyToGameMods
```

适用于 JAR 已经构建好，只需要重新复制的情况。

### 🎮 方式 4: 仅启动游戏

```bash
./gradlew launchTestGame
```

适用于只想启动游戏测试的情况。

## 配置说明

### 目标目录

```
G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods
```

### 启动脚本

```
H:\MinecraftMods\ImprovedMobs\testgame.ps1
```

### 修改目标目录

编辑 `build.gradle` 第 175 行左右:

```gradle
task copyToGameMods(type: Copy) {
    from(jar.archiveFile)
    into 'G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods'
    // ↑ 修改这里的路径
}
```

### 启用自动启动游戏

编辑 `build.gradle` 最后一行，取消注释:

```gradle
build.finalizedBy('copyToGameMods')
// copyToGameMods.finalizedBy('launchTestGame')  // ← 删除这行开头的 //
```

修改后，每次 `./gradlew build` 都会自动启动游戏。

## 快速对比

| 命令 | 编译 | 构建 | 复制 | 启动游戏 | 用时 |
|------|------|------|------|----------|------|
| `./gradlew build` | ✅ | ✅ | ✅ | ❌ | ~1.5分钟 |
| `./gradlew buildAndDeploy` | ✅ | ✅ | ✅ | ✅ | ~1.5分钟 |
| `./gradlew copyToGameMods` | ❌ | ❌ | ✅ | ❌ | ~5秒 |
| `./gradlew launchTestGame` | ❌ | ❌ | ❌ | ✅ | ~2秒 |

## 推荐工作流

### 日常开发

```bash
# 1. 修改代码
# 2. 构建并复制
./gradlew build

# 3. 手动启动游戏测试
# 4. 测试功能
# 5. 重复步骤 1-4
```

### 快速测试

```bash
# 一键构建、部署、启动
./gradlew buildAndDeploy
```

### 仅更新 JAR

```bash
# 如果只修改了资源文件或配置，不需要重新编译
./gradlew copyToGameMods
```

## 故障排除

### 问题: 目录不存在

**错误:** `Could not copy file ... because the destination directory does not exist`

**解决:**
```powershell
New-Item -ItemType Directory -Force -Path "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
```

### 问题: 文件被占用

**错误:** `The process cannot access the file because it is being used by another process`

**解决:** 关闭 Minecraft 游戏后重新构建

### 问题: PowerShell 脚本不存在

**错误:** `Cannot find path '...\testgame.ps1'`

**解决:** 在项目根目录创建 `testgame.ps1` 脚本

## 验证配置

### 查看可用任务

```bash
./gradlew tasks --group=deployment
```

**输出:**
```
Deployment tasks
----------------
buildAndDeploy - Build, copy to mods, and launch game
copyToGameMods - Copy built JAR to game mods directory
launchTestGame - Launch game using testgame.ps1 script
```

### 测试复制功能

```bash
# 1. 构建
./gradlew build

# 2. 检查文件是否存在
ls "G:/MinecraftGames/CTNH-BaopuEdition/.minecraft/versions/Create New Horizon/mods/integratedindustrialcraft-1.0.0.jar"
```

## 详细文档

完整配置说明请查看: `docs/AUTO_DEPLOY_GUIDE.md`
