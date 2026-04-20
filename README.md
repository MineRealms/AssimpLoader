# I3C - Improved Integrated Industrial Craft

<div align="center">

**A dynamic difficulty scaling addon for Improved Mobs based on industrial progression**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.1.3-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[English](#english) | [中文](#中文)

</div>

---

## English

### What is I3C?

**I3C** (Improved Integrated Industrial Craft) is an addon for Improved Mobs that creates dynamic difficulty scaling based on your industrial progression. Like the I3C bus (an advanced version of I2C), this mod acts as a "bus" that integrates and connects various mods in your modpack, providing strong customization capabilities.

The more machines you build, the higher the pollution, the harder the game becomes. Mobs will actively attack your machines, and new threats emerge as you progress through voltage tiers.

### Key Features

🏭 **Industrial Progression Scaling**
- Difficulty increases based on machine count and voltage tiers (ULV → MAX)
- Automatic detection of GregTech CEu Modern machines and multiblocks
- Configurable voltage tier thresholds and difficulty curves

☢️ **Dual-Layer Pollution System**
- **Temporary Pollution**: Chunk-based, can be absorbed by environment (trees, water, grass)
- **Permanent Pollution**: Global accumulation, directly increases difficulty
- All GT machines executing recipes generate pollution based on voltage tier

⚔️ **Voltage-Tier Based Threats**
- **MV (Tier 2)**: Zombies attack machines when pollution ≥ 50
- **HV (Tier 3+)**: Active zombie spawning when pollution ≥ 80
- **HV (Tier 3+)**: Active creeper spawning when pollution ≥ 120
- **Extreme**: Charged creeper spawning when pollution ≥ 200

🔧 **Fully Configurable**
- All parameters in `config/triaxis-difficulty.properties`
- Difficulty presets: NORMAL, HARD, HARDCORE, INSANE, CUSTOM
- Configure pollution rates, threat thresholds, spawn probabilities

⚡ **Performance Optimized**
- Async processing for all heavy operations
- No server lag even with hundreds of machines
- Optimized for large modpacks

🔌 **Extensible API**
- Public API for other mods to integrate
- Custom difficulty providers, pollution sources, threat handlers
- Comprehensive developer documentation

### Compatibility

**Required:**
- Minecraft 1.20.1
- Forge 47.1.3
- Improved Mobs

**Integrated:**
- ✅ GregTech CEu Modern - Machine detection, voltage tiers, pollution generation
- ✅ Spore (真菌孢子) - Hivemind proximity acceleration, pollution feedback
- ✅ Mekanism - Laser turret system (4 tiers)

**API Support:**
- Any mod can integrate via `I3CAPI` class
- See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for details

### Installation

1. Download I3C from [Releases](https://github.com/minerealms/improvedmobs-iic/releases)
2. Place the JAR file in your `mods/` folder
3. Install required dependencies (Improved Mobs)
4. Launch the game and configure via `/im config` or edit `config/triaxis-difficulty.properties`

### Quick Start

**In-Game Commands:**
```
/im difficulty          - Check current difficulty
/im pollution get ~ ~   - Check pollution in current chunk
/im scan               - Scan nearby machines
/im config preset HARD - Set difficulty preset
/im debug on           - Enable debug logging
```

**Configuration:**
Edit `config/triaxis-difficulty.properties` to customize:
- Pollution generation rates
- Threat trigger thresholds
- Difficulty weights
- Voltage tier parameters

### Game Balance

| Stage | Machine Count | Pollution | Difficulty | Threat Level |
|-------|--------------|-----------|------------|--------------|
| MV | 20-30 | 30-60 | +0.5~1.0 | Light |
| HV | 40-60 | 80-150 | +1.5~2.5 | Medium |
| IV-LuV | 100+ | 150-300 | +3.0~5.0 | High |
| ZPM-UHV | 200+ | 300+ | +5.0+ | Extreme |

### For Developers

I3C provides a comprehensive API for mod integration:

```java
import cn.minerealms.iic.api.I3CAPI;

// Query pollution
double pollution = I3CAPI.getTemporaryPollution(chunkPos);

// Add pollution from custom machines
I3CAPI.addTemporaryPollution(chunkPos, 10.0);

// Clean pollution (air scrubber)
I3CAPI.cleanPollutionInRadius(level, centerPos, radiusChunks, cleanAmount);

// Custom difficulty provider
DifficultyFetcher.add(new MyDifficultyProvider());
```

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for comprehensive documentation.

### Credits

- **MekanismTurrets** - Reference for code structure and art assets for laser turret system
- **Improved Mobs** - Base mod for difficulty scaling and mob AI
- **GregTech CEu Modern** - Machine detection and voltage tier system
- **Spore (真菌孢子)** - Hivemind integration

### License

This project is licensed under the MIT License - see [LICENSE](LICENSE) for details.

### Support

- **Issues**: [GitHub Issues](https://github.com/minerealms/improvedmobs-iic/issues)
- **Discord**: [MineRealms Discord](https://discord.gg/minerealms)
- **Wiki**: [Documentation](https://wiki.minerealms.cn/i3c)

---

## 中文

### 什么是 I3C？

**I3C**（Improved Integrated Industrial Craft，改进的集成工业）是 Improved Mobs 的扩展模组，基于工业进度动态调整游戏难度。就像 I3C 总线（I2C 总线的升级版）一样，这个模组充当"总线"的角色，整合并联动整合包中的各种模组，提供强大的自定义能力。

你建造的机器越多，污染越高，游戏难度就越大。怪物会主动攻击你的机器，随着电压等级的提升，新的威胁也会出现。

### 核心特性

🏭 **工业进度难度缩放**
- 基于机器数量和电压等级（ULV → MAX）动态调整难度
- 自动检测 GregTech CEu Modern 机器和多方块结构
- 可配置的电压等级阈值和难度曲线

☢️ **双层污染系统**
- **临时污染**：区块扩散，可被环境吸收（树木、水、草）
- **永久污染**：全局累积，直接增加难度
- 所有执行配方的 GT 机器都会根据电压等级产生污染

⚔️ **基于电压等级的威胁系统**
- **MV（等级 2）**：污染 ≥50 时僵尸攻击机器
- **HV（等级 3+）**：污染 ≥80 时主动生成僵尸
- **HV（等级 3+）**：污染 ≥120 时主动生成苦力怕
- **极限**：污染 ≥200 时生成闪电苦力怕

🔧 **完全可配置**
- 所有参数在 `config/triaxis-difficulty.properties` 中配置
- 难度预设：普通、困难、硬核、疯狂、自定义
- 配置污染速率、威胁阈值、生成概率

⚡ **性能优化**
- 所有重型操作异步处理
- 即使有数百台机器也不会卡服
- 针对大型整合包优化

🔌 **可扩展 API**
- 为其他模组提供公共 API
- 自定义难度提供者、污染源、威胁处理器
- 完整的开发者文档

### 兼容性

**必需：**
- Minecraft 1.20.1
- Forge 47.1.3
- Improved Mobs

**已集成：**
- ✅ GregTech CEu Modern - 机器检测、电压等级、污染生成
- ✅ Spore（真菌孢子）- 虫巢邻近加速、污染反馈
- ✅ Mekanism - 激光炮塔系统（4 个等级）

**API 支持：**
- 任何模组都可以通过 `I3CAPI` 类集成
- 详见 [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)

### 安装

1. 从 [Releases](https://github.com/minerealms/improvedmobs-iic/releases) 下载 I3C
2. 将 JAR 文件放入 `mods/` 文件夹
3. 安装必需的依赖（Improved Mobs）
4. 启动游戏，通过 `/im config` 配置或编辑 `config/triaxis-difficulty.properties`

### 快速开始

**游戏内命令：**
```
/im difficulty          - 查看当前难度
/im pollution get ~ ~   - 查看当前区块污染
/im scan               - 扫描附近机器
/im config preset HARD - 设置难度预设
/im debug on           - 启用调试日志
```

**配置：**
编辑 `config/triaxis-difficulty.properties` 自定义：
- 污染生成速率
- 威胁触发阈值
- 难度权重
- 电压等级参数

### 游戏平衡

| 阶段 | 机器数量 | 污染 | 难度 | 威胁等级 |
|------|---------|------|------|---------|
| MV | 20-30 | 30-60 | +0.5~1.0 | 轻微 |
| HV | 40-60 | 80-150 | +1.5~2.5 | 中等 |
| IV-LuV | 100+ | 150-300 | +3.0~5.0 | 高 |
| ZPM-UHV | 200+ | 300+ | +5.0+ | 极限 |

### 开发者

I3C 提供完整的 API 用于模组集成：

```java
import cn.minerealms.iic.api.I3CAPI;

// 查询污染
double pollution = I3CAPI.getTemporaryPollution(chunkPos);

// 从自定义机器添加污染
I3CAPI.addTemporaryPollution(chunkPos, 10.0);

// 清理污染（空气净化器）
I3CAPI.cleanPollutionInRadius(level, centerPos, radiusChunks, cleanAmount);

// 自定义难度提供者
DifficultyFetcher.add(new MyDifficultyProvider());
```

详见 [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) 获取完整文档。

### 致谢

- **MekanismTurrets** - 激光炮塔系统的代码结构和美术资源参考
- **Improved Mobs** - 难度缩放和怪物 AI 的基础模组
- **GregTech CEu Modern** - 机器检测和电压等级系统
- **Spore（真菌孢子）** - 虫巢集成

### 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE)。

### 支持

- **问题反馈**：[GitHub Issues](https://github.com/MineRealms/IIIC/issues)
