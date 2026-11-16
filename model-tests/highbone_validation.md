# 256-bone and Morph validation

This is an original acceptance asset, authored by the background Blender script.
The live Blender project is not modified. The 16 x 16 textured tile grid has one
mesh, 256 independently weighted bones, a two-second `Wave` clip, and a `Spread`
Morph target with a separate `MorphPulse` clip. All indices from 0 through 255
affect visible vertices. The GLB embeds its checker texture.

## Reproduction

```powershell
& 'G:\Program Files\Blender Foundation\Blender 5.3\blender.exe' --background --factory-startup --python model-tests/create_highbone_probe.py
.\gradlew.bat test --tests '*HighBoneAssetTest' --no-daemon --no-parallel
$env:JAVA_TOOL_OPTIONS='-Dhighbonetest.enabled=true'
.\gradlew.bat runClient -PwithoutOptionalMods --no-daemon --no-parallel
Remove-Item Env:JAVA_TOOL_OPTIONS
```

Do not run Gradle or another development client concurrently against this
checkout: compilation rewrites the class output used by an already running
client. The smoke runner is disabled unless `highbonetest.enabled=true`.

## Numerical Checks

`HighBoneAssetTest` checks imported weights, all 256 palette entries, embedded
texture, Morph channel sampling, four combined animation/Morph samples, all
256 tile centroids per sample, selected joint positions including B255, exact
deformed extrema, and conservative pose bounds. Blender evaluated geometry
provides the references, with a 0.2 mm comparison tolerance. Import produces
6,144 vertices from the 2,048 source vertices because of normal/UV seams.

## GPU Checks

`HighBoneSmokeTest` directly invokes `SkinnedMeshRenderer`, requires actual GPU
support for 256 matrices and Morph, and renders alongside the CPU implementation
into 512 x 512 targets. Uniform and CPU fallback cannot satisfy this runner.
The four stages are frozen at the same 0.65-second Wave sample:

1. Zero Morph.
2. Active Morph.
3. Morph restored to zero, matching the first mask exactly.
4. Active Morph after resource reload, matching the second GPU mask exactly.

Every stage waits at least 10 seconds and waits for the used material texture
before verification and screenshot capture. It checks CPU/GPU masks, GL errors,
TBO bindings on texture units 3 and 4, old managed-state disposal, new scene
identity, and retained snapshot immutability across reload. GPU timing uses one
warmed timestamp pair per stage with nonblocking result polling. Mean CPU/GPU
submission timings exclude the first 64 frames. Pixel readback happens outside
the timed submission work.

The palette upload is 16,384 bytes per draw. The single immutable Morph delta
buffer is 196,608 bytes. These figures describe this fixture; the timings do not
establish general gameplay FPS, large crowd performance, or shader-pack support.

## Verified Run: 2026-11-16

Unit result: two tests passed, no failures or errors.

Runtime log: `build/highbone-client.log`. Screenshot prefix:
`run/screenshots/assimp-highbone-1794830936198-`.

| Stage | CPU foreground pixels | CPU/GPU mask mismatch |
| --- | ---: | ---: |
| wave-zero | 90127 | 0 |
| wave-morph | 113314 | 0 |
| wave-restored | 90127 | 0 |
| wave-morph-reloaded | 113314 | 0 |

Active Morph changed 77,897 silhouette pixels. Exact zero restore and exact
post-reload active-mask equality passed. All four screenshots were captured and
the active/reloaded images were visually inspected. The log contains
`[HighBoneSmoke] SUCCESS run=1794830936198`; absence of the success marker means
a run did not finish even when Gradle itself exits successfully.

Observed mean CPU submission was 2.32-2.57 ms; mean GPU submission was 0.318-0.393
ms. The single warmed GPU timestamp intervals were 15.360-18.432 microseconds.
The screen also renders a CPU reference each frame, so these are diagnostics
for this fixture and driver, not an isolated throughput benchmark. This run
used the non-optional-mod development profile; Oculus/third-party shader packs
and integrated-world crowd loads were not validated here.
