# Character solver engine: Blender workflow

## Included fixture

Open `model-tests/block_player/block_player.blend`, select scene
`BlockPlayer_CharacterEngine`. The existing deer scene is also preserved in that
file. The independently generated player has 23 bones, 624 vertices, 312 triangles,
blended skin weights, Blink/Smile shape keys and four PBR texture channels.

Fifteen NLA tracks cover idle, walking, running, crouching, jumping, falling,
swimming, flying, elytra, riding, sleeping, death, item use and swinging. These
are compact validation animations, not production-quality character motion.

The muted authoring IK constraints demonstrate the Blender authoring setup.
Runtime IK, append transforms and physics are described by JSON sidecars;
FBX/GLB do not preserve their complete live constraint semantics. Baking a
constraint into an action records its resulting transforms, not an IK solver.

## Re-export

After editing the existing scene, execute this in Blender's Python console:

```python
from pathlib import Path
p = Path("H:/MinecraftMods/AssimpLoader/model-tests/scripts/export_block_player.py")
exec(compile(p.read_text(), str(p), "exec"), {"__file__": str(p)})
```

For validation without export, add `"ASSIMPLOADER_EXPORT": False` to that
execution dictionary. The script checks weights, blended vertices, action names,
height and floor, and restores selection, active action, NLA mutes and scene.
It exports only the fixture's rig and skinned meshes, restricting GLB to this
scene and FBX to its NLA tracks. Save the `.blend` separately after authoring.
The initial builder creates a new scene; use the exporter for subsequent edits.

## Runtime resources

All paths below are under
`assets/assimploader/models/character/block_player/` in mod or resource-pack assets.

| Resource | Responsibility |
| --- | --- |
| `profile.json` | Model, scale/yaw, humanoid roles, action mapping, head aim, sidecar references |
| `block_player.fbx` / `.glb` | Geometry, skin, imported actions, vertex morphs and PBR materials |
| `block_player.instance.json` | Existing AssimpLoader states, interpolation, foot placement and Spring Bones |
| `rig.json` | Append transforms, constrained IK, bone/group morphs and explicit morph defaults |
| `equipment.json` | Physical hand sockets, metre offsets and vanilla held-item orientation |
| `physics.json` | Body shapes, bone feedback modes, collision masks, joints, springs and fixed-step budget |

Resource packs override these assets through normal Minecraft resource lookup.
An arbitrary local filesystem path is not accepted as a character profile.
Unknown versioned fields and unresolved bones fail with diagnostics.

## Units And Ownership

- Mesh/model coordinates use metres. Author at unit scale 1 with feet at zero.
- `physics.json` body offsets, shape dimensions, joint positions and linear
  limits use metres in rigid bone/body axes. BOX size means half extents;
  CAPSULE uses radius and cylinder height along Y. Angles are radians and
  quaternions are XYZW.
- This FBX retains 100x imported bone scaling. The physics adapter strips it
  before applying metre offsets and restores the render scale during feedback.
  Spring Bone tails use the imported node's local units instead.
- `profile.scale` scales physical geometry, offsets and feedback consistently.
  The profile yaw aligns authored forward with the player's visual forward.
- FOLLOW bodies track animation and become dynamic when ragdoll is enabled.
  DYNAMIC bodies feed position/rotation back; HYBRID retains animated translation
  and feeds rotation back. Each avatar owns an isolated Bullet world.
- A Spring Bone and a rigid body cannot both own the same bone. Springs in
  `physics.json` apply bounded spring forces to Bullet joints; contacts,
  constraints and integration are solved by Bullet.
- Client block sampling reads only loaded blocks, caps shape count and uses
  cached proxies. Teleports reset physical state. Nothing changes player
  dimensions, movement, damage rules, server entities or authoritative hitboxes.

## Enable And Inspect

Use `/assimploader character enable` in a world, then third-person view. The
default profile is the included block player. Related commands:

```text
/assimploader character
/assimploader character menu
/assimploader character profile assimploader:models/character/block_player/profile.json
/assimploader character reload
/assimploader character morph Expression 1
/assimploader character ragdoll on
/assimploader character ragdoll auto
/assimploader character motion play solver_probe false
/assimploader character motion pause
/assimploader character motion seek 1
/assimploader character motion resume
/assimploader character motion stop
/assimploader character disable
```

The profile, enable flag and performance choices are stored in the client TOML
under `[character]`. Physics can be disabled independently. Collision
sampling interval/radius and world proxies are configurable. The physics sidecar
sets fixedStep, maxSubsteps, solverIterations and maxWorldBoxes. A dropped-time
counter reports overload; raising substeps increases CPU work.

The replacement currently targets the local player's third-person world render.
The profile menu lists `models/**/profile.json` resources and accepts an explicit
resource ID. Apply saves the selected profile, then the existing async loader
initializes it. The menu reports loading/failure status without pausing physics.
First-person arms, inventory preview and remote-player selection are separate
render integration work. A bad profile/model falls back to the vanilla player.
Resource reload, world switch and logout release the former animation/physics owner.

## Verification

```powershell
.\gradlew.bat test --tests cn.minerealms.assimploader.character.*
.\gradlew.bat runClient -PclientCharacterSmoke=Character_BlockPlayer_Acceptance --console=plain
```

The second command requires an existing disposable local world with that folder
name. It creates a platform in that world and moves the player there. Do not
point it at a world you want to preserve. Each stage settles for at least 10.5
seconds before assertions and capture. The progress ledger records the actual
test run and screenshots; launching the command alone is not acceptance.

See [semantic motion](CHARACTER_MOTION.md) for destination-key XYZ/rotation
Bezier curves, linear Morph tracks and stepped IK switches, and
[held equipment](CHARACTER_EQUIPMENT.md) for physical-hand mapping.

PMX/VMD parsing, SDEF/QDEF, UV/material/flip/impulse morph semantics and arbitrary
cross-rig retargeting are not implied by this Blender workflow. LBS, imported
vertex morphs and the implemented semantic sidecars are the supported path.
