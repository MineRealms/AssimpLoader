# Rocket Engine Showcase

## Placement

The dedicated engine block is `assimploader:showcase_rocket_engine`; the model cache
name is `rocket_engine`. It uses the existing Showcase renderer, persistent geometry
cache, item renderer, model visibility settings and resource reload lifecycle.

```text
/give @s assimploader:showcase_rocket_engine
/assimpdemo rocket
/assimpdemo rocket ~8 ~ ~8
```

The last command takes the **test stand ground origin**, not the nozzle origin. It
checks an 11 x 20 x 11 loaded, replaceable volume before changing blocks, builds a
9 x 9 ground pad and rear frame, and places the engine anchor twelve blocks above
the ground origin. The nozzle center is at `(anchor.x+0.5, anchor.y, anchor.z+0.5)`;
exhaust points straight down. Existing `/assimpdemo` still creates its eight small
exhibits, without adding a tall engine to the compact gallery.

The 1-block anchor is a placement/collision handle; it is not a physically simulated
mount or a full-engine collision body. Use a separate test area. A placed engine is
a static visual demonstration, not a flying rocket.

## Bundled And Local Assets

A clean checkout includes an original 4,976-triangle fallback engine: a hollow
steel bell, chamber, cooling bands and feed pipes. It has UV0 and scalar metal and
roughness materials. It is separate authored geometry, not a simplified copy of the
third-party model. Its source generator is `tools/prepare_rocket_showcase.py`.

The prepared third-party asset is optional and kept under the ignored local game
resource packs. The source archive did not include redistribution terms; neither
the original archive nor the 91 MB derived asset belongs in a source commit or
release JAR without permission.

Stage the prepared Blender file into a local pack:

```powershell
./tools/stage-rocket-showcase.ps1 `
  -BlenderPath '<Blender installation>/blender.exe' `
  -SourceBlend '<prepared asset>/rocket_engine_assimploader.blend' `
  -GameDirectory './run'
```

The input is the prepared `.blend` delivered with the original export. Run this
from the repository root. The script starts a separate background Blender process,
never saves its input, scales a runtime copy, centers it on the measured nozzle lip,
and exports only meshes and an identity root. No camera, preview light or CAD action
is exported. It checks that all 692,900 source triangles remain.

Enable `RocketEngine_Local_PBR` in Minecraft's resource-pack list and reload with
F3+T. The pack overrides exactly this model resource:

```text
run/resourcepacks/RocketEngine_Local_PBR/
  pack.mcmeta
  assets/assimploader/models/showcase/rocket_engine.fbx
  assets/assimploader/models/showcase/rocket_engine.manifest.json
```

The 24 embedded runtime textures contain six sets of base color, OpenGL tangent
normal, roughness and metallic maps. The loader converts these to its Oculus PBR
representation. No external absolute texture path is needed at runtime. The
original height maps stay in the editable asset; they are not physical geometry or
automatically imported displacement.

## Transform Contract

| Property | Bundled fallback | Local prepared engine |
| --- | --- | --- |
| Uniform source scale | 1 | 0.25, configurable at staging |
| Minecraft size X / Y height / Z | 2.149 / 4.600 / 2.149 | 2.637 / 4.816 / 2.421 |
| Nozzle center, model-local Y-up | `(0,0,0)` | `(0,0,0)` |
| Exhaust axis | `(0,-1,0)` | `(0,-1,0)` |
| Nozzle inner radius | 1.03425 blocks | 1.03425 blocks at default scale |
| Triangles | 4,976 | 692,900 |

The original prepared engine's Blender nozzle center is
`(-1.0669168, 0.6348529, 0)`, not the whole-engine bounding-box center. The lowest
motor lip has an inner ring radius near 4.137 original units. The staging transform
is `p_runtime = 0.25 * (p_source - nozzle_center)` in Blender Z-up, followed by the
standard FBX Y-up export and AssimpLoader coordinate conversion. No extra runtime
scale or translation should be applied beyond the existing block-center offset.

Changing `-Scale` also changes the nozzle radius and engine dimensions. Keep any
exhaust profile's radius and the stand clearance consistent with the generated
manifest. The fixed test stand is intended for the default scale.

## Verification Scope

The staging run checks the original triangle count and writes a geometry manifest.
`ShowcaseAssetsTest` covers the bundled resource chain, import, finite geometry and
fallback dimensions. Java compilation and game rendering are verified by the main
task; the asset-preparation script does not prove shader behavior or a frame rate.

See [PBR authoring](../guides/PBR_ASSET_SPEC.md), [large-model visibility](../guides/LARGE_MODELS.md) and
[Showcase placement](MODEL_SHOWCASE.md) for shared contracts.

Engine state, profile commands, volumetric rendering and physical-approximation
boundaries are documented in [Rocket exhaust](ROCKET_EXHAUST.md).
