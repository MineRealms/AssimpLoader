# 自动部署和启动游戏配置

## 功能说明

在 `build.gradle` 中添加了自动部署和启动游戏的任务，支持以下功能：

1. **自动复制 JAR** - 构建完成后自动复制到游戏 mods 目录
2. **延迟启动** - 等待 1 秒后启动游戏测试脚本
3. **灵活配置** - 可选择是否自动启动游戏

## 使用方法

### 方式 1: 仅构建和复制（推荐）

```bash
./gradlew build
```

**执行流程:**
1. 编译代码
2. 构建 JAR
3. 自动复制到游戏 mods 目录
4. **不会**自动启动游戏

### 方式 2: 构建、复制并启动游戏

```bash
./gradlew buildAndDeploy
```

**执行流程:**
1. 编译代码
2. 构建 JAR
3. 自动复制到游戏 mods 目录
4. 等待 1 秒
5. 执行 `testgame.ps1` 启动游戏

### 方式 3: 仅复制 JAR（不重新构建）

```bash
./gradlew copyToGameMods
```

**执行流程:**
- 复制已构建的 JAR 到游戏 mods 目录

### 方式 4: 仅启动游戏

```bash
./gradlew launchTestGame
```

**执行流程:**
- 等待 1 秒
- 执行 `testgame.ps1` 启动游戏

## 配置说明

### 目标目录

JAR 文件会被复制到:
```
G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods
```

如需修改，编辑 `build.gradle` 中的 `copyToGameMods` 任务:

```gradle
task copyToGameMods(type: Copy) {
    from(jar.archiveFile)
    into 'G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods'
    dependsOn reobfJar
}
```

### 启动脚本

游戏启动脚本位置:
```
H:\MinecraftMods\ImprovedMobs\testgame.ps1
```

如需修改脚本路径，编辑 `build.gradle` 中的 `launchTestGame` 任务:

```gradle
task launchTestGame(type: Exec) {
    commandLine 'powershell.exe', '-ExecutionPolicy', 'Bypass', '-File', "${projectDir}\\testgame.ps1"
    workingDir projectDir
    dependsOn copyToGameMods
}
```

### 自动启动游戏

**默认行为:** `./gradlew build` 只复制 JAR，**不会**自动启动游戏

**如需 build 后自动启动游戏:**

编辑 `build.gradle`，取消注释最后一行:

```gradle
// 让 build 任务完成后自动执行部署
build.finalizedBy(copyToGameMods)

// 如果需要 build 后自动启动游戏，取消下面这行的注释
copyToGameMods.finalizedBy(launchTestGame)  // ← 取消这行的注释
```

## 任务依赖关系

```
build
  └─> reobfJar
       └─> copyToGameMods
            └─> launchTestGame (可选)
```

## 示例输出

### 执行 `./gradlew build`

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :reobfJar
> Task :copyToGameMods
✓ JAR 已复制到游戏 mods 目录: G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods
> Task :build

BUILD SUCCESSFUL in 1m 25s
```

### 执行 `./gradlew buildAndDeploy`

```
> Task :compileJava
> Task :processResources
> Task :classes
> Task :jar
> Task :reobfJar
> Task :copyToGameMods
✓ JAR 已复制到游戏 mods 目录: G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods
> Task :launchTestGame
⏳ 等待 1 秒后启动游戏...
🚀 正在启动游戏测试脚本...
✓ 游戏启动脚本已执行

BUILD SUCCESSFUL in 1m 27s
```

## 故障排除

### 问题 1: 复制失败 - 目录不存在

**错误信息:**
```
Could not copy file ... to ... because the destination directory does not exist
```

**解决方法:**
确保目标目录存在:
```powershell
New-Item -ItemType Directory -Force -Path "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
```

### 问题 2: PowerShell 脚本执行被阻止

**错误信息:**
```
... cannot be loaded because running scripts is disabled on this system
```

**解决方法:**
任务已配置 `-ExecutionPolicy Bypass`，应该不会出现此问题。如果仍有问题，手动设置:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 问题 3: testgame.ps1 不存在

**错误信息:**
```
Cannot find path '...\testgame.ps1' because it does not exist
```

**解决方法:**
在项目根目录创建 `testgame.ps1` 脚本，示例内容:

```powershell
# testgame.ps1 - 启动 Minecraft 游戏

Write-Host "正在启动 Minecraft..." -ForegroundColor Green

# 方式 1: 使用启动器启动
Start-Process "G:\MinecraftGames\CTNH-BaopuEdition\PCL\Plain Craft Launcher 2.exe"

# 方式 2: 直接启动游戏（如果有直接启动脚本）
# Start-Process "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\start.bat"

Write-Host "游戏启动命令已执行" -ForegroundColor Green
```

### 问题 4: 复制时文件被占用

**错误信息:**
```
The process cannot access the file because it is being used by another process
```

**解决方法:**
1. 关闭 Minecraft 游戏
2. 确保没有其他程序占用 JAR 文件
3. 重新执行构建

## 高级配置

### 复制前删除旧版本

如果需要在复制前删除旧的 JAR 文件:

```gradle
task copyToGameMods(type: Copy) {
    from(jar.archiveFile)
    into 'G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods'
    dependsOn reobfJar

    doFirst {
        // 删除旧版本
        delete fileTree('G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods') {
            include 'integratedindustrialcraft-*.jar'
        }
        println "✓ 已删除旧版本 JAR"
    }

    doLast {
        println "✓ JAR 已复制到游戏 mods 目录: ${destinationDir}"
    }
}
```

### 添加备份功能

在复制前备份旧版本:

```gradle
task backupOldJar(type: Copy) {
    from('G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods') {
        include 'integratedindustrialcraft-*.jar'
    }
    into 'G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods\\backup'
    
    doLast {
        println "✓ 旧版本已备份"
    }
}

copyToGameMods.dependsOn backupOldJar
```

### 多环境部署

如果有多个测试环境:

```gradle
task copyToGameModsDev(type: Copy) {
    from(jar.archiveFile)
    into 'G:\\MinecraftGames\\Dev\\.minecraft\\mods'
    dependsOn reobfJar
}

task copyToGameModsProd(type: Copy) {
    from(jar.archiveFile)
    into 'G:\\MinecraftGames\\CTNH-BaopuEdition\\.minecraft\\versions\\Create New Horizon\\mods'
    dependsOn reobfJar
}
```

## 快速参考

| 命令 | 功能 | 自动复制 | 自动启动 |
|------|------|----------|----------|
| `./gradlew build` | 构建 JAR | ✅ | ❌ |
| `./gradlew buildAndDeploy` | 构建并部署 | ✅ | ✅ |
| `./gradlew copyToGameMods` | 仅复制 | ✅ | ❌ |
| `./gradlew launchTestGame` | 仅启动游戏 | ❌ | ✅ |

## 推荐工作流

### 开发调试流程

```bash
# 1. 修改代码
# 2. 构建并复制
./gradlew build

# 3. 手动启动游戏测试
# 4. 发现问题，修改代码
# 5. 重复步骤 2-4
```

### 快速测试流程

```bash
# 一键构建、部署、启动
./gradlew buildAndDeploy
```

### 仅更新 JAR（不重新构建）

```bash
# 如果 JAR 已经构建好，只需要重新复制
./gradlew copyToGameMods
```
