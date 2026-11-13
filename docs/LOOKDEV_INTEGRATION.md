# LookDev Integration Contract

> Historical integration handoff. CNC/CCB was subsequently removed at the user's request. References below to `ccb` in an earlier smoke run are retained as history, not a current fixture list. Current assets are listed in [Model Showcase](MODEL_SHOWCASE.md); current preview behavior is in [LookDev Preview](LOOKDEV_PREVIEW.md).

LookDev owner has implemented `LoadedModelsScreen`, selected-scene `PreviewGpuRenderer` VBO/EBO/shader resources, pure camera/catalog/draw-plan utilities, and six focused tests. `L` and `/lookdev` now open the native Screen. Old ModernUI fragments stay dormant and are not instantiated; Nodes tab is empty.

Dependencies on shared modules:

- `AssimpModelLoader.getLoadedModels()` -> canonical snapshot map used once per second or whenever `getCacheGeneration()` changes. Includes resource-pack and external/hot-reloaded loaded scenes; aliases are additionally deduplicated by scene identity.
- `ModelTextureCache.getTexture(AssimpScene, AssimpMesh)` -> actual per-mesh material resource location. Preview only calls it for materials with a texture and keeps no duplicate embedded image decode. Shared textures remain owned by global client resource lifecycle; preview releases only its own flat-white texture and GL buffers/program.
- Reload replaces scene identity or removes it from the snapshot; screen then closes old selected GPU resources automatically. Cache generation changes also close preview GPU resources even when scene identity stays the same. No worker-thread GL callback is used.

No Gradle was run by the LookDev owner. Root owns final compilation/testing/runtime. `PreviewCameraTest` (3), `PreviewSceneDataTest` (2), `LoadedModelCatalogTest` (1) cover pure math, real CIWS STATIC/DYNAMIC consistency, duplicate instance transforms, alias dedup and filtering.

Known display boundaries: still/bind-pose preview; fixed studio base-color shading; no PBR/node-generated material or shader injection; transparent pass is drawn after opaque but does not sort intersecting surfaces. Main framebuffer preview saves/restores GL and Minecraft state and never reads pixels back to CPU.

Files: `lookdev/ui/LoadedModelsScreen.java`; four `lookdev/preview/*.java`; two shaders in `shaders/lookdev/`; three test files in `lookdev/preview/`; `docs/LOOKDEV_PREVIEW.md`. Entry points `LookDevKeybind` and `LookDevCommand` are changed. `LookDevOverlayRenderer` no longer logs every ScreenEvent while inactive. Legacy `LookDevScreen` has an explicit dormant-source note only.

## Real Client Smoke

The optional development run is enabled only with an existing world folder:

```powershell
.\gradlew.bat runClient "-PclientSmokeWorld=New World" --console=plain --no-daemon --no-parallel
```

This forwards `autotest.enabled`, `autotest.capture` and `autotest.world`; normal runs do not open worlds or execute test commands. `PreviewSmokeTest` uses actual client commands, screen input methods, OpenGL draws and Minecraft's own `Screenshot.grab`. It captures the six showcase scenes, `ccb`, a GUI item sheet, then one scene after `reloadResourcePacks` (the same resource reload used by F3+T). It waits for frame/capture completion, checks GL errors, and saves/stops the integrated world. Each phase has a watchdog; a Gradle task succeeding alone is not a smoke success, so inspect the `[PreviewSmoke] SUCCESS/FAILED` marker.

Evidence paths: `build/fix-client-smoke.log` and `run/screenshots/assimp-smoke-<run>-*.png`. The root visually inspected captures on Windows x64 / RTX 4060 / OpenGL 4.6. This found and fixed FBX embedded image names that were not `*N`: `AssimpTexture` now retains `mFilename`, `AssimpScene.findEmbeddedTextureIndex` resolves exact/unambiguous short names, and the shared material cache uses that identity. This keeps world, item and preview texture resolution consistent.
