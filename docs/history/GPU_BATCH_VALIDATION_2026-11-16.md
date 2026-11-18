# GPU Batch Validation - 2026-11-16

## Scope

First production backend for configured entities: shared static/skinned geometry,
per-instance transforms/light/overlay/tint/bone palettes, instanced submission,
Multi-Draw Indirect, and optional current-frame GPU Hi-Z occlusion. All are client
visual operations. See [configuration and limits](../reference/GPU_BATCH_POLICY.md) and
[client/server responsibilities](../reference/CLIENT_VISUAL_AUTHORITY.md).

Default batch mode is OFF. Hi-Z is separately disabled by default. Shader packs,
unsupported drivers, transparent/animated materials, active Morph and unsupported
bone layouts retain the existing rendering path. This is not general item/block
batching or a replacement for server collision and hit validation.

## Environment And Workload

- Minecraft 1.20.1, Forge 47.1.3, Java 17, Windows.
- NVIDIA GeForce RTX 4060, OpenGL 4.6.0, driver 572.83.
- Run `1794843724825`, log `build/gpu-batch-acceptance.log`.
- Machine-readable results: `build/reports/gpu-batch-1794843724825.csv`.
- 64 or 256 deer, each independently sampled at a different animation phase:
  4176 vertices and 35 bones per model.
- Four original validation rigs: 6144 vertices and 256 bones each, Morph zero.
- Two static textured models loaded through the actual resource manager. The
  installed `file/Scaffold_Local_PBR` pack overrides `probe_textured.fbx` with
  17 meshes, 608253 vertices and 608253 indices per model.
- Small group: 70 models / 102 mesh instances / 1508346 submitted vertices.
  Large group: 262 models / 294 mesh instances / 2310138 submitted vertices.
- A deterministic depth wall covers the lower left third, leaving sky above it.
  Lighting, overlay, independently sampled poses and static/skinned lighting
  formulas are compared against the original immediate GPU paths.
- Batch geometry budget 128 MiB, frame budget 64 MiB, capacity 8192 mesh
  instances, Hi-Z maximum dimension 512. Largest admitted frame accounting was
  721200 bytes; the hierarchy used 1398100 bytes. Geometry capacity includes
  unused arena space and is not a measured driver-resident allocation count.

Snapshots are frozen during the timed renderer loop. Setup creates independent
poses; `independentPoseBuildMs` is a one-time setup observation, not a steady
animation-tick benchmark. Timing excludes import and geometry upload warmup.

## Measurements

Each stage waits at least ten seconds and excludes its first 64 frames. CPU
figures measure submission; GPU timestamps are retrieved asynchronously. GPU
elapsed intervals include command-stream gaps while the CPU submits immediate
draws, so they are not isolated shader-kernel timings. This is a controlled
renderer benchmark, not a whole-world FPS, server TPS or cross-driver claim.

| Models | Backend | Mesh Instances | API Submissions | Occluded | CPU Mean ms | CPU p95 ms | GPU Median ms |
|---:|---|---:|---:|---:|---:|---:|---:|
| 70 | Immediate | 102 | 102 | 0 | 6.381 | 9.492 | 5.623 |
| 70 | Instanced | 102 | 19 | 0 | 1.069 | 1.823 | 0.433 |
| 70 | MDI | 102 | 19 | 0 | 0.674 | 1.101 | 0.429 |
| 70 | MDI + Hi-Z | 102 | 19 | 13 | 0.839 | 1.197 | 0.476 |
| 262 | Immediate | 294 | 294 | 0 | 20.148 | 20.935 | 20.353 |
| 262 | Instanced | 294 | 19 | 0 | 2.368 | 4.108 | 0.676 |
| 262 | MDI | 294 | 19 | 0 | 1.674 | 2.330 | 0.670 |
| 262 | MDI + Hi-Z | 294 | 19 | 49 | 1.782 | 2.474 | 0.687 |

All six accelerated stages had **zero silhouette pixel differences**. RGB
comparisons use a 3/255 per-channel tolerance: 8 pixels exceeded it in each small
accelerated stage and 2 in each large stage. This includes the dense static model,
material tints, lightmap and overlay. The corrected static path preserves the
original per-vertex lighting/fog interpolation.

MDI does not reduce submissions beyond instancing for this material layout:
there are 19 texture groups. With Hi-Z, the GPU independently masks all 102/294
per-instance commands while MDI still submits only 19 groups. Hi-Z costs more
than MDI alone in this workload, despite culling geometry; it remains an explicit
opt-in rather than a promised speedup.

Final image inspected:
`run/screenshots/assimp-batch-1794843724825-7.png`. Eight captures exist for this
run. An earlier smaller-screen-coverage skeletal-only run
`1794842157812` also passed, but its numbers are not mixed into this table.

## Correctness Checks

- Actual GL dispatch/draw paths were required; a CPU or immediate fallback could
  not pass accelerated-stage acceptance.
- GPU checks passed for NPOT depth dimensions, occluded/in-front/sky bounds,
  wall edges, near/far/screen intersections and non-finite bounds.
- A mismatched frame token was rejected. A failed multisample build invalidated
  the old frame. Resize/rebuild with empty depth kept all instances visible.
- One-instance capacity rejection preserved the already accepted scene, and
  closed-renderer geometry accounting returned to zero.
- A 32 MiB arena correctly rejected the resource-pack-expanded static scene:
  its required vertex arena was 39588672 bytes. The final run uses the default
  128 MiB geometry budget. Earlier failed harness runs are not acceptance runs.
- Unit suite: 309 tests, 308 passed, one opt-in benchmark skipped, zero failures
  or errors. `test build` passed before runtime launch.

## Integration Verification

World run `1794844021314` passed all eleven stages, with an explicit
`[WorldRuntime] SUCCESS` in `build/gpu-batch-world.log`: terrain off/on, effects,
movement, teleport, resource reload, NBT recreation, AI movement, disk world
reopen, cache budget and removal. Runtime counters required actual MDI draws
and current-frame Hi-Z classification. The world lifecycle test uses one deer;
it is not a world crowd frame-rate measurement.

The isolated CLIENT_LOCAL check sampled four feet from client terrain while
host visual suppliers were forbidden. Client entity AABB and synchronized
switches remained unchanged. The supported mesh sole clearance was 0.004989624 m
for the simulation pose and 0.0046081543 m for its interpolated render pose.
All 4176 evaluated vertices remained inside the published pose bounds.
The effects-stage capture was inspected at
`run/screenshots/assimp-world-runtime-1794844021314-2-world-effects.png`.

Focused run `1794844391817` passed with `[BatchSmoke] SAFETY SUCCESS` in
`build/gpu-batch-safety.log`. The one-instance limit preserved accepted work;
foreign pose snapshots and non-finite transforms were rejected. A 1 MiB frame
budget admitted 63 instances of the 256-bone fixture before rejecting the next
palette, and the dense resource-pack static model was rejected by the 1 MiB
geometry budget. Closed-renderer geometry accounting returned to zero.
The final combined `test build runClient -PclientBatchSmoke -PclientBatchSafety`
command succeeded, with the unit totals above unchanged.

## Remaining Validation

Whole-world crowd frame pacing with continuously evaluated IK/springs/effects,
multi-vendor drivers, many overlapping transparent entities and shader-pack
fallback combinations need wider measurements. GPU batches intentionally leave
the established Oculus shader path in charge. General block/item host batching,
GPU Morph instance data and eight-weight batch skinning are further extensions.
There is no Rust/JNI/SIMD addition in this change: the measured bottleneck was
draw submission, which is addressed directly by GPU batching.
