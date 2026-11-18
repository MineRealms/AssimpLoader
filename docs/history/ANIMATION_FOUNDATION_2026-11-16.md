# Animation foundation: first increment

This is the historical first-increment record. Current layered animation, IK,
Morph, physics, integration and GPU results are tracked in the
[expansion implementation report](EXPANSION_IMPLEMENTATION_2026-11-16.md).

This implements the first bounded increment from
[the expansion assessment](EXPANSION_ARCHITECTURE_ASSESSMENT_2026-11-16.md).

## Implemented

- `AnimationState` owns playback, pause, transitions and lifecycle for one instance.
  Imported `AssimpScene` data is shared. Directly constructed states remain supported.
- `PoseSnapshot` publishes complete local/node poses, mesh instances, mesh-specific
  skinning palettes, attachment transforms and a conservative model-space AABB.
  Returned matrices, palettes and mesh-instance transforms are defensive copies.
- The scene hierarchy is evaluated once per publication. Renderers consume its mesh
  instances; they do not reconstruct the hierarchy from a flattened name map.
  Unanimated duplicate node names therefore retain their distinct bind transforms.
  Name-based attachment queries use the first depth-first match. Animated node names
  should be unique because imported animation channels identify nodes by name.
- Crossfade interpolates local translation, rotation and scale. Both clips advance
  during a normal transition. Interrupting a transition freezes the displayed source
  pose and starts the next target at zero, avoiding a pose discontinuity.
  Identical matrices and endpoints are copied exactly. Signed TRS decomposition
  preserves reflections; differing shear matrices are projected to TRS while blending.
- Missing target channels blend back to their bind transforms. Stop publishes the
  complete bind pose. Zero-duration transitions switch immediately.
- CPU skinning, GPU palette upload and material passes consume the same captured
  snapshot. CPU geometry caches distinguish snapshot identity across instances.
- Item playback uses the persistent `ItemStack` owner plus the actual rendered scene.
  Owners are compared by object identity and held weakly. Collected owners close their
  states when the reference queue is drained by the next manager operation or tick.
  A new stack identity starts fresh playback; callers rebuilding stacks each frame
  must retain an owner when they need persistent animation.
- Managed playback advances by 0.05 simulation seconds at client tick END and freezes
  when Minecraft reports a paused client. Rendering never advances playback.
  This is 20 Hz sampling; render interpolation is a later increment.

## Integration

```java
// Client thread. Keep owner stable for the lifetime of the displayed object.
var manager = ClientAnimationManager.getInstance();
AnimationState state = manager.getOrCreateAnimationState(owner, modelName, dynamicScene);
if (state != null) {
    state.transitionTo("run", 0.25); // seconds; false for an unknown clip
    PoseSnapshot pose = state.getPoseSnapshot();
    Matrix4f attachment = pose.getAttachmentTransform("hand", localOffset);
    // attachment is in model space: worldFromModel * attachment places the object.
    // Null means the named node is absent.
}
manager.release(owner);
```

Direct `AnimationState` users call `initialize()`, `update(seconds)` and `close()`
themselves. Do not also register the same state with a second update loop. Simulation
mutation is single-writer on the client thread; immutable publication does not make
the legacy mutable player or the shared asset classes thread-safe.

The one-argument manager API remains a shared default instance for compatibility.
Existing `getBoneHierarchy` and `getCachedBoneTransforms` methods remain available as
lazy compatibility views. New renderers should capture `getPoseSnapshot()` once per
draw. Mutating a legacy player directly requires `invalidateCache()` before rendering
if no simulation update follows.

Resource invalidation and logout clear managed instances and render caches. A scene
replacement closes its previous managed state. External callers retaining old states
must create new states for the new asset; the renderer rejects mismatched scenes.

## Scope And Remaining Work

The AABB is a conservative enclosure computed from cached bone influence boxes and
unweighted vertices. It is available to consumers; collision and world culling are
not automatically changed. It is not an exact deformed-mesh collision shape.

This increment supplies attachment matrices, not entity/item attachment registration,
JSON animation controllers, event dispatch, layered masks, IK application, Spring
Bone, Morph import/deformation or physics. Existing standalone helpers for some of
these features are not connected to this pose pipeline by this change.

GPU skinning still uses the existing vertex shader, TBO/uniform limits and CPU
fallback. The pre-existing CPU-all-influences versus GPU-strongest-four distinction
when configured above four influences remains. No Compute Shader, SIMD, JNI, Rust
or C++ backend is added. Snapshot construction currently allocates defensive copies;
allocation and frame-time profiling should precede storage or backend changes.

## Verification

Automated tests exercise real Assimp imports of generated glTF fixtures: independent
instances, transition start/mid/end/interruption, sparse target channels, pause/stop,
lifecycle, immutable frames, helper-parent transforms, repeated node names, per-mesh
bone offsets, attachment offsets, bounds, renderer material-pass consistency and
palette packing. Verified results follow.

- `gradlew.bat test build --console=plain --no-daemon --no-parallel`: 196 tests in
  51 suites; zero failures, errors or skips. Includes 25 additional regressions.
- Packaged outputs: `build/libs/assimploader-1.0.0.jar` and
  `build/libs/assimploader-1.0.0-all.jar`.
- Client regression runs use Minecraft 1.20.1 / Forge 47.1.3, Java 17.0.18,
  NVIDIA RTX 4060 / OpenGL 4.6. Optional Photon/LDLib runtime mods and Oculus are
  excluded by this run; ModernUI remains installed. The requested F3Tweaker jar was
  removed from `run/mods` before the final run.
- Preview smoke capture now waits at least 10 seconds after each model selection or
  item-view opening, including the preview reopened after resource reload. The first,
  faster capture run is superseded by the final delayed run.
- Final command: `gradlew.bat test build runClient "-PclientSmokeWorld=New World"
  -PwithoutOptionalMods --console=plain --no-daemon --no-parallel`.
  It completed successfully at 2026-11-16 16:30 CST. PreviewSmoke reported success
  for 9 scenes, 9 GUI items, view input and resource reload; 11 screenshots were
  produced with a minimum inter-capture interval of 10.149 seconds. The client
  exited normally and no Java process remained after verification.
- Capture run: `1794817677901`, under `run/screenshots/`. Inspected the
  [textured scene](../../run/screenshots/assimp-smoke-1794817677901-probe_textured.png)
  and [item views](../../run/screenshots/assimp-smoke-1794817677901-items.png).
  The existing ModernUI invalid-font-resource warnings remain in the log.
- This existing smoke covers showcase scenes, GUI items, view input and resource
  reload. It does not itself exercise a skinned crossfade asset in the live game.
  Subsequent dedicated FBX validation is recorded in
  [the Blender FBX acceptance report](ANIMATION_FBX_VALIDATION_2026-11-16.md):
  200 tests pass, with live independent instances, interrupted crossfade, attachments,
  stop, reload and matching CPU/GPU silhouette masks for the small fixture.
  Oculus shadows, 256+ bones and RGBA equivalence remain outside that coverage.
