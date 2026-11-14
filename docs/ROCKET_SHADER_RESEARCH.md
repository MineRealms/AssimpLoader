# Rocket Exhaust Shader Integration

## Scope And Status

This is a pinned local experiment for Oculus 1.8.0, Minecraft 1.20.1, and the inspected
`run/shaderpacks/ComplementaryUnbound_r5.3.zip`. The source archive SHA-256 is
`66061B3C5B4843E31BC9A7562A7AC697A51BB77C73DEFC7071F996B783EFACCE`.
The volume uses reduced-order gas transport with calibrated radiance, not a CFD solver.
The earlier demo compiled and displayed in the real client; see
[2026-11-14 validation](ROCKET_VALIDATION_2026-11-14.md) for screenshots, the
directive-parser correction and limitations of the verified scene.

Static verification used the installed Android SDK `glslangValidator`: the standalone
shared fragment compiled as GLSL 130 and 150; 24 expanded pack shader variants compiled
as GLSL 130 (three dimensions, world blur off/depth of field, both passes, both stages).
Pack comments were stripped before this syntax check to match preprocessing and avoid
the original ASCII-art comment's GLSL 130 line-continuation diagnostic. This does not
substitute for Oculus source transformation, driver compilation, or visual verification.

The first real Oculus load found a parser-specific failure that GLSL syntax validation
does not detect: `colortex9ClearColor = vec4(0.0)` is legal GLSL, but Oculus 1.8.0's
`DispatchingDirectiveHolder.java:219-235` requires four comma-separated literal floats
for clear-color directives and throws after reporting a short constructor. The owned
template uses `vec4(0.0, 0.0, 0.0, 0.0)`. All owned clear colors use four literal
components. The first experiment removed composite2 gates after invalid forced-enable
values produced unknown-option warnings. The current implementation uses the unused
composite1 slot instead, preserving original composite2 gates and motion-blur controls.
No forced-enable property is needed: existing programs are enabled by default
(`ShaderPack.java:233-238,264-268`). The patcher validates clear-color argument counts
before extracting another local fork.
The subsequent baseline client load confirmed that parser correction. The mass-flow
iteration now generates `RocketFlow_Local`, adds the dedicated volume history and
material buffers, and passes `tools/Test-RocketShaders.ps1` (12 expanded variants).
See the validation document for current driver/runtime results.

## Actual Pack Passes

Paths and line numbers below refer to files inside the original ZIP.

| Pass | Evidence | Local integration |
| --- | --- | --- |
| `program/composite.glsl` | Line 196 converts the scene to linear with `pow(color, vec3(2.2))`; lines 206-207 can apply bloom fog | Existing scene lighting/fog remains upstream |
| New `program/assimploader_exhaust.glsl` | Source ZIP has no world*/composite1 wrappers; Oculus reads composite1 between composite and composite2 | Half-resolution volume/history writes `colortex8/9/10` |
| `program/composite2.glsl` | Lines 74-103 read and write HDR `colortex0`; the program is enabled only by `MOTION_BLURRING` in `shaders.properties:95-97` | Original motion blur and option gate preserved |
| `program/composite3.glsl` | Lines 127-155 read `colortex0`, optionally blur/apply bloom fog, and write `colortex0` | Depth-aware upsample and volume composite immediately before the output |
| `program/composite4.glsl` | Lines 23-41 build bloom from `colortex0`; lines 72-73 output the encoded bloom atlas to `colortex3` | Unchanged, receives exhaust HDR |
| `program/composite5.glsl` | Lines 140-152 apply bloom and then `DoBSLTonemap`; lines 31-36 define exposure, tone curve and display gamma | Unchanged |
| `program/composite6.glsl` | Lines 38-48 apply existing TAA, outputting `colortex3/2` | Reactive mask bypasses a second flame history; volume has dedicated history upstream |

`lib/pipelineSettings.glsl:2-9` describes all eight occupied buffers: HDR scene 0,
previous depth 1, TAA 2, shared intermediate/bloom/final 3, volumetric state 4,
normal/reflection state 5, material state 6, and cloud/temporal state 7. These format
declarations are inside a block comment but are deliberately parsed as pack directives
by the shader loader. A search of every GLSL/program/properties file finds targets 8
and 9 only as otherwise unused sampler declarations in `lib/uniforms.glsl:77-78`.
The fork declares `8/9/10` as persistent half-resolution `RGBA16F` targets. Their
allocation/resize is cleared; ordinary frame boundaries retain their contents.

`colortex8.rgb` stores integrated radiance; `colortex8.a` stores scalar transmission.
`colortex9.xy` stores heat distortion in normalized screen coordinates, `.z` stores
linear scene distance, and `.w` stores the emission/extinction weighted plume distance.
These depth statistics support spatial edge rejection and temporal reprojection.
Target10 adds source identity, history length and encoded target dimensions.

## Oculus Source Evidence

The checked source tree is `H:/MinecraftMods/Oculus`, commit
`b3b278134f719afe32ba8b6b5d3a93f052175afc`, with `mod_version=1.8.0`.
The actual pinned runtime JAR's `Program.use()` was independently checked with
`javap`; its binding/update order matches this source.

- `gl/program/Program.java:28-35`: `use()` binds the GL program, then updates Iris
  uniforms, samplers, and images. An optional Mixin injected at **TAIL** can upload
  the exhaust uniform arrays with that exact program bound, before the draw.
- `pipeline/CompositeRenderer.java:269-280`: framebuffer bind, `program.use()`, blend
  state, custom pack uniform push, fullscreen draw. Pack custom uniforms must not
  reuse the `assimpExhaust*` names, because their later push could overwrite them.
- `pipeline/CompositeRenderer.java:133-149`: the renderer creates output framebuffers
  and advances its own buffer flipper. The mod must not assume a fixed texture ID or
  alter either ping-pong attachment.
- `pipeline/CompositeRenderer.java:135-138`: every output target in a pass must have
  identical dimensions. Half-resolution composite1 therefore writes **only 8/9/10**;
  it cannot also write full-resolution target 0.
- `shaderpack/properties/ShaderProperties.java:257-265` parses exactly two values for
  `size.buffer.<name>`. `gl/texture/TextureScaleOverride.java:8-23` treats values with a
  decimal point as relative; `PackDirectives.java:290-313` resolves `colortex8/9`.
  Use `size.buffer.colortex8=0.5 0.5` and corresponding target9/10 properties.
- `gl/IrisLimits.java:11`: 16 color targets are supported by this version. Target 8/9
  does not mean 11 simultaneous draw attachments; this pass uses only three.
- `samplers/IrisSamplers.java:213-218` provides depth 0, pre-translucent depth 1,
  and pre-hand depth 2. `IrisRenderingPipeline.java:1035-1055` defines copy timing.
- `pipeline/IrisRenderingPipeline.java:1073-1076` runs composite passes before final.
- `targets/RenderTargets.java:123-125,199` owns target allocation and resizing;
  `CompositeRenderer.java:207-224` rebuilds framebuffers after size changes.
  `CompositeRenderer.java:428-451` destroys programs on reload.
- `api/v0/IrisApi.java` exposes state/configuration/text rendering and sun rotation,
  but no public custom volume pass, uniform provider, or dynamic mod buffer API.
  The existing AssimpLoader `OculusShaderInjector` is a dormant facade returning false.

The client-only bridge is pinned to Oculus 1.8.0. Uniform locations live on the Program
instance, and an active `assimpExhaustCount` marker skips unrelated programs. The
expanded packet contains twelve engine vec4[16] arrays and three smoke vec4[24]
arrays (4,224 bytes at full capacity), plus scalar history/camera values. Each array
uses a batched glUniform4fv upload. This is not one SSBO or one contiguous GL upload.
Program-local caches naturally expire on reload; integer GL IDs alone are not keys.
The local occupancy sampler selects a unit unused by the linked program's active
samplers, binds a separately owned 3D texture there, and restores the active unit.
This advanced branch targets the current GPU rather than promising minimum GL3.2
uniform limits across hardware. Iris still owns every pack render target and flip.

An SSBO would require GL 4.3 or an extension plus a reserved binding/ownership contract.
Oculus's `ShaderStorageBufferHolder` owns pack buffer objects; its existence is not a
public mod API. A dynamic custom sampler can be registered through an internal
`ProgramBuilder` hook, but also needs sampler-unit allocation and reload cleanup.
Small ordinary uniform arrays are the simpler GL 3.2-compatible choice here.

## Coordinates And Integral

Pack `lib/util/spaceConversion.glsl:4-14` reconstructs view positions from depth and
transforms them using `gbufferModelViewInverse`. Use current render-camera-relative,
world-aligned emitter positions, subtracting the camera in Java double precision before
conversion to float. `Oculus/uniforms/CameraUniforms.java:28,81-96` shifts the pack's
`cameraPosition` at large coordinates; do not subtract that shifted float from an
absolute Java emitter coordinate. Directions are normalized world directions.

The shared file has no pack-specific names. Its entry point returns RGB radiance and
transmission A. A conservative finite cylinder rejects rays first. Sorted hit intervals
are merged; 16-48 total samples are distributed across their occupied union with at least
one sample per interval, excluding empty gaps and avoiding far-plane step dilution.
At each sample every overlapping emitter contributes absorption, scattering and source
radiance, followed by a single front-to-back Beer-Lambert integration. This avoids
incorrect composition in emitter list order. Advected value-noise FBM perturbs analytic
radial/axial density, shock diamonds modulate emission, and a separate outer smoke field
adds extinction and approximate single scattering. Transmission terminates below 0.01.
The expanding core includes `(r0 / r(z))^2` dilution under an assumed constant mean
axial velocity. Additional axial decay is artist-calibrated entrainment/cooling, and
the outer smoke field remains an empirical visual component rather than conserved fuel.

## Exhaust Temporal History And Adaptive Sampling

The local fork has its own history, separate from the pack's scene TAA. `composite1`
reads the previous ping-pong sides of three half-resolution `RGBA16F` targets and
writes their current sides in one framebuffer:

| Target | Contents |
| --- | --- |
| `colortex8` | Scene-linear HDR radiance RGB; integrated transmittance A |
| `colortex9` | Heat distortion XY; scene ray distance Z; weighted plume distance W |
| `colortex10` | Source slot plus one X; accepted history frame count Y; encoded target size ZW |

All three use `Clear=false`. This is intentional persistence: Oculus 1.8.0
`FinalPassRenderer` copies flipped, non-cleared targets from their alternate side
to their main side after the final pass. `composite1` therefore samples last frame's
textures while writing distinct attachments. Initial allocation and resize still
clear the targets. The stored dimensions reject history from a different viewport.
Integer dimensions are encoded exactly into a half-float exponent/mantissa pair,
so widths above 2048 retain their low bit.
`composite3` reads the current `8/9` pair without changing its existing upsample API.

Reprojection anchors each pixel at its extinction/radiance-weighted volume depth.
The current nozzle-relative point is transformed using previous nozzle position,
axis, radius and length, then projected by `gbufferPreviousModelView` and
`gbufferPreviousProjection`. Current and previous pack TAA jitter are removed and
restored respectively while reconstructing/projecting positions. Background geometry is separately reprojected using
the true Java camera delta, avoiding the shifted pack camera coordinates. Four
nearest history candidates are checked for matching source slot, volume depth,
visibility and target dimensions before selecting radiance. Background depth mismatch
rejects disocclusion. Half-resolution scene distance introduces a conservative
0.15-block plus 1.5-percent depth tolerance; this is not layered transparent depth.

The CPU invalidates the shared `assimpExhaustHistoryValid` flag on world/reload,
first frame, camera cut, source ordering/topology/profile changes and flow/state
discontinuities. GPU rejection additionally covers empty current coverage, frame
intervals over 100 ms, off-screen reprojection, movement over 24 half-resolution
pixels, large emitter transform changes, invalid HDR values, a greater than fourfold
radiance change and large transmittance changes. No historical radiance is kept at
an empty pixel. Radius/length compensation supports small continuous changes;
overlapping source intervals use current samples because one depth moment cannot
represent several independently moving volumes.

Accepted samples use a 55 ms response, capped by accumulated frame count, with
motion and luminance-change attenuation. Current quad derivatives provide local
neighborhood bounds for RGB and transmittance before blending, without an extra raw
volume pass or sampling an attachment being written. This is short animated-volume accumulation,
not indefinite convergence of frozen turbulence. Sampling jitter rotates each frame.
The blend remains scene-linear, before exposure, bloom and tone mapping; there is no
history exposure ratio because this target never contains exposed scene color.

The pack's `composite6` TAA bypasses its own history on exhaust pixels and resets
to current color there. A two-frame marker clears pixels after extinction. An active
exhaust history reset also resets scene TAA for that frame. This prevents the later
scene filter from reintroducing the flame history already rejected by the volume
pass. Heat distortion uses at most 35 percent history weight.

Projected emitter size selects 16/24/32/48 total integration samples over the occupied
interval union. Multiple intersecting emitters retain 48 samples. `composite1`
sets the shared integral's mutable `assimpExhaustStepBudget`; the standalone
backend keeps the full 48-step default. This changes sampling work, not density,
optical coefficients or the physical flow state. No extra per-entity draw or Java
quality uniform is needed. This design targets the static test stand; it does not
implement per-voxel motion vectors, long-lived flight trails or a CFD velocity field.

## Local Fork Procedure And License

Run from the repository:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/New-RocketShaderFork.ps1
```

The patcher verifies the exact ZIP hash and expected include/output anchors, validates
every extraction path, refuses an existing destination, and copies to the ignored
`run/shaderpacks/ComplementaryRocket_Local` directory. It never edits the original ZIP
or selects a shader pack. A second iteration must choose a new `-Destination` directory.
It copies only this project's new shaders/templates into the extracted local copy.
No original third-party shader sources are tracked in Git. Original license and credits
are preserved byte for byte, and a local experiment notice names and links the author.

The bundled **Complementary License Agreement 1.5** excludes modification from normal
usage (`License.txt:9-13`). Redistribution of a modified pack is conditional on visible
credit/link, unchanged license, a qualifying new name, and noticeably different visuals
in common gameplay including daytime overworld (`51-69`). Other uses are reserved
(`73-80`). The default local demo name and rocket-only changes do not establish those
redistribution conditions. Do not publish the generated directory or original shader
sources as an AssimpLoader dependency. Obtain author permission for distribution or
ship only independently authored integration code and an authorized local patch workflow.
The bundled license is the relevant primary source for this exact archive; the
[current upstream license](https://github.com/ComplementaryDevelopment/ComplementaryReimagined/blob/main/License.txt)
is version-specific and must not replace the inspected 1.5 terms by assumption.

## Practical Limits

- Emission enters linear HDR target 0 before bloom and tone mapping. Surface light
  now uses actual base color, roughness, mapped normals and metalness in targets12/13,
  GGX/Schlick response and actual-block occupancy visibility. Occupancy is half-block,
  covers the nearest engine's 32-block region and omits moving meshes/transparent
  transmission. It is single-scattering/local direct light, not multiple-scattering GI.
- Heat distortion reads the distinct ping-pong scene source, clamps the offset and
  rejects large depth discontinuities. It cannot recover off-screen/hidden color,
  correctly refract layered transparent surfaces, or integrate multiple refractive layers.
- Depth 0 gives a conservative nearest-surface bound. Water/glass attenuation, sky
  depth, first-person hand depth and Distant Horizons need runtime visual inspection.
- Dedicated volume history includes camera/emitter reprojection, rejection and a
  reactive scene-TAA mask as described above. A single volume depth cannot recover
  multiple transparent layers; ambiguous overlapping media reject temporal history.
- One pass handles at most 16 emitters. Work scales with half-resolution pixel coverage,
  16-48 samples and candidate emitter count. CPU distance/frustum culling and the bounded
  emitter cap are required; dense overlapping plumes still cost substantially more.
- Original scene motion blur remains in `composite2`; the volume pass occupies the
  pinned pack's unused `composite1` slot. Exhaust itself has no scene motion vectors.
  The normal pack is recovered by selecting the untouched original ZIP.
