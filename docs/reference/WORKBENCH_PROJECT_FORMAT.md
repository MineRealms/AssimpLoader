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

## Portable Resource Dependencies

Resource-pack layout remains `assets/<namespace>/models/...` inside a Minecraft
resource pack. A workbench ZIP is a different container with `manifest.json`,
`project.workbench.json`, copied `assets/`, and optional `extensions/`; do not place
a workbench ZIP in `resourcepacks` and expect Minecraft to activate it.

`WorkbenchAssets.importLoaded` copies a resource model with its referenced
textures, conventional LabPBR siblings, optional `.instance.json`, JSON parents,
and installed consumers' declared dependencies. Namespace and relative resource
paths are retained below the project's `assets/`. It writes an adjacent
`<model-stem>.resources.json` index:

```json
{
  "version": 1,
  "model": "example:models/avatar/model.fbx",
  "resources": {
    "example:models/avatar/profile.json": "example/models/avatar/profile.json",
    "shared:instances/base.json": "shared/instances/base.json"
  }
}
```

Index values are relative to the workbench `assets/` root. For a loose source
outside a workbench they are relative to its own directory. They cannot escape
that root or resolve through a symlink outside it. File-backed textures and
instance parents can use these original namespaced aliases. Index JSON is
limited to 1 MiB and 4096 aliases; resource collection is bounded to 4096 files,
512 JSON definitions and 2 GiB. Missing declared JSON dependencies stop a
resource-pack capture instead of publishing a silently incomplete project.

Consumers register `WorkbenchAssets.registerDependencyContributor(id, callback)`
during setup. The callback returns resource IDs to collect; the engine copies
their referenced JSON/image/model files. RigWorks owns profile discovery and
declares profile/equipment/rig/physics dependencies through this API. The engine
has no character-specific dependency names or imports.

Individual file import copies its adjacent `.instance.json`/resource index and
adjacent JSON definitions explicitly referring to that model, then follows their
confined file references. It does not copy every unrelated JSON in the folder.
An existing portable project's asset root is retained on reimport. Cross-folder
dependencies of arbitrary loose files still require a complete bundle or index;
the importer reports unresolved references instead of searching the disk.

`InstanceDefinitionResources.forScene` supports resource-backed and file-backed
scenes. Local definitions merge relative or indexed namespaced parents, validate
the declared source model, and rebind only the in-memory definition's model name
to the imported file identity. Original JSON bytes remain unchanged. The cache
is cleared on the existing resource/model reload lifecycle, so F3+T rereads local
sidecars. Managed animation owners still instantiate independent mutable states.

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

New material records default to `SOURCE`, preserving the imported transparency
classification, texture Alpha and culling. Explicit `OPAQUE` ignores Alpha;
`MASK` thresholds texture Alpha times the material factor; `BLEND` keeps continuous
Alpha. OPAQUE/MASK create a cached albedo variant on the image worker, leaving
source images and normal/LabPBR channels intact. Existing explicit modes retain
their names. Opening a new FBX must not implicitly force its hair cards opaque.

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

New imports call `Extension.imported` for supported extension markers and an
explicitly requested consumer, once each. ZIPs use this contract for every UI
entry point, including generic Import, Open and file drops. Import does not call
the local-project `opened` hook first: an old archive cannot be silently replaced
by a newer local profile before the consumer asks which copy to create/update.
Cancel/failure restores the previous edit session and removes only the newly
created owned import directory. Ordinary unmarked model imports remain generic.

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

## Extension API And Runtime Scopes

Register a client-side `WorkbenchExtensions.Extension` once with a stable ID
matching `[a-z0-9_.-]{1,64}` and a nonblank title. A different extension with the
same ID is rejected. `supports(context)` determines whether an existing project
enables the extension. Use an explicit namespaced document marker for consumer
semantics; a skeleton alone does not identify a player or vehicle model.

`canImport()` exposes an explicit import type. Its asynchronous
`initialize(context)` must finish preprocessing and stage its extension data
before reporting success. `panels(context)` supplies `Panel` objects whose
`build(Inspector)` method contributes controls to the existing Inspector. Edits
go through `context.session().edit(...)`, preserving shared undo and preview.
An extension can return the existing parent screen from `open(context)` when it
does not need a separate destination.

`ownsRootTransform()` opts into the paired root getter/setter used by the shared
Tree, Gizmo and preview. Keep one authoritative root transform: do not duplicate
it in both consumer data and `document.transform`. Only one extension should own
a project's root; the current screen selects the first available owner.
`handlesApplication()` suppresses the ordinary source-wide
`WorkbenchRuntime.apply(scene, document)`, while all available consumer `apply`
hooks still run. The consumer then owns its instance application and any
consumer-specific synchronization.

### Asynchronous Lifecycle

`Context.runTask(label, Supplier<CompletableFuture<Void>>)` wraps a panel task in
the owning workbench's busy state, expected-session check and error reporting.
It rejects an obsolete session or a conflicting busy operation. Completion is
delivered through the client executor, including rejection when an operation has
been superseded. The supplier itself starts on the calling thread; this API is
not a worker executor.

Consumers must schedule file reads and expensive preprocessing on bounded worker
queues, then marshal session/UI mutations back to the client thread before
completing `initialize` or `prepareAsync`. Save, Apply and Export await the
available extensions' `prepareAsync` stages in order, then invoke their short
synchronous `prepare` hooks before capturing data. `packageFiles` should return
the resulting immutable source snapshot, without rescanning the filesystem on
the client thread. Propagate validation errors by exceptional completion; do not
silently label the previous snapshot as newly prepared.

Do not recursively call Save from a task that already holds the workbench's busy
state. For an explicit consumer save, call `Context.saveProject()` on the client
thread. Its future covers persistence, the saved baseline and consumer `saved`
hooks. A `saved` hook receives a separate session containing exactly the written
document, and must not recursively save that snapshot session. If application
inside the hook fails, the future may fail even though the formal write already
succeeded; the UI reports that distinction.

### Owner And Texture Source

```java
try (var scope = WorkbenchRuntime.instance(owner, scene, document, textureSource)) {
    Matrix4f attachment = WorkbenchRuntime.socket(scene, poseSnapshot, "tool");
    // Render the owner's scene and consume attachment within this scope.
}
```

`owner` is a stable, per-instance runtime object, not the shared `AssimpScene`.
`document` is the accepted immutable override for that instance. The optional
`textureSource` is the actual model-file `Path` whose parent resolves relative
texture references; it is not a directory. `null` uses `scene.getSourcePath()`.
Changing one owner's document or texture source refreshes that owner's binding
without replacing another owner's scene. Even a null document establishes an
owner scope and suppresses source-wide overrides for that instance.

Use the scope on the render thread and close it with try-with-resources, including
on exceptions. It restores any outer scope. Capture
`WorkbenchRuntime.textureSource(scene)` while the scope is active before
scheduling image IO; a worker does not inherit the render thread's scope.
`release(owner, scene)` retires the owner's material/cache resources when the
instance is unloaded. This mechanism does not distribute documents over the
network; ownership and revision protocols belong to the consumer.

`WorkbenchRuntime.socket(scene, snapshot, name)` resolves a named socket against
the edited pose and multiplies its local transform onto the node frame. It
returns a model-space matrix, or `null` for a missing binding, socket or node.
Node paths are resolved through the imported hierarchy, and additive node edits
are included. The consumer attaches its item/effect and applies its model root
and world transform exactly once. A stored socket alone creates no item, entity,
equipment behavior or network event.

### Complete Extension Directory Snapshots

For a complete replacement of an extension-owned archive subtree,
`Extension.packageFiles(context)` can return:

```java
Map.of(
    "extensions/example/behavior/", new byte[0],
    "extensions/example/behavior/manifest.json", manifestBytes,
    "extensions/example/behavior/scripts/main.js", scriptBytes
)
```

The trailing slash plus empty byte array is an export instruction. Existing disk
files under that subtree are excluded from the archive before the supplied
snapshot files are added. Deleted or renamed source files therefore do not
reappear from an older on-disk project. Keep the marker with no other entries to
export an empty snapshot. An empty map alone preserves the legacy merge behavior
and removes nothing.

Markers must have empty values and safe portable paths under `extensions/`.
Only claim directories owned by that consumer. The marker itself is omitted
from the ZIP and its manifest; it does not create an empty directory entry or
delete files from the local project. Exact extra-file overrides without a marker
continue to replace only the same archive path. Shared export rejects duplicate
extra-file keys from different extensions, and the container also rejects path,
case and file/directory conflicts.

### Canonical Embedded Texture References

`WorkbenchDocument.MaterialOverride.from(scene, material)` normalizes recognized
embedded texture names to `*N`, where `N` is the texture index in that exact
source scene. The scene-aware overload also discovers available normal/specular
inputs. Consumers importing material defaults should use this overload so an
embedded filename is not mistaken for a missing external image.

ZIP export preserves existing `*N` references inside the original model. When a
configured name matches an embedded texture and no real external file resolves
for it, export canonicalizes the name to `*N` in the exported document. A genuine
external replacement remains an external texture dependency and is packaged as
described above. Canonicalization neither extracts embedded images nor modifies
the source FBX, and embedded indices must not be reused with a different model.
