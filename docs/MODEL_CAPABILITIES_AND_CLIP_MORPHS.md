# Model Capabilities And Processor Morphs

## Imported Asset Inventory

`model.capabilities()` returns a cached, immutable inventory for the imported
`AssimpScene`. Model instances sharing that scene share the inventory. The cache
has weak scene keys, and the returned values do not retain the scene. Reloading
into a new scene builds a new inventory. Access still requires a live model
handle on its owning client thread.

The inventory is not a live activation or rendering diagnostic:

| Section | Reported Evidence |
| --- | --- |
| `source` | Imported format/generator metadata, STATIC/DYNAMIC mode, resource/path when retained |
| `mesh` | Imported mesh/vertex/triangle counts and available normals, UV0 and tangents |
| `skin` | Imported skinned meshes and unique bone names; GPU eligibility remains `not_evaluated` |
| `clips` | Imported names, durations, skeletal/Morph channel counts; baking provenance remains `unknown` |
| `morph` | Imported target names, defaults, methods and animation channel count |
| `pbr` | Adapter workflows, factors and texture references, including unsupported UV indices |
| `authoring` | FBX constraint/physics adapter limitations and explicit sidecar alternatives |

`present` means copied data exists in the imported scene. `no_imported_data`
does not mean the original authoring project lacked the feature. STATIC import
pretransforms geometry; animation/skin requirements must be resolved by importing
the asset in DYNAMIC mode, not by enabling a script flag afterwards.

FBX authoring constraints and physics are reported as `sourcePresence: unknown`,
`imported: not_exposed_by_adapter`, and `runtime: source_graph_not_supported`.
The runtime can execute explicitly configured sidecar IK, append transforms,
limits, rigid bodies, joints and springs. It does not reconstruct those original
authoring graphs from sampled animation transforms. Baked animation does not
identify which original constraints contributed to the result.

PBR factors can be imported values or adapter defaults. Texture references do
not prove the textures were resolved or uploaded. Shader activity, GPU limits,
per-instance feature switches and solver state are deliberately not inferred.

```javascript
var caps = model.capabilities()
if (caps.source.importMode !== 'DYNAMIC') return
if (caps.morph.targetCount > 0) {
  // Pick a declared processor whose imported clip/mesh IDs match this inventory.
}
```

## Processor Morph Composition

Processor clips now compose their imported Morph channels as well as skeletal
channels. Both sample the same `AnimationPoseLayer` player. Skeletal evaluation
advances the clock once; Morph evaluation only reads that clock. Phase holds,
speed, looping, zero-weight timekeeping, enable/disable and reset therefore affect
both channels together.

Morph composition follows clip declaration order. Effective weight is clip weight
times the applicable mask weight. An empty mask selects all imported channels;
a nonempty mask selects only named meshes or nodes that own those meshes,
independent of which alias the importer used for the Morph channel. Where both names select the same mesh, the larger
mask value applies, matching ordinary animation-layer semantics. Mesh names are
valid mask keys for clips; pose masks remain node-only. Morph target names are
not mask keys.

- Override: `result = base + (sample - base) * effectiveWeight`.
- Additive: `result = base + (sample - importedDefault) * effectiveWeight`.
- Disabled clips and disabled processors contribute nothing and freeze their clocks.
- Enabled clips at zero weight contribute nothing but keep time.
- Input/default arrays are not mutated; resulting arrays belong to the instance.

This uses the existing Morph modifier hook after the base clip/layers and
explicit instance Morph weights. A processor therefore composes onto that input;
its override may supersede an earlier `model.morph()` value for selected meshes.
It does not advance physics, rerun scripts, or add a second animation clock.

`examples/animation_processors/assets/assimploader/animation_processors/morph_probe.json`
is a declaration for the existing original Blender Morph validation fixture. The
fixture is supplied by AssimpShowcase, while the processor implementation and
inventory API are in AssimpLoader.
