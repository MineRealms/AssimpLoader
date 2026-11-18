# Blender FBX PBR Import Research

Date: 2026-11-13. Source inspection only; no production code or runtime configuration changed.

> Historical snapshot before implementation. The missing-slot and dynamic-loader limitations below have since been addressed. Use the current [asset authoring contract](../guides/PBR_ASSET_SPEC.md), [PBR usage](../guides/OCULUS_PBR_USAGE.md) and [runtime verification](PBR_VALIDATION_2026-11-13.md). The proposed material sidecar was not implemented.

## Checked Sources

- Installed Blender 5.3 exporter: `G:/Program Files/Blender Foundation/Blender 5.3/5.3/scripts/addons_core/io_scene_fbx/export_fbx_bin.py`, lines 1641-1651 and 2130-2151. [Upstream exporter](https://projects.blender.org/blender/blender/src/branch/main/scripts/addons_core/io_scene_fbx/export_fbx_bin.py).
- Assimp native revision used by this project's LWJGL 3.3.1 binding: [FBXConverter.cpp at a34688230d152345d04710e256224b71ce8ba8a1](https://github.com/assimp/assimp/blob/a34688230d152345d04710e256224b71ce8ba8a1/code/AssetLib/FBX/FBXConverter.cpp), lines 1987-2000 and 2155-2210.
- Current `model/AssimpMaterial.java`, `render/ModelTextureCache.java`, and `model-tests/scripts/build_test_assets.py`.

## Actual Slot Mapping

| Blender input | FBX exported connection | Bundled Assimp texture type | Current AssimpMaterial |
| --- | --- | --- | --- |
| Base Color image | DiffuseColor | DIFFUSE | Saved and rendered |
| Tangent normal image | NormalMap | NORMALS | Saved; not directly supplied to Oculus |
| Roughness image | Shininess and ShininessExponent | SHININESS (from ShininessExponent) | Not saved |
| Metallic image | ReflectionFactor | METALNESS | Not saved |
| Specular image | SpecularFactor | SPECULAR | Saved; not LabPBR-packed automatically |
| Emission color / strength image | EmissiveColor / EmissiveFactor | EMISSIVE | Not saved |

For scalar Blender roughness, the exporter writes `Shininess = 100 * (1 - roughness)^2`; scalar metallic is written as `ReflectionFactor`. Assimp stores these as `AI_MATKEY_SHININESS` and `AI_MATKEY_REFLECTIVITY`. The current Java snapshot reads neither. The inverse roughness mapping is exporter-specific, not a universal FBX conversion rule.

The roughness *image* is linked as the original image; the scalar conversion does not mean every roughness image pixel has been converted to glossiness. Treating every imported SHININESS image as Blender roughness would be wrong for other exporters. A material sidecar declaring the source convention avoids this ambiguity.

## Consequences for This Project

1. Current FBX import preserves albedo and normal references but drops roughness, metallic and emissive slots/scalars before rendering. Reading extra slots is a prerequisite for automatic conversion.
2. `ModelTextureCache` uses `DynamicTexture` for embedded and filesystem images, and `SimpleTexture` for existing ResourceManager images. The Oculus registry requires a custom loader for the former; the latter can use adjacent `_n` / `_s` resources.
3. The six showcase assets contain bitmap albedo atlases and Principled scalar properties. They do not contain baked normal/roughness/metallic image maps, so their successful visual smoke test does not validate PBR.
4. Blender procedural node graphs need baking to UV-aligned image maps. FBX does not serialize the Principled shader graph into a shader Minecraft can execute.
5. Per-material PBR requires texture identities that include the material binding: two materials can share one albedo image but have different normal maps or roughness. A cache keyed only by albedo path cannot represent both Oculus holders.
6. LabPBR packing and source PBR import are separate stages. Directly renaming a roughness or glTF ORM image to `_s.png` is incorrect. Follow the selected shader pack's standard and [LabPBR specification](https://shaderlabs.org/wiki/LabPBR_Material_Standard).

## Practical Asset Contract

For the smallest initial integration, export FBX geometry/UVs and external PNG albedo, then bake/package matching LabPBR `name_n.png` and `name_s.png` as ResourceManager resources. Keep every map in the same UV layout. This can use Oculus' native resource loader without inferring ambiguous FBX material semantics.

For self-contained FBX/GLB support, retain source material slots/factors, convert them to an explicit target texture standard, and provide an AssimpLoader-owned albedo texture class plus a registered Oculus PBR loader. Pixel conversion can run off-thread; upload, binding and release must follow the existing render/resource lifecycle. This is feasible work, not current implemented support.
