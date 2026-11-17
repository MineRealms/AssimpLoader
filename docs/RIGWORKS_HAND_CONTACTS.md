# RigWorks Hand Contacts

AssimpLoader provides a constrained analytic two-bone solver. RigWorks selects
an item contact once per client tick, reads the primary hand's item display frame
from the incoming pose, and solves the opposite arm. No JavaScript runs inside
the solver. The primary arm is not moved to force an unreachable contact.

Contacts start disabled. Enable **Contacts** in LookDev's Player page, set
`character.handContacts` in `rigworks-client.toml`, or use the client command
`/assimploader character contacts on|off`. This is independent of the motion
mode and RigPhysics. `/assimploader character` includes the last contact error
and whether reach or bend limits clamped the target.

## Equipment Data

The profile's `equipmentDefinition` names the JSON resource. Existing files
without `grips` retain their original behavior. A grip can be selected by item
IDs, item tags, physical primary arm and state:

```json
{
  "version": 1,
  "right_hand": {"node":"Hand.R"},
  "left_hand": {"node":"Hand.L"},
  "grips": [{
    "id":"rifle_support",
    "tags":["my_pack:two_handed"],
    "states":["held"],
    "primary_arm":"right",
    "socket":[0.15,-0.1,0.05],
    "pole":[0.25,-0.5,-0.15],
    "min_bend":2,
    "max_bend":160,
    "weight":1,
    "free_support":true
  }]
}
```

Supply the actual model's mount offsets and quaternion, not these minimal
example mounts. Rules are evaluated in file order; the first match wins.
`using`, `charged` and `held` are alternatives. Put charging rules before charged
holding rules. `primary_arm` is `any` by default, or `left`/`right`; Minecraft's
main/off-hand setting is resolved to this physical arm. `free_support` optionally
requires the other inventory hand to be empty. At most 32 rules are accepted.

`socket` is a wrist contact point in the centered item model frame after the
resolved third-person camera transform. Item model coordinates in [0,1] need
their center (0.5,0.5,0.5) subtracted, matching ItemRenderer's centering step.
It is not a world-space position. The original model override, display context,
left-hand transformation and equipment scale participate in the target frame.
Different display transforms may require different left/right rules.

`pole` is a shoulder-relative vector in the rig's bind model axes. It follows
the torso's current rotation; its X component is mirrored for a right support
arm. This controls the elbow plane without assuming the imported bone's local
axis. Bend limits are degrees, where zero is a straight arm; 180 is excluded.
Unreachable targets clamp to the permitted segment length and bend range.

For an oriented palm/finger contact, add `rotation` (quaternion),
`orientation_weight` and `hand_offset` (metres in the hand's oriented frame).
A nonzero `hand_offset` requires full orientation weight so the target cannot
depend on an unknown partially blended hand orientation. With partial overall
weight, the output is blended back toward the incoming pose; exact contact and
the full-weight bend limits are therefore not promised at partial weight.

## Script Policy

```javascript
RigWorks.register('my_pack:contacts', function (frame) {
  frame.defaultAnimation()
  // JSON normally selects automatically. A script may select a rule explicitly.
  // frame.contact('rifle_support', true) // true: main inventory hand
  frame.contactWeight(0.8)
})
```

`contacts()` enumerates rule IDs. `contact(id, mainHand)` selects one without its
automatic item/state filters. `contactWeight(0..1)` changes its influence for
this tick. `noContact()` cancels it for this tick. `contactStatus()` reports the
previous completed solve, not a speculative result during the callback. Overrides
reset every tick; a failed controller restores automatic selection before the
native fallback. The global switch and ragdoll state remain authoritative.

## Evaluation And Scope

Contact IK runs after motion, clip/pose overlays and ground alignment, before
rigid-body feedback. Physics may subsequently move a dynamic hand; contacts are
disabled while ragdoll is active. Equipment and fishing-line consumers continue
to read the same completed visual snapshot. Server collision, combat and
inventory are not changed.

The block-player rules are optional wrist-target demonstrations calibrated for
this fixture's poses and vanilla item display transforms. They do not infer or
guarantee palm contact with a weapon's mesh. A resource pack changing that mesh,
display transforms or bone proportions must author its own grip data. FBX IK and
physics authoring graphs are not recovered from baked animation automatically.

Numerical acceptance covers real FBX bone axes, bone lengths, primary-hand
invariance, reachable/unreachable targets, bend limits, zero-weight behavior,
and actual ItemRenderer transforms. The 864-pose runtime grid compares each
full-weight wrist against an independent cosine-law reach calculation and
reports clamped targets separately. These fixed demonstration targets can become
unreachable as the primary arm and view direction change.

Acceptance samples completed tick poses. It does not establish exact contact
between interpolated ticks, collision-free hands, accuracy after physics
feedback, or arbitrary weapon geometry contact.
