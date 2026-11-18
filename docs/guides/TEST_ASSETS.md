# Blender / AssimpLoader Test Assets

All asset paths below are relative to [`model-tests/`](../../model-tests) in the repository root. Documentation is maintained under `docs/`.

## PBR Assets

`portal/` contains an original sci-fi portal: 77 source mesh parts, 3,266 triangles,
8 materials, a bottom-center root pivot, and a 3.48 x 3.90 x 1.26 block footprint.
The FBX and GLB embed real 512 x 512 albedo, OpenGL normal, roughness and metallic
images. Materials include titanium, polished steel, copper, ceramic, rubber,
thin translucent glass, light strips and an energy membrane. The glass windows
have visible coil geometry behind them. Blender transmission is retained for
editing; AssimpLoader uses alpha blending and PBR highlights, without physical refraction.

Editable source: `portal/portal_pbr.blend`; preview: `portal/portal_pbr_preview.png`;
part/material list: `portal/portal_manifest.json`. Geometry and generated textures
are original CC0 assets. No external image-generation API was used.
Rebuild with Blender using `scripts/build_portal_pbr.py -- --output <directory>`.
The script derives project paths from its own location and preserves other scenes.

`pbr/` contains the smaller three-material probe sharing a base-color image;
`scripts/build_pbr_probe.py` rebuilds it. The existing CIWS/knife atlas assets below
remain useful for geometry, color and scalar-material checks.

See the [asset authoring and FBX contract](PBR_ASSET_SPEC.md),
[PBR usage](OCULUS_PBR_USAGE.md) and
[world verification](../history/PBR_VALIDATION_2026-11-13.md) for actual game results.

Generated and checked on 2026-11-13 with Blender 5.3.0. These are visual game assets, with no firing, damage or other weapon behavior.

## Files

All assets have both `fbx/<name>.fbx` and self-contained `glb/<name>.glb` versions.

| Name | Triangles | Mesh objects | UV | Material / texture |
| --- | ---: | ---: | --- | --- |
| `ciws_material` | 2,572 | 7 | None | Original CIWS multi-material colors; no bitmap |
| `ciws_textured` | 2,572 | 7 | Per face | One material and one embedded 128x128 PNG atlas |
| `knife_material` | 162 | 1 | None | Steel blade, beveled edge, guard, ribbed grip, teal pins |
| `knife_textured` | 162 | 1 | Per face | One material and one embedded 128x128 PNG atlas |
| `probe_no_uv` | 48 | 4 | None | Asymmetric red/green/blue/yellow block for axes and missing-UV tests |
| `probe_textured` | 48 | 4 | Per face | Same geometry with one embedded 128x128 PNG atlas |

Textures are also available separately under `textures/`:

- `ciws_palette_128.png`
- `knife_palette_128.png`
- `probe_palette_128.png`

The image atlases are actual bitmap base-color textures, not procedural-only Blender shaders. Colors and a restrained pixel pattern are painted into the atlas; they are not photorealistic PBR bakes. No normal/roughness/metalness image maps are required.

Editable copy: `blender/assimp_test_assets.blend`. The original CIWS scene is preserved in that file; variants live in collection `AssimpLoader | Test Assets`. Use this copy as the source for reexporting existing variants; the export script derives its output path from its location in this repository.

## Units and Axes

- One model unit is one Minecraft block.
- Asset local coordinates use **Y up**, **+Z forward**, **+X right**.
- The Blender display root has a +90 degree X rotation, mapping the asset's Y up to Blender's Z up.
- FBX is exported with Y up and -Z forward using Blender's standard space conversion, at scale 1 with unit conversion enabled. Do not apply an extra 90 degree model rotation without checking the imported scene first.
- GLB uses its standard Y-up coordinates. It is supplied to compare formats and avoid side-file IO dependencies.
- Every exported root is at the origin. The spaced-out positions in the editable Blender gallery are not baked into the files.

Neutral CIWS dimensions are approximately X 1.863, Y 1.675 high, Z 2.863 long. Knife dimensions are X 0.35, Y 1.73 high, Z 0.164 thick. Knife origin is at the grip heel; its blade points along +Y.

## CIWS Pivots and Hierarchy

Material variant prefix: `CIWS_Material`. Textured variant prefix: `CIWS_Textured`.

```text
<prefix>_ROOT                        base floor center (0, 0, 0)
  <prefix>_00_FIXED_BASE            stationary base
  <prefix>_Yaw                      rotate LOCAL Y
    <prefix>_01_TURNTABLE
    <prefix>_02_FORK
    <prefix>_03_AMMO_AND_DRIVE
    <prefix>_Pitch                  rotate LOCAL X
      <prefix>_04_GUN_HOUSING
      <prefix>_05_OPTICAL_POD
      <prefix>_Rotor                rotate LOCAL Z
        <prefix>_06_SIX_BARREL_ROTOR
```

| Pivot | Position relative to asset root | Parent-local position |
| --- | --- | --- |
| Yaw | `(0, 0.38, 0)` | `(0, 0.38, 0)` relative to root |
| Pitch | `(0, 1.32, 0.02)` | `(0, 0.94, 0.02)` relative to yaw |
| Rotor | `(0, 1.32, 0.33)` | `(0, 0, 0.31)` relative to pitch |

The shared pitch origin passes through the two trunnions. The rotor inherits both yaw and pitch. Mesh origins are at their owning joint and their vertices are in that joint's local coordinates. The radar is intentionally omitted.

These exports retain the object hierarchy and pivots. They contain no armature and no baked animation clip; a display controller may rotate the named object nodes. Use `DYNAMIC` import to retain joints. `STATIC` import intentionally pretransforms the geometry and is suitable for a fixed showcase.

## Verification

- FBX 7400 files parsed with Blender's FBX parser: 11 / 2 / 5 model nodes for CIWS / knife / probe.
- Textured FBX files contain embedded image payloads (3,041 / 3,151 / 2,913 bytes); untextured files contain no Video image payload.
- GLB JSON inspected: only the intended hierarchy is present, with one embedded image for textured variants and zero for untextured variants.
- The six geometry/atlas exports exclude studio floors, cameras, lights, portals and default cubes. The separate `portal/` export contains only the intended portal hierarchy.
- Triangle counts above come from Blender loop triangulation; an importer may split meshes by material while preserving the triangle count.
- Actual AssimpLoader runtime display is handled by the separate world-showcase integration; these checks do not certify the Minecraft renderer.

## Rebuilding

`scripts/build_test_assets.py` builds a fresh test collection from a CIWS source scene and intentionally refuses to overwrite an existing test collection. For the supplied editable copy, use `scripts/export_existing_assets.py` to reexport its existing named variants.

Run scripts through Blender's Python environment. They depend on the CIWS source hierarchy and use local output paths near the top of the scripts. Both GLB selection and active-scene filtering are enabled to exclude objects from other open Blender scenes.
