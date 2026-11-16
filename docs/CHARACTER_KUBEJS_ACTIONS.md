# KubeJS Player Action Templates

The optional `client_scripts` integration lets a pack own player action policy.
Java still advances animation, IK, physics, skinning and rendering once per tick.
Script callbacks run once per character tick, before pose evaluation; they never
run inside a bone, vertex, physics substep or render loop.

The complete editable demo is
[`assimp_player_actions.js`](../examples/kubejs/client_scripts/assimp_player_actions.js).
It contains independently authored Mo' Bends-style poses and decisions. It does
not load or redistribute Mo' Bends classes or require its JAR.

## Enable

1. Install this mod's supported KubeJS/Rhino dependencies.
2. Put the example in `kubejs/client_scripts/assimp_player_actions.js`.
3. Enable the character in `/assimploader character menu`, or set the demo's
   `config.autoEnable = true` to select its block-player profile automatically.
4. Reload client scripts with KubeJS's client-script reload workflow. Controller
   registration replaces the same ID, and the latest registered ID is selected.

The demo is opt-in and is not installed in an existing pack automatically.
It controls the current local third-person character. It never changes server
collision, inventory, damage, reach or movement. First-person arms and remote
players remain outside this renderer's current scope.

## Included Actions

| Action | Source / behavior |
|---|---|
| Idle, walk, sprint, crouch, crouch-walk | Existing Blender clips, movement cadence and grounded foot IK |
| Jump, fall, swim, flight, elytra, ride, sleep, death | Existing state selection and Blender clips |
| Climb, sprint-jump, sitting on nonliving vehicles | Script posture overlays with forward-bending knees |
| Sword | Five editable slashes, reset window, guarded stance, sprint stance, configurable spin gate |
| Empty hand | Alternating punches and fist guard |
| Tools | Native-phase chopping / swinging |
| Bow | Configurable draw duration, support arm and head-relative aim |
| Shield | Physical using-hand block pose |
| Eat / drink | Hand-to-mouth motion synchronized to item use ticks |
| Crossbow, spear, spyglass, horn | Editable item-use poses |
| Main/offhand torch | Physical hand hold overlay; yields to active/two-handed actions |
| Cape | Optional mapped-bone overlay, skipped when the asset has no cape bone |

This is a configurable equivalent action set, not a frame-for-frame reproduction
of the referenced mod. The fixture has no cape mesh. The cape hook only affects
an asset that supplies a mapped cape bone. Sword/arrow trail effects, armor and
cape/elytra geometry are distinct rendering extensions; this action template
does not claim to supply them.

## Customization

| Entry | Meaning |
|---|---|
| `config.items` | Ordered item-ID / item-tag classification overrides |
| `config.attacks` | Named normalized keyframes and easing for arbitrary attacks |
| `config.combo` | Editable ordered sword move IDs |
| `comboResetTicks`, `guardStartTicks`, `guardEndTicks` | Combo and stance windows in game ticks |
| `spinAttack`, `spinRequiresSprint` | Spin eligibility |
| `alternateFists` | Alternate physical fists for main-hand punches |
| `config.mask` | Bone-role weights for the upper-body action overlay |
| `config.bones` | Per-template role to node-name overrides |
| `config.poses` | Static item-use / stance poses |
| `config.blockedStates` | Player states that suppress action overlays |
| `config.before(frame, config)` | Full replacement hook; return true after handling the tick |
| `config.after(frame, config)` | Additional Morph, IK, motion, physics or pose behavior |

All attack progress comes from Minecraft's **accepted swing generation and
normalized swing progress**. A new accepted swing immediately samples the next
move at its new phase. There is no script animation queue and no fixed half-second
attack delay. Clicks rejected by Minecraft do not create extra gameplay attacks.
The demo does not alter the player's attack strength or actual hit timing.

Example item overrides, inserted near the top of `config.items`:

```javascript
{ id: 'example:katana', attack: 'sword' },
{ tag: 'example:heavy_tools', attack: 'tool' },
{ id: 'example:magic_staff', use: 'bow' },
{ id: 'example:lantern', hold: 'torch' }
```

Define `config.attacks.heavy_slam`, then use
`{id:'example:hammer', attack:'heavy_slam'}` to dispatch it directly. For an
additional use pose, define `config.poses.cast`, then use
`{id:'example:wand', use:'cast'}`. ID and tag filters on one rule combine with AND.
Rules are checked in order for the requested classification field.

Attack keys are `[phase, pose]`, ordered from 0 to 1. A pose maps semantic roles
to local Euler XYZ degrees. `arm`, `forearm`, `hand` target the action hand;
`support_arm`, `support_forearm`, `support_hand` target the opposite hand.
Left-handed playback swaps the arm roles and mirrors Y/Z rotations. Full role
names such as `chest` and `spine` are also supported. Easing accepts `linear`,
`smooth`, `in`, `out`, or a JS function receiving and returning a normalized value.
Keep phase keys strictly increasing and define the bones you want for each move.

Only listed roles are affected. Attack overlays contain no leg bones, so walking
continues while attacking or using items. The spin adds a visual hips rotation;
it does not rotate the actual player or move their collision box.

## Controller API

```javascript
Assimp.character().register('pack:my_player', function (frame) {
  frame.defaultMotion() // base locomotion cadence and head look, without native action replacement
  var input = frame.input()
  var state = frame.state()
  var model = frame.model()
  if (String(input.get('swingActive')) === 'true') {
    model.pose('my_action', {bones: {
      'UpperArm.R': {rotation: [-90 * Number(input.get('swingProgress')), 0, 0]}
    }})
  } else model.removePose('my_action')
})
```

Use `frame.defaultAnimation()` to request the complete built-in controller,
including its legacy full-body action selection. Use `frame.defaultMotion()`
(alias `locomotion()`) for the reusable locomotion and head-aim layer underneath
scripted actions. Do not call both in the same tick.

`Assimp.character()` supplies `register(id, callback)`, `unregister(id)`,
`clear()`, `controllers()`, `use(id)`, `useDefault()`, `active()`, `failure()`,
`enable(boolean)`, `profile(resource)`, `reload()` and `status()`.

Registration/selection and profile/enable settings are allowed during top-level
client-script loading on Forge's mod loading thread. Character handles and frame
controls require the client thread. Registration only stores the callback; it
does not run a solver or load a model on that loading thread. Client-script reload
clears prior registrations and the next character tick retires old policy state.

Script state is a mutable Java Map belonging to the current model/player binding:
use `state.get(key)` and `state.put(key, value)`. It is cleared on controller
replacement, model reload or world/player replacement. Retain values in state,
not the tick-scoped `frame`. A frame expires at callback return.

An exception disables the active script controller and restores default player
animation. Script poses/layers, synchronized clocks and modified rig controls are
cleaned up. Diagnostics expose the script failure separately from model errors.
Returning to the default controller never removes the configured character model.

## Player Observation Snapshot

`frame.input()` is a read-only Java Map. Use `.get('field')`, `Number(...)`,
`String(...)`; do not depend on Java Map property sugar or Java boxed truthiness.

| Fields | Contents |
|---|---|
| `tick`, `time`, `delta` | Client tick, seconds, nominal 0.05 seconds |
| `forwardVelocity`, `strafeVelocity`, `verticalVelocity`, `speed` | Body-relative blocks/second |
| `bodyYaw`, `headYaw`, `headYawRelative`, `headPitch` | Degrees |
| `onGround`, `crouching`, `sprinting`, `jumping`, `falling` | Movement states |
| `swimming`, `flying`, `elytra`, `riding`, `sleeping`, `dead` | Action states |
| `climbing`, `inWater`, `underWater`, `inLava` | Environment states |
| `passengerLiving`, `vehicleType` | Mount category and registry ID |
| `primaryArm` | Physical dominant arm, `left` / `right` |
| `swingGeneration`, `swingActive`, `swingProgress`, `ticksAfterAttack` | Accepted native swing clock |
| `swingHand`, `swingArm`, `swingItem` | Inventory hand, physical arm and item snapshot |
| `usingItem`, `useHand`, `useArm`, `useTicks`, `useRemainingTicks`, `useAnimation`, `useItem` | Item-use state |
| `mainItem`, `offItem` | Held item snapshots |
| `attackStrength`, `fallDistance`, `hurtTime`, `x`, `y`, `z` | Additional client observations |

Each item snapshot is a read-only Map with `id`, `empty`, `count`, `attackType`,
`useAnimation`, `tags`. `tags` is a Java List. Default attack classification is
empty hand = `fist`, SwordItem = `sword`, all other items = `tool`; the template's
rules can override this. `swingHand`/`useHand` are `main`/`off`, while physical
arms are `left`/`right`, including left-dominant and offhand actions.

## Pose, Clip And Solver Controls

`frame.model()` is the existing weak, generation-bound model handle with the
following additions. Handles expire on reload; reacquire from the next frame.

```javascript
model.pose('gesture', {
  bones: {
    'UpperArm.R': {rotation: [-90, 0, -15], weight: 1},
    'Chest': {rotation: [0, 15, 0], position: [0, 0, 0], scale: [1, 1, 1], weight: 0.4}
  },
  weight: 1,
  additive: false,
  stage: 'pre_ik'
})
```

Rotation is bind/rest-relative local Euler XYZ in degrees. Translation is in
the **imported node's local units**, so do not assume every imported bone uses
metres. `additive:false` replaces the specified bone's rest-relative pose;
`true` adds to the current composed pose. Existing translation/scale can be
affected by replacement, so use additive poses for offsets that must retain a
base clip's translation (the demo spin uses this). `weight` is 0..1. Stages are
`pre_ik` (default, before ground/IK/physics) and `post_physics` (explicit visual
override after physical feedback). A solver can override a pre-IK bone later.

`removePose(id)` removes a pose. At most 64 script layer/pose IDs and 256 bones
per pose are accepted. Values must be finite; unknown nodes/options are errors.
Scripts do not supply arbitrary native-loop callbacks.

| Handle control | Behavior |
|---|---|
| `play(clip, blendSeconds)`, `state(name)` | Existing base clip/state selection |
| `playback()` | Read clip, seconds, duration, speed, playing/looping/synchronized flags |
| `playback(speed, loop)` | Base clip rate and looping |
| `phase(progress)`, `releasePhase()` | Pin/release normalized base phase without advancing simulation |
| `layer(id, clip, weight, additive, mask)` | Named clip overlay with node weights |
| `layerPhase(id, progress)` | Native or custom phase for a clip overlay |
| `layerPlayback(id, speed, loop)` | Release layer phase and set its normal playback rate |
| `layerPlayback(id)`, `layerReleasePhase(id)` | Inspect the layer clock or release phase pinning |
| `layerWeight(id, weight)`, `removeLayer(id)` | Existing layer controls |
| `bones()`, `node(name)`, `bounds()`, `attachment(name)` | Numeric node/bounds/attachment inspection |
| `clearScriptControls()` | Remove script poses/layers and release base phase/rate |

Additional frame controls are `bone(role)`, `bones()`, `clip(intent)`,
`profile()`, `morphs()`, `morph(name,weight)`, `ikChains()`, `ik(name,enabled)`,
`ikTarget(name,x,y,z)`, `ikStatus(name)`, `motions()`, `motion(name,loop)`,
`stopMotion()`, `seekMotion(seconds)`, `pauseMotion(boolean)`,
`ragdoll(boolean)`, `automaticRagdoll()`, `physics()`,
`physicsBodies()`, `body(id)`, `resetPhysics()`,
`impulse(body,x,y,z)` and `torqueImpulse(body,x,y,z)`.
IK targets and physics impulses are client visual controls only. Physics must be
present in the selected profile; a missing rig/body/chain produces a diagnostic.

Example extension at the bottom of the demo, before registration:

```javascript
AssimpPlayerActions.config.after = function (frame, config) {
  if (frame.morphs().contains('Expression')) {
    frame.morph('Expression', String(frame.input().get('crouching')) === 'true' ? 1 : 0)
  }
}
```

## Verification

```powershell
node model-tests/kubejs/player_actions_test.cjs
.\gradlew.bat test build --console=plain
.\gradlew.bat test build runClient -PclientKubeJsSmoke -PwithoutOptionalMods --console=plain
```

Verified 2026-11-17: the offline policy harness passed. The real client/Rhino
gate loaded the complete concatenated template with zero script load errors,
reported `JavaScript assertions passed`, and wrote `success:true` to its result
file. The final full build passed with **367 tests: 366 passed, one opt-in
benchmark skipped, zero failures/errors**. Focused regressions cover loading-thread
registration, shared-handle cleanup, clock pinning, batched pose publication and
restoring paused/non-looping playback when retiring a policy.

This offline harness verifies policy decisions and command payloads: rapid
accepted retriggers, native-phase sampling, five-hit combo/reset/spin gates,
mirrored offhand, alternating punches, item uses, ID/tag rules, upper-body masks,
posture cleanup, torch priority and extension hooks. It is not proof of actual
Minecraft/Rhino loading or final rendered poses; those require the real client
acceptance gate documented in the integration report.

The client acceptance task concatenates the canonical example with its probe
script. It checks actual Rhino loading, loading-thread registration, nested
pose/array conversion, clip/layer clocks and the unchanged demo controller against
a real Assimp model handle with synthetic input. This is distinct from the
earlier 13-stage actual-player acceptance and does not establish a visual review
of every new scripted action. The Gradle task fails if its machine-readable
`run/kubejs-smoke-result.json` is missing or reports failure.

## Reference Evidence

Local reference: `D:/Downloads/MoBends-forge-1.20.1-5.1.5.jar`, SHA-256
`db7658ac859329a312c133e3c2d72b10a522bdf6fd50911d87ee92ee9e12b194`.
`javap -p -c` on `SwordAction` confirms the five ordered slash classes, accepted
attack change detection and 20-tick combo reset. This template independently
authors equivalent policies and poses against the AssimpLoader rig. The original
mod's classes/assets are not bundled. Other preset categories follow the user's
provided behavior inventory; this document does not claim a full binary audit.
