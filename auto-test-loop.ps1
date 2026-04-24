# Auto-Test Loop Script
# Automatically builds, deploys, runs game, and fixes crashes in a loop
# Usage: .\auto-test-loop.ps1

$ErrorActionPreference = "Continue" # Don't stop on errors

$PROJECT_DIR = "H:\MinecraftMods\ImprovedMobs"
$LOG_DIR = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs"
$LATEST_LOG = Join-Path $LOG_DIR "latest.log"
$CRASH_REPORTS = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\crash-reports"

# Configuration
$MAX_ITERATIONS = 10
$WAIT_FOR_CRASH_SECONDS = 60

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "AssimpLoader Auto-Test Loop" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will:" -ForegroundColor Yellow
Write-Host "  1. Build and deploy the mod" -ForegroundColor Yellow
Write-Host "  2. Launch the game" -ForegroundColor Yellow
Write-Host "  3. Wait for crash or success" -ForegroundColor Yellow
Write-Host "  4. Analyze crash logs" -ForegroundColor Yellow
Write-Host "  5. Report to Claude for fixing" -ForegroundColor Yellow
Write-Host "  6. Repeat until stable" -ForegroundColor Yellow
Write-Host ""
Write-Host "Max iterations: $MAX_ITERATIONS" -ForegroundColor Cyan
Write-Host "Crash wait timeout: $WAIT_FOR_CRASH_SECONDS seconds" -ForegroundColor Cyan
Write-Host ""

for ($iteration = 1; $iteration -le $MAX_ITERATIONS; $iteration++) {
    Write-Host "=========================================" -ForegroundColor Magenta
    Write-Host "ITERATION $iteration / $MAX_ITERATIONS" -ForegroundColor Magenta
    Write-Host "=========================================" -ForegroundColor Magenta
    Write-Host ""

    # Step 1: Build and deploy
    Write-Host "[Step 1] Building and deploying..." -ForegroundColor Yellow
    & "$PROJECT_DIR\build-and-run.ps1"

    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Build failed! Check compilation errors above." -ForegroundColor Red
        Write-Host ""
        Write-Host "ACTION REQUIRED:" -ForegroundColor Red
        Write-Host "  - Fix compilation errors" -ForegroundColor Red
        Write-Host "  - Run this script again" -ForegroundColor Red
        exit 1
    }

    Write-Host ""
    Write-Host "[Step 2] Waiting for game to start..." -ForegroundColor Yellow
    Write-Host "  (Waiting $WAIT_FOR_CRASH_SECONDS seconds for crash or stabilization)" -ForegroundColor Gray

    # Wait for game to crash or stabilize
    Start-Sleep -Seconds $WAIT_FOR_CRASH_SECONDS

    # Step 2: Check for crashes
    Write-Host ""
    Write-Host "[Step 3] Checking for crashes..." -ForegroundColor Yellow

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
        Write-Host "Full crash report saved at:" -ForegroundColor Yellow
        Write-Host "  $($latestCrash.FullName)" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "ACTION REQUIRED:" -ForegroundColor Red
        Write-Host "  - Read the crash report above" -ForegroundColor Red
        Write-Host "  - Tell Claude to fix the crash" -ForegroundColor Red
        Write-Host "  - Run this script again after fix" -ForegroundColor Red
        Write-Host ""

        # Save crash summary for Claude
        $crashSummary = @"
CRASH DETECTED - Iteration $iteration

Crash Report: $($latestCrash.Name)
Time: $($latestCrash.LastWriteTime)

$crashContent
"@
        $crashSummary | Out-File -FilePath "$PROJECT_DIR\last-crash.txt" -Encoding UTF8

        Write-Host "Crash summary saved to: $PROJECT_DIR\last-crash.txt" -ForegroundColor Cyan
        Write-Host "You can read it with: cat H:\MinecraftMods\ImprovedMobs\last-crash.txt" -ForegroundColor Cyan

        exit 1

    } else {
        Write-Host "✅ No crash detected!" -ForegroundColor Green
        Write-Host "   Game appears stable." -ForegroundColor Green
        Write-Host ""
        Write-Host "ACTION:" -ForegroundColor Yellow
        Write-Host "  - Check if game is running" -ForegroundColor Yellow
        Write-Host "  - Test /lookdev command manually" -ForegroundColor Yellow
        Write-Host "  - If stable, testing complete!" -ForegroundColor Yellow
        Write-Host ""

        # Check latest.log for errors
        if (Test-Path $LATEST_LOG) {
            $recentErrors = Get-Content $LATEST_LOG -Tail 100 | Select-String -Pattern "\[ERROR\]|\[FATAL\]" -Context 0,2

            if ($recentErrors) {
                Write-Host "⚠️  Found errors in latest.log:" -ForegroundColor Yellow
                $recentErrors | ForEach-Object { Write-Host $_.Line -ForegroundColor Red }
                Write-Host ""
            }
        }

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
