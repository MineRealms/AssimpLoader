# Optional Compute Skinning

The existing Assimp scenes, immutable poses and Minecraft material draw path
remain the inputs. The optional OpenGL 4.3 backend deforms vertices once per
snapshot identity, retains the output in a vertex buffer, and draws it through
the existing skinned material shader with vertex skinning disabled. With Oculus,
a second compute pass scatters deformed geometry into Oculus's extended ENTITY
vertex buffer; the shader pack still owns material shading and the final draw.

```toml
[performance]
computeSkinningEnabled = false
computeSkinningMiB = 128
computeSkinningOwners = 128
```

The default is disabled. Missing GL 4.3, incompatible scenes, a buffer exceeding
the driver SSBO limit, or exhausted budgets retain vertex/CPU fallback. Oculus
uses the same opt-in for its extended posed-buffer path. Transparent materials
no longer disable accelerated opaque meshes across an entire scene.
`gpuSkinningEnabled = false` and optimization `FALLBACK` still take precedence.

## Scope And Lifetime

- Up to 1024 bones, eight influences per vertex and 32 Morph targets.
- Morph mixing precedes skinning; normalized and relative weight semantics
  match `MorphDeformation`. Normals use inverse-transpose skin matrices.
- Four SSBOs: immutable 112-byte bind vertices, palette, immutable Morph deltas,
  and 32-byte output vertices. The output becomes a VBO without CPU readback.
- Oculus additionally retains the pack-compatible extended ENTITY VBO. A
  per-triangle scatter writes positions, normalized normals and animated UV
  tangents; color, UV, mid-UV and PBR attributes stay resident. Constant light,
  overlay and entity IDs are supplied through the established draw bridge.
- Opaque materials draw before alpha-blended materials. The original material
  RenderTypes and shader-pack programs remain responsible for blending/PBR.
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

Inputs currently belong to each resident owner/mesh entry, while both compute
programs are shared with reference-counted lifetime. Immutable input data is
still duplicated across owners. No MDI, shared crowd arena, occlusion culling or worker
thread dispatch is claimed. Interpolation can cause a new dispatch each frame.
Automatic selection stays disabled until representative workload measurements
show a benefit over the existing vertex shader.

For Oculus, inspect `OculusEntityBufferCache.poseStatistics()` and
`gpuPoseStatistics()`. A changing pose should increase GPU dispatches while CPU
VBO uploads stop after initialization. Main/shadow passes reuse the same pose.
The extended buffers and compute inputs are charged to the configured cache
budgets, and a compute failure returns to CPU-deformed VBO submission.

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

The focused `OculusPoseComputeTest` checks the actual 56-byte Oculus 1.8 layout,
including tangents crossing a 32-bit word at byte 50. Enable
`ASSIMPLOADER_GL_TESTS=1` to compile and dispatch the real GLSL in a separate
hidden GLFW context, checking geometry, tangents and unchanged material bytes.

Synchronization follows the Khronos specifications:
[Compute Shader](https://wikis.khronos.org/opengl/Compute_Shader),
[glMemoryBarrier](https://wikis.khronos.org/opengl/GLAPI/glMemoryBarrier).
