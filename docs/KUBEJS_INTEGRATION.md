# KubeJS Integration

Player action policies, editable Mo' Bends-style presets, native attack clocks,
procedural bone layers and character solver/physics controls are documented in
[KubeJS player actions](CHARACTER_KUBEJS_ACTIONS.md). The complete opt-in template
is [rigworks_player_actions.js](../rigworks/examples/kubejs/client_scripts/rigworks_player_actions.js).

After the module split, the generic `Assimp` binding belongs to AssimpLoader;
player policy uses the optional `RigWorks` binding, and server deer controls
belong to AssimpShowcase. See [module ownership](MODULE_SPLIT_AND_DATA_DRIVEN_PLAN_2026-11-17.md),
[animation processors](ANIMATION_PROCESSORS.md), and [shader parameters](SHADER_PARAMETER_BRIDGE.md).

Optional integration for Minecraft 1.20.1 / Forge. Development verification pins
KubeJS `2001.6.5-build.26`, Rhino `2001.2.2-build.17`, and Architectury `9.1.12`.
None is bundled or required for ordinary AssimpLoader installations.

## Useful Workflows

| Workflow | Entry Point |
|---|---|
| Quest, interaction or stage changes | Request a configured animation state or blend to a clip |
| Expressions and model variants | Change a mesh's Morph weights |
| Partial-body actions | Add a masked additive or override animation layer |
| Aim and interaction targets | Update an existing JSON IK/LookAt modifier target |
| Pack-specific performance | Merge selected asset performance settings |
| Weapon, magic or exhibit effects | Toggle JSON-defined particles and trails |
| Tool and effect placement | Read named mount and node positions |
| Client selection and inspection | Query AABB, bone proxies, PCA OBB or triangles |
| Multiplayer showcase deer | Set server-side synchronized state, ground IK and effects |

Model files and most rig configuration still belong in resource packs, including
`kubejs/assets/<namespace>/models/...` and the existing `.instance.json` sidecars.
Scripts issue commands; clips, IK, Spring, Morph deformation and rendering stay
in the Java/GPU runtime. No JS callback runs per bone, vertex, draw or shadow pass.

## Client API

`Assimp` is available only in `client_scripts`. Top-level scripts are loaded on a
mod-loading thread in this KubeJS version. Register callbacks at top level and
call model operations inside client events, such as `ClientEvents.tick`.

```javascript
ClientEvents.tick(function (event) {
  if (!event.player) return
  var model = Assimp.instance(event.player.mainHandItem, 'highbone_probe')
  if (!model) return
  // Issue commands on a change or interaction, not unconditionally every tick.
})
```

`Assimp.instance(owner, model)` accepts a live client entity, block entity, or
nonempty ItemStack. It returns `null` until the dynamic model is imported by the
existing resource/render path. It shares the renderer's actual instance for that
same Java owner and model identity. It does not attach a renderer to arbitrary
entities or turn ordinary items into custom model items.
Unambiguous short names, full resource paths and cached `#dynamic` identities
resolve to the same owner playback, regardless of which alias was requested first.

Other globals: `apiVersion()`, `models()`, `statistics()`, `release(owner)`.
`models()` lists cached dynamic identities, including their `#dynamic` suffix.
Read values returned in Java maps with `map.get('key')`; use `Number(...)` before
strict JS numeric comparisons.

### Model Handle

| Method | Behavior |
|---|---|
| `isValid()`, `sameInstance(other)` | Detect expiry and compare owner instances |
| `model()`, `animations()`, `states()`, `currentState()` | Read playback metadata |
| `state(name)` | Request a sidecar state; returns false for an unknown name |
| `play(clip, blendSeconds)` | Blend to a clip; returns false for an unknown clip |
| `pause()`, `resume()`, `stop()`, `resetDynamics()` | Control the existing instance |
| `layer(id, clip, weight, additive, mask)` | Set a layer; mask is a node-to-weight JS object |
| `layerWeight(id, weight)`, `removeLayer(id)` | Change or remove a layer |
| `meshes()` | Ordered mesh list with names, vertices, bone counts and target names |
| `morph(meshIndex, weights)`, `resetMorphs()` | Replace or clear explicit Morph overrides |
| `performance(options)`, `performance()` | Strict sidecar keys; merge or read settings |
| `effects(enabled)`, `groundPlacement(enabled)` | Toggle configured instance features |
| `target(modifierId, x, y, z)` | Change an existing CCD IK or LookAt target |
| `bounds()`, `node(name)`, `attachment(name)` | Read numerical model-space bounds/positions |
| `raycast(mode, ox, oy, oz, dx, dy, dz, distance)` | Model-space hit map, or null |

Performance options are `updateIntervalTicks`, `renderInterpolation`, `ik`,
`springBones`, `particles`, `trails`, `maxParticleEmissions`, `maxTrailSamples`.
Client-wide budgets remain upper limits. The global PERFORMANCE mode bypasses
interpolation even when an instance enables it. A performance edit resets
dynamics/effect history, so apply it when selecting a profile or acquiring an
instance, rather than every tick.

Queries use the authoritative simulation pose. Ray modes are `BOUNDS`, `BONE`,
`OBB`, `MESH`; triangle queries above 100,000 vertices are rejected from scripts.
OBB fitting retains its own runtime budgets. Model-space positions and distances
must be transformed by a caller that knows the renderer's transform. These are
client visual queries and do not authorize server damage.

Handles hold only a weak reference. Resource reload, world change, owner removal,
or `release` invalidates them; reacquire with `Assimp.instance`. Invalid handles
and calls from another thread fail with an explanatory error. A renderer's own
world binding may reapply synchronized entity settings each tick, so control
those settings through the server API when applicable.

The [held Morph example](../examples/kubejs/client_scripts/assimp_held_morph.js)
handles owner changes, resource reload and logout, and only changes Morph when
crouching changes. It targets the existing 256-bone Showcase item.

## Server API

`AssimpServer` is available only in `server_scripts`; its plugin and API contain
no Minecraft client references. The first supported target is this mod's existing
`assimploader:deer_showcase` entity:

```javascript
EntityEvents.spawned('assimploader:deer_showcase', function (event) {
  AssimpServer.state(event.entity, 'auto')
  AssimpServer.groundPlacement(event.entity, true)
  AssimpServer.effects(event.entity, true)
})
```

`state(entity)` reads the current requested state. Changes use the entity's
existing synchronized data and NBT, so clients receive them through Minecraft's
normal entity synchronization. Calls require the server thread and a live
Showcase deer. `auto` restores the movement-driven state selection. This API
does not add a server animation simulator, arbitrary-entity replacement,
authoritative mesh damage or a new custom packet protocol.

The [server example](../examples/kubejs/server_scripts/assimp_deer.js) is opt-in.
Examples are not installed into an existing pack automatically.

## Verification

```powershell
.\gradlew.bat test build runClient -PclientKubeJsSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
.\gradlew.bat runClient -PclientRuntimeSmoke=Scaffold_PBR_Test -PclientKubeJsWorldSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
.\gradlew.bat runClient -PclientStartupSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

`-PwithKubeJS` adds optional development runtime mods. `-PclientKubeJsSmoke`
also installs one generated acceptance script for that run and removes it on
completion, refusing to overwrite a different existing script. The script uses
real KubeJS/Rhino and original GLB fixtures, rather than a simulated JS engine.
`-PclientKubeJsWorldSmoke` instead installs a server event script for the existing
world regression. Only its explicitly tagged test deer is modified. As with the
ordinary world smoke, use a disposable local test world: it places terrain and
test entities and saves that world. Both script harnesses remove only their own
unchanged generated file and refuse to replace a different existing file.
Final test results and runtime limits are recorded in the implementation report.
Real Rhino client scripts and an integrated server's `EntityEvents.spawned`
have been exercised. The combined world regression completed all 11 stages
including resource reload, NBT recreation and disk reopen. A standalone
dedicated server has not been launched for this integration.

Primary integration sources:
[KubeJSPlugin](https://github.com/KubeJS-Mods/KubeJS/blob/2001/common/src/main/java/dev/latvian/mods/kubejs/KubeJSPlugin.java),
[plugin discovery and client exclusion](https://github.com/KubeJS-Mods/KubeJS/blob/2001/common/src/main/java/dev/latvian/mods/kubejs/util/KubeJSPlugins.java),
[bindings](https://github.com/KubeJS-Mods/KubeJS/blob/2001/common/src/main/java/dev/latvian/mods/kubejs/script/BindingsEvent.java).
