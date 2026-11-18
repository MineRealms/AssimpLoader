# Development Client

The root `runClient` task loads AssimpLoader, RigWorks and Showcase from separate
source sets. Its working directory is `run/`. Put private models in a local
resource pack and player controllers in `run/kubejs/client_scripts/`.
`-PclientGameDir=<directory>` selects a separate client directory for acceptance
without closing an existing client. Use separate copied worlds and a distinct
`-ProsettaPort` when running more than one client.

## Runtime Dependencies

Modern UI uses the pinned Maven versions in `gradle.properties`. The Forge mod
uses `runtimeOnly fg.deobf(...)`. Core and Markdown use `minecraftLibrary` so
Forge's game class loader can see them; a plain Core `runtimeOnly` dependency
did not suffice. See the [upstream development configuration](https://github.com/BloCamLimb/ModernUI-MC#gradle-configuration).

Oculus and Embeddium are enabled by default; `-PwithoutOculus` disables them.
`-PwithKubeJS` adds KubeJS, Rhino and Architectury at runtime.

Pass `-Pf3TweakerJar=<absolute-path-to-jar>` to load a local F3Tweaker build. Its
parent becomes a local artifact repository and ForgeGradle remaps the artifact
through `fg.deobf`. `fg.deobf(files(...))` does not remap file dependencies. Keep
machine paths out of committed Gradle files. F3Tweaker must contain a root
`pack.mcmeta` with Minecraft 1.20.1's resource pack format 15.

ModernUI-Forge and the optional F3Tweaker use the existing `localRuntime`
configuration with `fg.deobf`. They stay on the development client's runtime
classpath without leaking mapped mod coordinates into RigWorks' outgoing
project dependencies or standalone test runtime.

```powershell
.\gradlew.bat runClient -PwithKubeJS '-Pf3TweakerJar=C:/local/mods/F3Tweaker.jar' --console=plain
```

Select `SCRIPTED` mode for a RigWorks KubeJS controller. `CLIPS` bypasses scripts.
The block-player example assumes its own skeleton; custom rigs need their own
clip policy or calibrated pose definitions.

## Imported Player Acceptance

Enable the resource pack and configure the imported profile before launching.
Select `CLIPS` or a registered `SCRIPTED` controller. Use a disposable world with
clear level ground and a standing player.

```powershell
.\gradlew.bat runClient -PwithKubeJS -PclientWorld=My_Test_World -PclientCharacterClipSmoke=My_Test_World --console=plain
```

`clientWorld` uses Quick Play. `clientCharacterClipSmoke` moves the player to
check idle/walk/run, real bone motion, renderer calls and PBR texture bindings.
It saves three screenshots under `run/screenshots/` and
`run/character-clip-smoke-result.json`. Success and failure both leave the client
open. Inspect the screenshots too: a numeric pass does not establish correct
facing, camera framing or every player interaction.

With shaders active and `computeSkinningEnabled=true`, add
`-PclientCharacterGpuSmoke` to require a resident Oculus GPU pose buffer,
advancing compute dispatches and no repeated CPU vertex uploads during each
animation stage. The result also records cache counters and the FPS at each
capture. These snapshots are diagnostics, not a controlled benchmark.
This GPU acceptance variant searches nearby for a clear, flat 7-by-55-block
lane, equips a sword and off-hand torch, and fixes the capture view while it
runs. It captures idle, walk, run, walking continuous jumps, sprinting continuous
jumps and crouch. Each moving-jump stage holds forward and jump through at
least three takeoffs, preserves the underlying walk/run clip, and returns to
the fixture lane before starting. Crouch exercises the hybrid native pose.
Use a copied test world: the test moves the player and replaces the two held
items. It does not change terrain. Mouse capture is restored when it finishes,
and the client stays open. All outputs are relative to `clientGameDir` when
that option is supplied.

Add `-PclientEquipmentWorkbenchSmoke` to validate the live equipment editor
after the clip/GPU stages. It checks both hands, save/cancel, camera isolation
and actual XYZ/ring/scale handle dragging. During a frozen interval it requires
resident VBO draws and cache hits to keep increasing while compute dispatches
and full vertex uploads stay unchanged. It saves translation, rotation and
scale screenshots, then leaves the workbench open. See the
[workbench guide](EQUIPMENT_TRANSFORM_WORKBENCH.md) for normal manual entry
points and JSON format.

## Native Action And View Acceptance

`-PclientVanillaCompatibilitySmoke=<copied-world>` opens the configured world,
keeps the current imported character and script selected, and runs an
unregistered player probe through Minecraft's actual native pose sampler.
The synthetic cases cover ground/air contact, crouch, dry crawl, swimming,
climbing, riding, sleep, Elytra, Riptide, freezing, hurt/death and both physical
hands' native item-use dispatch. Synthetic observations are explicitly separate
from real gameplay observations in the result.

It captures third-person and empty-hand first-person views, requiring completed
custom-arm draw calls. The integrated server temporarily clears the two held
items for the first-person capture and restores them. Camera and display options
are restored too. Success and failure leave the client open. Results are saved
as `vanilla-compatibility-smoke-result.json` and screenshots use the
`vanilla-compatibility-<run>-` prefix under the selected game directory.
Inspect these captures: finite bone matrices and matching native ArmPose names
do not prove pixel-perfect contact, every real input combination or multiplayer
delivery. This is a focused functional check, not a performance benchmark.
