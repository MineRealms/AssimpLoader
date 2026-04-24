# Auto-build and deploy script for AssimpLoader
# Usage: .\build-and-run.ps1

$ErrorActionPreference = "Stop"

$PROJECT_DIR = "H:\MinecraftMods\ImprovedMobs"
$MOD_JAR = "assimploader-1.0.0-all.jar"
$TARGET_DIR = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
$TARGET_JAR = "integratedindustrialcraft.jar"
$GAME_LAUNCHER = "G:\MinecraftGames\CTNH-BaopuEdition\testgame.ps1"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "AssimpLoader Auto-Build & Deploy" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Step 1: Build the mod
Write-Host ""
Write-Host "[1/4] Building mod..." -ForegroundColor Yellow
Set-Location $PROJECT_DIR

& .\gradlew.bat build --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Build successful!" -ForegroundColor Green

# Step 2: Copy JAR to mods folder
Write-Host ""
Write-Host "[2/4] Deploying to mods folder..." -ForegroundColor Yellow

$SOURCE_JAR = Join-Path $PROJECT_DIR "build\libs\$MOD_JAR"
$DEST_JAR = Join-Path $TARGET_DIR $TARGET_JAR

if (-not (Test-Path $SOURCE_JAR)) {
    Write-Host "❌ Source JAR not found: $SOURCE_JAR" -ForegroundColor Red
    exit 1
}

Copy-Item -Path $SOURCE_JAR -Destination $DEST_JAR -Force

Write-Host "✅ Deployed: $TARGET_JAR" -ForegroundColor Green

# Step 3: Check if testgame.ps1 exists
Write-Host ""
Write-Host "[3/4] Checking game launcher..." -ForegroundColor Yellow

if (Test-Path $GAME_LAUNCHER) {
    Write-Host "✅ Found: $GAME_LAUNCHER" -ForegroundColor Green
    $LAUNCH_GAME = $true
} else {
    Write-Host "⚠️  testgame.ps1 not found at: $GAME_LAUNCHER" -ForegroundColor Yellow
    Write-Host "   Skipping game launch." -ForegroundColor Yellow
    $LAUNCH_GAME = $false
}

# Step 4: Launch game (if script exists)
if ($LAUNCH_GAME) {
    Write-Host ""
    Write-Host "[4/4] Launching game..." -ForegroundColor Yellow

    # Use Start-Process to launch in new window
    $gameDir = Split-Path $GAME_LAUNCHER
    Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"$GAME_LAUNCHER`"" -WorkingDirectory $gameDir

    Write-Host "✅ Game launched in new window" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[4/4] Skipped game launch" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "✅ Build and deploy complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
