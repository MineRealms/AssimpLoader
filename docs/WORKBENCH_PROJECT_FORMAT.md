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
model, or an existing `.workbench.json` project. It copies texture references and
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

This API saves the project without invoking extension application, so a consumer
can apply the returned snapshot after success without a save/apply cycle. The
generic toolbar Save separately calls extension application against that saved
snapshot. Edits made after JSON writing begins remain dirty; neither the callback
nor the saved baseline claims that those newer edits reached disk. If only
recovery-file cleanup fails after a successful formal write, the baseline still
advances and the screen reports the cleanup warning.

`exportZip` exports the supplied draft and copied assets as a portable project;
it does not register new Minecraft block/item IDs or claim to be an automatically
installable resource pack. Runtime asset application and module-specific exports
belong to the workbench consumer integration.

An edited source bundle produces a hash warning on reopen. Saving configuration
does not silently approve new source content. FBX and texture file transfer is
separate from player visual-configuration synchronization.

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
