# EnhancedVisuals Integration - Mixin Implementation

## Overview

This document describes the Mixin-based integration with EnhancedVisuals for pollution visual effects.

## Architecture

### Components

1. **MixinPlugin** (`cn.minerealms.iic.mixin.MixinPlugin`)
   - Conditional mixin loading based on mod presence
   - Checks if `team.creative.enhancedvisuals.client.VisualManager` exists
   - Only loads EnhancedVisuals mixins if the mod is present

2. **VisualManagerMixin** (`cn.minerealms.iic.mixin.enhancedvisuals.VisualManagerMixin`)
   - Client-side mixin targeting `team.creative.enhancedvisuals.client.VisualManager`
   - Injects into `onTick()` method at HEAD
   - Calls `PollutionVisualEffects.checkAndTriggerEffects()` every tick

3. **PollutionVisualEffects** (`cn.minerealms.iic.industrial.PollutionVisualEffects`)
   - Client-side visual effects implementation
   - Direct API calls to EnhancedVisuals (no reflection)
   - Handles pollution thresholds and effect triggering

### Why Mixin Instead of Reflection?

**Previous Issues:**
- Wrong package names (`com.fuzs` vs `team.creative`)
- Server-side reflection of client-only classes
- Incorrect method signatures and invocation
- NullPointerException from improper object creation

**Mixin Advantages:**
- Compile-time type checking
- Automatic conditional loading via IMixinPlugin
- Direct API access (no reflection overhead)
- Client-side only execution
- Graceful degradation if EnhancedVisuals not present

## Pollution Thresholds

| Pollution Level | Effects |
|----------------|---------|
| 50-100 | Light: Green particles (3 count) |
| 100-150 | Moderate: Yellow particles (5 count) + Nausea I (10s) |
| 150-200 | Heavy: Orange particles (8 count) + Nausea II (15s) |
| 200+ | Severe: Red particles (12 count) + Nausea III (20s) + Blindness (5s) |

## Configuration

### Mixin Config (`integratedindustrialcraft.mixins.json`)

```json
{
  "plugin": "cn.minerealms.iic.mixin.MixinPlugin",
  "client": [
    "enhancedvisuals.VisualManagerMixin"
  ]
}
```

### Build Configuration (`build.gradle`)

```gradle
dependencies {
    // EnhancedVisuals is optional - loaded at runtime if present
    // No compile-time dependency needed
}

mixin {
    config "integratedindustrialcraft.mixins.json"
}
```

## Commands

```
/im pollution-visual debug          - Toggle debug logging
/im pollution-visual test <type>    - Test specific effect (light/moderate/heavy/severe/all)
/im pollution-visual diagnostics    - Show diagnostic info
```

## Testing

1. **With EnhancedVisuals:**
   - Mixin loads automatically
   - Effects trigger based on pollution
   - Test with `/im pollution-visual test all`

2. **Without EnhancedVisuals:**
   - Mixin plugin skips loading
   - No errors or crashes
   - Graceful degradation

## Implementation Details

### Thread Safety

- **Cooldown Map:** `ConcurrentHashMap<UUID, Long>` for thread-safe access
- **Effect Triggering:** Client main thread only (via mixin injection)
- **Pollution Data:** Synced from server via existing packet system

### Performance

- **Cooldown:** 5 seconds (100 ticks) between effects per player
- **Tick Cost:** Minimal (single pollution lookup + cooldown check)
- **Effect Cost:** EnhancedVisuals handles rendering efficiently

### Error Handling

- Try-catch around all effect triggering
- Debug logging for troubleshooting
- Graceful fallback if effects fail

## Migration from Reflection

**Old System (PollutionVisualManager):**
- ❌ Reflection-based API access
- ❌ Server-side execution
- ❌ Wrong package names
- ❌ Complex initialization
- ❌ Runtime errors

**New System (PollutionVisualEffects + Mixin):**
- ✅ Direct API access
- ✅ Client-side only
- ✅ Correct package names
- ✅ Simple implementation
- ✅ Compile-time safety

## Future Enhancements

1. **Configurable Thresholds:** Add config file for pollution thresholds
2. **Custom Particles:** Create custom particle textures for pollution
3. **Sound Effects:** Add ambient pollution sounds
4. **Screen Overlays:** Add vignette/blur overlays for severe pollution
5. **Biome-Specific Effects:** Different effects based on biome type

## Troubleshooting

### Mixin Not Loading

1. Check `logs/latest.log` for mixin errors
2. Verify EnhancedVisuals is installed
3. Check mixin config syntax
4. Ensure `MixinPlugin` class is compiled

### Effects Not Triggering

1. Enable debug: `/im pollution-visual debug`
2. Check pollution levels: `/im industrial status`
3. Verify cooldown (5 second delay)
4. Check diagnostics: `/im pollution-visual diagnostics`

### Compilation Errors

1. Ensure EnhancedVisuals is in `mods_dev/` folder
2. Refresh Gradle dependencies
3. Clean and rebuild: `./gradlew clean build`
4. Check Java 17 compatibility

## References

- **EnhancedVisuals:** https://www.curseforge.com/minecraft/mc-mods/enhancedvisuals
- **Mixin Documentation:** https://github.com/SpongePowered/Mixin/wiki
- **IMixinPlugin:** https://github.com/SpongePowered/Mixin/wiki/Mixin-Plugin
