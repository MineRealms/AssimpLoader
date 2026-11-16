# Deer Showcase

This opt-in creature exercises the same dynamic Assimp loading and configured
animation path as other entity renderers. It has no natural spawn registration.

## Resource Contract

- Entity: `assimploader:deer_showcase`.
- Model: `assimploader:models/validation/deer_showcase.glb`.
- Geometry preload: `assimploader:entity/deer_showcase`.
- Units: meters, Y up, model forward +Z; origin at ground level.
- Sidecar: `models/validation/deer_showcase.instance.json`, following the common
  `ModelInstanceDefinition` format. The model uses extensionless canonical cache
  identity `assimploader:models/validation/deer_showcase`.
- Automatic movement states: `idle` and `walk`. Additional named states can be
  requested directly, including the baked IK inspection state `ik_probe`.

The renderer does not infer bone names or install procedural modifiers. Exported
animation and sidecar configuration own those choices. A Blender IK constraint
must be baked into animation channels before export; Blender constraints are not
executed by the runtime.

## Commands

Commands require operator permission level 2.

```text
/assimpdeer spawn ~3 ~ ~3
/assimpdemo deer ~3 ~ ~3
/assimpdeer state @e[type=assimploader:deer_showcase,sort=nearest,limit=1] idle
/assimpdeer state @e[type=assimploader:deer_showcase,sort=nearest,limit=1] ik_probe
/assimpdeer ai @e[type=assimploader:deer_showcase,sort=nearest,limit=1] true
/summon assimploader:deer_showcase ~3 ~ ~3 {NoAI:1b,ShowcaseState:"idle"}
```

The convenience spawn command makes a persistent, stationary deer. It rejects
unloaded chunks, occupied space, build-height overflow and world-border overflow.
It does not alter terrain. Enabling AI selects `auto`, which requests `walk` while
the entity moves and `idle` otherwise. Disabling AI retains the selected state.

`ShowcaseState` is saved with the entity and synchronized to tracking clients.
State names accept 1-64 letters, digits, underscores, dots and hyphens. The server
does not read client assets; a state absent from the client's sidecar falls back
to `idle`. Each entity uses its own existing `ClientAnimationManager` instance.

## Runtime Checks

1. Export the model with skin weights, deformation bones and baked action tracks.
2. Start the client and create the deer on clear, loaded ground.
3. Allow at least 10 seconds for import and texture loading before taking images.
4. Check `idle`, `walk`, all authored action states and `ik_probe` independently.
5. Enable AI and check translation, facing and idle/walk transitions.
6. Save/reload the world and reload resources to check persistence and cache reset.

The server's entity bounding box is the collision shape for movement. Model-side
physics, spring bones, mesh picking and baked hoof IK are separate runtime or
asset features; creature registration alone does not verify them.

## Authored Asset

`model-tests/deer/deer_source.blend` preserves the user's original opened FBX.
`deer_rigged.blend` contains the single-mesh export rig and a separate hidden
`DEER_IK_AUTHORING` collection with four hoof targets and four knee poles. Unhide
that collection and hide `DeerMesh` to edit the IK rig against `DeerIKPreview`;
export uses the constraint-free `DeerRig` and `DeerMesh` only.
The `ik_probe` action is a baked four-leg lift cycle. All ten original actions are
retained, giving eleven exported clips at 30 FPS. Rebuild scripts, the FBX export,
albedo and numerical manifest are in the same directory.

The conversion merges 174 rigid source meshes into one skinned primitive with
35 bones and an embedded nearest-filtered albedo. Ear, tail and stirrup Spring
Bone settings, one torso spring collider, fourteen bone hit proxies and named
mounts are in `deer_showcase.instance.json`. Antlers remain rigid. These springs
are evaluated by AssimpLoader, not Blender physics. Movement uses the registered
entity's simple server bounding box; bone/mesh queries do not automatically apply
damage or provide server-authoritative projectile collision.

The model was supplied in the user's open Blender session. Its original author
and redistribution license were not supplied; do not treat this local validation
asset as cleared for publication.

```powershell
.\gradlew.bat test build runClient -PclientDeerSmoke=Scaffold_PBR_Test -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

The opt-in smoke first compares frozen CPU/GPU poses, then opens the specified
local test world, creates a raised test platform and a persistent deer, captures
idle/walk/gallop/baked IK and reload views, and exits. Each capture waits at least
ten seconds. It changes the test world's terrain and player location/mode.
