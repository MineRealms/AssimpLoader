# GT Pollution Integration & Hivemind Acceleration System

## Overview

Successfully integrated GregTech Modern's pollution system with IIC's temporary pollution calculation, and implemented Hivemind proximity acceleration for difficulty scaling.

## Implementation Summary

### 1. GT Pollution Scanner (`GTPollutionScanner.java`)

**Location**: `src/main/java/cn/minerealms/iic/industrial/GTPollutionScanner.java`

**Features**:
- Reflection-based detection of GT `EnvironmentalHazardSavedData`
- Scans GT pollution zones (HazardZone) in chunks
- Counts pollution sources in 3x3 chunk area
- Calculates multipliers based on source count:
  - 1-10 sources: 1.0x (no change)
  - 11-20 sources: 1.5x
  - 21-30 sources: 2.0x
  - 31-50 sources: 3.0x
  - 51+ sources: 4.0x

**Key Methods**:
- `scanChunkHazards(ServerLevel, ChunkPos)` - Returns `GTHazardInfo` with strength, source count, and spread status
- `countNearbyPollutionSources()` - Counts sources in 3x3 chunk area
- `calculateSourceMultiplier(int)` - Returns multiplier based on source count

### 2. Hivemind Proximity Manager (`HivemindProximityManager.java`)

**Location**: `src/main/java/cn/minerealms/iic/industrial/HivemindProximityManager.java`

**Features**:
- Caches Spore Hivemind (Proto) positions every 5 seconds (100 ticks)
- Detects when pollution chunks are near Hiveminds
- Calculates acceleration multiplier based on:
  - Distance (inverse - closer = stronger)
  - Biomass (log scale - more biomass = stronger)
  - Pollution level (linear - more pollution = stronger)
- Result range: 1.0x (no effect) to 5.0x (maximum acceleration)

**Key Methods**:
- `updateHivemindCache(ServerLevel)` - Updates cached Hivemind positions
- `checkProximity(ChunkPos, double)` - Returns `HivemindProximityInfo`
- `HivemindProximityInfo.calculateAccelerationMultiplier()` - Calculates final multiplier

### 3. Pollution Manager Integration

**Modified**: `src/main/java/cn/minerealms/iic/industrial/PollutionManager.java`

**Changes**:
- Added GT pollution scanning in `processPollutionDecayAndScanning()`
- GT pollution contribution = `strength × multiplier × gtPollutionWeight`
- Added to temporary pollution (not a new pollution value)
- Added `cleanPollutionInRadius()` method for Air Scrubber integration

### 4. Difficulty Getter Enhancement

**Modified**: `src/main/java/cn/minerealms/iic/industrial/IndustrialDifficultyGetter.java`

**Changes**:
- Checks Hivemind proximity when calculating difficulty
- Applies acceleration multiplier to local pollution factor
- Accelerated pollution increases mob HP faster
- Debug logging for acceleration events

### 5. Spore Integration Enhancement

**Modified**: `src/main/java/cn/minerealms/iic/industrial/SporeIntegration.java`

**New Methods**:
- `getHiveminds()` - Returns list of all Proto entities
- `getNodePosition(Object proto)` - Gets infection center BlockPos from Proto.NODE EntityDataAccessor
- `getBiomass(Object proto)` - Gets biomass value from Proto

### 6. Configuration System

**Modified**: `src/main/java/cn/minerealms/iic/industrial/TriAxisConfig.java`

**New Parameters**:

**GT Pollution Integration**:
- `gtPollutionWeight` (default: 0.8) - Weight for GT pollution contribution
- `gtSourceMultiplierBase` (default: 0.5) - Base multiplier per 10 sources
- `gtSourceThreshold` (default: 10) - Threshold for multiplier activation
- `airScrubberEfficiency` (default: 1.0) - Air Scrubber cleaning efficiency

**Hivemind Proximity System**:
- `hivemindProximityRadius` (default: 8.0 chunks) - Proximity detection radius
- `hivemindAccelerationFactor` (default: 2.0) - Maximum acceleration multiplier
- `enableHivemindAcceleration` (default: true) - Enable/disable system

### 7. Runtime Commands

**Modified**: `src/main/java/cn/minerealms/iic/commands/ConfigCommands.java`

**New Commands**:

**GT Pollution**:
- `/im config gt weight <value>` - Set GT pollution weight (0.0-2.0)
- `/im config gt multiplier <value>` - Set source multiplier base (0.1-1.0)
- `/im config gt threshold <value>` - Set source threshold (5-50)
- `/im config gt scrubber <value>` - Set Air Scrubber efficiency (0.5-2.0)

**Hivemind Proximity**:
- `/im config hivemind radius <chunks>` - Set proximity radius (4.0-16.0)
- `/im config hivemind acceleration <value>` - Set acceleration factor (1.0-5.0)
- `/im config hivemind enable <true|false>` - Enable/disable system

**Display Config**:
- `/im config` - Shows all configuration including GT and Hivemind settings

## How It Works

### GT Pollution Flow

1. **Scanning**: Every second, `PollutionManager` scans chunks near players
2. **GT Detection**: For each chunk, `GTPollutionScanner` checks for GT pollution zones
3. **Source Counting**: Counts pollution sources in 3x3 chunk area
4. **Multiplier Calculation**: Applies multiplier based on source count (1.0x to 4.0x)
5. **Contribution**: `strength × multiplier × gtPollutionWeight` added to temporary pollution
6. **Spreading**: Pollution spreads naturally via existing IIC system

### Hivemind Acceleration Flow

1. **Cache Update**: Every 5 seconds, `HivemindProximityManager` queries Spore for Proto positions
2. **Proximity Check**: When calculating difficulty, checks if pollution chunk is near Hivemind
3. **Acceleration Calculation**: 
   - Distance factor: `1.0 / max(1.0, distance)`
   - Biomass factor: `log10(max(10, biomass)) / 3.0`
   - Pollution factor: `min(2.0, pollution / 100.0)`
   - Final: `1.0 + (distanceFactor × biomassFactor × pollutionFactor × config)`
4. **Application**: Accelerated pollution increases difficulty, which increases mob HP

### Air Scrubber Integration (Ready for Implementation)

**Method**: `PollutionManager.cleanPollutionInRadius()`

**Usage**: Call when GT Air Scrubber completes a recipe
- Cleans pollution in radius (distance-based reduction)
- Respects `airScrubberEfficiency` config
- Removes chunks with pollution ≤ 0

## Configuration Examples

### Balanced (Default)
```properties
gtPollutionWeight=0.8
gtSourceThreshold=10
gtSourceMultiplierBase=0.5
hivemindProximityRadius=8.0
hivemindAccelerationFactor=2.0
enableHivemindAcceleration=true
```

### Aggressive Pollution
```properties
gtPollutionWeight=1.5
gtSourceThreshold=5
gtSourceMultiplierBase=0.8
hivemindProximityRadius=12.0
hivemindAccelerationFactor=3.0
```

### Minimal Impact
```properties
gtPollutionWeight=0.3
gtSourceThreshold=20
gtSourceMultiplierBase=0.3
hivemindProximityRadius=4.0
hivemindAccelerationFactor=1.5
```

## Testing Checklist

### GT Pollution Integration
- [ ] Place 15 Muffler machines in one chunk
- [ ] Verify pollution multiplier >1.0x in debug log
- [ ] Check source count detection (should show ~15)
- [ ] Verify pollution spreads to adjacent chunks

### Hivemind Acceleration
- [ ] Spawn Proto entity near polluted chunks
- [ ] Verify acceleration multiplier applies (check debug log)
- [ ] Confirm mob HP increases faster near Hiveminds
- [ ] Test with different biomass levels

### Air Scrubber (When Implemented)
- [ ] Run Air Scrubber with any recipe
- [ ] Verify pollution decreases in radius
- [ ] Check distance-based reduction works
- [ ] Test efficiency multiplier

### Configuration
- [ ] Adjust multipliers via commands
- [ ] Verify changes take effect immediately
- [ ] Check config file persistence
- [ ] Test `/im config` display

## Performance Notes

- **GT Scanning**: Runs async with machine scanning (every 1 second)
- **Hivemind Cache**: Updated every 5 seconds (100 ticks), not every tick
- **Proximity Checks**: Only for chunks with pollution >0
- **Reflection**: All GT and Spore classes cached on first use

## Debug Commands

Enable debug logging:
```
/im industrial debug on
```

Check Spore integration:
```
/im spore diagnose
```

View current config:
```
/im config
```

## Integration Points

### For Air Scrubber Integration
Add event listener in `AirScrubberListener.java`:
```java
@SubscribeEvent
public static void onRecipeComplete(RecipeFinishedEvent event) {
    // Detect Air Scrubber machine
    // Get cleaning radius and amount from recipe
    // Call PollutionManager.cleanPollutionInRadius()
}
```

### For Custom Pollution Sources
Add to `PollutionManager.processPollutionDecayAndScanning()`:
```java
// Your custom pollution detection
double customPollution = detectCustomSource(chunk);
newPollutionThisSec.merge(chunkPos, customPollution, Double::sum);
```

## Files Modified/Created

**Created**:
- `src/main/java/cn/minerealms/iic/industrial/GTPollutionScanner.java`
- `src/main/java/cn/minerealms/iic/industrial/HivemindProximityManager.java`

**Modified**:
- `src/main/java/cn/minerealms/iic/industrial/PollutionManager.java`
- `src/main/java/cn/minerealms/iic/industrial/IndustrialDifficultyGetter.java`
- `src/main/java/cn/minerealms/iic/industrial/SporeIntegration.java`
- `src/main/java/cn/minerealms/iic/industrial/TriAxisConfig.java`
- `src/main/java/cn/minerealms/iic/industrial/GTIntegration.java` (made `isGTLoaded()` public)
- `src/main/java/cn/minerealms/iic/commands/ConfigCommands.java`

## Build Status

✅ **BUILD SUCCESSFUL** - All classes compiled without errors

## Next Steps

1. Test in-game with GT Modern and Spore installed
2. Verify pollution detection and multipliers
3. Test Hivemind acceleration with different scenarios
4. Implement Air Scrubber event listener (optional)
5. Tune configuration values based on gameplay feedback
