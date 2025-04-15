# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Improved Mobs is a Minecraft Forge mod for 1.20.1 that makes mobs significantly more challenging through enhanced AI, equipment systems, and difficulty scaling. The mod integrates with industrial mods (GregTech CEu, Spore) to create dynamic difficulty based on player progression and environmental factors.

## Build Commands

```bash
# Build the mod JAR
./gradlew build

# Run Minecraft client for testing
./gradlew runClient

# Run dedicated server
./gradlew runServer

# Run data generators
./gradlew runData

# Clean build artifacts
./gradlew clean

# Reobfuscate JAR (happens automatically after build)
./gradlew reobfJar
```

The built mod JAR will be in `build/libs/`.

## Architecture

### Core Systems

**Difficulty System** (`difficulty/`, `api/difficulty/`)
- `DifficultyFetcher`: Registry for pluggable difficulty providers
- `DifficultyGetter`: Interface for custom difficulty implementations
- `PlayerDifficulty`: Per-player difficulty tracking
- Default implementations: `DefaultDifficulty`, `VanillaDifficulty`
- Integration points for ScalingHealth and industrial mods

**Industrial Integration** (`industrial/`) - 详见 `INDUSTRIAL_SYSTEM_REPORT.md`

核心组件：
- `PollutionManager`: 双层污染系统（临时污染 + 永久污染）
  - 临时污染：区块扩散，可被环境吸收，触发威胁
  - 永久污染：全局累积，转化为 difficulty
  - 异步处理，每秒更新
- `ThreatManager`: 基于电压等级的威胁系统
  - MV: 僵尸攻击机器（污染 ≥50）
  - HV: 主动生成僵尸和 Creeper（污染 ≥80/120）
  - 极高污染：闪电苦力怕（污染 ≥200）
- `IndustrialDifficultyGetter`: 多元化 difficulty 提供者
  - 玩家工业加成 + 局部污染 + 全局污染 + 时间因子
- `TriAxisDifficultyManager`: 三轴难度系统（时间/电压/污染）
- `GTIntegration`: GregTech CEu 机器检测（支持多方块）
- `SporeIntegration`: Spore mod 集成（虫巢/生物质）
- `MachineScanner`: 机器扫描和电压等级计算
- `TriAxisConfig`: 统一配置管理（所有参数可配置）

**Enhanced AI** (`ai/`)
- `BlockBreakGoal`: Allows mobs to break blocks
- `LadderClimbGoal`: Enables ladder climbing
- `StealGoal`: Item theft from players
- `ItemUseGoal`: Mobs can use items (potions, etc.)
- `FlyRidingGoal`, `WaterRidingGoal`: Mount riding behaviors
- `ZombieDestroyMachineGoal`: Zombies attack machines (MV: 污染 ≥50)
- `CreeperTargetMachineGoal`: Creepers attack machines (HV: 污染 ≥120)

**Mekanism Turrets Integration** (`mekanism_turrets/`)
- Laser turret system with four tiers (Basic, Advanced, Elite, Ultimate)
- `LaserTurretBlockEntity`: Main turret logic with GeckoLib animations
- `LaserEntity`: Continuous laser beam rendering and damage
- Energy system integration with Mekanism
- Configurable damage, range, cooldown, and energy capacity per tier

**Terrain Scanner** (`scanner/`)
- `ScannerItem`: Handheld device for terrain analysis
- `ScannerService`: Server-side chunk scanning and data aggregation
- Client-side minimap GUI showing 7x7 chunk area
- Network synchronization for scan data

### Platform Layer

**Forge-Specific** (`forge/`)
- `ImprovedMobsForge`: Main mod entry point, registration, and setup
- `capability/`: Forge capability system for player data
- `events/`: Event handlers for mob spawning, difficulty updates
- `network/`: Packet handlers for client-server communication
- `config/`: Configuration loading and specs
- `integration/`: Third-party mod integrations

### Mixins

The mod uses extensive Mixin modifications:
- `improvedmobs.mixins.json`: Core entity, AI, and pathfinding mixins
- `improvedmobs.mixins.compat.json`: Compatibility mixins
- `mixins.mekanism_turrets.json`: Turret-specific mixins (currently empty)

Key mixin targets:
- Entity sensing and targeting (`EntitySensingMixin`, `TargetGoalMixin`)
- Pathfinding enhancements (`pathfinding.*` package)
- Performance optimizations (`pathfinding.performance.*`)

### Configuration

**ImprovedMobs Configs** (`config/improvedmobs/`):
- `client.toml`: Client-side settings
- `common.toml`: Server-side gameplay settings (difficulty scaling, AI, equipment)

**Industrial Integration Config** (`config/triaxis-difficulty.properties`):
- 难度预设：NORMAL (默认), HARD, HARDCORE, INSANE, CUSTOM
- 污染系统：产生速率、衰减速率、转化阈值
- 威胁系统：触发阈值、生成概率
- Difficulty 权重：玩家加成、污染、时间因子
- 三轴难度：时间/电压/污染权重和曲线参数
- 所有参数都可配置，详见配置文件注释

**Mekanism Turrets Config**:
- Separate spec for turret parameters (damage, range, energy)

## Development Notes

### Dependencies

Key dependencies (see `gradle.properties`):
- Forge 47.1.3 for Minecraft 1.20.1
- TenshiLib: Core library dependency
- GeckoLib 4.8.3: Animation system for turrets
- Mekanism 10.4.16.80: Energy system and integration
- GregTech CEu 7.4.1: Industrial integration
- Spore (真菌孢子): Pollution system integration
- JEI: Recipe viewing integration

### Java Version

Requires Java 17 (configured in `build.gradle` line 21).

### Mod ID

The mod ID is `improvedmobs` (defined in `ImprovedMobs.MODID`).

### Resource Locations

- Assets: `src/main/resources/assets/improvedmobs/`
- Data: `src/main/resources/data/improvedmobs/`
- Generated resources: `src/generated/resources/` (from data generators)

### Adding New Features

**Adding mob AI:**
1. Create goal class in `ai/` package
2. Register in `ItemAITasks.initAI()` if item-related
3. Add to mob via event handlers in `forge/events/EventHandler.java`

**Adding difficulty providers:**
1. Implement `DifficultyGetter` interface
2. Register in `ImprovedMobsForge` constructor via `DifficultyFetcher.add()`
3. Return `Config.IntegrationType.ADD` to add to base difficulty

**Adding industrial features:**
1. Pollution sources: Modify `PollutionManager.processPollutionDecayAndScanning()`
2. Threat types: Add to `ThreatManager.checkAndTriggerThreats()`
3. Difficulty factors: Update `IndustrialDifficultyGetter.getDifficulty()`
4. Config parameters: Add to `TriAxisConfig` and update load/save methods

**Adding turret features:**
1. Block entities in `mekanism_turrets/common/block_entity/`
2. Register in `BlockEntityTypeRegistry`
3. Add GeckoLib animations in `assets/improvedmobs/animations/block/`

### Network Protocol

Packet handlers:
- `forge/network/PacketHandler`: Main mod packets
- `mekanism_turrets/common/packet/MekanismTurretsPacketHandler`: Turret-specific packets

### Debug Features

**Commands:**
- `/im industrial debug on/off` - Toggle debug logging
- `/im industrial scan` - Scan nearby machines and show voltage tiers
- `/im industrial status` - Show current difficulty state
- `/im industrial highlight on/off` - Toggle machine highlighting
- `/im industrial test` - Test GT integration
- `/im hud on/off` - Toggle HUD display

**Debug Output:**
- Debug line rendering via `DebugLineRenderer` (client-side)
- Industrial debug log: `logs/iic-debug.log` (overwritten on restart)
- Debug packets for visualizing AI goals and pathfinding
- Default: All debug features disabled (enable via commands)

**Performance Notes:**
- Pollution scanning: Async (CompletableFuture), every 1 second
- Environment caching: Background thread, every 5 minutes
- HUD updates: Every 1 second
- Threat detection: Every 1 second (probability-based)
- All heavy operations are off main thread

## Industrial Integration System

**完整文档**: 详见 `INDUSTRIAL_SYSTEM_REPORT.md`

### 系统概述

工业集成系统通过污染和电压等级动态调整游戏难度，创造基于工业发展的渐进式挑战。

### 污染系统（双层设计）

**临时污染（Temporary Pollution）**:
- 区块扩散，机器运行产生
- 可被环境吸收（树叶、草、花、水）
- 触发怪物攻击机器
- 公式：`0.01 * (1 + tier * 0.5)` 污染/秒（多方块 ×3）

**永久污染（Permanent Pollution）**:
- 全局累积，从临时污染转化（污染 >200 时）
- 直接增加 ImprovedMobs difficulty
- 不可逆转，代表长期环境影响

### 威胁系统（基于电压等级）

**MV 阶段（Tier 2）**:
- 污染 ≥50: 附近僵尸攻击机器（被动）

**HV 阶段（Tier 3+）**:
- 污染 ≥80: 主动生成僵尸（1%/秒）
- 污染 ≥120: 主动生成 Creeper（0.5%/秒）
- 污染 ≥200: 生成闪电苦力怕（0.1%/秒）

### Difficulty 多元化

```
工业加成 = 玩家工业加成 × 1.0
          + 局部临时污染 × 0.5
          + 全局永久污染 × 0.3
          + 时间因子 × 0.2
```

### 游戏阶段平衡

- **MV** (20-30台): 污染 30-60, Difficulty +0.5~1.0, 轻微威胁
- **HV** (40-60台): 污染 80-150, Difficulty +1.5~2.5, 中等威胁
- **IV-LuV** (100+台): 污染 150-300, Difficulty +3.0~5.0, 高威胁
- **ZPM-UHV** (200+台): 污染 300+, Difficulty +5.0+, 极限挑战

### 配置要点

所有参数可在 `config/triaxis-difficulty.properties` 配置：
- 难度预设：NORMAL (默认), HARD, HARDCORE, INSANE
- 污染阈值和转化率
- 威胁触发条件和生成概率
- Difficulty 权重分配
