# Imported Player Rendering Fixes

## Evidence

The Lusia FBX contains 241 bones, 27,380 triangles, 82,140 expanded vertices and
six materials, including a transparent eye overlay. Both the Oculus posed VBO
cache and the vanilla GPU skinning branch previously required the entire scene
to be opaque. That one overlay therefore sent the whole character to CPU
deformation and per-vertex BufferBuilder submission.

A 20-second live JFR found 942 render-thread stack samples. The first project
frame was `BaseAssimpRenderer.addVertex` in 520 samples and `CpuSkinning.deform`
in 140. Leaf stacks included Oculus extended-vertex/tangent generation and
normal-matrix inversion. This identifies CPU submission and deformation costs;
it is not a general GPU benchmark. The separate later screenshot was taken
after the user disabled shaders, so its FPS is not paired with that recording.

## Engine Changes

- Mixed opaque/transparent scenes retain accelerated body meshes. Transparent
  materials keep their normal blend RenderType and draw after opaque meshes.
- Optional OpenGL 4.3 skinning writes directly into Oculus's actual extended
  ENTITY VBO through a second compute pass, without GPU-to-CPU vertex readback.
- UV tangents follow current deformed triangles; PBR/UV/color fields stay intact.
- Main/shadow passes reuse pose output. Programs are shared across resident
  meshes; owners retain explicit memory budgets and reload/close cleanup.
- CPU fallback groups identical bone indices/weights before matrix blending and
  normal inverse-transpose. This asset has 5,184 groups for 82,140 vertices.
  Distinct positions, normals and Morph inputs still transform individually.
- A failure after partial drawing does not resubmit the entire model and blend
  translucent parts twice. Subsequent frames recover through the fallback.
- Oculus cache eligibility checks the actual world/extended-vertex render pass,
  rather than rejecting every open `Screen`. Chat and in-world editors retain
  resident VBO rendering; inventory/offscreen GUI previews remain excluded by
  the actual pass flags. The old screen check caused the workbench to fall back
  to CPU submission even with a frozen pose.

Configuration and limits: [Compute Skinning](COMPUTE_SKINNING.md).

## Local Shader Changes

Inspected pack: `RocketFlow_Local`, based on Complementary Unbound r5.3, LabPBR
mode. Its scene TAA uses camera reprojection and has no skeletal motion vectors.
LabPBR material encoding can overwrite the original entity no-TAA material ID.
That makes an animated custom rig eligible for unsuitable history accumulation.

`tools/Patch-RocketEntityTemporal.ps1` patches only this inspected local fork.
It keeps LabPBR material RGB and reserves the unused `colortex6` alpha for
entity/hand temporal eligibility. Metadata uses independent overwrite blending;
scene color still follows its original blend rules. Deferred reflection history
also recognizes these entities at any camera distance. Static terrain retains
the pack's TAA. This is a reactive-history solution, not skeletal motion-vector
generation or a universal shader-pack patch.

Scene history uses `colortex2` RGBA16F with explicit validity in alpha. Every
texel in the previous bilinear sample footprint must be valid before history
color is reused. Reactive pixels retain current RGB, avoiding a black sentinel
mixing into the newly exposed background. Oculus clear-color directives require
four literal components even though ordinary GLSL accepts `vec4(0.0)`.

The buffer blend directive follows the [Iris rendering reference](https://github.com/IrisShaders/docs/blob/main/src/content/docs/current/Reference/Shaders.Properties/rendering.mdx)
and was checked against the installed Oculus 1.8 implementation. Generate a new
local fork with `tools/New-RocketShaderFork.ps1`, or apply the patch to an existing
local directory. Original upstream pack sources are not committed or distributed.

## Verification

Focused animation/CPU/Oculus tests: 29 passed. The actual hidden-context
OpenGL scatter test was exercised with `ASSIMPLOADER_GL_TESTS=1`; ordinary test
runs skip that test when the opt-in is absent. Full root and RigWorks builds
passed, including tests and the three-module packaging boundary check. The shader validator checks 21
expanded variants, including scene TAA, deferred and entity LabPBR paths.

The final unified build discovered 392 root and 99 RigWorks tests: 489 passed,
two opt-in tests skipped, zero failures. It also compiled the client acceptance
sources and verified all three packaged module boundaries. The controller's
Node policy test separately covers repeated moving hops in both walking and
sprinting, stable contact hysteresis and uninterrupted clip phase.

In-game acceptance uses the separate client-directory support described in
[Development Client](DEVELOPMENT_CLIENT.md). A successful build or standalone
GLSL compile alone does not establish an FPS gain or visual artifact removal.

## Live Client Evidence

Read-only runtime captures on 2026-11-17 recorded the new build running the Lusia
player model through the `lusia:player_clips` KubeJS controller:

| Capture | Shader State | Observed FPS |
| --- | --- | ---: |
| `runtime-new-vanilla.json` | Shaders disabled | 206 |
| `runtime-new-shaders.json` | `RocketFlow_Local` active after client restart | 75 |

These are individual snapshots with different camera and debug conditions.
They are higher than the earlier user-reported roughly 25 FPS without shaders
and 10-19 FPS with shaders, but they are not a controlled benchmark and do not
support a measured speedup ratio or a direct shader-cost comparison.

The shaders-on capture also records the actual accelerated path:

| Runtime Counter | Value |
| --- | ---: |
| GPU pose buffers | 6 |
| Initial VBO uploads | 6 |
| Cumulative uploaded vertex bytes | 4,599,840 |
| Resident pose-cache bytes, including GPU deformation resources | 16,849,104 |
| `GpuPoseStatistics.dispatches` | 146,286 |
| GPU pose failure flag | `false` |

The nonzero GPU pose dispatch counter with only six initial uploads confirms
that this run updates the resident Oculus VBOs through compute rather than
uploading the full animated vertex stream on every pose. The character reports
`READY`, zero renderer fallbacks and no script failure. The snapshot's
`shadowDraws=0` does not independently verify shadow-pass reuse.

External evidence is retained under
`G:/DOWNLOAD-EDGE/lusia/assimploader_ready/game_validation/`, including the two
runtime JSON captures above. Acceptance run `1794930685142` passed idle
(`n_idle`), walk (`n_walk`), run (`n_run`), walking continuous jumps,
sprinting continuous jumps and crouch (native overlay over `n_idle`). Each
moving-jump stage observed four takeoffs with forward/jump held and retained
its walk/run base. The searched lane and inventory changes affect only the
copied test world. Initial captures still include startup work and should not
be interpreted as stable performance measurements.

The local development and isolated acceptance configurations enable compute
skinning. Visual debug overlays are disabled for the final clean captures.
The source default remains opt-in for hardware/compatibility fallback.

The [in-world equipment workbench](EQUIPMENT_TRANSFORM_WORKBENCH.md) edits
mount definitions without reloading geometry or invalidating resident VBOs.
Its live acceptance exercised both hands, actual translation/ring/scale drags,
save/cancel, camera isolation and pose freeze/release. The frozen interval
recorded 834 resident mesh draws, 139 pose-cache hits and zero new compute
dispatches. Total VBO uploads stayed at six. Final editor acceptance on
2026-11-18 captured 47 FPS in the
editor after fixing the screen fallback; this is another individual diagnostic
snapshot, not a controlled comparison. Selection lines are submitted in a
managed GUI batch instead of flushing every line separately.

The final workbench screenshots use prefix `equipment-workbench-1794931781598`
and include translation, rotation and scale views. Refreshed numeric fields
reset their horizontal scroll so negative signs remain visible. The client
retains the user's saved hand calibration. Separate `:rigworks:test` and the
unified build also pass after restricting ModernUI-Forge and F3Tweaker to
development-only runtime dependencies.

Full action-state coverage, calibrated finger grip and prolonged motion-artifact
inspection remain separate from these GPU/locomotion checks. Static screenshots
do not prove the absence of all temporal artifacts. Shadow-pass reuse was not
independently established by the captured counters.

## Action And View Follow-Up, 2026-11-18

The current build adds shared ground/air observations, compatible upper-body
actions and native fallback for missing imported clips. Equipment supports named
sockets and item/state rules. Native equipment layers are attached through a
temporary posed adapter; first-person rendering selects arm triangles from the
existing deformed GPU buffers instead of submitting the whole character again.
See [native actions](PLAYER_VANILLA_ACTIONS.md) and
[equipment/views](RIGWORKS_EQUIPMENT_VIEWS.md) for implementation boundaries.

This follow-up also preserves Oculus's hand pass, supplies the native frame for
shoulder pets, limits default spyglass pitch and retains the supplied snapshot
in selected-geometry fallback. Compound native-part rotations use matrix Euler
extraction: the installed JOML quaternion extraction gave a different X angle
for a known Z/Y/X composition. A regression checks pitch/yaw/roll and position.

Visual inspection caught an additional first-person placement problem after
the draw counter had already passed. Lusia's reference arm is about 0.491 blocks
long and its reference direction differs from native geometry. The arm view now
aligns the shoulder/elbow reference axis and fits reach to the native item camera;
world model scale is not changed. The final capture visibly includes the gloved
arm and fingers. A draw count alone was insufficient evidence.

The final unified build discovered **507 tests: 505 passed, two opt-in tests
skipped, zero failures**. It compiled both client integration source sets and
verified the three separate module artifacts.

Acceptance run `1794962312203` used the existing Lusia profile,
`lusia:player_clips`, `RocketFlow_Local`, and the copied
`Character_BlockPlayer_Acceptance` world. Native probes passed crouch, contact/air
phases, swim/crawl/climb, water/lava, flight, ride/sleep, Riptide, freeze/hurt/death,
both hands' native item-use dispatch and attack phases. These are synthetic
unregistered-player probes against actual Minecraft classes, not proof of every
combination of live keyboard inputs. Third-person and empty-hand first-person
captures exercised the real imported character. The server hand fixture and
camera were restored, and the client was left open.

Local evidence under `build/lusia-client-acceptance/`:

- `vanilla-compatibility-smoke-result.json`
- `screenshots/vanilla-compatibility-1794962312203-third-person.png`
- `screenshots/vanilla-compatibility-1794962312203-first-person.png`

The read-only runtime capture `build/runtime-vanilla-compatibility-current.json`
recorded `READY`, zero character fallbacks, 530 completed right-arm draws selecting
1,978 triangles, six resident GPU pose buffers, six initial vertex uploads,
55,428 compute dispatches and 55,428 shadow mesh draws, with no GPU cache failure.
That capture reported 57 FPS on an RTX 4060 with shaders active; it is one
diagnostic snapshot, not a controlled speedup measurement. The growing shadow
counter now establishes actual shadow-path drawing; earlier zero-shadow captures
did not. Both paths consume the resident pose resources.

Remaining verification boundaries: live two-client packet delivery/remote
playback, left-main-arm screenshots, every armor/mod-layer silhouette, prolonged
motion-artifact observation and resource reload through every view. The relay
currently sends profile/mode/flying metadata, not private overrides or asset
files. The broader configurable workbench and accepted visual-configuration
sharing remain [planned work](IN_GAME_WORKBENCH_TODO.md).
