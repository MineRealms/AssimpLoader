# JSON And KubeJS Animation Processors

This is the first skeletal composition processor. It is a general AssimpLoader
facility, independent of RigWorks and AssimpShowcase. RigWorks is used only by the
included player example to supply a rendered model handle and native player pose.

## Responsibilities

JSON declares named clip layers, rest-relative poses, masks, initial weights,
activation, and optional owned CCD IK chains. KubeJS changes numeric controls and
activation. Java compiles definitions, samples clips, blends transforms, schedules
IK, and manages lifetime. No script runs inside vertex, pose, or physics loops.

The current order is:

```text
base clip and existing model layers
  -> MOTION (including the RigWorks native player pose)
  -> BONE_MORPH
  -> GENERAL (processor clips, then processor poses)
  -> GROUND
  -> IK (processor-owned chains and existing IK modifiers)
  -> existing physics and spring stages
  -> render-pose interpolation, skinning, equipment
```

Clips inside a processor are evaluated in GENERAL so the native MOTION source
cannot erase them. Ordinary `handle.layer()` retains its original earlier stage.
Within a processor, clip declarations compose in JSON declaration order, followed
by poses in declaration order. Multiple processors and unrelated modifiers still
use their existing stage and registration order. This is not a dependency graph
compiler or automatic multiple-writer arbitration system.

## Handle API

```javascript
model.processor('pack:body', 'pack:animation_processors/body.json')
model.processorEnabled('pack:body', true)
model.processorEnabled('pack:body', 'walk_overlay', true)
model.processorWeight('pack:body', 'walk_overlay', 0.75)
model.processorPhase('pack:body', 'walk_overlay', 0.4)
model.processorReleasePhase('pack:body', 'walk_overlay')
model.processorTarget('pack:body', 'hand_reach', 0.4, 1.2, 0.3)
model.processorControls('pack:body')
model.reloadProcessor('pack:body', 'pack:animation_processors/body.json')
model.removeProcessor('pack:body')
```

`processor(id, resource)` compiles once per live animation instance. Calling it
each tick with the same ID/resource is an inexpensive lookup and does not parse
JSON, read a file, allocate players or reset playback. Changed resources must be
applied with `reloadProcessor`. A rebuilt model instance compiles independently.

`processorDefinition(id, object)` accepts an inline declaration and explicitly compiles a
replacement on each call. Use it at initialization or after changing a definition,
not for per-tick weight changes. Resource declarations are the preferred route.
Separate method names avoid Rhino's ambiguous conversion of JavaScript objects
to overloaded Java `String` and `Map` arguments.

Reload validates all bones, clips, masks, values and owned IK chains before
replacing either installed stage. Failure preserves the previous processor and
playback state. This transaction covers this processor's definition; it does not
roll back arbitrary side effects elsewhere in a KubeJS reload.

Control changes are consumed at the next normal animation evaluation. They do
not advance simulation time or publish extra physics steps. Clip phase is a
normalized `[0,1]` sample; it stays externally synchronized until released.
Enabled clips advance while their weight is zero. Disabled clips, or a globally
disabled processor, freeze their private playback clock. Re-enabling resumes it.

## Schema And Spaces

The complete example declaration is
`examples/animation_processors/assets/assimploader/animation_processors/block_player_demo.json`.

- `version` is numeric integer `1`; other versions are rejected.
- `enabled` and other switches are actual booleans.
- `clips`: exact imported `clip` name, optional `mask`, `weight`, `enabled`,
  `additive`, `speed`, and `loop`.
- `poses`: `bones` with `rotation` in Euler XYZ degrees, `position`, `scale`, and
  optional bone weight; optional processor pose `weight`, `mask`, `enabled`,
  `additive`, and `space` (`local` or `model`).
- A local pose delta uses each bone's imported local axes. `space: "model"`
  converts joint rotation deltas through the imported bind axes, including roll;
  it does not convert position offsets into absolute world targets.
- `additive: true` composes onto the incoming pose. An override pose uses the
  skin bind pose as its reference and blends from the incoming pose.
- A missing mask affects all written nodes. A present mask affects only listed
  nodes. Unknown node names are errors.
- `ik`: `chain`, model-space `target`, optional `enabled`, `weight`, `iterations`,
  and `tolerance`. These chains are owned by the processor and use the existing
  CCD solver in the normal IK stage. Removing the processor removes these chains.
  Existing model IK remains independent and can still receive `model.target()`.
- Poses and IK targets use the model's imported units. Rotation is in degrees.
  Asset import may leave different node scales; this API does not assume that
  one imported node unit is one metre for every asset.

Weights are finite `[0,1]`; speed is `[-16,16]`. Definition input is bounded to
64 Ki characters, 64 named controls and 1024 controlled bone operations.
Each mask/pose supports at most 256 nodes. At most eight CCD chains are allowed,
with 2..32 descendant nodes and 1..128 iterations. Numeric target/position bounds
are `[-1000000,1000000]`; pose rotations are within `[-36000,36000]` degrees;
positive scale is `[0.001,1000]`. Unknown fields and wrong JSON types fail early.

## Lifetime And Example

Script-owned processors share the bounded script-control registry. They are
retired by `clearScriptControls`, character script cleanup, model replacement or
animation-state close. A processor retains no Minecraft entity or animation-state
reference. Different instances have independent clip clocks, weights and targets.
`resetDynamics` resets private clip clocks, preserving runtime control values.

The block-player example now lives in the independent
[RigWorks examples](https://github.com/MineRealms/RigWorks/blob/main/examples/kubejs/README.md).
Enable its `examples/animation_processors` resource pack, then install
`examples/kubejs/client_scripts/assimp_animation_processor.js` from that repository.
The example registers `example:hybrid_player` with RigWorks. Use
the Blender block-player profile and Scripted mode. It retains original native
poses, mixes a masked baked walking clip, adds a configurable sword guard, and
exposes an opt-in left-hand IK contact. It clears its influence during native
item use, accepted swings, swimming and other special native states.

The fixture is deliberately specific to the block-player bone names. The engine
and handle API are generic. Adapt the JSON names and masks for a different asset.
The script does not automatically enable a character renderer or replace user
scripts. Reload the script through KubeJS; explicitly call `reloadProcessor` when
editing JSON while retaining a live instance.

## Current Boundary

This version does not import Blender constraint graphs, expose arbitrary FBX
features, infer weapon contact sockets, change server collision, schedule
arbitrary shaders, or provide per-processor physics ownership. Processor clips
compose both skeletal and Morph channels using one clock. Explicit modifier
dependencies are compiled and validated; see [evaluation plans](POSE_EVALUATION_PLAN.md).
Imported resource facts are available through [capabilities](MODEL_CAPABILITIES_AND_CLIP_MORPHS.md).
Owned CCD chains do not add joint hinge/angle limits; the existing constrained
rig solvers remain separate. A declared IK target is an input, not an automatic
two-handed item contact solution. RigWorks provides a separate optional
[item contact policy](https://github.com/MineRealms/RigWorks/blob/main/docs/guides/RIGWORKS_HAND_CONTACTS.md) built on the engine's constrained
two-bone solver. Humanoid mapping and native player observations belong to RigWorks.
