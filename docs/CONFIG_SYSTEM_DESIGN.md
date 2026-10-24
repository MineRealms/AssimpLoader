# AssimpLoader Configuration System Design

## Overview
This document outlines the comprehensive configuration system for AssimpLoader, replacing hardcoded flags and constants with a flexible, user-configurable system.

## Configuration File Format
- **Format**: TOML (Forge standard)
- **Location**: `.minecraft/config/assimploader-common.toml` (common config)
- **Location**: `.minecraft/config/assimploader-client.toml` (client config)

## Configuration Categories

### 1. Debug Configuration
**Category**: `debug`
**Config Type**: Client

```toml
[debug]
    # Enable debug logging to separate log file
    enabled = false
    
    # Enable performance profiling
    profiling = false
    
    # Debug log file path (relative to .minecraft)
    logPath = "logs/assimploader-debug.log"
    
    # Rate limit for debug messages (milliseconds)
    rateLimitMs = 3000
    
    # Debug categories to enable (CORE, RENDER, ANIMATION, CACHE, IK, SPLAT)
    categories = ["CORE", "RENDER", "ANIMATION"]
```

**Configurable Values**:
- `DEBUG_ENABLED` (AssimpLoader.java:47) → `debug.enabled`
- `DebugLogger` rate limiting → `debug.rateLimitMs`
- Debug categories → `debug.categories`

---

### 2. Animation Configuration
**Category**: `animation`
**Config Type**: Common

```toml
[animation]
    # Maximum bones per vertex (affects memory and performance)
    # Range: 1-8, Default: 4
    maxBonesPerVertex = 4
    
    # Enable IK (Inverse Kinematics) system
    ikEnabled = true
    
    # IK solver type: "FABRIK", "CCD", "TWO_BONE"
    ikSolverType = "FABRIK"
    
    # Maximum IK iterations for FABRIK solver
    # Range: 1-50, Default: 20
    ikMaxIterationsFABRIK = 20
    
    # Maximum IK iterations for CCD solver
    # Range: 1-30, Default: 10
    ikMaxIterationsCCD = 10
    
    # IK solve distance threshold (meters)
    # Range: 0.001-1.0, Default: 0.01
    ikDistanceThreshold = 0.01
    
    # Enable animation blending
    blendingEnabled = true
    
    # Animation blend time (seconds)
    # Range: 0.0-2.0, Default: 0.3
    blendTime = 0.3
```

**Configurable Values**:
- `MAX_BONES_PER_VERTEX` (SkinnedMeshProcessor.java:29) → `animation.maxBonesPerVertex`
- `MAX_ITERATIONS` (FABRIKSolver.java:24) → `animation.ikMaxIterationsFABRIK`
- `MAX_ITERATIONS` (CCDSolver.java:18) → `animation.ikMaxIterationsCCD`
- `SOLVE_DISTANCE_THRESHOLD` (FABRIKSolver.java:25) → `animation.ikDistanceThreshold`
- `DISTANCE_THRESHOLD` (CCDSolver.java:19) → `animation.ikDistanceThreshold`

---

### 3. Rendering Configuration
**Category**: `rendering`
**Config Type**: Client

```toml
[rendering]
    # Enable PBR (Physically Based Rendering) pipeline
    pbrEnabled = true
    
    # Enable GPU skinning for skeletal animations
    gpuSkinningEnabled = true
    
    # Use compute shaders for skinning (requires OpenGL 4.3+)
    useComputeShaders = false
    
    # Enable instanced rendering for multiple models
    instancingEnabled = true
    
    # Enable frustum culling optimization
    frustumCullingEnabled = true
    
    # Enable mesh batching
    meshBatchingEnabled = true
    
    # Shader cache size (number of compiled shaders)
    # Range: 10-1000, Default: 100
    shaderCacheSize = 100
    
    # Custom shader mode: "VANILLA", "OCULUS", "DUAL"
    # VANILLA: Use Minecraft's shader system
    # OCULUS: Use Oculus/Iris shader injection
    # DUAL: Support both modes dynamically
    shaderMode = "DUAL"
```

**Configurable Values**:
- PBR pipeline toggle → `rendering.pbrEnabled`
- GPU skinning → `rendering.gpuSkinningEnabled`
- Compute shader usage → `rendering.useComputeShaders`
- Shader mode → `rendering.shaderMode`

---

### 4. Oculus/Iris Compatibility
**Category**: `oculus`
**Config Type**: Client

```toml
[oculus]
    # Enable Oculus/Iris shader compatibility
    enabled = true
    
    # Enable shader injection for custom materials
    shaderInjectionEnabled = true
    
    # Enable PBR material support in Oculus
    pbrSupportEnabled = true
    
    # Shader injection priority (higher = inject later)
    # Range: 0-1000, Default: 100
    injectionPriority = 100
    
    # Enable Gaussian Splatting shader injection
    splatShaderEnabled = true
```

**Configurable Values**:
- Oculus compatibility → `oculus.enabled`
- Shader injection → `oculus.shaderInjectionEnabled`
- PBR support → `oculus.pbrSupportEnabled`

---

### 5. Performance Configuration
**Category**: `performance`
**Config Type**: Client

```toml
[performance]
    # GPU optimization level: "AUTO", "HIGH", "MEDIUM", "LOW", "FALLBACK"
    # AUTO: Detect based on GPU capabilities
    optimizationLevel = "AUTO"
    
    # Enable async model loading
    asyncLoadingEnabled = true
    
    # Model cache size (number of cached models)
    # Range: 10-1000, Default: 100
    modelCacheSize = 100
    
    # Texture cache size (MB)
    # Range: 64-2048, Default: 512
    textureCacheSizeMB = 512
    
    # Enable TBO (Texture Buffer Object) for bone matrices
    tboEnabled = true
    
    # Maximum TBO size (number of bones)
    # Range: 64-16384, Default: 1024
    maxTBOBones = 1024
```

**Configurable Values**:
- GPU optimization level → `performance.optimizationLevel`
- Cache sizes → `performance.modelCacheSize`, `performance.textureCacheSizeMB`
- TBO usage → `performance.tboEnabled`

---

### 6. Hot-Reload Configuration
**Category**: `hotreload`
**Config Type**: Client

```toml
[hotreload]
    # Enable hot-reload system
    enabled = true
    
    # Hot-reload directory (relative to .minecraft)
    # Supports subdirectories: models/, textures/, animations/
    resourcePath = "AssimpLoader/Resource"
    
    # Auto-reload on file change (requires file watcher)
    autoReloadEnabled = false
    
    # File watcher poll interval (milliseconds)
    # Range: 100-10000, Default: 1000
    watcherPollInterval = 1000
    
    # Supported file extensions for hot-reload
    supportedExtensions = ["fbx", "gltf", "glb", "dae", "obj", "blend"]
```

**Configurable Values**:
- `EXTERNAL_MODEL_DIR` (ModelHotReloadManager.java:23) → `hotreload.resourcePath`
- Hot-reload toggle → `hotreload.enabled`

---

### 7. Gaussian Splatting Configuration
**Category**: `splat`
**Config Type**: Client

```toml
[splat]
    # Enable Gaussian Splatting rendering
    enabled = true
    
    # Enable LOD (Level of Detail) system
    lodEnabled = true
    
    # LOD distance thresholds (blocks)
    lodDistanceHigh = 16.0    # Full detail
    lodDistanceMedium = 32.0  # 50% splats
    lodDistanceLow = 64.0     # 25% splats
    lodDistanceCull = 128.0   # Cull completely
    
    # LOD sampling factors
    lodFactorHigh = 1    # Every splat
    lodFactorMedium = 2  # Every 2nd splat
    lodFactorLow = 4     # Every 4th splat
    
    # Enable compressed PLY support
    compressedPLYEnabled = true
    
    # Splat cache size (number of loaded splat models)
    # Range: 5-100, Default: 20
    cacheSize = 20
```

**Configurable Values**:
- `LOD_DISTANCE_*` (GaussianSplatLOD.java:19-22) → `splat.lodDistance*`
- `LOD_FACTOR_*` (GaussianSplatLOD.java:25-27) → `splat.lodFactor*`

---

### 8. Development/Testing Configuration
**Category**: `dev`
**Config Type**: Client

```toml
[dev]
    # Enable auto-test system (executes commands on world join)
    autoTestEnabled = false
    
    # Auto-test commands to execute
    autoTestCommands = ["/lookdev"]
    
    # Delay before executing auto-test (ticks)
    # Range: 0-1200, Default: 100
    autoTestDelay = 100
    
    # Enable example blocks (CNC machine, etc.)
    exampleBlocksEnabled = true
    
    # Enable LookDev node editor
    lookdevEnabled = true
```

**Configurable Values**:
- `AUTO_TEST_ENABLED` (AutoTestManager.java:17) → `dev.autoTestEnabled`
- `TEST_COMMANDS` (AutoTestManager.java:18-20) → `dev.autoTestCommands`
- `DELAY_TICKS` (AutoTestManager.java:21) → `dev.autoTestDelay`

---

## Implementation Strategy

### Phase 1: Create Config Classes
1. Create `AssimpLoaderConfig.java` with Forge's config builder
2. Define all config categories and values
3. Add validation and range constraints
4. Implement config reload listeners

### Phase 2: Migrate Hardcoded Values
1. Replace `DEBUG_ENABLED` with `AssimpLoaderConfig.DEBUG.enabled.get()`
2. Replace `MAX_BONES_PER_VERTEX` with `AssimpLoaderConfig.ANIMATION.maxBonesPerVertex.get()`
3. Replace all other hardcoded constants
4. Update hot-reload path to use config value

### Phase 3: Add Config GUI (Optional)
1. Integrate with Forge's config screen API
2. Add tooltips and descriptions
3. Add "Reset to Default" buttons
4. Add config validation feedback

### Phase 4: Documentation
1. Write user-facing config documentation
2. Add in-game tooltips
3. Create example config files for common scenarios
4. Document performance implications

---

## Config Access Pattern

```java
// Example usage in code
public class SkinnedMeshProcessor {
    private int getMaxBonesPerVertex() {
        return AssimpLoaderConfig.ANIMATION.maxBonesPerVertex.get();
    }
    
    private boolean isIKEnabled() {
        return AssimpLoaderConfig.ANIMATION.ikEnabled.get();
    }
}
```

---

## Migration Notes

### Breaking Changes
- Hot-reload path changes from `H:/MinecraftMods/ImprovedMobs/models` to `.minecraft/AssimpLoader/Resource/`
- Debug logging now disabled by default (was always enabled)
- Some features may be disabled by default for performance

### Backward Compatibility
- System properties still work as overrides:
  - `-Dassimploader.debug=true` → overrides `debug.enabled`
  - `-Dautotest.enabled=true` → overrides `dev.autoTestEnabled`

---

## Performance Implications

### High-Performance Config (for powerful GPUs)
```toml
[rendering]
    gpuSkinningEnabled = true
    useComputeShaders = true
    instancingEnabled = true
    
[performance]
    optimizationLevel = "HIGH"
    tboEnabled = true
    maxTBOBones = 4096
```

### Low-Performance Config (for integrated GPUs)
```toml
[rendering]
    gpuSkinningEnabled = false
    useComputeShaders = false
    pbrEnabled = false
    
[performance]
    optimizationLevel = "LOW"
    modelCacheSize = 50
    textureCacheSizeMB = 256
```

---

## Future Enhancements
1. **Runtime Config Reload**: Allow config changes without restart
2. **Per-Model Overrides**: Config overrides in model JSON files
3. **Profile System**: Predefined config profiles (Performance, Quality, Balanced)
4. **Telemetry**: Optional performance metrics collection
5. **Auto-Tuning**: Automatic optimization level detection and adjustment
