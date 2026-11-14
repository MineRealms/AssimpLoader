# Static Rocket Stand Validation, 2026-11-14

## Build And Assets

The flow-based implementation builds with:

```powershell
.\gradlew.bat build -PwithOculus -PwithoutOptionalMods --console=plain
.\tools\Test-RocketShaders.ps1
```

The suite contains 171 tests in 47 suites, with zero failures/errors. Twelve
expanded GLSL variants pass glslangValidator, covering the three dimension wrappers,
the local composite/material shaders and the vanilla core fragment shader.
Deliverable: `build/libs/assimploader-1.0.0-all.jar`.

Focused checks cover nozzle continuity and ideal-gas closure, design flow/thrust,
zero-flow state, pressure-matched shock suppression, altitude pressure, mass-flow
response, wall thermal lag and timestep consistency, interpolation, rejected
non-finite input, and the fallback engine's resources/pivot/size.

The optional original PBR engine was read through the project's LWJGL Assimp importer:
6 meshes, 6 materials, 692,900 triangles, 24 embedded PBR textures, no animations.
No geometry reduction was applied. Its license-unknown FBX remains in the local
resource pack; the bundled fallback is independently authored.

## Runtime Setup

Minecraft 1.20.1 / Forge 47.1.3 / Oculus 1.8.0 / Embeddium 0.3.31,
NVIDIA RTX 4060. Active pack: the machine-local `RocketFlow_Local` derivative of
Complementary Unbound r5.3, with `RocketEngine_Local_PBR` resources enabled.
This work targets that exact pack and Oculus source version.

The opt-in harness creates one stationary stand in a disposable copy of
`Scaffold_PBR_Test`. It captures low flow, nominal flow, close-up, night, camera
rotation, initial shutdown, residual wall heat, hydrolox, solid fuel, shaders off,
and shaders restored after the same resource reload used by F3+T.

Every capture checks the source/state, expected shader backend and `GL_NO_ERROR`.
Dedicated target10 is read back for volume-history coverage/frame count; target8
is read back for finite HDR radiance and transmission. This is direct evidence of
the exhaust pass, separate from the shader pack's ordinary scene TAA.

The most recent completed runtime run finished at 20:35:23 with `SUCCESS` in
`run/saves/Rocket_Flow_Acceptance`, nozzle anchor `(132, -48, -106)`.
All 11 captures passed. Instrumentation counted 11,259 participating shader-program
uploads and 686 vanilla draws; these counts are not frame-rate measurements.
At the end of that run, the harness left the client at the night close-up of the
working stand. This records that run's outcome, not the current process state.

At restored capture, target8 was 640x360 RGBA16F with peak linear HDR radiance
4.5507812, 533 pixels above 1.0 and minimum transmission 0.15979004. Target10 had
69,291 covered pixels, 22,935 accepting more than one history frame and a maximum
of 16 frames. No NaN/Inf radiance or GL error was observed.

After that acceptance, the generated/live temporal helper was synchronized with
two conservative source changes: its projected bounds use the full 2.5-radius
support, and history is rejected when the weighted volume point is outside its
engine interval. The synchronized GLSL passes syntax validation. The user stopped
further client testing, so those two changes have not been runtime-verified; the
screenshots and readback numbers below describe the preceding accepted version.

| Check | Screenshot |
| --- | --- |
| 35 percent nominal mass-flow request | [Low flow](images/rocket/flow-low.png) |
| Nominal kerolox | [Nominal](images/rocket/flow-nominal.png) |
| Compression cones and bright pinch | [Close-up](images/rocket/flow-closeup.png) |
| Heated nozzle, local lighting and gas | [Night](images/rocket/flow-night.png) |
| Camera yaw +18 degrees / pitch -10 degrees | [Off axis](images/rocket/flow-off-axis.png) |
| Valve closed, residual flow and smoke | [Shutdown](images/rocket/flow-shutdown.png) |
| Negligible flow, persistent hot wall | [Cooling](images/rocket/flow-cooled.png) |
| Water-rich, soot-free profile | [Hydrolox](images/rocket/flow-hydrolox.png) |
| Denser soot-rich profile | [Solid](images/rocket/flow-solid.png) |
| Same density field with shaders disabled | [Vanilla](images/rocket/flow-vanilla.png) |
| Shader re-enable and F3+T-equivalent reload | [Restored](images/rocket/flow-reloaded.png) |

Visual inspection confirms the nozzle attachment, camera rotation, blue-white
compression structure, moving density supports, surface illumination and residual
wall emission. The second downstream compression cell is dimmer as mixing cools the
jet. This acceptance does not establish a measured photometric match to a real engine.

## Verified Behavior

- Commands persist/synchronize the requested mass flow and selected engine profile.
- Actual mass flow approaches the request with a valve/chamber response time.
- Exit velocity, temperature, density, pressure, Mach, thrust and exhaust power
  follow the profile/nozzle model together; nominal kerolox flow is about 1,592 kg/s
  for this large 2.07 m exit diameter. These are idealized calibration values.
- The metal wall stays hot after the jet stops; old smoke keeps moving and expires.
- All sources share a half-resolution volume pass with occupied-interval sampling.
- Dedicated history uses prior camera/nozzle transforms, rejection and short clamping.
- Real block occupancy shadows local jet illumination; captured PBR material data
  controls diffuse/specular surface response. Sun/moon shadows shade the gas.
- HDR volume output reaches the original pack bloom, exposure and tone mapping.
- Shader disable, re-enable and resource reload recover the selected backend.

## Bugs Found And Addressed

1. The old vanilla plume rotated around the crosshair because `AFTER_LEVEL` received
   Forge's separate local pose stack. The renderer now captures the actual world
   view at `AFTER_ENTITIES` and uses it for late integration.
2. Oculus requires four explicit floats in clear-color directives. The generator
   validates this instead of relying only on a GLSL compiler.
3. Off-screen sources previously consumed the visible engine cap. Conservative
   plume bounds now reject them before upload slots are assigned.
4. The vanilla depth copy now matches the actual source depth/stencil format.
5. GLSL150 core requires `texture()`, not removed `texture3D()`, for the occupancy grid.
6. The occupancy upload now saves/resets/restores pixel unpack stride/offset/image
   state and PBO binding, which Minecraft texture uploads can leave non-default.
7. The 3D sampler receives a distinct unit even with zero sources or a disabled
   bridge. Allocation inspects all active sampler types and array elements. Oculus's
   cached sampler-binding method keeps its state consistent with the actual GL bind.
8. The occupancy region follows the first uploaded source, rather than an inactive
   or culled source that happened to sort first.
9. Reload clearing is marshalled to the client thread. One earlier run observed an
   empty volume after reload without enough CPU diagnostics to prove its historical
   cause; later one-engine runs include those diagnostics and verify recovery.

## Physical And Visual Boundaries

This is a reduced-order static-stand simulation, not compressible CFD or chemistry.
Mass continuity, gas closure, momentum/pressure thrust and lumped thermal response
are implemented. The transitional nozzle branch, mixing, eddy motion, optical
coefficients, condensation and shock geometry are calibrated approximations.
Condensation does not solve humidity/saturation. Three spectral Planck samples plus
a separate chemical tint are not a spectroscopic engine model.

Smoke carries approximate packet mass, temperature and velocity, with finite lifetime
and a bounded count. No pressure-projection fluid grid or multiple-scattering GI is
claimed. Local shadowing uses conservative half-block collision occupancy in a
32-block region; arbitrary entity mesh shadows and glass transmission are absent.
Refraction is limited to visible screen color and compatible scene depth.

The runtime state is client-side visual simulation driven by synchronized server
settings. The stationary stand does not accelerate or consume fuel. Moving missiles,
long continuous wakes, all-dimension runtime acceptance and generic shader-pack
adapters are outside this scene. Instrumentation counts are not a performance
benchmark, and no battlefield frame-rate claim is made.

Original shader ZIP SHA-256 remains
`66061B3C5B4843E31BC9A7562A7AC697A51BB77C73DEFC7071F996B783EFACCE`.
Only owned additions and exact-source patch tools belong to the tracked changes.

## Reproduce

Create a new disposable world copy, stage/enable the original model resource pack,
generate/select the local shader fork, then run:

```powershell
.\gradlew.bat runClient -PwithOculus -PwithoutOptionalMods `
  -PclientRocketSmoke='<disposable world folder>' --console=plain
```

The harness intentionally modifies that disposable world and leaves the client open.
Commands and data format: [Rocket exhaust](ROCKET_EXHAUST.md).
Equations: [Flow model](ROCKET_FLOW_MODEL.md).
Pass/history layout: [Temporal rendering](ROCKET_TEMPORAL.md).

## Earlier Baseline

The earlier six-capture, 169-test minimum demo is superseded by this flow-based
acceptance. Its original five images remain in `images/rocket/pbr-*.png`,
`stopped.png` and `vanilla-off-axis.png` as historical attachment checks; they do
not demonstrate the current transported smoke, thermal wall or dedicated history.
