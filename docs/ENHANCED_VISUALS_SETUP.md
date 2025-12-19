# EnhancedVisuals Integration - Development Setup

## Required Setup for Compilation

The EnhancedVisuals integration uses Mixin to inject into EnhancedVisuals classes. To compile the mixin classes, you need EnhancedVisuals in your development environment.

### Option 1: Download EnhancedVisuals JAR (Recommended)

1. Download EnhancedVisuals from CurseForge:
   - URL: https://www.curseforge.com/minecraft/mc-mods/enhancedvisuals/files/5339265
   - File: `EnhancedVisuals-forge-1.20.1-2.1.0.jar`

2. Place the JAR in `mods_dev/` folder:
   ```bash
   cp EnhancedVisuals-forge-1.20.1-2.1.0.jar H:\MinecraftMods\ImprovedMobs\mods_dev/
   ```

3. Compile the project:
   ```bash
   ./gradlew compileJava
   ```

### Option 2: Compile Without EnhancedVisuals

If you don't want to download EnhancedVisuals, you can temporarily disable the mixin classes:

1. Comment out the mixin in `integratedindustrialcraft.mixins.json`:
   ```json
   {
     "client": [
       // "enhancedvisuals.VisualManagerMixin"
     ]
   }
   ```

2. The mod will compile but EnhancedVisuals integration will be disabled.

## Runtime Behavior

### With EnhancedVisuals Installed

- Mixin loads automatically
- Pollution visual effects trigger based on pollution levels
- Test with `/im pollution-visual test all`

### Without EnhancedVisuals Installed

- MixinPlugin skips loading the mixin
- No errors or crashes
- Graceful degradation (no visual effects)

## Build Configuration

The `build.gradle` uses `compileOnly` for EnhancedVisuals:

```gradle
// EnhancedVisuals - Optional integration (compile-only for mixin support)
compileOnly fg.deobf(fileTree(dir: 'mods_dev', include: 'EnhancedVisuals*.jar'))
```

This means:
- ✅ EnhancedVisuals is available at compile time (for mixin classes)
- ✅ EnhancedVisuals is NOT included in the built JAR
- ✅ Users can optionally install EnhancedVisuals at runtime
- ✅ Mod works with or without EnhancedVisuals

## Troubleshooting

### Compilation Error: Cannot find EnhancedVisuals classes

**Problem:**
```
error: cannot find symbol
import team.creative.enhancedvisuals.client.VisualManager;
```

**Solution:**
1. Download EnhancedVisuals JAR (see Option 1 above)
2. Place in `mods_dev/` folder
3. Run `./gradlew --refresh-dependencies compileJava`

### Mixin Not Loading at Runtime

**Problem:** Effects not triggering with EnhancedVisuals installed

**Solution:**
1. Check `logs/latest.log` for mixin errors
2. Verify EnhancedVisuals version matches (1.20.1-2.1.0)
3. Ensure mixin config is correct

### ClassNotFoundException at Runtime

**Problem:**
```
java.lang.ClassNotFoundException: team.creative.enhancedvisuals.client.VisualManager
```

**Solution:**
- This is expected if EnhancedVisuals is not installed
- MixinPlugin should prevent this error
- Check that `MixinPlugin.shouldApplyMixin()` is working

## File Structure

```
src/main/java/cn/minerealms/iic/
├── mixin/
│   ├── MixinPlugin.java                    # Conditional mixin loading
│   └── enhancedvisuals/
│       ├── VisualManagerMixin.java         # Mixin into EnhancedVisuals
│       └── EnhancedVisualsHelper.java      # API wrapper
├── industrial/
│   └── PollutionVisualEffects.java         # Effect management
├── network/
│   └── TriggerPollutionEffectPacket.java   # Test command packet
└── commands/
    └── PollutionVisualCommand.java         # Commands

src/main/resources/
└── integratedindustrialcraft.mixins.json   # Mixin configuration

mods_dev/
└── EnhancedVisuals-forge-1.20.1-2.1.0.jar  # (Download this)
```

## Testing

### Unit Testing (Without EnhancedVisuals)

```bash
# Compile without EnhancedVisuals
./gradlew compileJava

# Should fail if EnhancedVisuals not in mods_dev/
```

### Integration Testing (With EnhancedVisuals)

```bash
# 1. Download and place EnhancedVisuals in mods_dev/
# 2. Compile
./gradlew compileJava

# 3. Run client
./gradlew runClient

# 4. In-game testing
/im pollution-visual test all
```

### Runtime Testing (Mod Compatibility)

```bash
# Test with EnhancedVisuals
1. Place EnhancedVisuals in run/mods/
2. Start game
3. Check logs for: "[PollutionVisuals] EnhancedVisuals integration initialized"
4. Test effects: `/im pollution-visual test all`

# Test without EnhancedVisuals
1. Remove EnhancedVisuals from run/mods/
2. Start game
3. Check logs for: No errors related to EnhancedVisuals
4. Commands still work (no-op)
```

## CI/CD Considerations

If you're using CI/CD (GitHub Actions, etc.), you'll need to:

1. Download EnhancedVisuals JAR in CI script
2. Place in `mods_dev/` before compilation
3. Or: Disable mixin in CI builds

Example GitHub Actions:

```yaml
- name: Download EnhancedVisuals
  run: |
    mkdir -p mods_dev
    wget -O mods_dev/EnhancedVisuals-forge-1.20.1-2.1.0.jar \
      https://edge.forgecdn.net/files/5339/265/EnhancedVisuals-forge-1.20.1-2.1.0.jar

- name: Build
  run: ./gradlew build
```

## References

- **EnhancedVisuals CurseForge:** https://www.curseforge.com/minecraft/mc-mods/enhancedvisuals
- **File ID 5339265:** https://www.curseforge.com/minecraft/mc-mods/enhancedvisuals/files/5339265
- **Mixin Documentation:** https://github.com/SpongePowered/Mixin/wiki
- **IMixinPlugin:** https://github.com/SpongePowered/Mixin/wiki/Mixin-Plugin
