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

Active morphs use the vanilla-compatible CPU drawing path, including meshes
without bones. GPU skinning and cached bind VBO drawing remain available for
inactive morphs. CPU results are cached per completed pose, and culling bounds
are recalculated from morphed positions and the same bone influence boxes used
for skinning. A future GPU implementation can replace deformation without
changing per-instance weights or snapshot ownership.

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

Assimp reference: [attachment stream contract](https://github.com/assimp/assimp/blob/v5.2.5/include/assimp/mesh.h)
and [glTF delta conversion](https://github.com/assimp/assimp/blob/v5.2.5/code/AssetLib/glTF2/glTF2Importer.cpp).
