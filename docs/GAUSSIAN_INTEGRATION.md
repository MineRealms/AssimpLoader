# Gaussian Module Integration

Owner `lookdev_preview` completed the second independent assignment from
`TASK_SPLAT.md`. No Gradle, build, or test command was run. Root owns unified
compilation and verification. `git diff --check` was clean.

## Implemented

- CPU and GLSL use `11 + 3 * shDim` scalar values; SH dimensions are 1/4/9/16.
  Oversized coefficients are truncated and missing or non-finite coefficients
  are zero-filled. Quaternion, opacity, scale, and bounds remain finite.
- JOML depth uses row 2 (`m02/m12/m22/m32`). Existing fastutil primitive sorting
  replaces recursive last-pivot quicksort. Index and depth storage is reused.
- LOD selects original indices and an explicit valid count. The renderer packs
  and uploads the full cloud once per cached scene, then streams only selected
  indices. Temporary native cloud storage is freed immediately after upload;
  native index staging is reused and freed on eviction or deletion.
- OpenGL 4.3 uses SSBO. OpenGL 3.3 uses real scalar TBO data (`R32F`, samplerBuffer)
  and integer order (`R32I`, isamplerBuffer), bound to texture units 0 and 1.
  Capacity checks and failed-allocation cleanup are in place. Incoming GL state,
  including indexed SSBO ranges and buffer textures, is restored after drawing.
- CPU resource and GPU caches obey `splatCacheSize`, including unloaded-config
  defaults. No per-point/per-frame log spam or fake Oculus success remains.
- PLY supports uncompressed ASCII, binary little endian, and binary big endian
  scalar vertex fields by property name. Correct Graphdeco logit opacity,
  quaternion w/x/y/z, and channel-major SH mapping are preserved. Corrupt or
  truncated data fails explicitly. The repository's count-prefixed `.splat`
  format is retained and is not advertised as the generic 32-byte format.

## Ownership and Lifecycle

No world feature, gameplay registration, shader-pack injection, or automatic
resource listener was added. There are no callers outside this module today.
`GaussianSplatRenderer` remains a standalone API:

- Render-thread `initialize()`, `render(...)`, `clearCache()`, and `delete()`.
- `clearCache()` drops GPU clouds and native index staging; `delete()` also drops
  the shader program. Owners must call these on resource reload and shutdown.
- `GaussianSplatOculusInjector.initialize()` does nothing; `isInjected()` is false.
- Existing compatibility methods (`applyLOD`, `applyLODWithFrustum`, snapshot
  `getSortedIndices`) remain available, but the hot render path does not call
  their allocating versions.

## Verification Pending Root

Nine focused JUnit tests were added in these classes:

- `GaussianSplatBufferTest` (2): stride, truncation, padding, finite transforms.
- `GaussianSplatSorterTest` (2): matrix row, selected indices, deterministic ties,
  and 100,000 equal-depth points without bespoke recursive stack overflow.
- `GaussianSplatLODTest` (2): original indices/count and unloaded configuration.
- `SplatLoaderTest` (3): Graphdeco mapping, endian handling, truncation, bad counts.

The real SSBO/TBO output and GL state restoration still need an OpenGL context.
No benchmark was run and no numeric speedup or visual runtime pass is claimed.
Detailed user-facing notes: `GAUSSIAN_FIXES_2026-11-13.md`.
