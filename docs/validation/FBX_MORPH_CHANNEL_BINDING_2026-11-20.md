# FBX Morph Channel Binding

## Report And Evidence

The Madoka Exedra 100107 character appeared with persistent closed eyes, an
open mouth, and overlapping facial features. The exact installed FBX from the
1.21.1 test profile was inspected with the current engine classes, without
starting or changing the live game.

- The face mesh has 60 targets. Every imported `AIAnimMesh.mWeight` is 1.0,
  so its default activation sum is 60.0, including mutually exclusive eyelid,
  brow, mouth, and tooth targets.
- The ten skeletal clips each contain a morph channel with all-zero weights.
  Its name is `exedra_100107_Face_Mesh*0`. The actual mesh name is
  `Face_Mesh.002`; the owner node is `exedra_100107_Face_Mesh`.
- Previous playback matched only the mesh or owner name exactly and ignored
  that channel. Initial `AnimationState` weights therefore remained all 1.0.
- Comparing the actual CPU-skinned neutral face with all-zero versus all-one
  target arrays measured a maximum vertex displacement of 0.15322742 blocks.
  This is geometry deformation, not just a material or normal difference.
- This mesh exceeds the GPU morph limit of 32 targets and follows the existing
  CPU fallback. No incorrect CPU/GPU packing was found in this investigation.

Assimp's [FBX converter](https://github.com/assimp/assimp/blob/v5.2.5/code/AssetLib/FBX/FBXConverter.cpp)
names morph animation channels after `owner-node*geometry-ordinal`. It also
sets `aiAnimMesh.mWeight` to 1.0 for single-shape blend-shape channels, regardless
of the authored `DeformPercent`. That value cannot reliably represent the
author's neutral facial activation. This work does not change defaults for
other formats, such as glTF, where authored defaults are already tested.

## Change

`MorphChannelBinding` now shares matching and mask behavior across
`AnimationState`, `ClipMorphComposition`, and `WorkbenchPose`.

Exact mesh and owner names retain their meaning. For FBX, an encoded `*0`
channel can also bind to a matching owner with exactly one mesh. Matching is
deliberately conservative: geometry ordinals cannot safely identify final
material-split meshes, so multi-mesh owners are not guessed. Non-FBX literal
asterisk names retain their previous behavior.

Explicit per-target defaults in a character package remain useful. Setting
all facial expression defaults to zero protects neutral/stopped display;
resetting only the two blink targets does not clear the other 58 expressions.
Stopping an animation still returns the imported defaults, by design. The
asset package must not depend on a zero animation track for neutral face setup.

## Verification

Focused test classes passed on both the 1.20.1 Forge and 1.21.1 NeoForge nodes:

```text
MorphChannelBindingTest
MorphTargetTest
ProcessorMorphCompositionTest
BlenderMorphProbeTest
```

The exact installed Madoka FBX was then loaded with the rebuilt 1.21.1 classes.
All ten imported channels resolved and each clip's sampled face weight sum was
0.0. Stopping returned the original default sum of 60.0, confirming why package
neutral defaults must also be explicit. No game launch or visual acceptance is
claimed by these offline checks.

Local diagnostic tools are generated under `build/tmp/face-audit/` and are not
part of the shipped engine or committed assets.
