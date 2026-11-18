# Loading and Lifecycle Integration

Implementation complete. Owner: `loading_lifecycle`. Parent owns unified Gradle verification and any follow-up compile/test corrections.

## Integration APIs

- `AssimpModelLoader.getLoadedModels()` returns an immutable snapshot with canonical keys `namespace:full/extensionless/path#static` or `#dynamic`; aliases are excluded.
- `AssimpModelLoader.getCacheGeneration()` changes on add/replacement/removal/clear. `getCachedModel(name[, mode])` remains compatible; simple aliases resolve only when unambiguous.
- `AssimpModelLoader.addCacheInvalidationListener(Runnable)` provides a common-side-safe callback on replacement/removal/resource reload. Listeners must schedule GL work on the render thread. No client classes will be referenced by the core cache.
- `AssimpScene.getSourceResource()` exposes the original `ResourceLocation` (including extension) for resource texture resolution. `getSourcePath()` remains available for file imports.
- `getAllCachedModels()` is retained and delegates to the same immutable canonical snapshot. LookDev can use `getCacheGeneration()` before building a new snapshot, rather than polling and deduping every frame.
- Geometry bake will pass `context.getTransforms()` as the fourth argument to `AssimpBakedModel`'s constructor. Rendering owner must add this constructor.
- `ModelCacheLifecycle` uses `ModelEvent.RegisterAdditional` to invalidate before each model bake/reload, avoiding per-model cache clearing and duplicate block/item imports.

## Implemented Fixes

- `ResourceFileIO` provides read/seek/tell/size/open/close callbacks for native Assimp resource imports. Main files and side files are supplied by ResourceManager; absolute paths, namespace switches, encoded paths and traversal above the namespace root are rejected. Every callback, native stream buffer and native scene is released after the Java copy.
- Geometry block/item references share one canonical scene per import mode and reload, avoiding a second native import. Different resource formats that collide on the same extensionless identity report a conflict instead of silently returning another file.
- The repository replaces the old duplicate alias cache. It exposes immutable canonical snapshots and a generation counter, preserves unique legacy names, rejects ambiguous alias lookups, and notifies common-side-safe invalidation listeners. Pending imports are versioned per identity and resource reload; an obsolete parse cannot republish.
- Async parsing uses a bounded four-or-fewer worker pool and queue. In-flight requests share ownership. A 60-second wait timeout reports an error without an inline second parse or pretending native work was cancelled. Shutdown stops new work while running imports release their own native allocations.
- External hot reload scans and decodes away from the render thread and atomically publishes complete replacement batches. Required import failure keeps the prior batch intact. Deleted overrides restore the original pack scene or remove the external-only entry. Resource reload resets override tracking before geometry bake.
- `ModelRegistry`, `ModelCacheManager` and `ResourcePackIntegration.loadModel` delegate to this same repository; the null-returning model loader stub is gone. Native memory import now validates null data before logging and explicitly frees its direct input buffer. Auto-detect import releases the analysis scene in a finally block.

## Verification Added

Unified build follow-up: material color readers now request their actual float capacity and default RGB alpha to 1. Scalar opacity reads request one value (the previous zero capacity silently ignored it); explicit opacity includes 0 and 1 and takes precedence over legacy transparency fields. RGBA alpha is retained, and duplicate alpha properties are not multiplied. FBX transparent colors are already factored by the importer, so their fallback does not apply TransparencyFactor again. Five added native material-import tests cover RGB, explicit opacity endpoints, conflicting legacy transparent color, OBJ Tr, and glTF RGBA. These follow-up tests are awaiting the parent's unified rerun. Assimp's native [material property reader](https://github.com/assimp/assimp/blob/v5.2.2/code/Material/MaterialSystem.cpp) and [FBX material conversion](https://github.com/assimp/assimp/blob/v5.2.2/code/AssetLib/FBX/FBXConverter.cpp) were checked for element counts and precedence.

13 tests in `ResourceModelImportTest`, `ResourceFileIOTest`, `ModelRepositoryTest` and `AsyncModelLoaderTest` cover actual external glTF .bin and OBJ .mtl imports, missing/traversing side files, stream callback contracts, immutable snapshots, namespace/mode aliases, replacement/removal, superseded background publication, duplicate JSON references, override restoration, null memory input and timeout ownership. These tests have not been run by this owner, per unified-build instruction. `git diff --check` passes.

The seek callback follows signed C-style offsets (`END + offset`) rather than assuming all end-relative offsets should be subtracted. Reference: [Assimp DefaultIOStream](https://github.com/assimp/assimp/blob/v5.2.2/code/Common/DefaultIOStream.cpp).

## Configuration and Migration

- `hotreload.enabled` gates the client reload command and watcher; `hotreload.resourcePath` is resolved against the game directory. Supported extensions and polling interval are now used. Filesystem scanning and native parsing happen on workers.
- `/assimploader reload` is now a local client command. It works on multiplayer without OP because it affects only this client's assets. It no longer falsely succeeds on a dedicated server while doing nothing.
- Put overrides under `<resourcePath>/<namespace>/models/...` or the legacy `<resourcePath>/models/...` (the latter means namespace `assimploader`). Preserve the resource path, e.g. `models/showcase/ciws_textured.fbx`; do not rely on ambiguous filename-only substitution.
- `rendering.externalModelDirectory` first searches `<root>/<namespace>/<resourcePath>`; only `assimploader` also accepts legacy `<root>/<resourcePath>`. Main model symlinks outside the root are rejected.
- The `modelCacheSize` budget applies to unpinned/on-demand file models. JSON-baked models are pinned until the next resource reload, because evicting one would make an already placed block disappear.
- Parent should update the config comment for `performance.modelCacheSize` to say "Maximum unpinned/on-demand file models; JSON models stay pinned until resource reload". No configuration fields were removed.

## Files

Core: `AssimpModelLoader`, `AsyncModelLoader`, new `ModelRepository` and `ResourceFileIO`. Source metadata: `model/AssimpScene`. Geometry: `render/AssimpGeometryLoader`. Facades: `cache/ModelCacheManager`, `resource/ModelRegistry`, `ModelResourceLocation`, `ResourcePackIntegration`. Lifecycle: new `resource/ModelCacheLifecycle`; `hotreload/ModelHotReloadManager`, `ModelHotReloadCommand`, `HotReloadEventHandler`, new `ModelHotReloadWatcher`. Four new test classes as above.
