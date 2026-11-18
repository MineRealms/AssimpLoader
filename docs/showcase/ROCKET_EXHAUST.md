# Static Rocket Test Stand

Forge 1.20.1, Oculus 1.8.0 and the local Complementary Unbound r5.3 derivative.
This demonstration targets that specific pipeline. It does not attempt universal
shader-pack support. The original ZIP stays unchanged.

## Use

Enable the prepared `RocketEngine_Local_PBR` resource pack and the generated
`RocketFlow_Local` shader pack. The fallback engine is bundled; the original
high-detail asset is machine-local. See [asset staging](ROCKET_ENGINE_SHOWCASE.md).

```text
/assimpdemo rocket ~8 ~ ~8
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> flow 500
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> flow auto
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> stop
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> start
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> status
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> profile assimploader:hydrolox
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> profile assimploader:solid
/assimpexhaust <nozzleX> <nozzleY> <nozzleZ> profile assimploader:kerolox
```

The placement command prints the **nozzle anchor**, twelve blocks above the ground
point. Flow is in **kg/s**. `flow 0` closes the flow command, `flow auto` restores the
profile nominal flow. Start/stop retains the override. Settings persist in block
entity NBT and synchronize to clients. Profile maximum-flow ratio bounds overdrive.

Profiles live at `assets/<namespace>/exhaust_profiles/<name>.json` and reload with
F3+T. Three supplied profiles have different gas, soot/water, extinction and emission
inputs. They are illustrative calibrations, not measurements of named real engines.
The client config retains `rocketExhaust.enabled` and `rocketExhaust.shaderPackBridge`.

## State And Physics

`RocketEngineState` advances actual mass flow toward commanded mass flow with a
time response. `ExhaustFlowState` contains exit velocity, temperature, pressure,
density, Mach number, thrust, energy flux and nozzle-wall temperature in SI units.
See [flow-model equations and assumptions](ROCKET_FLOW_MODEL.md).

Mass flow alone cannot identify the gas temperature, mixture or nozzle geometry.
The profile supplies a design exit state and gas constants; nozzle radius supplies
area. The design state infers throat area and a nominal choked-flow calibration.
At positive flow the state enforces:

```text
mdot = rho_exit * velocity_exit * area_exit
pressure_exit = rho_exit * R * temperature_exit
thrust = mdot * velocity_exit + (pressure_exit - pressure_ambient) * area_exit
power = mdot * (cp * (temperature_exit - temperature_ambient) + velocity_exit^2 / 2)
```

Startup blends a subsonic branch into the design supersonic branch. This transition
is an explicit approximation; nozzle separation and transient shocks are not solved.
Pressure-matched or subsonic flow suppresses the shock pattern.

Nozzle heating integrates gas-side convection, areal heat capacity and Stefan-
Boltzmann radiation using 10 ms internal steps. Wall temperature can keep rising
briefly while residual gas empties after shutdown, then cool independently of flow.
The demo does not solve regenerative cooling, conduction, melting or ablation.

Ambient pressure/temperature, metres per block, gravity and wind are explicit
inputs. Optional altitude pressure is a bounded exponential model. Minecraft height
is not treated as measured Earth altitude. There is no fictitious atmosphere texture.
The engine is bolted to a stationary stand; calculated thrust is a reaction-load
diagnostic and does not move a vehicle. Runtime state is client visual simulation,
driven by synchronized server commands and client resource profiles.

## GPU Rendering

All visible engines share one half-resolution screen-space pass. CPU work is bounded
to 16 engine sources and 24 transported smoke packets. Batched uniform arrays contain
flow, thermal, material, current/previous transform and smoke data; no per-engine
raymarch draw or large ordinary particle cloud is submitted.

- Intersect finite jet cylinders and smoke spheres, then merge occupied ray intervals.
- Allocate 16/24/32/48 samples by projected size; omit empty gaps and terminate at low transmission.
- Sample a three-dimensional, advected, domain-warped value/cellular noise density field.
- Apply jet spreading, mixing/cooling, radial compression cones and axial shock knots.
- Integrate separate absorption, scattering and emissive source terms with Beer-Lambert transmission.
- Shade smoke with a Henyey-Greenstein phase term, pack celestial shadow maps and local jet light.
- Reproject scene-linear volume history using previous nozzle transforms and actual camera motion.
- Reject depth disocclusion, resize, cuts, source-order/profile/flow changes and ambiguous media.
- Clamp short history and mark volume pixels reactive to the pack's later scene TAA.
- Upsample with scene-depth rejection; refract only compatible background depth.
- Composite HDR before the original pack Bloom, exposure and tone mapping.

Smoke packets retain world-space position, velocity, temperature and gas mass.
Drag approaches configured wind, hot gas receives buoyancy from density contrast,
and Minecraft collision shapes redirect impact flow along the ground. Packet radius
grows and density falls with volume. Shutdown stops new packets while old gas
continues to move and cool for a bounded lifetime. The packets are overlapping
density supports evaluated in the volume shader, not billboard sprites.
The packet mass is normalized against the Gaussian support volume; turbulent
masking and packet expiry remain approximations. Extinction coefficients are
converted from inverse metres to inverse blocks before ray integration.

Original motion blur is preserved: the volume uses the source pack's unused
`composite1` slot. [Temporal target layout and rejection details](ROCKET_TEMPORAL.md).

## Material And Lighting Integration

The private fork captures linear base color/roughness in full-resolution target12,
and mapped world normal/metalness in target13, from terrain/block/entity/hand
G-buffer programs. Local jet illumination evaluates diffuse plus GGX/Schlick BRDF
using these materials, with three samples along the luminous jet.

Local-light visibility uses a 64-cubed, half-block occupancy texture derived from
actual loaded block collision boxes in a 32-block region around the closest engine.
It is rebuilt when the local block snapshot changes, checked at most once per second.
The same occupancy query shadows direct light scattered by gas. Pack shadow maps
supply external sun/moon visibility. No Minecraft block-light levels are rewritten.

Visible metal around the bell receives temperature-driven surface emission. Gas
color combines a three-band Planck approximation for particulate continuum with a
separate configured chemiluminescence tint. Kelvin does not uniquely determine blue
gas emission; these RGB/exposure coefficients remain visual calibration.

## Limits

This is a reduced-order renderer and transport model, not CFD or chemical equilibrium.
Spreading, eddy speed, soot optics, condensation and shock geometry remain calibrated
closures. There is no humidity/saturation solver, multiple-scattering GI, fuel tank,
flying projectile dynamics or moving-missile wake acceptance scene in this scope.

The voxel shadow region covers the closest static engine; it does not include
arbitrary moving entity meshes, model self-shadowing or transmission through glass.
Half-block conservative occupancy and finite shadow steps can over-shadow thin
geometry. Screen-space refraction cannot recover hidden scene color. Mixed
transparent depths and independently moving overlapping media remain approximate;
ambiguous volume history is rejected rather than retained.

With shaders disabled, a standalone half-resolution backend uses the same gas field
and a display curve. It does not reproduce this fork's HDR/material lighting system.
General adapters, battlefield clustering and flying vehicles are outside this demo.

## Build And Research

```powershell
.\gradlew.bat build -PwithOculus -PwithoutOptionalMods
.\tools\New-RocketShaderFork.ps1 -Destination .\run\shaderpacks\RocketFlow_Local
.\tools\Test-RocketShaders.ps1
.\gradlew.bat runClient -PwithOculus -PwithoutOptionalMods -PclientRocketSmoke=Rocket_Flow_Test
```

The patcher requires a new destination and the exact inspected source ZIP. The
opt-in runtime harness expects an existing disposable world copy and leaves the
client open. [Runtime evidence](../history/ROCKET_VALIDATION_2026-11-14.md) and
[Oculus source/pass research](../history/ROCKET_SHADER_RESEARCH.md) distinguish verified paths.

Primary references: [NASA rocket thrust/nozzle equations](https://www.grc.nasa.gov/www/k-12/airplane/rktthsum.html),
[PBRT participating-media phase functions](https://pbr-book.org/4ed/Volume_Scattering/Phase_Functions),
[NVIDIA volume rendering](https://developer.nvidia.com/gpugems/gpugems/part-vi-beyond-triangles/chapter-39-volume-rendering-techniques).
