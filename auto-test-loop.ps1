# Auto-Test Loop Script
# Automatically builds, deploys, runs game, and fixes crashes in a loop
# Usage: .\auto-test-loop.ps1

$ErrorActionPreference = "Continue" # Don't stop on errors

$PROJECT_DIR = "H:\MinecraftMods\ImprovedMobs"
$LOG_DIR = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs"
$LATEST_LOG = Join-Path $LOG_DIR "latest.log"
$CRASH_REPORTS = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\crash-reports"

# Status marker files for Claude to monitor
$STATUS_FILE = Join-Path $PROJECT_DIR "test-status.txt"
$CRASH_FILE = Join-Path $PROJECT_DIR "last-crash.txt"

# Configuration
$MAX_ITERATIONS = 10
$WAIT_FOR_CRASH_SECONDS = 300  # 5 minutes for modpack to load

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "AssimpLoader Auto-Test Loop" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will:" -ForegroundColor Yellow
Write-Host "  1. Build and deploy the mod" -ForegroundColor Yellow
Write-Host "  2. Launch the game" -ForegroundColor Yellow
Write-Host "  3. Wait for crash or success" -ForegroundColor Yellow
Write-Host "  4. Write status to test-status.txt" -ForegroundColor Yellow
Write-Host "  5. Claude monitors and fixes issues" -ForegroundColor Yellow
Write-Host "  6. Repeat until stable" -ForegroundColor Yellow
Write-Host ""
Write-Host "Max iterations: $MAX_ITERATIONS" -ForegroundColor Cyan
Write-Host "Crash wait timeout: $WAIT_FOR_CRASH_SECONDS seconds" -ForegroundColor Cyan
Write-Host ""
Write-Host "STATUS FILE: $STATUS_FILE" -ForegroundColor Cyan
Write-Host "CRASH FILE: $CRASH_FILE" -ForegroundColor Cyan
Write-Host ""

for ($iteration = 1; $iteration -le $MAX_ITERATIONS; $iteration++) {
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

    Write-Host "=========================================" -ForegroundColor Magenta
    Write-Host "ITERATION $iteration / $MAX_ITERATIONS" -ForegroundColor Magenta
    Write-Host "=========================================" -ForegroundColor Magenta
    Write-Host ""

    # Update status: Building
    @"
STATUS: BUILDING
ITERATION: $iteration / $MAX_ITERATIONS
TIMESTAMP: $timestamp
PHASE: Compiling and deploying mod
"@ | Out-File -FilePath $STATUS_FILE -Encoding UTF8

    # Step 1: Build the mod
    Write-Host "[Step 1] Building mod..." -ForegroundColor Yellow
    Set-Location $PROJECT_DIR
    & .\gradlew.bat build --console=plain

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Build failed! Check compilation errors above." -ForegroundColor Red

        # Update status: Build failed
        @"
STATUS: BUILD_FAILED
ITERATION: $iteration / $MAX_ITERATIONS
TIMESTAMP: $timestamp
ERROR: Compilation failed
ACTION: Fix compilation errors and restart
"@ | Out-File -FilePath $STATUS_FILE -Encoding UTF8

        Write-Host ""
        Write-Host "Status written to: $STATUS_FILE" -ForegroundColor Cyan
        exit 1
    }

    Write-Host "✅ Build successful!" -ForegroundColor Green

    # Step 2: Deploy to mods folder
    Write-Host ""
    Write-Host "[Step 2] Deploying to mods folder..." -ForegroundColor Yellow

    $MOD_JAR = "assimploader-1.0.0-all.jar"
    $TARGET_DIR = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\mods"
    $TARGET_JAR = "integratedindustrialcraft.jar"

    $SOURCE_JAR = Join-Path $PROJECT_DIR "build\libs\$MOD_JAR"
    $DEST_JAR = Join-Path $TARGET_DIR $TARGET_JAR

    if (-not (Test-Path $SOURCE_JAR)) {
        Write-Host "❌ Source JAR not found: $SOURCE_JAR" -ForegroundColor Red
        exit 1
    }

    Copy-Item -Path $SOURCE_JAR -Destination $DEST_JAR -Force
    Write-Host "✅ Deployed: $TARGET_JAR" -ForegroundColor Green

    # Step 3: Launch game
    Write-Host ""
    Write-Host "[Step 3] Launching game..." -ForegroundColor Yellow
    $gameProcess = Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"$PROJECT_DIR\testgame.ps1`"" -WorkingDirectory $PROJECT_DIR -PassThru
    Write-Host "✅ Game launched (PID: $($gameProcess.Id))" -ForegroundColor Green

    # Update status: Game launching
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    @"
STATUS: GAME_LAUNCHING
ITERATION: $iteration / $MAX_ITERATIONS
TIMESTAMP: $timestamp
PHASE: Game starting, waiting $WAIT_FOR_CRASH_SECONDS seconds
"@ | Out-File -FilePath $STATUS_FILE -Encoding UTF8

    Write-Host ""
    Write-Host "[Step 4] Waiting for game to start..." -ForegroundColor Yellow
    Write-Host "  (Waiting $WAIT_FOR_CRASH_SECONDS seconds for crash or stabilization)" -ForegroundColor Gray

    # Wait for game to crash or stabilize
    Start-Sleep -Seconds $WAIT_FOR_CRASH_SECONDS

    # Step 5: Check for crashes
    Write-Host ""
    Write-Host "[Step 5] Checking for crashes..." -ForegroundColor Yellow

    # Find latest crash report
    $latestCrash = Get-ChildItem -Path $CRASH_REPORTS -Filter "crash-*.txt" -ErrorAction SilentlyContinue |
                   Sort-Object LastWriteTime -Descending |
                   Select-Object -First 1

    if ($latestCrash -and $latestCrash.LastWriteTime -gt (Get-Date).AddSeconds(-$WAIT_FOR_CRASH_SECONDS)) {
        Write-Host "❌ CRASH DETECTED!" -ForegroundColor Red
        Write-Host "   Crash report: $($latestCrash.Name)" -ForegroundColor Red
        Write-Host ""

        # Extract relevant crash info
        Write-Host "=========================================" -ForegroundColor Red
        Write-Host "CRASH REPORT SUMMARY" -ForegroundColor Red
        Write-Host "=========================================" -ForegroundColor Red

        $crashContent = Get-Content $latestCrash.FullName -Raw

        # Extract exception
        if ($crashContent -match "(?s)Description: (.+?)(?:\r?\n\r?\n|\Z)") {
            Write-Host "Description:" -ForegroundColor Yellow
            Write-Host $matches[1] -ForegroundColor White
            Write-Host ""
        }

        # Extract stack trace (first 30 lines)
        if ($crashContent -match "(?s)A detailed walkthrough of the error.*?(\r?\n.*?){1,30}") {
            Write-Host "Stack Trace (first 30 lines):" -ForegroundColor Yellow
            Write-Host $matches[0] -ForegroundColor White
            Write-Host ""
        }

        Write-Host "=========================================" -ForegroundColor Red
        Write-Host ""

        # Save crash summary for Claude
        $crashSummary = @"
CRASH DETECTED - Iteration $iteration
Timestamp: $timestamp
Crash Report: $($latestCrash.Name)
Crash Time: $($latestCrash.LastWriteTime)

========================================
FULL CRASH REPORT
========================================

$crashContent
"@
        $crashSummary | Out-File -FilePath $CRASH_FILE -Encoding UTF8

        # Update status: Crashed
        @"
STATUS: CRASHED
ITERATION: $iteration / $MAX_ITERATIONS
TIMESTAMP: $timestamp
CRASH_REPORT: $($latestCrash.Name)
CRASH_TIME: $($latestCrash.LastWriteTime)
CRASH_FILE: $CRASH_FILE
ACTION: Read crash file and fix the issue
"@ | Out-File -FilePath $STATUS_FILE -Encoding UTF8

        Write-Host "Status written to: $STATUS_FILE" -ForegroundColor Cyan
        Write-Host "Crash report written to: $CRASH_FILE" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "⏸️  PAUSED - Waiting for Claude to fix..." -ForegroundColor Yellow
        Write-Host "   Claude should:" -ForegroundColor Yellow
        Write-Host "   1. Read: cat $STATUS_FILE" -ForegroundColor Cyan
        Write-Host "   2. Read: cat $CRASH_FILE" -ForegroundColor Cyan
        Write-Host "   3. Fix the crash" -ForegroundColor Cyan
        Write-Host "   4. Restart this script" -ForegroundColor Cyan

        exit 1

    } else {
        Write-Host "✅ No crash detected!" -ForegroundColor Green
        Write-Host "   Game appears stable." -ForegroundColor Green
        Write-Host ""

        # Check latest.log for errors
        $hasErrors = $false
        if (Test-Path $LATEST_LOG) {
            $recentErrors = Get-Content $LATEST_LOG -Tail 100 | Select-String -Pattern "\[ERROR\]|\[FATAL\]" -Context 0,2

            if ($recentErrors) {
                Write-Host "⚠️  Found errors in latest.log:" -ForegroundColor Yellow
                $recentErrors | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
                Write-Host ""
                $hasErrors = $true
            }
        }

        # Update status: Success
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        @"
STATUS: SUCCESS
ITERATION: $iteration / $MAX_ITERATIONS
TIMESTAMP: $timestamp
RESULT: No crash detected, game appears stable
ERRORS_IN_LOG: $hasErrors
ACTION: Verify game is running and test /lookdev command
"@ | Out-File -FilePath $STATUS_FILE -Encoding UTF8

        Write-Host "Status written to: $STATUS_FILE" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "=========================================" -ForegroundColor Green
        Write-Host "✅ ITERATION $iteration COMPLETE - NO CRASH" -ForegroundColor Green
        Write-Host "=========================================" -ForegroundColor Green

        # If no crash, assume success and exit
        exit 0
    }
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Max iterations reached ($MAX_ITERATIONS)" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
