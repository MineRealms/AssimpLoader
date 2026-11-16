# Optional Compute Skinning

The existing Assimp scenes, immutable poses and Minecraft material draw path
remain the inputs. The optional OpenGL 4.3 backend deforms vertices once per
snapshot identity, retains the output in a vertex buffer, and draws it through
the existing skinned material shader with vertex skinning disabled.

```toml
[performance]
computeSkinningEnabled = false
computeSkinningMiB = 128
computeSkinningOwners = 128
```

The default is disabled. Missing GL 4.3, incompatible scenes, a buffer exceeding
the driver SSBO limit, or exhausted budgets retain vertex/CPU fallback. Oculus
continues to use its existing compatible submission and posed-buffer path.
`gpuSkinningEnabled = false` and optimization `FALLBACK` still take precedence.

## Scope And Lifetime

- Up to 1024 bones, eight influences per vertex and 32 Morph targets.
- Morph mixing precedes skinning; normalized and relative weight semantics
  match `MorphDeformation`. Normals use inverse-transpose skin matrices.
- Four SSBOs: immutable 112-byte bind vertices, palette, immutable Morph deltas,
  and 32-byte output vertices. The output becomes a VBO without CPU readback.
- A 128 MiB per-mesh hard limit complements the configurable total budget and
  driver limits. Counts and byte estimates are checked before allocations.
- LRU eviction operates on owners. A client tick releases closed owners and
  enforces reduced budgets even when those owners are no longer rendered.
- Resource reload, logout and renderer cleanup release all allocations.
- A dispatch failure clears Compute resources and disables this backend until
  reload; the established vertex path remains available.
- Indexed SSBO binding ranges, generic SSBO binding, shader program, VAO and
  array-buffer bindings are restored. A vertex-fetch memory barrier precedes
  drawing; the buffer-update barrier also permits explicit diagnostic readback.

Inputs and shader programs currently belong to each resident owner/mesh entry.
This makes allocation accounting and eviction explicit, but duplicates immutable
input data across owners. No MDI, shared crowd arena, occlusion culling or worker
thread dispatch is claimed. Interpolation can cause a new dispatch each frame.
Automatic selection stays disabled until representative workload measurements
show a benefit over the existing vertex shader.

## Acceptance

```powershell
.\gradlew.bat test build runClient -PclientComputeSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

The opt-in test uses the original Blender 256-bone, 6144-vertex fixture. It checks
every position/normal against CPU deformation, combined Morph, zero restoration,
resource recreation and cached output reuse. CPU/GPU silhouette captures wait
at least ten seconds after each change. Readback exists only for diagnostics.

Initial run `1794835563300` passed four stages: maximum position component error
`4.7683716e-7`, normal component error `1.1920929e-7`, silhouette mismatch zero
in every stage. One resident entry used 1,134,592 buffer bytes. Final run
`1794836019101` repeated all four stages and passed nonzero SSBO range-binding
restoration on all four slots, a 1 MiB immediate memory trim, resource recreation
and closed-owner release. The log is `build/compute-final-validation.log`.
This fixture weights all
256 bones but does not itself prove eight simultaneous influences per vertex.

Cached-draw timestamps and warmed submission measurements from this fixture do
not measure continuous-changing-pose Compute cost or prove an FPS improvement.

Synchronization follows the Khronos specifications:
[Compute Shader](https://wikis.khronos.org/opengl/Compute_Shader),
[glMemoryBarrier](https://wikis.khronos.org/opengl/GLAPI/glMemoryBarrier).
