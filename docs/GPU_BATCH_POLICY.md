# GPU Batch Policy

The client configuration exposes independent rendering budgets in
`config/assimploader-client.toml` (development: `run/config/assimploader-client.toml`).

```toml
[performance.batch]
mode = "OFF"
maxInstances = 4096
geometryMiB = 128
frameMiB = 32
hiZEnabled = false
hiZMaxResolution = 1024
hiZBias = 0.0005
```

`OFF` preserves the existing submission path. `INSTANCED` groups opaque mesh
instances by material texture and mesh identity. `MDI` uses groups of indirect
commands sharing one texture and geometry arena. Admission and hardware checks
remain the responsibility of the rendering backend. A selected mode does not
override an unsupported driver, shader pack, transparent material, or exhausted
budget.

| Field | Accepted Range |
|---|---|
| `maxInstances` | 1-16384 queued mesh instances per batch |
| `geometryMiB` | 1-256 MiB shared geometry storage |
| `frameMiB` | 1-128 MiB transient instance, palette and command storage |
| `hiZEnabled` | Used only with `MDI` |
| `hiZMaxResolution` | 64-1024 pixels for the largest pyramid dimension |
| `hiZBias` | Finite depth tolerance in [0, 0.05] |

Config loading and reloading publish one immutable `GpuBatchPolicy`; draw calls
read that snapshot without parsing TOML. Backend resource changes must occur on
the render thread, not the Forge config watcher thread. Geometry and frame budgets
are separate so an asset cache cannot consume space intended for per-frame poses.

## Draw Planning

`BatchDrawPlan.build` accepts indexed arena ranges and returns ordered source
indices, indirect commands, and texture command ranges. The source-index mapping
must also be used when uploading transforms, bone palettes and lighting. Mesh
keys use Java identity; texture keys use value equality. First-seen texture and
mesh order is stable, with original order retained within each group. This
reordering is suitable for opaque geometry only.

Without Hi-Z, repeated geometry produces one instanced command. In `MDI` with
Hi-Z, every instance instead has its own command so compute can independently
zero `instanceCount` without requiring a visible-instance compaction pass. These
commands are still submitted together by texture. This trades command storage
for simple independent occlusion and does not claim additional instance merging
while Hi-Z is active.

Commands use the 20-byte `DrawElementsIndirectCommand` layout:
`count`, `instanceCount`, `firstIndex`, `baseVertex`, `baseInstance`. `firstIndex`
counts indices, not bytes. `TextureGroup.byteOffset()` provides the byte offset
for MDI submission. Serialization uses native byte order, preserves the caller's
byte-order setting, and validates capacity before writing.

The policy affects client visuals only. Model occlusion and visual bounds never
change server entity collision boxes, hit validation, movement or damage.
Independent local terrain/effect inputs are documented in
[Client Visual Authority](CLIENT_VISUAL_AUTHORITY.md).

## Runtime Backend

The first world integration is `ConfiguredEntityRenderer`, including the deer
showcase. Block entities, items, GUI previews, transparent scenes, animated
materials, active Morph and meshes with more than four bone influences retain
their previous renderer. `GpuInstanceBatcher` also exposes a render-thread scene
submission API for future hosts. OpenGL 4.3 and vertex SSBO support are required;
unsupported drivers, FALLBACK mode, active/unknown Oculus shader state and
non-main targets do not admit world batches.

Static and skinned geometry share a bounded 64-byte vertex arena. Three quarters
of `geometryMiB` is reserved for vertices, the rest for indices. Geometry is
uploaded once per mesh identity until reload. Exceeding either arena partition
rejects the whole scene. Per-instance records carry a model-view matrix, material
tint, lightmap/overlay coordinates and a bone palette range. Static and skinned
instances retain their existing lighting formulas. Palettes support up to 1024
bones, subject to the frame budget. No GPU vertex readback is needed for bounds.

The client opens collection after sky rendering and finishes before the end of
the entity pass. A buffer-source mixin flushes pending opaque batches before
vanilla submits a buffered surface, including automatic translucent-buffer
changes. Mixed immediate/transparent rendering can therefore split a frame into
several smaller batches. Budgets bound each queued batch and its live GPU data;
`maxInstances` is a per-batch mesh-instance capacity, not a total entity limit.
The existing renderer handles scenes which cannot enter a batch.

Each Hi-Z build uses the current frame's main-target opaque depth and matching
projection. The GPU reduces maximum depth into a bounded pyramid and classifies
conservative eye-space model bounds. A second compute dispatch updates indirect
`instanceCount`, followed by command/storage barriers. Failed depth admission,
stale frame tokens, multisample/unknown targets, screen/clip-plane intersections
and non-finite bounds keep geometry visible. No previous-frame occlusion or
production visibility readback is used. A 1024-square pyramid uses less than
8 MiB. Hi-Z is an independent opt-in because its compute and command overhead
can exceed the work it saves.

Configuration changes recreate or release renderer resources on the render
thread. Resource reload, logout and shutdown clear geometry, commands, palettes,
depth hierarchy and queued owners. Indexed SSBO ranges, VAO, indirect-buffer
binding, programs, textures and Minecraft's program state are restored.
`/assimploader performance` includes the current batch policy and draw counters.

## Acceptance Commands

```powershell
.\gradlew.bat test build
.\gradlew.bat runClient -PclientBatchSmoke
.\gradlew.bat runClient -PclientBatchSmoke -PclientBatchSafety
.\gradlew.bat runClient -PclientRuntimeSmoke=Scaffold_PBR_Test -PclientRuntimeBatchSmoke
```

Run these sequentially. The explicit crowd test compares immediate GPU skinning,
INSTANCED, MDI and MDI+Hi-Z for 64/256 independently sampled deer poses, four
256-bone rigs, and static textured models. It checks silhouette and RGB output,
actual submission counters, occlusion classifications, capacity rejection,
cleanup, NPOT/sky/near/far/screen/non-finite/stale-depth/resize cases, and records
warmed CPU submission and asynchronous GPU timestamp samples to
`build/reports/gpu-batch-<run>.csv`. Captures wait at least ten seconds per stage.
These are controlled renderer measurements, not whole-world FPS or server-tick
benchmarks. The world run separately checks binding, movement, terrain, effects,
reload, save/reopen, cache bounds and removal.
The safety-only run checks admission limits, frame and geometry budgets, invalid
inputs and cleanup without repeating the timed crowd stages.

## Verification Scope

Focused unit tests cover defaults, immutable snapshots, finite bounds, independent
Hi-Z admission, geometry identity, texture equality, deterministic grouping,
palette/source ordering, 20-byte command layout and the 16384-instance hard limit.
Unit tests do not establish actual OpenGL support or frame-rate improvement;
runtime backend acceptance and representative multi-instance measurements are
required separately.
