# Phase 3: Package Restructuring Plan

## File Movement Map

### Pollution System → pollution/
- PollutionManager.java → pollution/PollutionManager.java
- PollutionVisualEffects.java → pollution/visual/PollutionVisualEffects.java

### Difficulty System → difficulty/
- IndustrialDifficultyManager.java → difficulty/DifficultyManager.java
- IndustrialDifficultyGetter.java → difficulty/DifficultyProvider.java
- DifficultySmoother.java → difficulty/DifficultySmoother.java
- GameStageCalculator.java → difficulty/GameStageCalculator.java
- TriAxisDifficultyManager.java → difficulty/TriAxisDifficultyManager.java
- HazardScanner.java → difficulty/HazardScanner.java
- MachineScanner.java → difficulty/MachineScanner.java

### Threat System → threat/
- ThreatManager.java → threat/ThreatManager.java
- HordeManager.java → threat/horde/HordeManager.java
- HordeIntegrationManager.java → threat/horde/HordeIntegrationManager.java

### Integration → integration/
- GTIntegration.java → integration/gregtech/GTIntegration.java
- GTPollutionScanner.java → integration/gregtech/GTPollutionScanner.java
- SporeIntegration.java → integration/spore/SporeIntegration.java
- SporeAsyncWorker.java → integration/spore/SporeAsyncWorker.java
- SporeApiDiagnostics.java → integration/spore/SporeApiDiagnostics.java
- HivemindProximityManager.java → integration/spore/HivemindProximityManager.java

### Keep in industrial/
- TriAxisConfig.java (legacy, marked as deprecated)
- IndustrialLogger.java (utility, used across all systems)

## Import Update Strategy

After moving files, need to update imports in:
1. All moved files (update their own package declarations)
2. All files that import moved classes
3. Command files (command/ and commands/)
4. AI goals (ai/)
5. Event handlers (forge/)
6. Network packets (network/)
7. Client code (client/)

## Execution Steps

1. Create new package directories
2. Move files to new locations
3. Update package declarations in moved files
4. Update all import statements across the project
5. Compile and verify
6. Commit changes
