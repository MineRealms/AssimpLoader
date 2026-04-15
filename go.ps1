<#
  全自动构建 & 部署模组脚本
  1. 执行gradlew build编译
  2. 复制编译好的jar到游戏mods文件夹（强制覆盖）
  3. 启动测试脚本
  4. 自动退出
#>

# 强制停止脚本报错（可选，更稳定）
$ErrorActionPreference = "Stop"

# ===================== 固定路径（已按你的需求填写） =====================
$modSourcePath = "H:\MinecraftMods\ImprovedMobs\build\libs\*"
$gameModsPath = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
$testScriptPath = "H:\MinecraftMods\ImprovedMobs\testgame.ps1"
# ========================================================================

try {
    Write-Host "`n=== 开始构建模组 ===" -ForegroundColor Cyan
    # 执行构建命令（自动等待完成）
    & .\gradlew build

    # 间隔 0.5 秒
    Start-Sleep -Milliseconds 500

    Write-Host "`n=== 复制文件到游戏Mods文件夹（强制覆盖） ===" -ForegroundColor Cyan
    # 复制所有文件，强制覆盖已存在的文件
    Copy-Item -Path $modSourcePath -Destination $gameModsPath -Recurse -Force

    # 间隔 0.5 秒
    Start-Sleep -Milliseconds 500

    Write-Host "`n=== 启动测试脚本 ===" -ForegroundColor Cyan
    # 执行测试脚本
    & $testScriptPath

    Write-Host "`n=== 所有操作执行完成，自动退出 ===" -ForegroundColor Green
}
catch {
    Write-Host "`n❌ 执行失败：$_" -ForegroundColor Red
    pause
}

# 自动退出脚本
exit