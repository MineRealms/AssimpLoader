# Selective Vanilla Animation

RigWorks client controllers can select native sampling and skeletal application independently. `frame.defaultAnimation()` retains the previous full native policy. `frame.defaultMotion()` / `locomotion()` retain native locomotion while suppressing attack and held-item poses.

```js
frame.nativeAnimation('rigworks:animation/native/default.json')
frame.nativeAnimationDefinition({ attack: false, items: true, rightArm: 0.5, leftLeg: 0 })
```

`nativeAnimationDefinition(options)` validates a small inline object each call. The distinct method name avoids Rhino String/Map overload ambiguity. `nativeAnimation(resource)` caches the compiled immutable JSON once per player/model binding, up to 32 definitions. `reloadNativeAnimation(resource)` explicitly replaces that cached definition only after successful validation; call once from script state when an asset changes. Binding disposal clears the cache. A script reload alone does not imply that an existing binding's resource cache has refreshed.

| Field | Default | Meaning |
| --- | --- | --- |
| `version` | `1` | Schema version |
| `enabled` | `true` | Enable the native modifier |
| `attack` | `true` | Sample native swing, including body twist |
| `items` | `true` | Sample both native arm poses: bow, crossbow, shield, eating, holding |
| `root` | `1` | Renderer root orientation/translation: flight, swimming, sleeping, death |
| `body` | `1` | Hips, spine, chest and upper chest |
| `head` | `1` | Neck and head |
| `leftArm`, `rightArm` | `1` | Upper arm, forearm and hand of that physical side |
| `leftLeg`, `rightLeg` | `1` | Thigh, shin, foot and toes of that physical side |

Weights are finite numbers in `[0,1]`; unknown fields, strings used as numbers and nonboolean switches are rejected. Resource JSON is limited to 4096 characters. Missing fields use the defaults. The included `rigworks:animation/native/motion.json` matches `defaultMotion()`.

## Composition

1. Native behavior is evaluated on a separate vanilla model. Attack/item switches affect sampling; head look remains available internally for aimed weapons even when head output is omitted.
2. A bind-referenced candidate is evaluated from `SkinBindPose.locals`. Selected channels blend local translation, rotation and scale into the incoming skeletal pose. Zero preserves the incoming local matrix exactly; selecting an arm or leg also resets its secondary joints toward native bind pose.
3. Root contribution transforms the composed skeleton once at the mapped hips, including limbs supplied by clips. It can be retained while body weight is zero, or omitted while body motion remains.
4. AssimpLoader model processors evaluate their clip/pose layers after native MOTION and before IK. Existing physics stages continue afterward.

Weights describe skeleton regions, not isolated semantic deltas: disabling attack removes its effects from every sampled part; enabling it while selecting only one arm applies the attack only through that arm's output. Native two-handed item actions are sampled together to preserve vanilla logic. Omitting one arm deliberately delegates that arm to another layer. No automatic two-hand IK is implied.

Partial channels use ordinary rig hierarchy inheritance. A preserved head or arm can therefore move globally when its selected parent body moves; its local pose stays intact. For a full native pose, independent vanilla limb orientations are preserved as before. Custom additional humanoid roles retain the legacy bind reset when all anatomical weights are one, and remain untouched for partial selections.

The optional `examples/kubejs/client_scripts/rigworks_native_channels.js` combines native behavior with the existing JSON clip/pose processor example. Special movement and item interactions keep their full native pose. It requires the example processor resource pack and the Blender block-player rig; no changes are made to server collision or authority.

## Hand Contacts

The tick frame also delegates item-support policy to RigWorks' configured hand contacts:

```js
var rules = frame.contacts()
if (rules.contains('my_pack:two_handed')) {
  frame.contact('my_pack:two_handed', true) // primary item is in the main hand
  frame.contactWeight(0.75)
}
// frame.noContact() disables this tick's contact selection.
var status = frame.contactStatus()
```

The JSON grip definition still owns item selectors, socket offsets, pole and bend limits. Script controls select a rule and weight for this tick; they do not duplicate the numerical solver. `contactStatus()` reports the last evaluated result (`available`, `rule`, `support`, `error`, `clamped`). A missing contact system returns an empty rule list and `available: false`; selecting or weighting a missing system throws a diagnostic error. `noContact()` is safe when no system exists.
