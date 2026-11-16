# Character held equipment

The optional `equipmentDefinition` character-profile resource selects physical right/left hand sockets. Without it, sockets default to the profile's `right_hand` and `left_hand` humanoid bindings, using their reference rotations. Missing hand roles omit that hand.

```json
{
  "version": 1,
  "enabled": true,
  "right_hand": {
    "node": "Hand.R",
    "position": [0, 0.055, 0],
    "rotation": [0.70710678, 0, 0, 0.70710678],
    "scale": 1
  },
  "left_hand": null
}
```

- `position` is metres along the posed bone's rigid axes. Imported FBX bone scale is removed before applying the offset.
- `rotation` is a normalized local quaternion `[x,y,z,w]`; `scale` controls the displayed vanilla item size. Character profile scale still applies to the entire character and its equipment.
- `right_hand` and `left_hand` are physical hands. The player's dominant-hand option determines which physical socket receives the main-hand inventory stack. The offhand uses the opposite socket.
- `enabled:false` suppresses equipment. Omitted or null sockets suppress that physical hand.
- The renderer receives the exact immutable interpolated `PoseSnapshot` used for the character's mesh. It does not read the subsequent simulation pose, advance time, or modify inventory.
- Vanilla third-person item display transforms, glint, item overrides, and item custom renderers run through Minecraft's item renderer. The definition supplies the socket orientation for that rig.

This path replaces local third-person world held-item layers only. Armor, capes, elytra geometry, first-person arms, inventory previews, and custom per-item grip/aim poses remain separate work. Server inventory, entity dimensions, collision, reach, and damage authority are unchanged.

The fixture definition supplies an initial grip. Visual acceptance with swords, blocks and shields, both dominant-hand settings, and ragdoll poses is still required; compilation and numeric transform tests alone do not establish that every item has a correct grip.
