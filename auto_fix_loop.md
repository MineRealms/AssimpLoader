# Auto-Fix Loop Plan

## Workflow
1. **Monitor logs** - Check latest.log modification time
2. **Detect errors** - Look for ERROR, FATAL, or crash patterns
3. **Stop game** - Kill Minecraft process
4. **Analyze & Fix** - Read error, identify root cause, fix code
5. **Build** - Run gradlew build
6. **Start game** - Run the PowerShell script
7. **Loop** - Return to step 1

## Implementation
- Cron job every 2 minutes
- Check log file timestamp
- If new errors found, trigger fix cycle
- Auto-restart game after fix
