# Oculus pose buffer cache

Opaque configured entities and animated Showcase blocks can retain one current
vertex buffer per instance mesh. CPU Morph/skinning and Iris extended-vertex
encoding happen once for each published pose. The main view, shadow pass and
repeated draws reuse that buffer until the pose identity changes. Existing VBOs
are updated with dynamic usage; a fresh GL buffer is not created every tick.

This extends the existing Oculus ENTITY buffer implementation. It uses the
pack-selected shader, expanded vertex format and normal Minecraft light/overlay/
entity IDs. It does not run AssimpLoader's custom skinning shader under the pack,
replace the pack's shadow pipeline, or require Compute/native dependencies.

## Configuration

In `assimploader-client.toml`, under `[oculus]`:

```toml
poseCacheEnabled = true
poseCacheMiB = 128
poseCacheInstances = 128
```

These global limits complement the existing pose evaluation cadence and asset
feature budgets. `FALLBACK` optimization disables this path. Transparent materials,
instance material animation, previews and unavailable optional APIs use the
normal submission path. Allocation/draw failure disables cached Oculus rendering
until resource reload.

Memory is reserved before encoding using the actual Iris entity stride and the
mesh index count. Skipped invalid triangles can only reduce the actual size. A
pose exceeding the whole budget falls back before skinning/encoding. Least-
recently-used owners are evicted to satisfy the byte and owner limits. The budget
counts resident vertex payload, not driver overhead, shared sequential index
buffers or transient driver copies during an upload.

Closed animation owners release their buffers at the next client tick. This
also handles entity removal when no further model is drawn. Reload/logout closes
all buffers. Strong cache keys hold playback states, whose entity bindings use
weak owners; the animation manager remains responsible for closing released or
collected owners. The resident owner/byte limits bound retained states.

## Counters and validation

`OculusEntityBufferCache.poseStatistics()` reports owners, buffers, reserved
bytes, upload count/bytes, actual cached mesh draws, shadow draws, same-pose hits
and over-budget fallbacks. Upload bytes are cumulative traffic, whereas resident
bytes describe current buffers. These counters do not measure GPU time.
The existing `/assimploader performance` client command also prints them.

The integrated-world runner requires main/shadow cached draws and same-pose
reuse, then repeats active Morph, terrain, movement, teleport, reload, NBT and
disk reopen checks. Its budget stage temporarily selects 1 MiB/one owner and
asserts the cache stays within both limits. It restores the original settings
on completion/failure. The final removal stage requires both closed playback
and absence of its cached GPU buffers.

```powershell
.\gradlew.bat test build runClient -PclientRuntimeSmoke=Scaffold_PBR_Test -PwithOculus -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

Enable entity shadows in the selected pack for this strict runner. A completed
run needs its `SUCCESS` marker. The cache is motivated by the stable JFR interval
in [the preceding profile](../history/OCULUS_ANIMATION_VALIDATION_2026-11-16.md), where repeated
vertex submission dominated attributed Java execution samples. Reuse counters
show avoided encodes/uploads; they do not establish a general FPS increase.

## Verified 2026-11-16

Run `1794833212718` completed eleven stages and ten delayed images, including
active 256-bone Morph in main/shadow passes, ground IK, effects, movement,
teleport, reload, NBT recreation, disk reopen, budget reduction and removal.
The active-Morph image was inspected and retains both textured models and ground
shadows. GL error assertions passed.

After reopen, one interval reported 4,221 cached mesh draws, 626 uploads and
3,595 same-pose hits, with 1,382,976 resident vertex bytes across three owners.
That is about 6.74 draws per upload in that interval. With the reduced limits,
the cache retained one owner and 516,096 bytes. Removal released that owner's
GPU buffers; final config values were restored to 128 MiB / 128 owners.

Log: `build/oculus-pose-cache-validation.log`, containing the `SUCCESS` marker
and `BUILD SUCCESSFUL`. Images:
`run/screenshots/assimp-world-runtime-1794833212718-*.png`.
