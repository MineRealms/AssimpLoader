# Workbench Project Format

The in-game workbench owns editable configuration. Imported Assimp scenes remain
read-only; source meshes, weights and authored animation keys are not rewritten.

## Files

The project root defaults to `config/assimploader/workbench` in the game directory;
the `assimploader.workbenchDirectory` JVM property can override it. Each import
creates an independent folder containing:

```text
project.workbench.json
assets/model.fbx
assets/textures/...
```

`WorkbenchProjectStore` accepts a model file, a folder containing one unambiguous
model, an existing `.workbench.json` project, or an exported `.zip`/`.rig.zip`
package. `open` also recognizes ZIP packages. It copies texture references and
glTF external buffers; folder imports also retain image, MTL and binary sidecars.
Source files are untouched. Models import in `DYNAMIC` mode so node hierarchy,
skinning and animation remain available. Run this blocking service on a worker.

Missing texture files become diagnostic issues. Multiple source models require
explicit selection. Paths in a saved project are relative to its directory;
parent traversal, absolute model paths and symlink escapes are rejected. Absolute
texture references found during import are copied into `assets/textures` and
remapped in material configuration; this does not rewrite the source FBX.

## Document

Schema version 1 stores:

- `asset_id`, `source`, and SHA-256 `content_hash` of relative asset filenames and
  their contents, so moving the project does not change its identity.
- Root position in metres, intrinsic XYZ Euler degrees and nonuniform scale.
- Per-node additive transform and visibility. Node keys are structural paths
  from `SceneMetadata.nodePath`, including sibling indices for duplicate names.
- Material RGBA, metallic, roughness, HDR emission, alpha mode/cutoff, double-sided
  state and texture slot paths. Texture values are relative to the model file.
- Named sockets with node path and local transform.
- Animation clip, time, speed, loop and playback preference.
- Namespaced `extensions` for consumer modules, preserved on read/write and undo.

Scene metadata is extracted afresh from the source, rather than duplicated as
editable JSON. It includes bounds, meshes, UV availability, bones, morph target
counts, animation durations and import diagnostics. Units still require an author
decision when source metadata does not describe the intended Minecraft size.

The shared `WorkbenchSession` maintains immutable drafts and bounded undo/redo.
Begin/commit a transaction around a drag; cancel restores its starting document.
`markSaved(persisted)` records the exact snapshot written by an asynchronous save,
and `revert` returns to that version. Newer edits and an active drag remain in
memory and stay dirty. First-save asset copying rebases source identity and
inherited texture paths throughout the same session, including undo/redo and an
active transaction, instead of replacing the session with an older snapshot.
Neither drag updates nor undo import FBX, write JSON or allocate GPU buffers.

## Persistence And Portability

JSON writes use a temporary sibling followed by an atomic move when supported.
The store also exposes `saveDraft`, `readRecovery`, `readDraft` and `discardDraft`.
`saveDraft(file, savedBase, draft)` records a SHA-256 of the canonical saved base
under the draft-only `_recovery` field. `readRecovery` returns an optional draft
and diagnostics: the saved base hash and asset identity must match, and the draft
must not be older than the formal file. Corrupt, stale and legacy drafts without
this saved-version reference are left on disk for inspection and skipped. They
cannot prevent opening a valid formal project. The compatibility `readDraft`
method returns only an accepted optional draft.

`open` reads the formal project; the screen separately applies an accepted draft
to its edit session and reports `Recovered unsaved draft`. Recovery never replaces
the formal file. Importing a directory containing `project.workbench.json` opens
that project and preserves its configuration instead of making a new default
asset project.

`WorkbenchSession.revert` only resets memory. The screen's explicit Revert/Discard
also invalidates old recovery generations and deletes the recovery file on the
bounded IO worker before resetting the session or closing. `WorkbenchRecovery`
rejects stale queued writes and removes the result of a write invalidated while
in progress. Cleanup failure leaves the session and window intact and reports an
error. Project opening checks for unsaved edits before IO and before replacement.

## Extension Save Contract

`WorkbenchExtensions.Context.saveProject()` delegates to its owning
`ProjectSaver.saveProject(expectedSession)`. Call it on the client thread after
writing the latest extension state into `context.session()`. Its future completes
on that thread after the atomic write and saved-baseline update, returning the
exact immutable persisted document. Failure completes exceptionally; a mismatched
session or an incompatible busy operation is rejected. `context.projectPath()`
returns the formal JSON path, or `null` before the first project copy exists.

After open/import has selected the formal document or an accepted recovery draft,
`Extension.opened(context)` may stage newer consumer-specific configuration in the
same edit session. Such changes remain dirty until saved. A callback failure is
reported as a diagnostic without preventing the valid project from opening. The
hook is not invoked while rebasing the first asset copy during Save.

Shared Save/Apply/Export await `Extension.prepareAsync(context)` to validate and
capture external source files, then run `Extension.prepare(context)` on the client
thread before taking a snapshot. Save invokes `Extension.saved(savedContext)` after the formal write and
baseline update. The latter receives a separate session containing exactly the
persisted snapshot. It may apply consumer configuration, but must not recursively
save that snapshot session. Save does not call `Extension.apply`; the toolbar
Apply separately prepares and applies the current draft. A consumer returning
`handlesApplication()` suppresses the ordinary source-wide runtime Apply.

Edits made after JSON writing begins remain dirty; neither the callback nor the
saved baseline claims that those newer edits reached disk. If only recovery-file
cleanup fails after a successful formal write, the baseline still advances and
the screen reports the cleanup warning.

Consumers contribute `Panel` implementations to the same Inspector, editing
namespaced document extensions through the shared session. `canImport()` and
asynchronous `initialize(context)` opt into an explicit import type; a skeleton
alone never enables a character consumer. `ownsRootTransform()` and the paired
root getter/setter let the shared tree, gizmo and preview use the consumer's
single root transform. Unknown extension data remains round-trippable without
the consumer installed. See [extension integration](../guides/WORKSPACE_EXTENSIONS.md).

`exportZip` exports the supplied draft and copied assets as a portable project;
it does not register new Minecraft block/item IDs or claim to be an automatically
installable resource pack. Runtime asset application and module-specific exports
belong to the workbench consumer integration.

An edited source bundle produces a hash warning on reopen. Saving configuration
does not silently approve new source content. FBX and texture file transfer is
separate from player visual-configuration synchronization.

## Portable ZIP Packages

Both ordinary assets and character projects use the same ZIP container. It is not
a Minecraft resource pack and does not add registry entries. ZIP compression uses
the standard DEFLATE method at its best compression level. Exported entry order
is stable and entry timestamps are normalized.

```text
manifest.json
project.workbench.json
assets/model.fbx
assets/textures/...
extensions/<consumer>/...       # optional opaque consumer files
```

`manifest.json` uses this independent container schema:

```json
{
  "format": "assimploader.asset-package",
  "version": 1,
  "project": "project.workbench.json",
  "files": {
    "project.workbench.json": {"bytes": 1234, "sha256": "<64 lowercase hex characters>"},
    "assets/model.fbx": {"bytes": 5678, "sha256": "<64 lowercase hex characters>"}
  }
}
```

The manifest lists every non-directory file except itself, with its exact
uncompressed byte length and SHA-256. Missing, additional, duplicated, corrupt or
case-conflicting entries fail the import. Unknown container versions are rejected;
arbitrary namespaced document `extensions` and opaque `extensions/` files are
retained without requiring their consumer mod. Checksums detect content changes;
they do not authenticate the author or make scripts trusted.

`exportZip(project, document, destination)` exports that exact edit snapshot, the
project's current `assets/` and optional `extensions/` folders. The overload
`exportZip(project, document, destination, Map<String, byte[]> extensionFiles)`
lets a consumer provide additional files under `extensions/`; an exact matching
path replaces its copied consumer file in the archive only. Other locations are
rejected. The engine never runs these files during import. Consumers decide how
to interpret them after opening the project.

Texture overrides added after initial import are copied into the ZIP. Existing
project-relative assets keep their paths; external replacement textures are
stored under content-addressed `assets/textures/override-<sha256>-<name>` paths.
Only the exported document's texture references and asset `content_hash` are
updated. The original FBX, saved project and in-memory edit snapshot are not
modified. Missing configured textures produce an actionable export error instead
of a package that depends on an author's local disk. Embedded `*N` textures remain
inside the source model. This process preserves FBX geometry and authored clips;
it does not bake transforms or write a new FBX.

Import/Open ZIP calls `WorkbenchProjectStore.importZip`. Each import creates a
new independent project directory under the configured workbench root. Extraction
uses a fresh staging directory, validates the complete manifest, project document,
source model path and configured texture paths, then publishes the directory with
an atomic move when supported. Only then is the model passed to Assimp. A failed
load also removes this new directory; an existing project is never replaced.

Limits apply to streamed expanded bytes rather than trusting ZIP headers: 2 GiB
total including the manifest, 10000 entries, 4 MiB project JSON, 4 MiB manifest,
1024-character paths and 32 path components. Absolute paths, drive/UNC paths,
backslashes, `.`/`..`, Windows reserved filenames, file/directory collisions and
case conflicts are rejected. Imported members are created as ordinary files in
the staging directory; no symbolic links or archive file permissions are applied.
glTF external buffer/image URIs must resolve to included files under `assets/`.
All IO remains on the bounded workbench worker.

Legacy ZIP exports containing only `project.workbench.json`, `assets/` and optional
`extensions/` are accepted with the same path and size limits. They display a
diagnostic explaining that manifest checksums are absent; exporting them again
upgrades the container. They do not acquire trust or execute scripts automatically.
Recovery drafts and editor layout are not packaged.

This local full-asset archive remains separate from multiplayer configuration
distribution. Consumers such as RigWorks can synchronize small JSON/behavior
packages while requiring players to obtain large FBX/textures separately.

## Preview Resource Lifetime

The standalone renderer owns normal/specular GPU textures created from packed
material images, independently of Oculus child textures. It caches them per
material index while both material and packed-image identities remain unchanged.
Replacement and renderer close release the owned images and GL texture IDs;
project switching and resource-generation changes close the renderer. Numeric
material edits use uniforms, without re-uploading these images each frame.
These GPU objects and transient selections are not serialized. Undo/redo refreshes
socket rows and clears a selection whose socket no longer exists in the draft.
The source scene remains read-only; this preview is still simplified PBR rather
than the shader pack's complete world-lighting pipeline.
