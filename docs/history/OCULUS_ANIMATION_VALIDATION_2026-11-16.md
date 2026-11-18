# Oculus animated world validation

## Environment and runs

Windows, Java 17.0.18, Minecraft 1.20.1 / Forge 47.1.3, Oculus 1.8.0 and
Embeddium 0.3.31. The local `RocketFlow_Local` pack is a modified Complementary
pack already present in this workspace. Entity shadows were explicitly enabled
with `ENTITY_SHADOWS_DEFINE=1`. This is evidence for this pack/configuration,
not all Oculus packs or an AssimpLoader-owned postprocessing stack.

```powershell
.\gradlew.bat test build runClient -PclientRuntimeSmoke=Scaffold_PBR_Test -PwithOculus -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

The runner requires the active shader API and both main/shadow render passes for
the deer. Its first three stages also require new main and shadow invocations of
the 256-bone Showcase after applying Morph weights 0, 1 and 0. Rendering retains
the shader pack's CPU/vanilla vertex submission path. The direct TBO GPU result
in [the high-bone report](../../model-tests/highbone_validation.md) is separate.

Run `1794832216685` passed the original nine terrain/effects/movement/teleport/
reload/NBT/navigation/removal stages with JFR enabled. Run `1794832541673` passed
the expanded ten stages, including a full disk world save, exit to title, reopen
of the same save, UUID lookup, settings verification, and old-state disposal.
Nine images waited at least 10.5 seconds and for textures. The active-Morph
terrain image was inspected: both models, textures and ground shadows are visible.

Selected observations from the expanded run:

| Stage | Main passes | Shadow passes |
| --- | ---: | ---: |
| 256-bone Morph 0 | 266 | 266 |
| 256-bone Morph 1 | 652 | 652 |
| 256-bone Morph restored 0 | 1055 | 1055 |

These count invocations, not isolated GPU draw timings or a per-pixel shadow
comparison. Actual pose vertices/edges were checked against the conservative
AABB in every deer capture. Logs are `build/world-oculus-validation.log` and
`build/world-oculus-final-validation.log`; both contain `[WorldRuntime] SUCCESS`
and end in `BUILD SUCCESSFUL`. Images use the respective run IDs under
`run/screenshots/assimp-world-runtime-*`.

The final build reported 275 tests: 274 passed, one optional benchmark skipped,
zero failures or errors.

`assimploader:showcase_highbone_probe` now exposes the existing original GLB
through the normal Showcase block/item resource chain. It is omitted from the
ordinary gallery arrangement and uses the existing JSON instance definition.

## JFR evidence

`-PclientJfr` produces `build/reports/assimp-client.jfr` on exit. The first
Oculus run recorded 189 seconds and 11,563,236 bytes, including cold startup,
world loading and resource reload. Whole-run Java sample leaders included Iris
smooth tangent generation and BufferBuilder; cold asset conversion also appeared
in allocation samples. Those totals are not steady frame measurements.

Use the dependency-free Java 17 source-file consumer to select a stable interval:

```powershell
java model-tests/ProfileJfr.java build/reports/assimp-client.jfr 2026-11-16T12:31:37Z 2026-11-16T12:31:59Z
```

For this 22-second effects/movement interval on the render/main thread, there
were 801 sampled Java execution stacks; 548 contained AssimpLoader. Nearest-mod-
frame attribution placed 496 in `BaseAssimpRenderer.addVertex`, 14 in
`CpuSkinning.deform`, and 9 in `renderMesh`. This attributes descendant Iris/
Minecraft work to the calling mod method; it is not 496 samples of only the
method's own instructions. Native/blocked GPU time is excluded.

Sampled allocation weights were 456,922,400 bytes overall and 223,677,464 bytes
with a recorded AssimpLoader frame. Leading mod attributions were CPU deformation
41,721,848; PoseGraph collection 29,296,048; PoseSnapshot collection 27,197,360;
snapshot capture 14,662,672; spring apply 10,482,864. These are sampled statistical
weights, not exact allocation counts or retained heap. Six managed model states
were present, including existing test-world entities. Output is retained locally
as `build/reports/assimp-jfr-steady.txt`.

This profile supports reducing repeated per-pass vertex encoding/submission in
the Oculus path before adding a native CPU skinning backend. It does not establish
universal performance ratios or show that Java cannot handle pose evaluation.
