# Static Rocket Stand Temporal Rendering

This implementation targets the inspected Complementary Unbound r5.3 ZIP with
Oculus 1.8.0. The shader-pack source archive stays unchanged. It does not depend
on the pack's ordinary scene TAA to accumulate exhaust.

## Pass Lifetime

| Stage | Read | Write | Size | Purpose |
| --- | --- | --- | --- | --- |
| Existing `composite` | Existing scene targets | Existing targets | Full | Scene linear lighting/fog |
| New `composite1` | Previous `8/9/10`, scene depth, source uniforms | Current `8/9/10` | Half | Adaptive volume integration and temporal resolve |
| Existing `composite2` | Scene `0`, depth | Scene `0` | Full | Original optional motion blur, preserved |
| Modified `composite3` | Scene `0`, current `8/9`, PBR `12/13` | Scene `0` | Full | Depth-aware volume upsample, refraction and local lighting |
| Existing `composite4/5` | HDR scene, bloom atlas | Existing targets | Full | Bloom, exposure and tone mapping |
| Modified `composite6` | Scene, scene TAA history, current `10` | Existing scene targets | Full | Existing TAA with exhaust reactive rejection |
| Oculus final history copy | Alternate `8/9/10` | Main `8/9/10` | Half | Preserve resolved exhaust for the next frame |

The unused `composite1` slot is verified absent in all three source dimensions.
The patcher creates six new wrappers; no original stages need renumbering.
The existing template filename `tools/rocket-shader/composite2.glsl` is retained,
but it is installed as `program/assimploader_exhaust.glsl` in the generated fork.
Original motion-blur sources, settings and enable gates are preserved. Scene
motion blur has no exhaust velocity vectors; the stand's exhaust is composited
after that optional effect.

Oculus `CompositeRenderer` creates the output attachment opposite each program's
sampler side and flips that target for subsequent passes. `FinalPassRenderer`
copies flipped non-cleared targets back to their main texture at frame end. Thus
one half-resolution pass can sample last frame and write this frame without
framebuffer feedback. Writing full-resolution scene `0` in this framebuffer would
be invalid; upsampling remains a separate full-resolution pass.

## History Targets

All three use `RGBA16F`, actual `size.buffer=0.5 0.5` and `Clear=false`.

| Target | Channels |
| --- | --- |
| `8` | RGB scene-linear HDR radiance, A Beer-Lambert transmittance |
| `9` | XY refraction offset, Z scene ray distance, W radiance/extinction-weighted volume distance |
| `10` | X source slot plus one or a reactive marker, Y accepted history frames, ZW encoded dimensions |

Dimension encoding is `2^(floor(n/1024)-8) * (1 + (n mod 1024)/1024)`.
Every integer dimension from 1 through 16384 has an exact, unique half-float
representation under this encoding. Plain half-float integer storage would round
odd dimensions above 2048, which could reject valid history or miss a resize.
Initial allocation/resize still clears targets. Source slot 17 marks a visible
volume without a valid engine anchor. Negative source markers keep scene TAA
reactive for two frames after a pixel becomes empty.

## Uniform And Core Contract

Root's Java integration supplies the following current/previous-frame data:

```glsl
uniform vec4 assimpExhaustPreviousOriginRadius[16];
uniform vec4 assimpExhaustPreviousAxisLength[16];
uniform float assimpExhaustDeltaTime;
uniform int assimpExhaustHistoryValid;
uniform vec3 assimpExhaustPreviousCameraDelta;
```

Previous origins are relative to the previous actual camera. Camera delta is
current actual camera position minus previous actual camera position, calculated
in Java double precision before conversion. `pCurrentRelative + cameraDelta`
therefore gives a stationary world's previous-camera-relative point. The pack's
shifted `cameraPosition` must not be substituted. Previous uniforms can be declared
by the shared core behind `ASSIMP_EXHAUST_HISTORY_UNIFORMS`; otherwise the temporal
helper declares them under that guard.

`assimpExhaustHistoryValid=0` must be supplied on the first frame, reload/world
change, camera cut, source ordering/identity/topology change, profile/state change
and discontinuous flow change. Slot identity is valid only while this flag is true.
The same validity value must be used by every program during a rendered frame.

The local pass defines a mutable `int assimpExhaustStepBudget=48` and the macro
`ASSIMP_EXHAUST_STEP_BUDGET` before including root's environment and shared volume
helpers. The shared integral uses the macro for its total occupied-interval sample
allocation; absent the macro, other backends keep their constant default. The core
also provides `assimpExhaustDominantEmitter`, `assimpExhaustMeanDistance` and
`assimpExhaustHeatVector`. A negative dominant emitter denotes puff-only/nozzleless
history and must not be transformed against an arbitrary engine.

`assimpExhaustPuffCount` is included in the scene reactive reset and sampling choice.
Puff-only visible pixels still render current radiance, but reject temporal reuse.
The environment include is installed before the core so volume scattering can
sample the real pack sun/moon shadow data. Root's material patcher owns targets
12/13; the temporal pass reserves only 8/9/10.

## Reprojection And Rejection

The current weighted volume point is converted into nozzle-local axial/radial
coordinates. Previous radius/length and the minimal current-to-previous axis
rotation reconstruct its previous position. Previous view/projection matrices
produce the history UV. Current pack TAA jitter is removed during reconstruction;
previous jitter is restored during projection. The representative source comes
from the integral; multiple intersecting engine intervals reject history.

Four neighboring history texels are checked independently. Matching source slot,
dimensions, weighted volume distance and unoccluded previous scene distance are
required. Picking compatible candidates before reading color avoids interpolated
source identities and background bleeding. A separate background-surface
reprojection compares previous scene depth to reject disocclusion.

Additional rejection covers empty current coverage, invalid or uninitialized
history, frame gaps over 100 ms, off-screen positions, motion over 24 half-resolution
pixels, axis/radius/length jumps, more than fourfold luminance changes and large
transmittance changes. Empty pixels return zero radiance and unit transmittance
immediately; opaque volume does not divide by transmittance. Invalid floating-point
samples are neutralized before writing the half-float target.

Accepted history uses at most 16 frames and a 55 ms response, attenuated by screen
motion and luminance changes. Quad derivatives bound the local current RGB and
transmittance neighborhood without additional raymarches or reading the current
output attachment. History is clipped to these bounds before blending. Heat
distortion keeps at most 35 percent historical contribution. Animated density
therefore keeps a short response instead of freezing turbulent detail.

The history contains unexposed scene-linear radiance. Pack exposure, bloom and
tone mapping remain downstream, so no exposure ratio belongs in this blend.
The pack's own scene TAA writes current color on visible/just-extinguished exhaust
pixels, and resets for an active exhaust history discontinuity. It must not apply
a second, longer flame history after the volume's rejection decision.

## Adaptive Cost And Limits

Projected conservative emitter size selects 16/24/32/48 total samples over the
occupied interval union. Overlapping engine intervals use 48; puff presence sets
a minimum of 32. Jitter changes every rendered frame to sample between previous
steps. Density, emission, mass flow and extinction are unaffected by sample LOD.

This is designed for the static test stand. It has no per-voxel velocity buffer,
multi-layer transparent depth or moving-projectile trail reconstruction. One depth
moment cannot represent independently moving overlapping media, so it rejects
those histories. Scene depth is half-resolution and tolerances are conservative;
thin foreground geometry and glass still need visual acceptance in the real client.
This is not full multiple-scattering GI or CFD.

## Verification

- PowerShell parser accepts the patcher.
- Installed Android SDK `glslangValidator` accepts the temporal helper as GLSL130
  with pack TAA enabled and its independent scene-reactive branch.
- Expanded local volume shaders, including the latest shared core/environment,
  compile as GLSL130 in all three dimensions, vertex and fragment (six variants).
- All 1..16384 dimension encodings were checked for unique, exact float16 storage.
- Real Oculus client acceptance completed at 20:35:23 on 2026-11-14. After resource
  reload, GPU target10 contained 22,935 pixels accepting multiple history frames,
  with a maximum of 16 frames; target8 retained finite HDR radiance. All11 captures
  checked GL_NO_ERROR. See [runtime evidence](../history/ROCKET_VALIDATION_2026-11-14.md).
- The subsequently synchronized 2.5-radius LOD bound and out-of-engine-interval
  history rejection pass syntax validation but were not rerun in the client at
  the user's request. The runtime figures above describe the preceding version.
