# Custom Rig Native Pose Calibration

RigWorks can combine imported locomotion with Minecraft's actual player pose
sampler. The native sampler runs once per character tick, not once per render
pass or vertex. Scripts select channels and weights; Java evaluates the
hierarchy. Existing Oculus VBO and skinning caches consume the completed pose.

## Profile Data

An FBX A-pose is not Minecraft's neutral arms-down pose. Bone names alone do
not calibrate that difference. Each humanoid binding may specify:

```json
{
  "right_upper_arm": {
    "node": "MyArm.R",
    "referenceRotation": [0, 0, 0, 1],
    "nativeRotation": [0, 0, 0, 1]
  }
}
```

Both rotations are normalized XYZW quaternions, with identity defaults.
`referenceRotation` retains its existing rotation-axis correction meaning.
`nativeRotation` post-rotates the imported bind-local rotation when constructing
the native pose candidate. It keeps local translation and signed scale and does not
alter source nodes, skin bind offsets, imported clips or unselected channels.
Two-argument `HumanoidBone` construction remains supported.

Calibrate each limb against its actual imported bind data. Align the upper
arm and forearm to the native neutral direction, then the wrist/palm. Do the
same for thighs and shins. Preserve the intended shoe/sole orientation: a
high-heeled foot bone need not point horizontally. Arbitrary quaternion values
copied from a different rig are not a calibration.

## Lusia State Policy

The Lusia source and installed development resource packs share the calibrated
profile; the source controller is also copied to both development game directories.
Local workbench equipment overrides can differ from the resource definition.
Twelve native limb corrections were
computed from the Assimp-imported FBX. Arms, forearms, palms, thighs and shins
use calibrated neutral directions; feet retain their original global
orientation to preserve the high-heeled geometry.

| State | Pose Source |
| --- | --- |
| Idle, walk, sprint | Imported `n_idle`, `n_walk`, `n_run` |
| Head aim | Native head channel |
| Holding, item use, accepted swing | Native physical arm channels over the imported base |
| Crouch and crouch walk | Native body, limbs and root |
| Swimming and elytra | Native limbs plus the renderer's root rotation |
| Riding, sleep, death | Native pose and renderer orientation |
| Creative flight, jumping, falling, climbing, standing in water | Native baseline player pose |

Full native and arm weights approach their targets over 0.12 seconds. The
controller submits one native selection per tick and continues the imported
base underneath. Main/off-hand inventory is mapped to the player's physical
left/right arms. `lusia:poseSource`, `lusia:nativeBody`,
`lusia:nativeLeftArm` and `lusia:nativeRightArm` expose the policy state for
diagnostics. Native holding does not curl custom finger bones automatically.

Ordinary jumps and falls use actual `onGround` observations; `input.jumping`
is the held jump key and cannot identify flight. Lusia retains the selected
idle/walk/run base and advances its playback through flight and short landing
contacts. Three distinct grounded tick observations release the native air
layer, preventing a one-tick run-pose flash between repeated moving jumps.
Holding space after a sustained landing does not itself retain that layer.
`lusia:airborne`, `lusia:groundTicks` and `lusia:airPhase` expose contact state.

The native driver adds a bounded visual knee bend only when both thighs and
shins are mapped. It follows ordinary airborne contact weight and actual
vertical velocity, targeting .33-.57 radians of knee bend and half that amount at
the hip. Knees retain their local pivot and inherit the posed thigh/helper
frame. Swimming, flight, climbing, riding, sleep and death disable this ordinary
jump adjustment; profiles without articulated legs retain native rigid-leg
behavior. This is an animation approximation and does not change Minecraft
movement, gravity, collision or network authority.

The driver samples `PlayerRenderer.setupRotations`, including elytra, swim,
sleep and death turns. It removes the yaw supplied by the replacement renderer
and applies the remaining root transform to the composed skeleton. Blender
inspection confirmed all weighted Lusia bones descend from the pelvis, so
that transform covers the entire visible model. Held-item mounts consume the
same rendered pose.

Partial native arm/head layers retain authored joint translations and inherit
the imported parent/helper motion. They must not reuse local matrices that
cancel the animated chest or clavicle from a full-native candidate. The Lusia
FBX probe originally measured 1.3-5.6 cm of shoulder drift in that case; the
corrected partial-layer probe measured zero for idle/walk/run. Full-native
candidate coordinates stayed unchanged.

Use the [equipment workbench](EQUIPMENT_TRANSFORM_WORKBENCH.md) to calibrate
individual left/right sockets in the running world and persist their JSON.
Socket transforms position the item; they do not solve arm IK or finger grips.

## Authored Clips And IK

All 33 original actions remain in the editable blend/GLB. They include
cinematics rather than a full Minecraft action set. `SQ_fly` is an upright
hover with over two metres of root displacement, and `SQ_Fly_talk_01` is also
upright. They are unsuitable replacements for horizontal elytra or swimming.
The native baseline does not create new authored crouch, swim, death, combat
or finger animations.

Use Blender to author or bake model-specific silhouettes, finger grips and
action timing. Use the script for state selection and blend policy. Add
calibrated IK only for contacts such as feet on terrain or a support hand on
a weapon. IK cannot infer contacts, fix all interpenetration, or replace
authored body mechanics. Lusia currently has no newly installed hand-contact
or ground-contact solver in this change.

## Verification

The controller policy test covers imported locomotion, special states,
transition weights, physical hand selection and one native submission per
tick, plus moving held-space jump sequences, stable landing hysteresis and
uninterrupted locomotion phase. Java tests cover strict profile parsing, candidate-only calibration,
preserved bind data, disabled-native clip behavior and a calibrated hand
following a root rotation, contact-weight lifecycle and articulated knee
hierarchy/pivots. The external FBX calibration tool verifies limb
directions numerically.

Live GPU acceptance run `1794930685142` passed idle, walk, sprint, walking
continuous jumps, sprinting continuous jumps and crouch with the calibrated
profile and corrected composition. Both moving-jump stages held forward and
jump through four takeoffs while preserving the authored base clip. The
workbench also passed actual translation, rotation-ring and scale drags while
the character pose was frozen. Bed/vehicle contact, swimming, elytra,
sleep, death and model-specific finger grips still need separate visual checks.
