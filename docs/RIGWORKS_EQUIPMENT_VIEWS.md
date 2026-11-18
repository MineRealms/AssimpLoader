# Equipment And Player Views

RigWorks reuses native item rendering and native/compatible player RenderLayers.
The character pose is evaluated by the normal animation update; world, shadows,
equipment, first person and inventory views observe that completed pose.

## Equipment JSON

Existing version-1 `right_hand`, `left_hand` and `grips` definitions remain valid.
Named `sockets` and ordered `overrides` are optional. The first matching override
wins. Arm names always mean physical left/right, independent of main-hand choice.

```json
{
  "version": 1,
  "right_hand": {"node": "Hand.R"},
  "left_hand": {"node": "Hand.L"},
  "sockets": {
    "spyglass": {"node": "Head", "position": [0, 0, 0.08]},
    "back": {"node": "Chest", "position": [0, 0.2, 0]}
  },
  "overrides": [
    {"arm": "right", "items": ["minecraft:trident"], "states": ["using"],
     "contexts": ["third_person_right_hand"],
     "mount": {"node": "Hand.R", "position": [0, 0, -0.1]}}
  ]
}
```

Rules accept `items`, `tags`, `states` (`held`, `using`, `charged`),
`use_animations` and `contexts`. Item/tag selectors are alternatives; other
nonempty selectors must all match. Each rule declares exactly one named `socket`
or inline `mount`. Empty selectors accept any value. JSON and editor round trips
preserve sockets, rules and grip constraints. Offsets are metres; imported FBX
bone scale does not change item size.

Active spyglasses use the head frame and native `HEAD` display context, including
the native side offset and default pitch limit. An explicit `spyglass` socket is already the final item
frame and suppresses that default offset. Ordinary held items use the native
`ItemInHandRenderer` with third-person left/right context.

## Native Equipment Layers

`CharacterEquipmentLayers.render` temporarily supplies a reusable posed
`PlayerModel` adapter to the actual player renderer's layer list, then restores
the original model in `finally`. The ordinary held-item layer is excluded because
RigWorks draws hand sockets separately. Native armor hooks, trims/glint, head
items, cape, Elytra, arrows, stingers, shoulder pets and spin effects retain native
rendering behavior. Resource reload must call `CharacterEquipmentLayers.clear()`.

Head/limb parts follow the current humanoid bone frames. Body armor uses the
chest/spine/hips frame and inferred shoulder/torso/limb dimensions. Optional `head`
and `back` sockets override inferred attachment frames; their local axes follow
vanilla ModelPart coordinates (+Y down). This is an attachment adapter, not a
cloth or armor fitting solver. Arbitrary silhouettes and custom mod layers need
visual calibration; a rigid vanilla armor sleeve cannot bend at a custom elbow.

## First Person

`PlayerFirstPersonArmsMixin` replaces only `PlayerRenderer.renderLeftHand` and
`renderRightHand`. Native item equip/use, map rendering and hand-selection logic
remain active. A cached triangle mask accepts triangles whose three vertices have
at least half their weight on the selected upper-arm descendant chain. It never
draws the whole player, evaluates animation again or changes server hitboxes.

The mask shares existing deformed vertex buffers through `MeshDrawSelection`;
only its element buffer is separate. Materials and PBR retain engine rendering.
Oculus draws preserve the enclosing `HAND_SOLID`/`HAND_TRANSLUCENT` phase;
they must not replace its render-stage uniform with the world entity stage.
The imported shoulder-to-elbow reference axis is aligned with native +Y and the
arm chain's reach is fitted to the native 12-pixel first-person arm. This view
scale is separate from world character scale, so small arms do not disappear
below the native item camera. Authored elbow/wrist motion remains relative to
the fitted shoulder. Missing arm mapping or an empty mask leaves native
arms visible. First-person configuration can disable this adapter. Native Minecraft
does not draw an arm for every ordinary held item; this hook preserves that rule.

## Remote Players

The optional `rigworks:appearance` channel relays the selected profile, mode and
flight state plus a versioned full visual configuration and content-addressed
JSON/JS package. The server assigns revisions, uses the real sender UUID,
rate limits changes and supplies tracking/login/dimension catalogs.
Movement, inventory, use/swing state and gameplay authority remain vanilla.
Servers without this channel can still be joined; local character display works.

Remote characters share imported scenes but own separate animation controllers,
native pose drivers and hand contacts. Distance and instance limits bound their
work. Missing resources or invalid profiles leave the native player visible.
`SCRIPTED` playback uses a verified package in an isolated per-owner interpreter,
with native fallback on absence or failure. It does not execute another player's
callback in KubeJS's global context.

Both clients must already have matching resources. This channel does not transfer
FBX/textures. Complete equipment sockets/rules/transforms, character transforms,
rig/calibration and action mappings are included in the visual snapshot; generic
editor projects are separate. Upload is enabled by default and sends changed
content; disabling it withdraws the shared appearance. Remote display opt-out also
stops downloads. See [character configuration sync](CHARACTER_CONFIGURATION_SYNC.md)
for cache, limits, local persistence and runtime verification boundaries.

## Verification Boundary

Focused tests exercise schema selection/round trips, real FBX arm masks, shoulder
placement and equipment frame scale. They do not prove every mod's custom armor,
all silhouettes, map framing, shader pass behavior or every resource-reload path.
Those require the combined Forge client acceptance recorded by the integration
owner. Inventory preview reuses the custom-player render entry and the same pose;
it does not run an independent animation simulation.
