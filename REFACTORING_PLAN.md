# IIC Project Refactoring Plan

## Current Issues Analysis

### 1. Command System Issues
- Commands scattered across multiple files (ImprovedMobsCommand, HudCommands, ConfigCommands, etc.)
- No unified command structure
- No config reload command
- Missing pollution/difficulty manipulation commands

### 2. Configuration Issues
- TriAxisConfig uses properties file (not standard Forge config)
- No runtime reload capability
- Config scattered between TriAxisConfig and MekanismTurretsConfig
- No unified config management

### 3. Package Structure Issues
```
Current structure:
cn.minerealms.iic/
├── ai/                    # Mob AI goals
├── client/                # Client-side rendering
├── commands/              # Commands (scattered, inconsistent)
├── forge/                 # Forge event handlers
├── industrial/            # Industrial system (too many classes in one package)
├── integration/           # Mod integrations
├── mixin/                 # Mixins
├── network/               # Network packets
├── scanner/               # Terrain scanner
├── server/                # Server-side services
├── turrets/               # Mekanism turrets (complex subsystem)
└── util/                  # Utilities
```

**Problems:**
- `industrial/` package has 20+ classes with mixed responsibilities
- No clear separation between core systems (pollution, difficulty, threats)
- Integration code mixed with core logic
- Commands not organized by feature

### 4. Code Organization Issues
- PollutionManager handles both temporary and permanent pollution
- IndustrialDifficultyManager vs IndustrialDifficultyGetter confusion
- ThreatManager, HordeManager, HordeIntegrationManager overlap
- SporeIntegration, GTIntegration scattered

## Refactoring Goals

### 1. Unified Command System
**Structure:**
```
/im                          # Root command
├── config                   # Configuration management
│   ├── reload              # Reload all configs
│   ├── get <key>           # Get config value
│   ├── set <key> <value>   # Set config value (runtime)
│   └── save                # Save current config to file
├── pollution                # Pollution management
│   ├── get [chunk]         # Get pollution at location
│   ├── set <value> [chunk] # Set temporary pollution
│   ├── add <value> [chunk] # Add temporary pollution
│   ├── clear [radius]      # Clear temporary pollution
│   ├── permanent           # Permanent pollution commands
│   │   ├── get             # Get global permanent pollution
│   │   ├── set <value>     # Set permanent pollution
│   │   └── add <value>     # Add permanent pollution
│   └── visual              # Visual effects testing
│       └── test <level>    # Test pollution visual effects
├── difficulty               # Difficulty management
│   ├── get [player]        # Get difficulty for player
│   ├── info                # Show difficulty breakdown
│   └── recalculate         # Force recalculation
├── hud                      # HUD management
│   ├── toggle              # Toggle HUD on/off
│   └── reload              # Reload HUD data
├── debug                    # Debug commands
│   ├── industrial          # Industrial system debug
│   ├── spore               # Spore integration debug
│   └── mixin               # Mixin status
└── scanner                  # Terrain scanner (if needed)
```

### 2. Improved Package Structure
```
cn.minerealms.iic/
├── core/                           # Core mod infrastructure
│   ├── IntegratedIndustrialCraft.java
│   ├── config/                     # Unified config system
│   │   ├── IICConfig.java         # Main config class
│   │   ├── ConfigManager.java     # Config loading/saving/reloading
│   │   ├── sections/              # Config sections
│   │   │   ├── PollutionConfig.java
│   │   │   ├── DifficultyConfig.java
│   │   │   ├── ThreatConfig.java
│   │   │   └── TurretConfig.java
│   │   └── ConfigCommand.java     # Config manipulation commands
│   └── registry/                   # Forge registries
│       └── IICRegistries.java
├── command/                        # Unified command system
│   ├── IICCommand.java            # Root command
│   ├── PollutionCommand.java      # Pollution subcommands
│   ├── DifficultyCommand.java     # Difficulty subcommands
│   ├── HudCommand.java            # HUD subcommands
│   └── DebugCommand.java          # Debug subcommands
├── pollution/                      # Pollution system
│   ├── PollutionManager.java      # Main pollution manager
│   ├── TemporaryPollution.java    # Chunk-based temporary pollution
│   ├── PermanentPollution.java    # Global permanent pollution
│   ├── PollutionScanner.java      # Pollution source scanning
│   └── visual/                     # Visual effects
│       ├── PollutionVisualEffects.java
│       └── PollutionVisualEventHandler.java
├── difficulty/                     # Difficulty system
│   ├── DifficultyManager.java     # Main difficulty manager
│   ├── DifficultyProvider.java    # ImprovedMobs integration
│   ├── DifficultyCalculator.java  # Calculation logic
│   ├── DifficultySmoother.java    # Smoothing algorithm
│   └── GameStageCalculator.java   # Game stage calculation
├── threat/                         # Threat system
│   ├── ThreatManager.java         # Main threat manager
│   ├── ThreatType.java            # Threat type enum
│   ├── ThreatSpawner.java         # Threat spawning logic
│   └── horde/                      # Horde system
│       ├── HordeManager.java
│       └── HordeIntegrationManager.java
├── integration/                    # Mod integrations
│   ├── gregtech/                   # GregTech integration
│   │   ├── GTIntegration.java
│   │   ├── GTPollutionScanner.java
│   │   └── GTMachineDetector.java
│   ├── spore/                      # Spore integration
│   │   ├── SporeIntegration.java
│   │   ├── SporeAsyncWorker.java
│   │   ├── SporeApiDiagnostics.java
│   │   └── HivemindProximityManager.java
│   └── enhancedvisuals/            # EnhancedVisuals integration
│       ├── EnhancedVisualsHelper.java
│       └── VisualManagerMixin.java (in mixin package)
├── ai/                             # Mob AI
│   ├── ZombieDestroyMachineGoal.java
│   └── CreeperTargetMachineGoal.java
├── client/                         # Client-side
│   ├── hud/                        # HUD system
│   │   ├── DifficultyHudRenderer.java
│   │   ├── DifficultyHudData.java
│   │   └── HudUpdateService.java (move to server)
│   └── ClientEventHandler.java
├── network/                        # Network packets
│   ├── PacketHandler.java
│   ├── SyncHudDataPacket.java
│   └── TriggerPollutionEffectPacket.java
├── scanner/                        # Terrain scanner (unchanged)
├── turrets/                        # Mekanism turrets (unchanged)
├── mixin/                          # Mixins
│   ├── MixinPlugin.java
│   └── enhancedvisuals/
│       └── VisualManagerMixin.java
├── util/                           # Utilities
│   ├── MixinLoadTracker.java
│   └── IndustrialLogger.java
└── forge/                          # Forge event handlers
    └── EventHandler.java
```

### 3. Configuration System Redesign

**New Config Structure (Forge TOML):**
```toml
# config/integratedindustrialcraft-common.toml

[pollution]
    # Temporary pollution settings
    [pollution.temporary]
        decay_rate = 0.1
        spread_rate = 0.05
        absorption_rate = 0.02
        max_per_chunk = 500.0
    
    # Permanent pollution settings
    [pollution.permanent]
        conversion_threshold = 200.0
        conversion_rate = 0.01
        difficulty_multiplier = 0.3

[difficulty]
    # Difficulty calculation weights
    player_industrial_weight = 1.0
    local_pollution_weight = 0.5
    global_pollution_weight = 0.3
    time_factor_weight = 0.2
    
    # Smoothing settings
    smoothing_enabled = true
    smoothing_window = 100

[threat]
    # Threat thresholds
    mv_threshold = 50.0
    hv_threshold = 80.0
    hv_creeper_threshold = 120.0
    severe_threshold = 200.0
    
    # Spawn probabilities
    zombie_spawn_chance = 0.01
    creeper_spawn_chance = 0.005
    lightning_creeper_chance = 0.001

[hud]
    enabled = true
    update_interval = 20
    position_x = 10
    position_y = 10

[turrets]
    # Turret settings (from MekanismTurretsConfig)
    ...
```

## Refactoring Phases

### Phase 1: Configuration System
**Goal:** Unified, reloadable configuration system

**Tasks:**
1. Create `core/config/` package structure
2. Implement `IICConfig` with Forge ConfigSpec
3. Migrate TriAxisConfig properties to TOML
4. Implement `ConfigManager` with reload capability
5. Create `/im config` commands
6. Update all code to use new config system

**Files to modify:**
- NEW: `core/config/IICConfig.java`
- NEW: `core/config/ConfigManager.java`
- NEW: `core/config/sections/*.java`
- MODIFY: `IntegratedIndustrialCraft.java`
- DELETE: `industrial/TriAxisConfig.java` (migrate to new system)
- MODIFY: All files using TriAxisConfig

### Phase 2: Command System Unification
**Goal:** Clean, hierarchical command structure

**Tasks:**
1. Create `command/` package
2. Implement `IICCommand` as root command
3. Implement subcommand classes
4. Migrate existing commands
5. Add new pollution/difficulty manipulation commands
6. Add config commands

**Files to modify:**
- NEW: `command/IICCommand.java`
- NEW: `command/PollutionCommand.java`
- NEW: `command/DifficultyCommand.java`
- NEW: `command/HudCommand.java`
- NEW: `command/DebugCommand.java`
- DELETE: `commands/ImprovedMobsCommand.java`
- DELETE: `commands/HudCommands.java`
- DELETE: `commands/ConfigCommands.java`
- DELETE: `commands/PollutionVisualCommand.java`
- DELETE: `commands/VisualTestCommand.java`
- DELETE: `commands/IndustrialDebugCommand.java`
- DELETE: `commands/SporeDebugCommand.java`
- DELETE: `commands/MixinStatusCommand.java`
- DELETE: `commands/HordesCommands.java`

### Phase 3: Package Restructuring
**Goal:** Clear separation of concerns

**Tasks:**
1. Create new package structure
2. Move pollution-related classes to `pollution/`
3. Move difficulty-related classes to `difficulty/`
4. Move threat-related classes to `threat/`
5. Reorganize integration classes
6. Update imports across the project

**Files to move:**
- `industrial/PollutionManager.java` → `pollution/PollutionManager.java`
- `industrial/PollutionVisualEffects.java` → `pollution/visual/PollutionVisualEffects.java`
- `industrial/IndustrialDifficultyManager.java` → `difficulty/DifficultyManager.java`
- `industrial/IndustrialDifficultyGetter.java` → `difficulty/DifficultyProvider.java`
- `industrial/DifficultySmoother.java` → `difficulty/DifficultySmoother.java`
- `industrial/GameStageCalculator.java` → `difficulty/GameStageCalculator.java`
- `industrial/ThreatManager.java` → `threat/ThreatManager.java`
- `industrial/HordeManager.java` → `threat/horde/HordeManager.java`
- `industrial/HordeIntegrationManager.java` → `threat/horde/HordeIntegrationManager.java`
- `industrial/GTIntegration.java` → `integration/gregtech/GTIntegration.java`
- `industrial/GTPollutionScanner.java` → `integration/gregtech/GTPollutionScanner.java`
- `industrial/SporeIntegration.java` → `integration/spore/SporeIntegration.java`
- `industrial/SporeAsyncWorker.java` → `integration/spore/SporeAsyncWorker.java`
- `industrial/SporeApiDiagnostics.java` → `integration/spore/SporeApiDiagnostics.java`
- `industrial/HivemindProximityManager.java` → `integration/spore/HivemindProximityManager.java`

### Phase 4: Code Cleanup
**Goal:** Remove redundancy, improve clarity

**Tasks:**
1. Merge TemporaryPollution and PermanentPollution into PollutionManager
2. Clarify DifficultyManager vs DifficultyProvider roles
3. Clean up threat system overlaps
4. Remove unused code
5. Add documentation

### Phase 5: Testing & Verification
**Goal:** Ensure everything compiles and works

**Tasks:**
1. Compile after each phase
2. Fix any compilation errors
3. Verify config loading
4. Test command system
5. Git commit after each successful phase

## Implementation Notes

- **DO NOT modify turrets/** - Keep turret system unchanged
- Use `@Deprecated` for old classes during migration
- Keep backward compatibility where possible
- Add `@since` and `@deprecated` JavaDoc tags
- Each phase should compile successfully before moving to next phase

## Success Criteria

- ✅ All configs in standard Forge TOML format
- ✅ `/im config reload` works
- ✅ `/im pollution` commands work
- ✅ `/im difficulty` commands work
- ✅ Clear package structure with <10 classes per package
- ✅ No compilation errors
- ✅ All imports updated correctly
- ✅ Git committed after each phase
