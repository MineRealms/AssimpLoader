# In-World Equipment Transform Workbench

The workbench edits RigWorks physical left/right hand mounts over the normal
Minecraft world. Existing world lighting, Oculus PBR materials and the GPU pose
cache keep rendering. The character's current visual pose, displayed world
position, body heading and held item copies are frozen during calibration. There is no second preview
scene or model import.

## Open And Adjust

Load and enable a custom RigWorks character, then press **Ctrl+H**. The binding
can be changed in Minecraft Controls. Other entry points are
`/assimploader character equipment` and **Character > Equipment**.

| Control | Action |
| --- | --- |
| Right / Left | Select the physical hand, independent of the player's primary arm |
| W / translate tool | Drag a colored local XYZ translation arrow |
| E / rotate tool | Drag a local rotation ring |
| R / scale tool | Drag the item scale handle |
| Position XYZ | Bone-frame offset in metres |
| Rotation XYZ | Intrinsic XYZ Euler angles in degrees |
| Scale | Uniform item scale |
| Drag an axis label | Scrub that numeric component; Shift makes smaller changes |
| Up / Down in a field | Step a value; Shift makes smaller changes |
| Drag the world | Orbit the camera without changing player aim |
| Right/middle drag or Shift+drag | Pan the camera |
| Mouse wheel | Zoom |
| Shift+wheel | Move the camera framing vertically |
| F or focus icon | Switch focus between the body and selected hand |
| Ctrl+Z / Ctrl+Y | Undo / redo edits, including which hand was edited |
| Reset icon | Restore the selected hand's resource-pack definition |
| Save JSON or Ctrl+S | Save both hand mounts |
| Escape / close icon | Close and discard changes made since the last save |

Hover a gizmo handle to highlight it and read its axis/value tooltip. The wrist
binding point and configured item pivot are distinct markers. Selection geometry
identifies the current item while the numeric fields remain available for exact
values. The gizmo and mount use the same frozen character frame. Translation
uses the wrist's local axes; rotation uses the item's current local axes.
All scale handles edit the mount's uniform scale.

The gold selection overlay traces the actual baked item geometry. Generated
flat items use their pixel-side contours instead of an alpha-rectangle box.
This is an always-visible geometry-edge overlay, not a shader-pack silhouette
postprocess. Items using a custom built-in renderer currently retain the wrist,
pivot and gizmos but do not receive this geometry outline.

The surrounding world keeps ticking. This is a client visual editor: the camera does not
rotate the player, move the server entity or modify its inventory. It uses
vanilla visual block collision for the camera boom, ignores fluids, and stops
at unloaded chunks. Walls can constrain framing. Closing restores the previous
first/third-person camera mode and normal character display. If the real player
moved while editing, the character returns to its authoritative position.
Changing character/world invalidates the editor.

## Saved Data

Overrides are local to the current game directory:

```text
config/rigworks/equipment/<profile namespace>/<profile path>.json
```

If the profile path already ends in `.json`, no additional suffix is appended.
For example, `example:models/player/profile.json` writes
`config/rigworks/equipment/example/models/player/profile.json`. The file contains
an equipment definition, not a replacement character profile.

```json
{
  "version": 1,
  "enabled": true,
  "right_hand": {
    "node": "Hand.R",
    "position": [0, 0.05, 0],
    "rotation": [0, 0, 0, 1],
    "scale": 1
  },
  "left_hand": {
    "node": "Hand.L",
    "position": [0, 0.05, 0],
    "rotation": [0, 0, 0, 1],
    "scale": 1
  }
}
```

JSON rotations remain normalized **XYZW quaternions**, compatible with existing
equipment resources. The editor displays Euler degrees. Nodes, enable state,
the other hand and support-hand grip definitions are preserved. Saving validates
the definition and atomically replaces its file. Invalid overrides log a warning
and fall back to resource-pack equipment. Overrides load on character bind and
resource/character reload, including F3+T. There is no per-frame file access.

Remove the local file and reload the character to return completely to the
resource pack. To distribute calibration with a pack, place the saved equipment
JSON at the resource referenced by the character profile's `equipmentDefinition`.
The editor never rewrites resource-pack ZIPs or source FBX files.

The offsets apply to **all items using that hand mount for that profile**. They
are measured in the scale-free bone frame before the character profile's
uniform model scale. They precede Minecraft's own `THIRD_PERSON_LEFT_HAND` / `THIRD_PERSON_RIGHT_HAND` item
display transform. This editor does not rewrite individual items' display JSON
or create finger-grip animations.

## Performance And Verification

Preview and save replace only the small equipment definition. They do not reload
the character scene, re-run skinning, invalidate pose buffers or upload animated
vertices. The frozen pose reuses its resident deformation result, so ordinary
camera orbit does not dispatch skinning again. The screen uses the same world
rendering path as normal gameplay; the selection overlay runs only while editing.

Unit coverage includes transform/quaternion conversion, persistence, profile
isolation, invalid paths and two-hand edit/save/cancel transactions. The opt-in
`-PclientEquipmentWorkbenchSmoke` checks live previews, closing without saving,
JSON persistence, real translation/ring/scale dragging, pose freeze/release,
unchanged character ownership/VBO uploads, no redundant frozen-pose compute,
camera aim isolation
and a final screenshot. Combine it with imported-character GPU acceptance in a
copied world. Outputs are `equipment-workbench-smoke-result.json` and a screenshot
under the selected client game directory. The final editor stays open.

Live acceptance on 2026-11-18 passed both hand transactions, all three handle
modes and camera restoration with `RocketFlow_Local` enabled. Its frozen
interval recorded 834 resident mesh draws, 139 pose-cache hits, zero extra
compute dispatches and six total initial vertex uploads. Screenshots use
prefix `equipment-workbench-1794931781598`. Custom-renderer item outlines and
model-specific finger/contact animation remain outside this editor's scope.
