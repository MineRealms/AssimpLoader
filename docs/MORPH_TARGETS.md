# Morph Targets

Morph geometry and sparse weight animation keys are copied from Assimp before
the native scene is released. `AssimpMesh.getMorphTargets()` exposes immutable
targets; position and normal streams use deltas from the imported bind mesh.
Assimp attachment streams contain replacements, including glTF targets whose
file data originally contains deltas.

`PoseSnapshot.capture(frame, scene, locals, weightsByMesh)` copies each weight
array. Missing meshes keep their imported default weights. Counts must match
the mesh's target count and weights must be finite. Snapshots expose defensive
weight copies through `getMorphWeights(mesh)`.

Relative morphs retain signed weights and permit extrapolation. Normalized and
vertex blend morphs clamp negative weights to zero and normalize the target sum
when it exceeds one; otherwise the bind mesh supplies the remaining weight.
Positions and normals start from bind data on every evaluation, then skinning
applies the current palette. Normal vectors are normalized after morphing.

`AssimpAnimation.getMorphChannels()` contains sparse Assimp morph animation
channels. `sample(ticks, targetCount)` returns dense linearly interpolated
weights, holding the first or last key outside the key range. Mesh names may
refer to mesh-bearing node names; playback resolves those names to meshes.

Opaque skinned meshes can morph in the existing vertex shader before skinning.
Shared immutable position/normal deltas use an RGBA32F texture buffer on unit 4;
only normalized per-instance weights are uploaded per draw. The limit is 32 targets,
128 MiB per mesh, and the driver's texture-buffer capacity. Allocation and texture
attachment are checked before caching. Resource reload deletes the delta buffers;
draw completion restores all changed texture and buffer bindings.

Meshes without bones, oversized target data, transparent scenes, material parameter
animation and custom-shader-incompatible paths retain vanilla-compatible CPU
drawing. GPU failures use the existing renderer disable/fallback mechanism. CPU
results are cached per completed pose. Culling bounds use CPU morph positions and
the same bone influence boxes as skinning, independently of the selected renderer.
No Compute dispatch or GPU readback is required for rendering or model queries.

The current morph rendering contract covers positions and normals. Animated
UVs, colors, and tangent streams are not exposed. The existing renderer does
not consume morph tangents. Assimp's morph-key API exposes weight keys but does
not preserve source interpolation metadata, so STEP and cubic source curves
cannot be reconstructed by this layer.

Validation: `MorphTargetTest` imports an actual embedded-buffer glTF through
Assimp, checks default weights and animation keys after native release,
independent captures, the zero/one/zero round trip, bounds, malformed counts,
and relative versus normalized weighting. Its fixture is procedural test data,
not a custom importer.

`MorphTargetTBOTest` checks actual imported delta packing, capacity and shared
weight semantics without creating a GL context. `AnimationSmokeTest` additionally
uses the Blender GLB with a mixed-weight skinned shape key, rejects CPU fallback
in the accelerated column, compares silhouettes, checks unit-4 binding restoration
and exercises resource recreation. Results and limits are recorded in the
[implementation report](EXPANSION_IMPLEMENTATION_2026-11-16.md).

Assimp reference: [attachment stream contract](https://github.com/assimp/assimp/blob/v5.2.5/include/assimp/mesh.h)
and [glTF delta conversion](https://github.com/assimp/assimp/blob/v5.2.5/code/AssetLib/glTF2/glTF2Importer.cpp).
