# Quick build and deploy (no game launch)
# Usage: .\build-deploy.ps1

$ErrorActionPreference = "Stop"

$PROJECT_DIR = "H:\MinecraftMods\ImprovedMobs"
$MOD_JAR = "assimploader-1.0.0-all.jar"
$TARGET_DIR = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
$TARGET_JAR = "integratedindustrialcraft.jar"

Write-Host "Building and deploying..." -ForegroundColor Cyan

Set-Location $PROJECT_DIR
& .\gradlew.bat build --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}

$SOURCE_JAR = Join-Path $PROJECT_DIR "build\libs\$MOD_JAR"
$DEST_JAR = Join-Path $TARGET_DIR $TARGET_JAR

Copy-Item -Path $SOURCE_JAR -Destination $DEST_JAR -Force

Write-Host "✅ Done! Deployed to: $DEST_JAR" -ForegroundColor Green
