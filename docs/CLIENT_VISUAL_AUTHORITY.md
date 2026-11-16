# Client Visual Inputs And Server Authority

```toml
[visual]
mode = "CLIENT_LOCAL"
groundPlacement = true
effects = false
```

These keys live in `config/assimploader-client.toml`. The default mode is
`HOST_CONTROLLED`, preserving each configured entity renderer's feature and
animation inputs. `CLIENT_LOCAL` uses the two local switches for terrain
placement and attachment effects. The deer showcase selects idle/walk from its
locally observed displacement instead of its custom synchronized state override.
Resource JSON still declares the actual rig, IK chains, contact points, state
definitions, effect limits and update cadence.

| Responsibility | HOST_CONTROLLED | CLIENT_LOCAL |
|---|---|---|
| Terrain sampling | Loaded client level collision shapes | Loaded client level collision shapes |
| Ground/effect switches | Renderer host inputs | Local client configuration |
| Deer showcase state input | Synchronized override, or automatic locomotion | Automatic local locomotion |
| Visual bounds and occlusion | Local published pose | Local published pose |
| Entity position replication | Normal Minecraft synchronization | Normal Minecraft synchronization |
| Collision box, movement, damage, projectile hit authority | Server | Server |

`MinecraftGroundSampler` rejects a server level, runs on the client thread and
does not load missing chunks. There are no server terrain requests, server pose
evaluation, native world pointers, collision-box mutations or damage packets in
the visual binding or GPU batch path. Missing client terrain produces no ground
target; the existing bounded correction and state allow-lists remain in force.

Visual bounds may extend beyond the authoritative entity AABB. They control
culling and optional explicit visual picking only. They are not an enlarged
server collision box. OBB/MESH/BONE queries do not automatically become damage
or projectile hit validation.

This is a calculation/input mode, not a network registry compatibility switch.
The custom deer entity still requires its registered entity type on the server.
Client-only installation on an unmodified server requires a compatible existing
entity/render binding; it does not create the custom deer or replace arbitrary
vanilla entities automatically. Such general replacement remains a separate
integration task.

Configuration events publish an immutable policy. The next client binding refresh
observes a changed mode. Neither mode changes server rules or allocates GPU
resources on the configuration watcher thread.
