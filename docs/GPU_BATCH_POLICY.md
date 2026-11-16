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
| `maxInstances` | 1-16384 submitted mesh instances per frame |
| `geometryMiB` | 1-256 MiB shared geometry storage |
| `frameMiB` | 1-128 MiB transient instance, palette and command storage |
| `hiZEnabled` | Used only with `MDI` |
| `hiZMaxResolution` | 64-2048 pixels for the largest pyramid dimension |
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

## Verification Scope

Focused unit tests cover defaults, immutable snapshots, finite bounds, independent
Hi-Z admission, geometry identity, texture equality, deterministic grouping,
palette/source ordering, 20-byte command layout and the 16384-instance hard limit.
Unit tests do not establish actual OpenGL support or frame-rate improvement;
runtime backend acceptance and representative multi-instance measurements are
required separately.
