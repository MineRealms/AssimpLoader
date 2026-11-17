# Module Split Validation

This is the current implementation record. Older reports, package paths and
completion checklists describe earlier snapshots and must not override source
or the acceptance evidence below.

## Ownership And Artifacts

| Mod | Sources | Dependency | Gameplay server behavior |
| --- | --- | --- | --- |
| AssimpLoader | `src/main` | Platform and optional integrations | None registered |
| RigWorks | `rigworks/src/main`, Gradle `:rigworks` | AssimpLoader plus platform/optional KubeJS | None registered |
| AssimpShowcase | `src/showcase`, root source set | AssimpLoader plus platform/optional KubeJS | Blocks, deer, vehicles, commands, networking, world light placement |

Published artifacts:

- `build/libs/assimploader-1.0.0-all.jar`: engine and bundled dependencies.
- `build/libs/assimpshowcase-1.0.0.jar`: separate content Mod.
- `rigworks/build/libs/rigworks-1.0.0.jar`: independent player Mod.

The plain engine JAR is an intermediate artifact; normal installations use the
`-all` JAR. The build verifies archive contents and forbids reverse source
dependencies. RigWorks is ready for later Gradle extraction through replacing
its project dependency and supplying its own wrapper/settings/platform versions.

Engine facilities extracted from content include the animation clock, model
visibility, an item model contract, optional diagnostic providers, optional
LookDev page registration, and program-bound shader providers. Generic rig
solvers and Bullet math stay in the engine; player observation, replacement,
profiles and equipment policy live in RigWorks. Rocket semantics live in Showcase.

The engine returns from its dedicated-server constructor before client config,
native initialization or rendering setup. Source checks find no gameplay
registries, common setup hooks, server tick subscriptions, server commands,
network channels or world writes in the engine. The integrated server was used
by the combined world acceptance. A separate dedicated-server boot and a runtime
with the engine alone were not exercised in this pass; archive/source isolation
does not substitute for those additional runtime configurations.

## Implemented Data Controls

[Animation processors](ANIMATION_PROCESSORS.md) compile resource JSON once per
instance. KubeJS controls activation, weights, clip phase and IK targets.
Native player motion precedes processor skeletal clips/poses; owned CCD chains
run in the IK stage, before existing physics. Invalid replacements preserve the
installed processor. Instance close, script retirement and explicit removal
clean up both installed stages. Inline declarations use `processorDefinition`
to avoid Rhino overload ambiguity.

[Shader parameters](SHADER_PARAMETER_BRIDGE.md) declare types, defaults, disabled
values and ranges in JSON. Commands run outside draw callbacks. The bridge
checks actual GL uniform types, caches locations per program object and retires
removed declarations on KubeJS reload. Native providers can restore their own
values after retirement. It does not create shader-pack passes or inject GLSL.

Player templates now use `RigWorks`, profiles use the `rigworks:` namespace,
and player config lives in `rigworks-client.toml`. Existing Showcase registry
and resource IDs remain `assimploader:*` for world compatibility. See
[RigWorks migration](../rigworks/README.md). Example scripts are opt-in.

## Evidence

| Gate | Result |
| --- | --- |
| Full build, tests, all integration compilation | Passed; 402 tests, zero failures/errors, one skipped benchmark |
| Release archive/source dependency checks | All three artifacts isolated |
| Oculus + KubeJS + all three development Mods | Startup and initial resource bake passed |
| Actual FBX, native standard/slim player model | 54 swim phase samples; maximum endpoint error `5.5300177e-7` model metres |
| Bow, charging/charged crossbow, hand/skin/look variants | 192 samples; maximum endpoint error `5.383012e-7` model metres |
| Head pitch, walk, crouch, flight, ride, sleep, attack | Native runtime probe passed |
| Fishing | Physical arm selection, item-display/socket transform and curve endpoint unit checks passed; mixin loaded |
| Player template | Node assertions passed: accepted attack phase, rapid retrigger, five-hit combo, mirroring and cleanup |
| Real Rhino | Generic binding, player template, processor declaration/control/rejection rollback and shader array conversion passed |
| OpenGL parameter bridge | Five types, disabled values, mismatched-type skip and binding-reload retirement passed |

The native probe uses an unregistered synthetic player, actual Minecraft
PlayerRenderer/PlayerModel code and the imported FBX. It compares elbows,
wrists and leg endpoints against original ModelPart matrices, including renderer
root transforms. It preserves limb lengths and does not mutate the real player's
position, items or input. Screenshots wait at least 10.5 seconds after stage changes.

Failures found during actual runs, then repaired:

- Development runtime selected a root JAR already rewritten by reobfuscation;
  dependent development runtimes now select class directories.
- A generated `ModifyArgs` helper could not load through Forge modules; fishing
  uses direct argument modifiers instead.
- Slim shoulders had a half-pixel reference-pose difference; the private native
  sampler now follows the actual renderer's standard/slim initial pose.
- The integration KubeJS manifest shadowed the production manifest; the combined
  development manifest includes the engine and acceptance plugins.
- Module-local class resources could not access models owned by another Mod;
  acceptance fixtures now load through Minecraft's resource manager.
- Rhino could not choose between String/Map overloads; definition methods now
  have distinct names.

Ignored local evidence: `build/module-startup-client.log`,
`build/module-pose-client.log`, `build/module-kubejs-client.log`,
`build/module-final-build.log`, `run/lookdev-smoke-result.json`,
`run/kubejs-smoke-result.json` and timestamped screenshots in `run/screenshots`.
These local files are diagnostic evidence, not redistributable assets.

## Remaining Engine Work

- Automatic item-specific support sockets, grip contacts and constrained hand IK.
- A capability inventory distinguishing imported, baked, unsupported and absent FBX data.
- A compiled dependency graph with channel ownership and rigid-body arbitration.
- Processor clip Morph composition and configurable per-channel native action selection.
- Last-known-good transactions for an entire script reload, beyond individual definitions.
- Arbitrary render-pass/texture scheduling and shader source integration.

Native arm parity does not prove arbitrary item mesh contact, custom reference
calibration, live key behavior, every resource pack's fishing rod tip, armor or
remote/first-person player replacement. The fishing tip currently follows the
vanilla generated rod geometry and its display transform. PMX/VMD remains deferred.
Git timestamp correction and its recovery artifacts are recorded separately in
[the history audit](HISTORY_DATE_CORRECTION_2026-11-17.md).
