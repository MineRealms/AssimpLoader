#!/bin/bash
# Auto-build and deploy script for AssimpLoader

set -e  # Exit on error

PROJECT_DIR="H:/MinecraftMods/ImprovedMobs"
MOD_JAR="assimploader-1.0.0-all.jar"
TARGET_DIR="G:/MinecraftGames/CTNH-BaopuEdition/.minecraft/versions/Create New Horizon/mods"
TARGET_JAR="integratedindustrialcraft.jar"
GAME_LAUNCHER="G:/MinecraftGames/CTNH-BaopuEdition/testgame.ps1"

echo "========================================="
echo "AssimpLoader Auto-Build & Deploy"
echo "========================================="

# Step 1: Build the mod
echo ""
echo "[1/4] Building mod..."
cd "$PROJECT_DIR"
./gradlew build --console=plain

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"

# Step 2: Copy JAR to mods folder
echo ""
echo "[2/4] Deploying to mods folder..."
cp "build/libs/$MOD_JAR" "$TARGET_DIR/$TARGET_JAR"

if [ $? -ne 0 ]; then
    echo "❌ Failed to copy JAR!"
    exit 1
fi

echo "✅ Deployed: $TARGET_JAR"

# Step 3: Check if testgame.ps1 exists
echo ""
echo "[3/4] Checking game launcher..."

if [ -f "$GAME_LAUNCHER" ]; then
    echo "✅ Found: $GAME_LAUNCHER"
    LAUNCH_GAME=true
else
    echo "⚠️  testgame.ps1 not found at: $GAME_LAUNCHER"
    echo "   Skipping game launch."
    LAUNCH_GAME=false
fi

# Step 4: Launch game (if script exists)
if [ "$LAUNCH_GAME" = true ]; then
    echo ""
    echo "[4/4] Launching game..."
    cd "$(dirname "$GAME_LAUNCHER")"
    powershell.exe -ExecutionPolicy Bypass -File "$GAME_LAUNCHER"
else
    echo ""
    echo "[4/4] Skipped game launch"
fi

echo ""
echo "========================================="
echo "✅ Build and deploy complete!"
echo "========================================="
