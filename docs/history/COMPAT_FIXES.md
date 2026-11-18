# Optional Compatibility Fixes

PBR follow-up (2026-11-13): the custom dynamic loader is now implemented and has run with Oculus shaders. See [current usage](../guides/OCULUS_PBR_USAGE.md) and [runtime verification](PBR_VALIDATION_2026-11-13.md). The [source investigation](OCULUS_PBR_RESEARCH_2026-11-13.md) records the initial findings before implementation.

Owner: `loading_lifecycle`. Implementation is ready for the parent's unified Gradle build. No Gradle command was run by this owner.

## Oculus

Local API signatures and bytecode were checked with `javap` against `mods_dev/oculus-mc1.20.1-1.8.0.jar.disabled`:

- `net.irisshaders.iris.api.v0.IrisApi.getInstance()`, `isShaderPackInUse()`, `isRenderingShadowPass()` provide actual shader status.
- `Iris.getPipelineManager().getPipelineNullable()` returns the current pipeline. `WorldRenderingPipeline` exposes `getPhase()`, `getCurrentNormalTexture()` and `getCurrentSpecularTexture()`.
- The real PBR package is `net.irisshaders.iris.texture.pbr`. `PBRTextureManager.INSTANCE.getOrLoadHolder(int)` returns a `PBRTextureHolder` exposing `normalTexture()` and `specularTexture()`. The manager has no public setters for arbitrary PBR texture IDs.
- `PBRTextureLoaderRegistry.INSTANCE.getLoader(Class)` selects exact texture classes. Oculus registers native loaders for `SimpleTexture` and `TextureAtlas`. `SimplePBRLoader` loads adjacent `_n` and `_s` resources using its own `ResourceManager` path. The registry also exposes public `register(Class, PBRTextureLoader)`: a loader for an AssimpLoader-owned dynamic texture class can supply arbitrary normal/specular images through its consumer.

Reflection handles and unavailable states are cached until reload/logout/shutdown. Active shader status is queried at most once per render frame, instead of repeatedly resolving classes and methods in mesh draw loops. Pipeline objects are obtained fresh so a changed shader pack does not leave a stale pipeline reference. API failures report once and disable that optional capability until reset.

`oculus.enabled`, `shaderMode`, `pbrEnabled` and `oculus.pbrSupportEnabled` gate the optional PBR integration. Actual shader-pack status remains observable when integration is disabled, because custom GPU programs must yield while a pack is active. Unknown Oculus API state also selects the vanilla rendering path from the first failed call. A non-null pipeline alone no longer implies PBR support: the diagnostic requires actual normal and specular bindings.

`ModelTextureCache` calls the native loader bridge when resolving material albedo textures. Resource-backed `SimpleTexture` images retain Oculus' `_n`/`_s` convention, and holders are prepared once per base texture object. Oculus owns the PBR textures and their deletion. AssimpLoader neither mutates private holder maps nor releases native PBR IDs. Its own weak preparation cache resets with texture/resource cleanup.

Embedded and filesystem images use per-material `AssimpPbrTexture` objects. A bounded background pool prepares authored maps; the public registry loader creates fresh child textures owned by Oculus. Shared albedo never merges distinct PBR bindings. Non-null holder fields alone do not prove sampling: diagnostics distinguish authored children from defaults, and the client test compares PBR on/off screenshots. Resource suffix helpers preserve image extensions and ignore dots in directory names. Shader injection, path tracing and shader pack benchmarks remain outside this repair.

## Photon and LDLib

`mixins.photon_compat.json` now uses `PhotonMixinPlugin`. It checks optional class resources by string before accepting each target, without initializing Photon or LDLib classes. Both target mixins are `@Pseudo` and use string targets. Missing Photon, missing LDLib, or missing required editor/material classes skips the mixins. The required class paths were verified in the local Photon 1.1.17 and LDLib 1.0.48 jars.

Common setup checks both mod IDs and editor class resources without `Class.forName` initialization. The mixin configuration is registered by the existing build manifest once; runtime duplicate `Mixins.addConfiguration` registration is removed. This bridge adds model/material import menus to Photon's particle editor. The dormant LookDev node/shader integration remains unavailable and is not described as a path tracer.

## Focused Verification

Eight pure JUnit tests were added in `compat/OculusCompatibilityTest` and `compat/OptionalIntegrationGuardsTest`. They cover suffix naming, absent bindings, both required mod IDs, missing/partial editor classes, compatible targets, unexpected target rejection, and resource probing without class initialization.

The initial module handoff used static inspection. Subsequent root verification enabled Oculus 1.8.0 and Complementary Unbound r5.3, compared PBR and shader toggles, and checked resource reload. See the dated runtime verification for commands, screenshots and remaining limits.
