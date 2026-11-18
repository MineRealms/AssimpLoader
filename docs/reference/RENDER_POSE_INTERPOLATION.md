# Render Pose Interpolation

Instance sidecars can opt into interpolation:

```json
{
  "performance": {
    "updateIntervalTicks": 1,
    "renderInterpolation": true
  }
}
```

The default is `false`. The global `PERFORMANCE` runtime mode always bypasses
interpolation. `QUALITY`, `BALANCED` and `CUSTOM` honor the explicit asset setting.

## Timing And Ownership

`AnimationState.getPoseSnapshot()` remains the authoritative simulation pose.
Collision, attachments, trails and particles consume that pose. Drawing
`getRenderPose(partialTick)` does not advance clips, modifiers, simulation time,
particle emission or trail history.

The manager calls `advanceRenderTick()` before cadence evaluation on every
managed tick, including skipped evaluations. A positive simulation update
publishes a pair of snapshots and starts a visual interval whose length is the
update's elapsed seconds multiplied by 20. With cadence 4, partial tick 0.5
samples progress 0.125, 0.375, 0.625 and 0.875 over four ticks. This smooths the
previous update interval and therefore adds that interval's visual latency.
Changing cadence uses the last completed interval until the next publication.

Zero-time pose publications clear interpolation history: animation changes,
layer edits, explicit Morph changes, modifier edits, performance changes,
cache invalidation and teleport/dynamics reset. Pause and close also clear it.
Switching through `PERFORMANCE` discards old render history. Reload creates new
scene/state identities and cannot blend an earlier scene.

## Geometry And Cost

Local translation and scale interpolate linearly; rotation uses shortest-arc
quaternion interpolation. Morph weights interpolate before deformation. The
snapshot rebuilds global transforms, mesh palettes and conservative bounds for
the intermediate pose, because a rotating mesh can extend outside the union of
its endpoint bounds. Unchanged shears are preserved; changing shear, projective
matrices or degenerate scales snap to the current transform.

Each instance retains two simulation snapshots and at most one generated render
snapshot. Repeated consumers at the same interpolation phase receive the same
snapshot object, allowing main/shadow skinning and GPU buffer caches to reuse it.
Generated snapshots have negative frame IDs; authoritative frame IDs and
compatibility caches remain unchanged. Consumers must key by snapshot identity,
since interpolation produces multiple visual poses per simulation update.

Interpolation increases per-frame pose/bounds work and can require more skinning
and vertex uploads than the default 20 Hz snapshot path. Enable it selectively,
especially for large meshes, active Morph targets or long cadence intervals.

## Numerical Coverage

`RenderPoseInterpolationTest` checks managed cadence 4, repeated-consumer caching,
independent instances, untouched authoritative pose and simulation clocks,
pause/edit/reset/policy clearing, shortest-arc rotation, intermediate vertex
bounds, Morph geometry, duplicate bind nodes and invalid transform fallback.
