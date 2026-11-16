# Character engine implementation ledger

Started 2026-11-17, based on AssimpLoader `5cd4523`.

## Authorized Scope

Block-style Blender player, FBX/GLB plus versioned semantic JSON, local player
replacement, player-driven animation, skeletal constraints and real rigid-body
physics. Build engine semantics analogous to MMD before adding PMX/VMD parsing.
Equipment, display timeline and user workflow follow the core. Client visuals
must not change server movement, AABB, collision or damage authority.

## Work In Progress

- [x] Independent Blender rig, weighted meshes, actions, Morph/PBR fixtures.
- [x] Typed character profile, player inputs and animation priority/controller.
- [ ] Local player rendering, lifecycle, fallback and consistent visual frame.
- [ ] Bullet rigid bodies, joints, collision, ragdoll and bounded world sampling.
- [ ] Format-independent append/IK/morph/evaluation semantics and numerical fixtures.
- [ ] Runtime validation, delayed captures and completed-frame diagnostics.
- [ ] Equipment/display/workflow extensions after the core loop.

No completion or runtime claim follows from adding a source file or dependency.
PMX/VMD binary parsers are out of scope. Preserve unsupported feature diagnostics.

## Numerical Acceptance

2026-11-17: `gradlew test --tests cn.minerealms.assimploader.character.*`: 28 passed.
FBX and GLB contain exactly the 15 authored actions after restricting export to
this scene's NLA tracks. Both retain the skeleton, Blink and Smile morphs.
Blender reports zero unweighted vertices (624 vertices, 312 triangles, 23 bones).
FBX idle geometry: floor approximately zero, maximum Y approximately 1.828 m.
Limited knee IK on the actual FBX reaches 0.000938 m error.

The actual FBX physics fixture has 17 bodies and 16 joints. Rest joint frames
coincide numerically. Each of scales 0.5, 1 and 2 survives animated secondary
motion, five seconds of ragdoll/floor collision and return to animation, with
1,326 fixed steps, zero discarded time and finite bodies inside the test region.
FBX retains a 100x bone unit scale: physical bone frames explicitly remove it,
while rendered bone transforms preserve it. Shape dimensions and offsets are
metres in rigid bone axes. The profile's uniform visual scale affects shapes,
joint offsets, target positions and feedback together.

FBX import through Assimp 5.2.x gives single-shape channels default weight 1.
The rig's explicit `defaults` restores the authored zero expression; it does not
globally rewrite other imported models. Group morphs are flattened with cycle
and depth checks before per-frame evaluation.

The player event path and loaded-block collision adapter are implemented but
have not yet passed client runtime acceptance. Equipment, first-person arms,
display timeline and profile selection are still pending. SDEF/QDEF, UV/material
morph and format-specific motion tracks remain separate unimplemented semantics.

## Sources And Evidence

Public implementation documentation, not an official exhaustive format standard:

- [babylon-mmd model behavior](https://noname0310.github.io/babylon-mmd/docs/reference/understanding-mmd-behaviour/introduction-to-pmx-and-pmd/)
- [babylon-mmd motion behavior](https://noname0310.github.io/babylon-mmd/docs/reference/understanding-mmd-behaviour/introduction-to-vmd-and-vpd/)
- [JBullet upstream](https://github.com/stephengold/jbullet), version 1.0.3,
  zlib license; vecmath 1.5.2 and stack-alloc 1.0.2 are explicit runtime dependencies.

The user authorized reference to the CustomSteve analysis tree and live Ghidra.
Ghidra target observed 2026-11-17: `df_java_bridge.dll`, x86-64 PE,
SHA-256 `dc13e3f11e4a95b02a1778d6242a534b2cffcaefc2d67454cf3f5f42e8933d97`.
Export inventory confirms separate bone Morph, grants, IK, skinning and physics
service boundaries. This is static interface evidence, not observed runtime
behavior or copied implementation. New engine code and test assets are independent.

Connected Blender: 5.3.0. Existing deer scene must be preserved; create a separate
character scene/project. Screenshot acceptance waits at least ten seconds.
