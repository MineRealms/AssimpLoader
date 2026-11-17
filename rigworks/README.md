# RigWorks

RigWorks is the optional client character application built on AssimpLoader.
It owns the local player renderer, Minecraft player observations, native pose
retargeting, held items, fishing-line attachment, character profiles, client
configuration, and player KubeJS policies.

AssimpLoader owns reusable model import, animation layers, IK, morph solving,
rigid-body simulation, skinning, rendering and debugging infrastructure.
RigWorks does not depend on AssimpShowcase or register gameplay entities,
blocks, packets, server commands or server tick handlers.

## Development

Build with `gradlew.bat :rigworks:build` from the repository root. The combined
development client is launched from the root project. RigWorks is a separate
Forge mod and jar with mod ID `rigworks`; it is not shaded into AssimpLoader.

The Gradle project dependency on AssimpLoader is declared in `build.gradle`.
When extracting this directory into a separate repository, replace that project
dependency with the published AssimpLoader dependency and supply the Forge
Gradle plugin bootstrap and Minecraft/Forge version properties. Sources and
resources do not reference AssimpShowcase.

Java packages, the mixin configuration/refmap, the configuration file and all
character assets have their own RigWorks identity. This also prevents split
packages between the separately loaded Forge modules.

## Client Configuration

Settings are in `config/rigworks-client.toml`, under `[character]`. Existing
AssimpLoader character settings are not read as runtime configuration by this
module. The default profile is
`rigworks:models/character/block_player/profile.json`.

The character remains disabled by default. Use the Player page in LookDev to
enable it and select Vanilla, Scripted or Clips. The compatibility command
namespace `/assimploader character` is registered by RigWorks itself.

## KubeJS

The optional client binding is `RigWorks`. Generic model scripting remains
available through `Assimp` from AssimpLoader.

The player example is `examples/kubejs/client_scripts/rigworks_player_actions.js`.
Install it into the game's `kubejs/client_scripts` directory and use
`/kubejs reload client_scripts`. The module does not silently install or replace
user scripts. The example registers a policy but does not enable the character
renderer automatically.

Existing scripts using `Assimp.character()` must migrate to `RigWorks`, for example:

```javascript
RigWorks.register('my_pack:player', function (frame) {
  frame.defaultAnimation()
})
```

Profiles, rig definitions, physics assets and equipment sockets remain JSON
resources. AssimpLoader now provides a first [JSON animation processor](../docs/ANIMATION_PROCESSORS.md)
with cached clips, poses, masks, Morph and owned CCD IK. Modifier dependencies
are compiled into a validated [evaluation plan](../docs/POSE_EVALUATION_PLAN.md).
The RigWorks controller registry retains its last successful generation after a
failed standard client script reload; other KubeJS event handlers and global
side effects retain KubeJS's own reload behavior.

Native motion can be selected per channel through [JSON or script definitions](../docs/RIGWORKS_NATIVE_CHANNELS.md).
Optional [support-hand contacts](../docs/RIGWORKS_HAND_CONTACTS.md) have an
independent Player-page toggle and start disabled. Socket coordinates and item
selection are equipment data, with script overrides for each tick.

The example defaults `nativeItemActions` to bow/crossbow. Those actions delegate
the entire pose to the native driver, clearing script arm overrides first;
charged crossbow holding is preserved too. Pack authors may remove entries to
provide their own complete arm/contact solution. Classifying a custom item as
bow-like does not make Minecraft treat it as a native bow, so semantic item
rules continue to support explicit custom poses.
