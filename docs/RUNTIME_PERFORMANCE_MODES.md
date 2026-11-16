# Runtime Performance Modes

Runtime workload policy is configured in `config/assimploader-client.toml`. The
development client uses `run/config/assimploader-client.toml`. Forge creates the
section when it loads the updated client configuration.

```toml
[performance.runtime]
mode = "BALANCED"
```

## Presets

| Setting | QUALITY | BALANCED (default) | PERFORMANCE |
|---|---:|---:|---:|
| Nearby update interval, ticks | 1 | 1 | 1 |
| Distant update interval, ticks | 1 | 2 | 4 |
| Distant threshold, blocks | 64 | 64 | 32 |
| Hidden update interval, ticks | 1 | 10 | 20 |
| Last-render grace period, ticks | 40 | 40 | 20 |
| Total emissions per instance update | 32 | 16 | 8 |
| Samples per trail | 4096 | 128 | 32 |
| GPU Morph targets per mesh | 32 | 32 | 32 |
| GPU Morph storage per mesh, MiB | 128 | 128 | 128 |

One simulation tick is 0.05 seconds. Presets enable IK, Spring Bone, particles,
trails and GPU Morph. Budgets are upper bounds; they do not expand an asset's
smaller configured capacities. The runtime policy does not itself add rendering
interpolation, a new GPU backend, Compute dispatch or native execution.

`performance.optimizationLevel`, `rendering.gpuSkinningEnabled`,
`performance.tboEnabled` and `performance.maxTBOBones` retain their existing
backend and compatibility behavior. Runtime modes select how often instance work
is evaluated and bound effect work. They do not override driver limits, Oculus
compatibility decisions or an explicitly disabled GPU backend.

## Custom Settings

`QUALITY`, `BALANCED` and `PERFORMANCE` use the table above. Set `CUSTOM` to read
the individual fields below; changing a custom field while another mode is
selected does not change that preset.

```toml
[performance.runtime]
mode = "CUSTOM"
nearUpdateTicks = 1
farUpdateTicks = 4
farDistance = 48.0
hiddenUpdateTicks = 20
visibilityGraceTicks = 40
ikEnabled = true
springBonesEnabled = true
particlesEnabled = true
trailsEnabled = true
maxParticleEmissions = 8
maxTrailSamples = 64
gpuMorphEnabled = true
maxMorphTargets = 16
maxMorphMiB = 64
```

| Field | Accepted range |
|---|---|
| `nearUpdateTicks`, `farUpdateTicks`, `hiddenUpdateTicks` | 1-20 ticks |
| `farDistance` | 1-4096 blocks, finite |
| `visibilityGraceTicks` | 0-1200 ticks |
| `maxParticleEmissions` | 0-32; zero suppresses emission |
| `maxTrailSamples` | 2-4096 |
| `maxMorphTargets` | 1-32 |
| `maxMorphMiB` | 1-128 MiB |

Far and hidden intervals loaded from TOML are raised to at least the nearby
interval. Forge validates individual numeric bounds. Direct Java construction of
a policy rejects invalid bounds and inconsistent intervals.

## GPU Morph Budget

Skinned GPU Morph admission checks the cached policy, driver texture-buffer
capacity, the 32-target shader limit and the 128-MiB hard safety limit. A disabled
or oversized GPU Morph uses the existing CPU deformation fallback. Reducing this
budget controls GPU storage admission; it can increase CPU work and is not a
guaranteed frame-rate improvement. Static/transparent/unsupported paths retain
their existing fallback behavior.

Config loading and reloading publish one immutable policy snapshot. Reading the
policy during a draw does not parse TOML or allocate a new policy. Existing cached
GPU delta buffers remain allocated until normal resource invalidation even if a
new policy prevents their use; lowering a limit does not synchronously evict GL
resources from Forge's config watcher thread.

Validation includes preset cadence and budget boundaries, disabled/oversized GPU
Morph admission, finite values and direct-constructor rejection. Live configuration
and runtime integration results are recorded by the implementation report.

## Instance Scheduling

Managed item, block and entity instances report their nearest rendered distance.
After the visibility grace period, previously rendered instances use the hidden
interval. Instances which have never rendered retain the nearby interval because
absence of a render observation does not prove that they are invisible. Instance
phases distribute evaluations across ticks. Skipped time accumulates and advances
playback at the next evaluation; pausing discards pending time. Multiple render
passes only report visibility and never advance playback.

Optional sidecar settings constrain one asset further:

```json
"performance": {
  "updateIntervalTicks": 1,
  "ik": true,
  "springBones": true,
  "particles": true,
  "trails": true,
  "maxParticleEmissions": 4,
  "maxTrailSamples": 32
}
```

The effective interval is the larger of the asset and global intervals. Feature
flags require both levels to permit the feature. Emissions share one instance
budget across all emitters in definition order; a saturated earlier emitter can
consume that update's budget. Excess emissions are dropped, avoiding catch-up
bursts. Disabling a feature resets its simulation/history. Spring substeps remain
bounded by each spring's configuration when a distant instance catches up.

`/assimploader performance` prints the active policy and managed-instance
evaluation/skip counters. This is a client command. Direct `update(seconds)` calls
remain deterministic and bypass the managed cadence. Pose interpolation is not
implemented, so distant animation may visibly step at its selected cadence.
