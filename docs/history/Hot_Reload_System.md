# Model Hot-Reload System

This guide describes the current client implementation. Model parsing runs on workers; complete replacement batches are published to the shared model repository. See [loading and lifecycle details](../reference/LOADING_FIXES.md) and the [PBR asset specification](../guides/PBR_ASSET_SPEC.md) for material requirements.

## Configure the Model Directory

Edit `<game-directory>/config/assimploader-client.toml`:

```toml
[hotreload]
    enabled = true
    resourcePath = "AssimpLoader/Resource"
    autoReloadEnabled = false
    watcherPollInterval = 1000
    supportedExtensions = ["fbx", "gltf", "glb", "dae", "obj", "blend"]
```

Relative paths resolve against the active Minecraft game directory, not the shell working directory or another mod's project. Forge `runClient` normally uses this project's `run/` directory. An absolute directory can be configured explicitly; there is no hardcoded developer drive.

The default override layout is:

```text
<game-directory>/AssimpLoader/Resource/
  assimploader/
    models/
      showcase/
        ciws_textured.fbx
        portal_pbr.fbx
        textures/
          body.png
```

The legacy layout `<resourcePath>/models/...` also means namespace `assimploader`. Other namespaces must use `<resourcePath>/<namespace>/models/...`.

Keep the complete model resource path. For example, both of these target `assimploader:models/showcase/ciws_textured.fbx`:

```text
AssimpLoader/Resource/assimploader/models/showcase/ciws_textured.fbx
AssimpLoader/Resource/models/showcase/ciws_textured.fbx
```

Use one layout per model. Two files targeting the same identity cause the batch to fail instead of selecting one arbitrarily. Model identity includes namespace, full extensionless path and import mode; a bare filename is only a convenience alias when unambiguous. Paths must satisfy Minecraft resource naming rules, so use lowercase names. Symlinks escaping the configured root are rejected.

## Manual Reload

```text
/assimploader reload
```

This is a local client command. It needs no OP permission and affects only this client's model assets, including on multiplayer. The mod and command must be installed on the client, and `hotreload.enabled` must be true.

The command:

1. Creates the root's `models/` directory if needed and scans the configured root recursively.
2. Imports files matching `supportedExtensions` away from the render thread.
3. Preserves already loaded STATIC/DYNAMIC variants; a new external-only model starts as STATIC.
4. Publishes the completed batch only if imports succeeded and the resource generation is still current.
5. Invalidates dependent client render resources; current scenes and textures are rebuilt/uploaded as needed by the normal client render path.

A failed required import leaves the previous batch intact. Deleting an override restores the scene saved when that override was first applied, or removes the entry if it existed only as an external model. A scene initially loaded through `rendering.externalModelDirectory` can itself be that saved original; F3+T performs a fresh lookup against the current files and packs. A successful command reports the number of imported **variants**, which can exceed the file count.

The command does not create block/item registry entries or JSON wrappers for arbitrary new files. Replace a model that already has a registered renderer, or use [LookDev](../guides/LOOKDEV_PREVIEW.md) to inspect loaded scenes.

## F3+T and Persistent Overrides

F3+T performs Minecraft's full resource-pack reload and model rebake. It clears the repository and hot-reload override tracking before loading the JSON-referenced resources again. `/assimploader reload` publishes an external batch directly; it does not trigger a full resource-pack reload.

To make the same external files participate in initial loading and F3+T, also configure:

```toml
[rendering]
    externalModelDirectory = "AssimpLoader/Resource"
```

The geometry loader first searches `<externalModelDirectory>/<namespace>/<resourcePath>` and, only for namespace `assimploader`, the legacy `<externalModelDirectory>/<resourcePath>`. It then falls back to the selected resource packs and bundled resources. The default value is empty, which disables this initial-load override. Keep it aligned with `hotreload.resourcePath` when using one folder for both workflows.

Without `rendering.externalModelDirectory`, F3+T restores the resource-pack model; run `/assimploader reload` afterward to reapply the external batch. F3+T is also the normal path for resource-pack/JSON changes. If resource reload overlaps a background external import, the stale batch is rejected; retry once resource loading finishes.

## Automatic Reload

Set `hotreload.autoReloadEnabled = true` to enable polling. `watcherPollInterval` is milliseconds, defaults to 1000, and accepts 100 through 10000. Scans run on a worker and fingerprint regular files by path, size and modification time. A changed fingerprint requests the same batch reload as the command.

This is a polling watcher, not a guarantee that an editor has finished writing every side file. Export to a temporary location, then replace the completed files together, or use manual reload after the exporter finishes. A failed automatic reload is logged; fix/save the files again or run the command to retry.

## Textures and Animations

- Keep embedded images or relative texture references valid. For file imports, external textures must remain inside the model file's directory or its descendants; do not reference an absolute authoring-machine path or `../` outside that directory.
- Keep glTF `.bin` and other required side files with their model. Reload imports complete scenes, including animation data supported by the selected format and import mode.
- STATIC import pretransforms geometry. Use a DYNAMIC JSON model when node hierarchy, pivots or animation must remain available.
- For PBR conventions, raw Blender maps and LabPBR `_n`/`_s` files have different meanings. Follow the [PBR specification](../guides/PBR_ASSET_SPEC.md); a grayscale roughness image is not a ready-to-use `_s` texture.

## Troubleshooting

| Symptom | Check |
|---|---|
| Command is unavailable | Client has AssimpLoader installed; use the command in the client's chat. It is not a dedicated-server command. |
| Reload reports disabled | `hotreload.enabled` in the active instance's client config. |
| No variants imported | Active game directory, configured root, allowed extensions and full resource paths. |
| Previous model remains | `logs/latest.log` for import errors, duplicate identities, invalid resource names or a concurrent resource reload. |
| Model reverts after F3+T | Configure `rendering.externalModelDirectory` or reapply the external batch after resource reload. |
| Missing texture | Relative paths, embedded image names and directory containment; examine material source diagnostics before judging PBR in the world. |

Reload cost depends on file count, geometry, texture decoding and subsequent GPU upload. Workers avoid native parsing on the render thread, but no fixed stall time or zero-overhead guarantee is made.
