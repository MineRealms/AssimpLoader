# Monitor script for Claude to check test status
# Usage: .\monitor-test.ps1

$PROJECT_DIR = "H:\MinecraftMods\ImprovedMobs"
$STATUS_FILE = Join-Path $PROJECT_DIR "test-status.txt"
$CRASH_FILE = Join-Path $PROJECT_DIR "last-crash.txt"
$LATEST_LOG = "G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Test Status Monitor" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Check if status file exists
if (Test-Path $STATUS_FILE) {
    Write-Host "Current Status:" -ForegroundColor Yellow
    Write-Host "----------------------------------------" -ForegroundColor Gray
    Get-Content $STATUS_FILE
    Write-Host "----------------------------------------" -ForegroundColor Gray
    Write-Host ""

    # Parse status
    $statusContent = Get-Content $STATUS_FILE -Raw
    if ($statusContent -match "STATUS: (\w+)") {
        $status = $matches[1]

        switch ($status) {
            "CRASHED" {
                Write-Host "⚠️  CRASH DETECTED - Action required!" -ForegroundColor Red
                Write-Host ""
                if (Test-Path $CRASH_FILE) {
                    Write-Host "Crash file available at: $CRASH_FILE" -ForegroundColor Yellow
                    Write-Host "Read with: cat $CRASH_FILE" -ForegroundColor Cyan
                }
            }
            "BUILD_FAILED" {
                Write-Host "⚠️  BUILD FAILED - Fix compilation errors!" -ForegroundColor Red
            }
            "BUILDING" {
                Write-Host "🔨 Building in progress..." -ForegroundColor Yellow
            }
            "GAME_LAUNCHING" {
                Write-Host "🎮 Game launching, waiting for result..." -ForegroundColor Yellow
            }
            "SUCCESS" {
                Write-Host "✅ Test passed - No crash detected!" -ForegroundColor Green
            }
            default {
                Write-Host "❓ Unknown status: $status" -ForegroundColor Yellow
            }
        }
    }
} else {
    Write-Host "⚠️  No status file found" -ForegroundColor Yellow
    Write-Host "   Status file: $STATUS_FILE" -ForegroundColor Gray
    Write-Host "   Run auto-test-loop.ps1 to start testing" -ForegroundColor Gray
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Recent Log Errors (last 50 lines):" -ForegroundColor Yellow
Write-Host "=========================================" -ForegroundColor Cyan

if (Test-Path $LATEST_LOG) {
    $errors = Get-Content $LATEST_LOG -Tail 50 | Select-String -Pattern "\[ERROR\]|\[FATAL\]|\[WARN\].*assimp"

    if ($errors) {
        $errors | ForEach-Object {
            if ($_.Line -match "\[ERROR\]|\[FATAL\]") {
                Write-Host $_.Line -ForegroundColor Red
            } else {
                Write-Host $_.Line -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "✅ No errors found in recent logs" -ForegroundColor Green
    }
} else {
    Write-Host "⚠️  Log file not found: $LATEST_LOG" -ForegroundColor Yellow
}

Write-Host ""
