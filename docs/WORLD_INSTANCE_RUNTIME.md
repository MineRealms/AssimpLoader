# World instance runtime

Configured entities bind the existing `ModelInstance` to a weak entity owner.
The client tick refreshes its world transform, terrain sampler, feature switches
and particle sink before the selected pose evaluation. Draw calls do not advance
IK, springs, particles or trail history. Visibility cadence still controls pose
evaluation; binding refresh itself does not skin vertices.

## Declarative terrain placement

These optional sidecar sections use scene node names and meters. `states` is an
optional allow-list of names declared in the same sidecar's `states` section;
omitting it applies the modifier in every state. Unknown nodes/states and invalid
chain ancestry fail definition binding and use the established logged fallback.

```json
"groundBody": {
  "node": "hips",
  "feet": ["hoofFL", "hoofFR", "hoofBL", "hoofBR"],
  "rise": 0.3,
  "drop": 0.4,
  "maxOffset": 0.2,
  "sole": 0.005,
  "states": ["idle"]
},
"footPlacement": {
  "FL": {
    "chain": ["legFL0", "legFL1", "legFL2", "hoofFL"],
    "rise": 0.3,
    "drop": 0.4,
    "sole": 0.005,
    "weight": 1,
    "states": ["idle"]
  }
}
```

Ground body adjustment samples up to 16 feet and applies the lowest available
vertical correction, bounded by `maxOffset`, before individual foot CCD. It keeps
the lowest leg in reach rather than stretching bones. Each foot performs one
ground query and at most 24 CCD iterations on a chain of 2-16 ordered nodes.
Springs run after ground adjustment and IK. Zero-time publication does not query
terrain or advance dynamics. The global/asset IK performance switches gate both
ground stages. This is visual pose correction, not server movement physics,
foot rotation from a surface normal, or a gait contact planner.

Each terrain trace is capped at 64 meters (`rise + drop`). Sole offsets and body
offset limits are finite and capped as well. The Minecraft sampler validates its
public input before traversing block shapes, so a malformed large value cannot
turn one update into an unbounded world trace.

For explicit hosts, call `setWorldContext(modelToWorld, sampler)` on the client
thread before updating playback. Call `clearWorldContext()` when detaching.
`ConfiguredEntityRenderer` provides the Minecraft adapter automatically and
exposes `groundPlacementEnabled` and `worldEffectsEnabled` for host overrides.

## Effects and lifetime

Bound trail samples store world positions. Rendering cancels the current entity
transform before drawing the history, so old samples do not follow entity
movement. A translation discontinuity greater than four blocks resets spring
dynamics and effects. Particles use the current tick's copied anchor and the
existing per-instance/global emission budgets. Disabling effects clears history
and accumulated emissions; re-enabling does not emit catch-up bursts.

The deer defaults to ground IK enabled and effects disabled. Both flags are
synchronized and saved to NBT. Operator commands:

```text
/assimpdeer ground <targets> <enabled>
/assimpdeer effects <targets> <enabled>
```

Client entity removal explicitly releases the managed animation owner. Resource
reload closes old states and recreates them through the normal resource path.
Weak-owner collection remains a fallback for hosts without explicit lifetime
events.

## Visual bounds

`ConfiguredEntityRenderer` additionally tests the current pose's conservative
visual AABB, including movement between entity tick endpoints and a conservative
horizontal envelope during yaw changes. This keeps protruding antlers and
animated parts visible outside the server collision box. World anchors in this
calculation stay in double precision; a world-border test checks that 1/8-block
offsets survive. It does not replace the server collision shape or implement
occlusion queries/Hi-Z. Terrain/effect transforms still use float world positions;
this bounds improvement does not claim to fix their far-world precision.

## Numerical checks

`DeerAssetTest` samples 60 frames on a two-height analytical ground, checks all
four hoof target distances against a 25 mm tolerance, and checks every deformed
vertex against the published dynamic AABB. Observed maximum hoof error was
7.272 mm. Existing Blender references independently check actual geometry extrema
and named node positions across all eleven clips in both GLB and FBX.

`WorldInstanceTest` checks that trail history stays fixed under movement, is
cleared by teleport/detachment, and does not emit particles after a disabled
interval. `ModelWorldBoundsTest` checks known rotated/scaled extrema at the world
border.

The opt-in integrated-world runner is:

```powershell
.\gradlew.bat test build runClient -PclientRuntimeSmoke=Scaffold_PBR_Test -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

It modifies the named local test world's platform, entity and player position.
Stages cover terrain disabled/enabled, effects, controlled movement, teleport,
resource reload, NBT entity recreation, navigation and entity removal. Every
image waits at least 10.5 seconds and for material textures. Each sampled pose
checks actual deformed extrema and vertex containment; stage assertions also
check hoof errors, emission counts, world trail positions and old-state closure.
The final stages now also save and fully reopen the world from disk. A completed run requires the
`[WorldRuntime] SUCCESS` log marker, not just Gradle's build result.

Do not run another Gradle build while a development client is using this
checkout's class output. Optional `-PclientJfr` records a profile to
`build/reports/assimp-client.jfr` on normal exit. This records the full client;
separate model stacks from Minecraft, resource reload and UI costs when reading
the results.

## Verified world run: 2026-11-16

Run `1794831654168` completed all nine stages with eight delayed images. Each
sample contained 4,176 deformed vertices inside the pose AABB. The maximum
in-world hoof error was 7.162 mm. Controlled displacement was 2.587 blocks;
navigation moved 1.046 blocks and selected `walk`. Effects emitted 42 particles
in their initial interval and retained 13 bounded trail samples. Seven-block
teleport, resource recreation, NBT replacement and entity removal assertions all
passed. The old instance was closed after replacement and removal.

The full `test build` in that run reported 274 tests, 273 passed, one optional
benchmark skipped, zero failures/errors. Log: `build/world-runtime-validation.log`;
screenshots: `run/screenshots/assimp-world-runtime-1794831654168-*.png`.
This run excluded optional shader mods and did not reopen the world from disk.

Expanded Oculus run `1794832541673` subsequently passed ten stages including
actual disk save/reopen and nine delayed images. It additionally verified the
256-bone Showcase's Morph 0/1/0 in main and shadow passes. See the separate
[Oculus validation and JFR report](OCULUS_ANIMATION_VALIDATION_2026-11-16.md).
