# Rendering Integration Contract

Rendering owner is implementing:

- `render.ModelTextureCache.getTexture(AssimpScene scene, AssimpMesh mesh)` -> ResourceLocation, shared lazy per-scene/per-material texture resolution.
- `render.ModelTextureCache.getEmbeddedTexture(AssimpScene scene, int index)` -> ResourceLocation or null.
- `render.ModelTextureCache.clear()` -> release owned dynamic textures, render thread only.
- `render.SceneTransforms` -> cached scene bind node transforms/bounds; dynamic rendering traverses hierarchy rather than treating local vertex data as world coordinates.
- `render.AssimpBakedModel(AssimpScene, TextureAtlasSprite, ResourceLocation, ItemTransforms)` new constructor; existing three-arg constructor retained. Loading owner: pass `context.getTransforms()` in the bake call to honor resource JSON display transforms.
- `render.ClientRenderResources.clear()` -> release owned rendering caches and reset ClientAnimationManager; safe scheduling to render thread. Core reload hooks can invoke it from client event code without coupling common loader to client classes. Rendering owner will install client resource reload/logout/shutdown listeners.

LookDev may use its own preview-only GPU buffers and texture decoder initially, but should use the shared `ModelTextureCache` API above for one texture decode across preview/world/item. Source resource location is available via `AssimpScene.getSourceResource()` (loading owner), source filesystem via getSourcePath().

Parent owns skeleton/source weight extraction; rendering owner selects strongest four weights at upload and will not modify BoneHierarchyBuilder/AnimationPlayer/AnimationState.

Implementation status: APIs above now exist. `rendering.CachedMeshRenderer` caches immutable static/node mesh VAO/VBO/EBO on the render thread, uses Minecraft lightmap/overlay textures and restores its touched bindings. Shader packs and transparent meshes retain the vanilla batch path. Skinned meshes use the shared GPU skinning renderer or a palette-cached CPU fallback, preserving animation with shaders/GPU disabled. `CpuSkinning` cache weakly owns instance animation state and recomputes only when AnimationState publishes a new palette. Resource lifecycle clears all these caches.

ClientRenderResources installs reload, logout, shutdown event listeners and checks loader cache generation once per scene render. Loader should still clear the model registry at reload start; renderer cleanup does not remove loaded source models. AssimpBakedModel now receives ItemTransforms and uploads no embedded images during baking. LookDev should acquire its texture ID through Minecraft TextureManager for ModelTextureCache.getTexture(scene,mesh), and must not release those shared IDs.

Final rendering changes are ready for the parent's unified Gradle verification. No Gradle command was executed by this owner. Added tests cover imported CIWS DYNAMIC/STATIC world bounds, node normal matrices, item fit, resource texture paths, CPU skinning and strongest-four GPU influences. `git diff --check` passes (only existing CRLF conversion notices).

Both immediate GPU paths now bind Minecraft lightmap and overlay textures, apply fog/global color, and restore depth/cull/blend state, texture bindings, shader samplers, VAO/VBO/program. Bone TBO uses unit 3, leaving units 1/2 for overlay/lightmap. `AnimationState(String, AssimpScene)` and the parent's local node animation map are integrated; parent chains are applied exactly once. CPU skinning keeps animation active when shaders are enabled, GPU skinning is disabled, a palette exceeds capacity, or GPU setup/draw fails.

Runtime gates for final report: verify GUI/hand/dropped/frame item appearance, mixed materials, F3+T/replacement/deletion cleanup, and Oculus rendering in a real client. No benchmark was requested or run. Pure tests cannot prove GL shader compilation, visual output, or third-party shader pack compatibility.

Unified tests found that both STATIC and DYNAMIC still used FBX centimeters. The importer now enables Assimp's `aiProcess_GlobalScale`, using each importer-supplied file-unit scale for geometry, node/animation translations and bone offsets. The CIWS test compares FBX STATIC/DYNAMIC against the independently exported meter-based GLB. This is a format-unit correction; item fitting is a separate display-context operation. Evidence was checked against the exact bundled Assimp revision `a34688230d152345d04710e256224b71ce8ba8a1`, `FBXImporter::SetFileScale`, `BaseImporter::UpdateImporterScale` and `ScaleProcess`.

Final static integration review: position/normal/UV attributes match 3/3/2 floats, 32-byte stride and 0/12/24-byte offsets. `FALLBACK` now selects Minecraft batching and CPU skinning; `LOW` disables TBO use while retaining basic VBO/uniform skinning. Other presets use available GPU features. `tboEnabled=false` selects uniforms (64 bones); TBO palettes are bounded by both `maxTBOBones` and the hardware capacity, with larger palettes using CPU skinning. Drawing preparation now runs inside the state guard so exceptions restore prior state, and the direct skinned-render API also restores state. Cached GPU failures release cached buffers/program before permanently falling back until the next resource generation. Invalid triangle indices are filtered as in the vanilla path. No configuration, compatibility or Gaussian sources were changed by this review; no Gradle command was run.
