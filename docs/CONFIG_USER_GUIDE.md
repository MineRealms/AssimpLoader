# AssimpLoader Configuration Guide

This guide explains all configuration options available in AssimpLoader and how to use them effectively.

## Configuration Files

AssimpLoader uses two configuration files:

- **`config/assimploader-client.toml`** - Client-side settings (rendering, debug, performance)
- **`config/assimploader-common.toml`** - Common settings (animation, shared logic)

These files are automatically created in your `.minecraft/config/` directory when you first run the mod.

---

## Debug Configuration

Control debug logging and profiling features.

### `debug.enabled`
- **Type**: Boolean
- **Default**: `false`
- **Description**: Enable debug logging to a separate log file
- **When to use**: Enable when troubleshooting issues or reporting bugs

### `debug.profiling`
- **Type**: Boolean
- **Default**: `false`
- **Description**: Enable performance profiling
- **When to use**: Enable to identify performance bottlenecks

### `debug.logPath`
- **Type**: String
- **Default**: `"logs/assimploader-debug.log"`
- **Description**: Path to debug log file (relative to .minecraft)

### `debug.rateLimitMs`
- **Type**: Integer (100-60000)
- **Default**: `3000`
- **Description**: Rate limit for duplicate debug messages in milliseconds
- **Performance**: Higher values reduce log spam but may hide issues

### `debug.categories`
- **Type**: List of Strings
- **Default**: `["CORE", "RENDER", "ANIMATION"]`
- **Options**: CORE, RENDER, ANIMATION, CACHE, IK, SPLAT, LOOKDEV
- **Description**: Which debug categories to enable

---

## Animation Configuration

Control skeletal animation and IK system behavior.

### `animation.maxBonesPerVertex`
- **Type**: Integer (1-8)
- **Default**: `4`
- **Description**: Maximum bones that can influence a single vertex
- **Performance**: Higher values increase memory usage and GPU load
- **Quality**: 4 is standard for games, 8 for high-quality animations

### `animation.ikEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Inverse Kinematics system
- **Performance**: Disable if you don't use IK to save CPU cycles

### `animation.ikSolverType`
- **Type**: Enum
- **Default**: `FABRIK`
- **Options**: 
  - `FABRIK` - Fast, natural-looking (best for long chains)
  - `CCD` - Simple, fast (good for tentacles/tails)
  - `TWO_BONE` - Specialized for limbs (arms/legs)
- **Description**: Which IK solver algorithm to use

### `animation.ikMaxIterationsFABRIK`
- **Type**: Integer (1-50)
- **Default**: `20`
- **Description**: Maximum iterations for FABRIK solver
- **Performance**: Lower = faster but less accurate

### `animation.ikMaxIterationsCCD`
- **Type**: Integer (1-30)
- **Default**: `10`
- **Description**: Maximum iterations for CCD solver
- **Performance**: Lower = faster but less accurate

### `animation.ikDistanceThreshold`
- **Type**: Double (0.001-1.0)
- **Default**: `0.01`
- **Description**: Distance threshold for IK convergence (meters)
- **Quality**: Lower = more precise but slower

### `animation.blendingEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable smooth animation blending

### `animation.blendTime`
- **Type**: Double (0.0-2.0)
- **Default**: `0.3`
- **Description**: Animation blend time in seconds
- **Quality**: Higher = smoother transitions but slower response

---

## Rendering Configuration

Control rendering pipeline features and quality.

### `rendering.pbrEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Physically Based Rendering
- **Performance**: Disable for significant performance boost on low-end GPUs
- **Quality**: PBR provides realistic lighting and materials

### `rendering.gpuSkinningEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Use GPU for skeletal animation calculations
- **Performance**: GPU skinning is 5-10x faster than CPU
- **Compatibility**: Requires OpenGL 3.3+

### `rendering.useComputeShaders`
- **Type**: Boolean
- **Default**: `false`
- **Description**: Use compute shaders for skinning (experimental)
- **Performance**: Faster than vertex shaders for complex models
- **Compatibility**: Requires OpenGL 4.3+

### `rendering.instancingEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable instanced rendering for multiple identical models
- **Performance**: Massive performance boost when rendering many copies

### `rendering.frustumCullingEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Skip rendering models outside camera view
- **Performance**: Essential optimization, keep enabled

### `rendering.meshBatchingEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Batch multiple meshes into single draw calls
- **Performance**: Reduces draw call overhead

### `rendering.shaderCacheSize`
- **Type**: Integer (10-1000)
- **Default**: `100`
- **Description**: Number of compiled shaders to cache
- **Memory**: Higher = more memory but fewer recompilations

### `rendering.shaderMode`
- **Type**: Enum
- **Default**: `DUAL`
- **Options**:
  - `VANILLA` - Use Minecraft's vanilla shaders only
  - `OCULUS` - Use Oculus/Iris shader injection
  - `DUAL` - Support both modes dynamically
- **Description**: Shader system mode

---

## Oculus/Iris Compatibility

Configure shader pack compatibility features.

### `oculus.enabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Oculus/Iris compatibility layer
- **When to use**: Disable if not using shader packs

### `oculus.shaderInjectionEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Inject custom material code into shaders
- **Quality**: Required for PBR materials in shader packs

### `oculus.pbrSupportEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable PBR material support in Oculus
- **Quality**: Provides realistic materials with shader packs

### `oculus.injectionPriority`
- **Type**: Integer (0-1000)
- **Default**: `100`
- **Description**: Shader injection priority (higher = later)
- **Advanced**: Adjust if conflicts with other mods

### `oculus.splatShaderEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Gaussian Splatting shader injection
- **Quality**: Required for 3DGS rendering with shaders

---

## Performance Configuration

Optimize performance for your hardware.

### `performance.optimizationLevel`
- **Type**: Enum
- **Default**: `AUTO`
- **Options**:
  - `AUTO` - Detect based on GPU capabilities
  - `HIGH` - High-end GPU (RTX 3060+, RX 6700+)
  - `MEDIUM` - Mid-range GPU (GTX 1660, RX 580)
  - `LOW` - Low-end GPU (GTX 1050, integrated)
  - `FALLBACK` - Minimal features for compatibility
- **Description**: GPU optimization preset

### `performance.asyncLoadingEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Load models asynchronously
- **Performance**: Prevents game freezing during model loading

### `performance.modelCacheSize`
- **Type**: Integer (10-1000)
- **Default**: `100`
- **Description**: Number of models to keep in cache
- **Memory**: Higher = more RAM but faster loading

### `performance.textureCacheSizeMB`
- **Type**: Integer (64-2048)
- **Default**: `512`
- **Description**: Texture cache size in megabytes
- **Memory**: Adjust based on available VRAM

### `performance.tboEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Use Texture Buffer Objects for bone matrices
- **Performance**: 10x faster than uniform arrays for 100+ bones
- **Compatibility**: Requires OpenGL 3.1+

### `performance.maxTBOBones`
- **Type**: Integer (64-16384)
- **Default**: `1024`
- **Description**: Maximum bones in TBO
- **Memory**: Higher = more VRAM but supports more complex models

---

## Hot-Reload Configuration

Configure the hot-reload development system.

### `hotreload.enabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable hot-reload system
- **When to use**: Essential for development, can disable in production

### `hotreload.resourcePath`
- **Type**: String
- **Default**: `"AssimpLoader/Resource"`
- **Description**: Hot-reload directory (relative to .minecraft)
- **Directory structure**:
  ```
  .minecraft/
    AssimpLoader/
      Resource/
        models/     - Model files (FBX, GLTF, etc.)
        textures/   - Texture files
        animations/ - Animation files
  ```

### `hotreload.autoReloadEnabled`
- **Type**: Boolean
- **Default**: `false`
- **Description**: Automatically reload on file change
- **Performance**: Requires file watcher, may impact performance

### `hotreload.watcherPollInterval`
- **Type**: Integer (100-10000)
- **Default**: `1000`
- **Description**: File watcher poll interval in milliseconds
- **Performance**: Lower = more responsive but higher CPU usage

### `hotreload.supportedExtensions`
- **Type**: List of Strings
- **Default**: `["fbx", "gltf", "glb", "dae", "obj", "blend"]`
- **Description**: File extensions to watch for hot-reload

---

## Gaussian Splatting Configuration

Configure 3D Gaussian Splatting rendering.

### `splat.enabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Gaussian Splatting rendering
- **Performance**: Disable if not using 3DGS models

### `splat.lodEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable Level-of-Detail system
- **Performance**: Essential for large point clouds

### `splat.lodDistanceHigh`
- **Type**: Double (1.0-256.0)
- **Default**: `16.0`
- **Description**: Distance for full detail (blocks)

### `splat.lodDistanceMedium`
- **Type**: Double (1.0-256.0)
- **Default**: `32.0`
- **Description**: Distance for 50% splats (blocks)

### `splat.lodDistanceLow`
- **Type**: Double (1.0-256.0)
- **Default**: `64.0`
- **Description**: Distance for 25% splats (blocks)

### `splat.lodDistanceCull`
- **Type**: Double (1.0-512.0)
- **Default**: `128.0`
- **Description**: Distance to cull completely (blocks)

### `splat.lodFactorHigh`
- **Type**: Integer (1-10)
- **Default**: `1`
- **Description**: Render every Nth splat at high detail

### `splat.lodFactorMedium`
- **Type**: Integer (1-10)
- **Default**: `2`
- **Description**: Render every Nth splat at medium detail

### `splat.lodFactorLow`
- **Type**: Integer (1-10)
- **Default**: `4`
- **Description**: Render every Nth splat at low detail

### `splat.compressedPLYEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Support compressed PLY files
- **Performance**: Faster loading and less disk space

### `splat.cacheSize`
- **Type**: Integer (5-100)
- **Default**: `20`
- **Description**: Number of splat models to cache
- **Memory**: Higher = more RAM but faster loading

---

## Development Configuration

Tools for mod development and testing.

### `dev.autoTestEnabled`
- **Type**: Boolean
- **Default**: `false`
- **Description**: Execute commands automatically on world join
- **When to use**: For automated testing only

### `dev.autoTestCommands`
- **Type**: List of Strings
- **Default**: `["/lookdev"]`
- **Description**: Commands to execute during auto-test

### `dev.autoTestDelay`
- **Type**: Integer (0-1200)
- **Default**: `100`
- **Description**: Delay before executing commands (ticks)

### `dev.exampleBlocksEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable example blocks (CNC machine)
- **When to use**: Disable in production modpacks

### `dev.lookdevEnabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enable LookDev node editor
- **When to use**: For material authoring

---

## Performance Presets

### High-End PC (RTX 3060+, 16GB+ RAM)
```toml
[rendering]
    pbrEnabled = true
    gpuSkinningEnabled = true
    useComputeShaders = true
    instancingEnabled = true

[performance]
    optimizationLevel = "HIGH"
    modelCacheSize = 200
    textureCacheSizeMB = 1024
    maxTBOBones = 4096

[animation]
    maxBonesPerVertex = 8
    ikMaxIterationsFABRIK = 30
```

### Mid-Range PC (GTX 1660, 8GB RAM)
```toml
[rendering]
    pbrEnabled = true
    gpuSkinningEnabled = true
    useComputeShaders = false

[performance]
    optimizationLevel = "MEDIUM"
    modelCacheSize = 100
    textureCacheSizeMB = 512
    maxTBOBones = 1024

[animation]
    maxBonesPerVertex = 4
    ikMaxIterationsFABRIK = 20
```

### Low-End PC (Integrated GPU, 4GB RAM)
```toml
[rendering]
    pbrEnabled = false
    gpuSkinningEnabled = false
    useComputeShaders = false
    instancingEnabled = true

[performance]
    optimizationLevel = "LOW"
    modelCacheSize = 50
    textureCacheSizeMB = 256
    maxTBOBones = 512

[animation]
    maxBonesPerVertex = 2
    ikMaxIterationsFABRIK = 10
    ikMaxIterationsCCD = 5

[splat]
    lodDistanceHigh = 8.0
    lodDistanceMedium = 16.0
    lodDistanceLow = 32.0
    lodDistanceCull = 64.0
```

---

## Troubleshooting

### Models not loading
1. Enable debug logging: `debug.enabled = true`
2. Check `logs/assimploader-debug.log`
3. Verify hot-reload path exists: `.minecraft/AssimpLoader/Resource/models/`

### Poor performance
1. Lower `performance.optimizationLevel` to `"LOW"`
2. Disable `rendering.pbrEnabled`
3. Reduce `animation.maxBonesPerVertex` to `2`
4. Lower `splat.lodDistance*` values

### Shader conflicts
1. Set `rendering.shaderMode = "VANILLA"`
2. Disable `oculus.shaderInjectionEnabled`
3. Check `oculus.injectionPriority` (try different values)

### Animation issues
1. Disable IK: `animation.ikEnabled = false`
2. Increase `animation.ikMaxIterations*`
3. Lower `animation.ikDistanceThreshold`

---

## Command-Line Overrides

You can override config values via JVM arguments:

```bash
# Enable debug mode
-Dassimploader.debug=true

# Enable profiling
-Dassimploader.profiling=true

# Enable auto-test
-Dautotest.enabled=true
```

These override the config file values.
