# Pose Evaluation Plan

Modifiers are compiled into one immutable plan whenever IDs are installed,
replaced or removed. Evaluation iterates the compiled stages once for local
transforms and once for Morph composition. It does not rescan all modifiers for
each category or reread dependency metadata every frame.

## Ordering And Validation

The category order remains `MOTION`, `BONE_MORPH`, `GENERAL`, `GROUND`, `IK`,
`PHYSICS`, `SPRING`, `POST_PHYSICS`. Without explicit dependencies, insertion order
is retained within each category. `PoseModifier.dependencies()` defaults to an
empty set and can declare full instance modifier IDs that must execute first.
Dependencies on earlier categories are allowed. Within a category, a stable
topological sort prefers the earliest inserted currently ready stage.

Missing IDs, dependency cycles, and dependencies on later categories are rejected
when the candidate plan is compiled. Dependencies express ordering; they do not
force a disabled solver to execute or override performance settings. Pose and
Morph passes both honor the existing IK/Spring performance gates.
Plans accept at most 512 modifiers and 64 dependencies per modifier.

Physics and Spring stages are exclusive writers of their declared bones: two
Physics stages, two Spring stages, or a Physics/Spring pair cannot declare the
same bone. IK can intentionally feed Physics on the same bone. Other ordered
overlays remain legal. Custom modifiers must accurately declare their writes in
`controlledBones()`; the engine cannot infer arbitrary Java matrix mutations.

## Atomic Ownership Changes

`AnimationState.replaceModifiers(replacements, removals)` validates the complete
candidate before publishing it or closing any previous modifier. Removals are
applied first, then replacements; a replacement can reuse a removed ID. Existing
IDs retain their insertion positions unless explicitly removed. New IDs follow
the map's iteration order, so use `LinkedHashMap` when declaration order matters.

`replaceModifiers(replacements)` and `removeModifiers(ids)` are batch shortcuts.
Single-ID `addModifier` and `removeModifier` use the same validation. Removing a
stage while keeping its dependents is rejected. Remove or rewire the dependents
in the same batch. On validation failure, the installed plan and old resource
ownership are unchanged; incoming rejected modifiers remain caller-owned.

After successful validation, retired objects are closed only if the candidate
does not retain their object identity under another ID. Retirement and instance
close release each identity once, in reverse compiled order, so dependents release
their references before their inputs. Ordinary modifier
evaluation exceptions are not transaction rollback points. Modifier close/apply
implementations should not throw after installation.

## Processors And Scripts

Processor JSON accepts an optional `after` array of up to 64 full modifier IDs:

```json
{
  "version": 1,
  "after": ["character:motion", "kubejs:processor:base"],
  "poses": {}
}
```

`after` applies to the processor's `GENERAL` stage. Its companion `IK` stage
automatically depends on the main stage. For direct Java installation, bind the
companion using `processor.ikStage(mainModifierId)` and install both in one batch.
KubeJS `processorDefinition` and resource processor installation do this
automatically. A processor cannot be rebound to another main modifier ID.

Processor replacement/removal changes both IDs in one transaction. Script cleanup
collects all owned processor stages and poses into one removal batch, so script
processors may depend on each other without leaving orphaned stages. If an asset
modifier still depends on a script stage, cleanup is rejected and script ownership
tracking remains available for a later retry.

`state.evaluationSchedule()` and `model.schedule()` return the same immutable
diagnostic list. Each entry includes `id`, `category`, `after` and `bones`.
Its immutable list implementations have public declaring classes so pinned
Rhino can read `size`, `get`, `contains` and iterators on Java 17 without module
reflection overrides.
This describes the compiled ordering, including currently disabled stages; use
performance and processor controls to inspect activation separately. Dependency
metadata is snapshotted on compilation. Change it by replacing/reinstalling the
modifier, not by mutating a set after installation.
