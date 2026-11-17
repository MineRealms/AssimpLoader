# Player Pose And Action Integration

## Cause And Repair

Replacing `RenderPlayerEvent` bypassed the original `PlayerRenderer` and
`PlayerModel` setup. A clip selector alone cannot reproduce interpolated swim
strokes, Forge held-item arm poses, sleeping roots and flight pitch. The old head
aim also used the wrong visible forward axis for the fixture.

`VanillaPlayerPoseDriver` now calls original arm-pose selection, model preparation,
model animation and renderer root rotation. `VanillaPoseRetargeter` transfers
independent part rotations and offsets into the hierarchical FBX skeleton while
preserving limb lengths. It does not apply body rotation twice to child limbs.
Minecraft already applies crouching render offsets before the replacement hook;
the solver does not add a second crouch root offset.

`SkinBindPose` reconstructs neutral transforms from inverse skin binds. Blender
exports are saved at frame zero outside NLA ranges with strip extrapolation off,
so an attack track cannot silently become the model's default pose. The block
player FBX, GLB and Blender source have been repaired and re-exported. Model-space
script rotations are converted through each bone's recovered bind orientation,
which keeps upper/lower limbs consistent despite imported FBX bone roll.

## Ownership

| Mode | Locomotion/head/root | Attacks and item use |
|---|---|---|
| VANILLA, default | Actual original player model and renderer | Actual original player model arm poses and swing progress |
| SCRIPTED | Original baseline via `frame.defaultMotion()` | KubeJS templates with role masks and native accepted swing phase |
| CLIPS | Existing profile clip state machine | Existing authored clip selection |

`frame.defaultAnimation()` requests the full original pose. Missing or failed
script controllers fall back to that original driver. Changing modes rebuilds
the binding and releases old animation, script and physical owners. RigPhysics
has a separate switch. Disabling it does not disable original player behavior.

Java evaluates animation, IK, Morph, rigid bodies and skinning. KubeJS supplies
one policy callback per character tick, outside vertex and physics substep loops.
Server movement, attack acceptance, reach and collision remain authoritative.
Rapid input follows accepted swing generations; no visual half-second queue is
introduced. Rejected clicks do not create extra swings.

## Referenced Action Set

The local Mo' Bends 5.1.5 JAR was inspected with bytecode/decompilation tools.
Useful architecture was its separate base/upper-body action layers, five sword
moves, a reset window, stance windows, handedness and item classification. The
editable demo uses independent equivalent poses and decisions rather than
redistributed classes or exact copied frame curves. Reference material remains
under ignored `build/reference`; it is not included in the mod artifact.

The demo covers sword up/down/inward/outward/spin, alternating punches, tools,
bow, shield, food/drink and additional use/hold poses. The default sword spin
requires sprinting and appears as the fifth eligible move. Its other four moves
are arm/torso poses and are tested for actual imported hand displacement.

## Verification Boundaries

Unit and offline tests cover original-pose retargeting, imported reference poses,
actual FBX arm/knee model axes, travel/head direction and editable policy timing,
combo reset and mirroring. A real Rhino/Oculus title-screen acceptance executes
all five demo attacks against the imported FBX and verifies hand movement.

`VanillaRuntimeProbe` uses a non-world player to feed deterministic states into
the actual Minecraft renderer/model and Mixin in an active client world. It checks
head pitch, crouch, walk, three swim phases, flight pitch, ride/sleep, item arm
poses and four attack phases without mutating the real player's inputs/save.
Its results identify uncovered cases explicitly.

Runtime run `1794883594421` passed. At pitch -60/+60 the visible face Y direction
was +0.8660/-0.8660. Crouch lowered the head by 0.2275 model metres. Three swim
phases changed arm direction by 1.2528 and leg direction by 0.5649 (unit-vector
distance). Elytra body Y was +0.8660/0/-0.8660 at pitch -60/0/+60. The actual arm
dispatcher selected ITEM, BOW_AND_ARROW, BLOCK and CROSSBOW_CHARGE; four attack
phases changed arm direction by 1.0773. The real player's state was unchanged.

Run it with:

```text
gradlew runClient -PwithKubeJS -PclientLookDevSmoke=Character_BlockPlayer_Acceptance -PclientVanillaPoseOnly
```

The separate LookDev acceptance exercises real render ownership, mode/physics
changes, script failure fallback and the GUI/debug paths with an active shader
pack. A successful numeric pose probe is not evidence for remote players,
first-person arms or all third-party renderer combinations.

## Remaining Scope

The current replacement is local third-person. Native armor, cape and elytra
geometry layers and first-person/remote replacement require their own rendering
integration. The fixture has equipment attachment definitions but its simplified
hands are not a complete vanilla `PlayerModel` layer substitute. PMX/VMD parsing,
MMD-specific weights/deformation and their format-specific constraints remain
deferred. These limits are separate from original locomotion/action pose reuse.
