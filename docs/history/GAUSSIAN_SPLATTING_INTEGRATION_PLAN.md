# Gaussian Splatting Integration Plan

## Executive Summary

Integration of JSplat's Gaussian Splatting renderer into AssimpLoader's Minecraft rendering pipeline. Bottom-up implementation with Oculus shader compatibility.

---

## Architecture Overview

```
JSplat Renderer → Minecraft Rendering Pipeline
    ↓                       ↓
LWJGL/OpenGL 4.3 → Minecraft RenderSystem (OpenGL 3.3+)
    ↓                       ↓
SSBO + Instancing → VBO + Instancing (compatible)
    ↓                       ↓
Standalone Viewer → Integrated with AssimpLoader
```

---

## Phase 1: Core Rendering Infrastructure (Bottom Layer)

### 1.1 Buffer Management
**Path:** `src/main/java/cn/minerealms/assimploader/rendering/splat/`

**Class: `GaussianSplatVBO.java`**
```java
// Manages VAO/VBO/EBO for splat quad geometry + SSBO for splat data
- VAO: single quad (4 vertices, 6 indices)
- SSBO binding 0: gaussian_data (position, rotation, scale, opacity, SH)
- SSBO binding 1: gaussian_order (sorted indices)
- Methods: upload(), bind(), render(), delete()
```

**Class: `GaussianSplatBuffer.java`**
```java
// Packs splat data into GPU buffer format
- Stride: 11 + shDim*3 floats per splat
- Layout: pos(3) + rot(4) + scale(3) + opacity(1) + sh(N*3)
- Conversions: scale=exp(logScale), opacity=sigmoid(logit)
- Methods: pack(), upload(), resize()
```

**Integration Point:** Follows `SkinnedMeshVBO.java` pattern
- Reuse interleaved buffer approach
- Cache VBOs in renderer (like `vboCache` in SkinnedMeshRenderer)

---

### 1.2 Shader System
**Path:** `src/main/resources/assets/assimploader/shaders/core/`

**Files:**
- `gaussian_splat.vert` - Port from JSplat with modifications:
  - Change `#version 430` → `#version 330` (Minecraft baseline)
  - Replace SSBO with UBO if GL 4.3 unavailable (fallback path)
  - Keep covariance math, SH evaluation, quad expansion
  
- `gaussian_splat.frag` - Port from JSplat:
  - Gaussian kernel evaluation
  - Alpha blending with early discard
  - Render modes: full, depth, billboard

**Class: `GaussianSplatShader.java`**
```java
// Compiles and manages splat shaders
- Extends SkinnedMeshShader pattern
- Uniforms: view_matrix, projection_matrix, cam_pos, sh_dim, scale_modifier
- Methods: compile(), bind(), setUniforms(), unbind()
```

**Integration Point:** Register with `PhotonShaderCompiler`
- Add to shader compilation pipeline
- Support Photon full shader mode
- TODO: Oculus injection mode (Phase 3)

---

### 1.3 Data Structures
**Path:** `src/main/java/cn/minerealms/assimploader/rendering/splat/data/`

**Class: `GaussianSplat.java`**
```java
// Immutable splat data (port JSplat Splat interface)
- position: vec3
- rotation: quaternion (w,x,y,z)
- scale: vec3 (log-space)
- opacity: float (logit-space)
- sh: float[] (spherical harmonics coefficients)
```

**Class: `GaussianSplatData.java`**
```java
// Container for splat point cloud
- List<GaussianSplat> splats
- int shDegree (0-3)
- BoundingBox bounds
- Methods: getSplats(), getShDegree(), getBounds()
```

**Class: `GaussianSplatSorter.java`**
```java
// Depth-based sorting for alpha blending
- Sorts splats back-to-front based on view matrix
- Uses view matrix row 2 for depth computation
- Methods: sort(List<GaussianSplat>, Matrix4f viewMatrix)
```

---

## Phase 2: Renderer Implementation (Middle Layer)

### 2.1 Core Renderer
**Path:** `src/main/java/cn/minerealms/assimploader/rendering/splat/`

**Class: `GaussianSplatRenderer.java`**
```java
// Main rendering entry point (parallel to SkinnedMeshRenderer)
public void render(
    GaussianSplatData data,
    Matrix4f mvp,
    Matrix4f mv,
    Vector3f cameraPos,
    float scaleModifier
) {
    // 1. Get/create VBO from cache
    GaussianSplatVBO vbo = vboCache.computeIfAbsent(data, this::createVBO);
    
    // 2. Sort splats by depth
    sorter.sort(data.getSplats(), mv);
    
    // 3. Upload sorted indices to SSBO
    vbo.uploadOrder(sorter.getSortedIndices());
    
    // 4. Bind shader and set uniforms
    shader.bind();
    shader.setViewMatrix(mv);
    shader.setProjectionMatrix(mvp);
    shader.setCameraPos(cameraPos);
    shader.setShDim(data.getShDegree());
    shader.setScaleModifier(scaleModifier);
    
    // 5. Render instanced
    vbo.bind();
    GL31.glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, data.getSplats().size());
    vbo.unbind();
    
    shader.unbind();
}

// Cache management
private Map<GaussianSplatData, GaussianSplatVBO> vboCache = new HashMap<>();
private GaussianSplatSorter sorter = new GaussianSplatSorter();
private GaussianSplatShader shader;
```

**Integration Point:** Called from `AssimpItemRenderer` or `OffscreenRenderer`
- Add detection: if model contains splat data → route to GaussianSplatRenderer
- Otherwise → existing SkinnedMeshRenderer path

---

### 2.2 OpenGL State Management
**Class: `GaussianSplatRenderState.java`**
```java
// Manages GL state for splat rendering
public static void setup() {
    RenderSystem.disableCull();
    RenderSystem.enableBlend();
    RenderSystem.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    RenderSystem.enableDepthTest();
    RenderSystem.depthMask(false); // No depth writes for transparent splats
}

public static void restore() {
    RenderSystem.enableCull();
    RenderSystem.defaultBlendFunc();
    RenderSystem.depthMask(true);
}
```

---

## Phase 3: LookDev Integration (Top Layer)

### 3.1 Preview Renderer
**Path:** `src/main/java/cn/minerealms/assimploader/lookdev/render/`

**Extend: `OffscreenRenderer.java`**
```java
// Add splat rendering path in renderModel()
private void renderModel() {
    if (currentModel instanceof GaussianSplatData) {
        renderSplatModel((GaussianSplatData) currentModel);
    } else {
        // Existing mesh rendering
    }
}

private void renderSplatModel(GaussianSplatData data) {
    GaussianSplatRenderState.setup();
    
    splatRenderer.render(
        data,
        projectionMatrix.mul(viewMatrix),
        viewMatrix,
        cameraPos,
        1.0f // scale modifier
    );
    
    GaussianSplatRenderState.restore();
}
```

**Add: `GaussianSplatPreviewRenderer.java`**
```java
// Dedicated splat preview renderer (parallel to MaterialPreviewRenderer)
- Preview camera controls (distance, yaw, pitch)
- Real-time sorting and rendering
- Update throttling (60 FPS)
```

---

### 3.2 View Mode Extension
**Extend: `ViewMode.java`**
```java
public enum ViewMode {
    FULL,
    BASE_COLOR,
    // ... existing modes
    GAUSSIAN_SPLATTING, // NEW
    SPLAT_DEPTH,        // NEW - depth visualization
    SPLAT_OPACITY       // NEW - opacity visualization
}
```

---

### 3.3 Node Editor Integration
**Path:** `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/nodes/`

**Add: `GaussianSplatNode.java`**
```java
// Node for splat data input
- Outputs: splat cloud data
- Properties: file path, SH degree
- TODO: Load .ply, .splat, .spz files via JSplat IO modules
```

**Add: `SplatColorNode.java`**
```java
// Modifies splat colors
- Inputs: splat data, color multiplier
- Outputs: modified splat data
```

**Add: `SplatOpacityNode.java`**
```java
// Modifies splat opacity
- Inputs: splat data, opacity multiplier
- Outputs: modified splat data
```

---

## Phase 4: Oculus Shader Compatibility

### 4.1 Shader Injection Strategy
**Path:** `src/main/java/cn/minerealms/assimploader/lookdev/iris/`

**Extend: `OculusShaderInjector.java`**
```java
// Add splat-specific injection
public static String injectSplatToGBuffer(
    String originalShader,
    GaussianSplatData data
) {
    // 1. Parse shader AST
    TranslationUnit ast = parseShader(originalShader);
    
    // 2. Inject SSBO declarations at BEFORE_DECLARATIONS
    injectSplatBuffers(ast);
    
    // 3. Inject splat rendering function at BEFORE_FUNCTIONS
    injectSplatRenderFunction(ast);
    
    // 4. Call splat render in main() at MAIN_END
    injectSplatRenderCall(ast);
    
    return ast.toString();
}
```

**Challenge:** Oculus gbuffers may not support SSBO
- **Fallback:** Use texture buffers (TBO) like BoneMatrixTBO
- **Alternative:** Pre-sort and upload as vertex attributes (limited count)

---

### 4.2 Dual-Mode Compilation
**Extend: `DualModeShaderManager.java`**
```java
public static CompiledShader compileSplat(GaussianSplatData data) {
    if (isOculusAvailable()) {
        return compileSplatInjectMode(data);
    } else if (isPhotonAvailable()) {
        return compileSplatFullMode(data);
    } else {
        return null; // No shader support
    }
}

private static CompiledShader compileSplatInjectMode(GaussianSplatData data) {
    // Use OculusShaderInjector to inject splat rendering
    String injectedShader = OculusShaderInjector.injectSplatToGBuffer(
        getGBufferShader(),
        data
    );
    // TODO: Register with Iris shader reload
    return new CompiledShader(CompilationMode.INJECTION, injectedShader, null, null);
}

private static CompiledShader compileSplatFullMode(GaussianSplatData data) {
    // Use PhotonShaderCompiler to compile standalone splat shader
    return PhotonShaderCompiler.compile(
        "gaussian_splat",
        loadSplatVertexShader(),
        loadSplatFragmentShader(),
        getSplatUniforms(data)
    );
}
```

---

## Phase 5: IO Integration (Data Loading)

### 5.1 JSplat IO Modules
**Path:** `src/main/java/cn/minerealms/assimploader/io/splat/`

**Class: `SplatLoader.java`**
```java
// Loads splat data from various formats
public static GaussianSplatData load(ResourceLocation location) {
    String extension = getExtension(location);
    
    SplatListReader reader = switch (extension) {
        case "ply" -> new PlySplatReader();
        case "splat" -> new GsplatSplatReader();
        case "spz" -> new SpzSplatReader();
        default -> throw new UnsupportedFormatException(extension);
    };
    
    List<MutableSplat> splats = reader.readList(openStream(location));
    return convertToGaussianSplatData(splats);
}
```

**Dependencies:** Add JSplat modules to build.gradle
```gradle
implementation 'de.javagl:jsplat:0.0.1-SNAPSHOT'
implementation 'de.javagl:jsplat-io-ply:0.0.1-SNAPSHOT'
implementation 'de.javagl:jsplat-io-gsplat:0.0.1-SNAPSHOT'
implementation 'de.javagl:jsplat-io-spz:0.0.1-SNAPSHOT'
```

---

### 5.2 Resource Pack Integration
**Path:** `assets/assimploader/splats/`

**Structure:**
```
assets/
  assimploader/
    splats/
      example.ply
      example.splat
      example.spz
```

**Class: `SplatResourceManager.java`**
```java
// Manages splat resource loading and caching
- Cache loaded splat data
- Hot-reload detection
- Resource pack support
```

---

## Phase 6: Performance Optimization

### 6.1 LOD System
**Class: `GaussianSplatLOD.java`**
```java
// Level-of-detail for large point clouds
- Reduce splat count based on distance
- Cull splats outside frustum
- Adaptive quality based on FPS
```

### 6.2 Profiling Integration
**Extend: `PerformanceProfiler.java`**
```java
// Add splat-specific profiling
PerformanceProfiler.start("splat_sort");
sorter.sort(splats, viewMatrix);
PerformanceProfiler.end("splat_sort");

PerformanceProfiler.start("splat_render");
vbo.render();
PerformanceProfiler.end("splat_render");
```

### 6.3 Caching Strategy
- VBO cache: reuse buffers for same splat data
- Sorted index cache: cache sorted indices per view angle bucket
- Shader cache: reuse compiled shaders

---

## Implementation Task Breakdown (Bottom-Up)

### Sprint 1: Foundation (8-10 hours)
1. ✅ Create `GaussianSplat.java` data structure
2. ✅ Create `GaussianSplatData.java` container
3. ✅ Create `GaussianSplatBuffer.java` packing logic
4. ✅ Create `GaussianSplatVBO.java` buffer management
5. ✅ Port `gaussian_splat.vert` shader
6. ✅ Port `gaussian_splat.frag` shader
7. ✅ Create `GaussianSplatShader.java` compiler
8. ✅ Test: Render single splat quad

### Sprint 2: Core Renderer (6-8 hours)
1. ✅ Create `GaussianSplatSorter.java`
2. ✅ Create `GaussianSplatRenderState.java`
3. ✅ Create `GaussianSplatRenderer.java`
4. ✅ Implement VBO caching
5. ✅ Implement instanced rendering
6. ✅ Test: Render 1000 splats with sorting

### Sprint 3: LookDev Integration (4-6 hours)
1. ✅ Extend `OffscreenRenderer` for splat rendering
2. ✅ Create `GaussianSplatPreviewRenderer`
3. ✅ Add `GAUSSIAN_SPLATTING` to `ViewMode`
4. ✅ Test: Preview splat cloud in LookDev

### Sprint 4: IO Integration (4-6 hours)
1. ✅ Add JSplat dependencies to build.gradle
2. ✅ Create `SplatLoader.java`
3. ✅ Create `SplatResourceManager.java`
4. ✅ Test: Load .ply file and render

### Sprint 5: Oculus Compatibility (8-10 hours)
1. ✅ Implement TBO fallback for SSBO
2. ✅ Create `GaussianSplatOculusInjector` for shader injection
3. ✅ Integrate with `OculusShaderInjector` framework
4. ✅ Generate vertex/fragment shader code for gbuffers
5. ⬜ Test: Render splats with Oculus installed
6. ⬜ TODO: Handle shader pack conflicts

### Sprint 6: Performance (4-6 hours)
1. ✅ Implement `GaussianSplatLOD`
2. ✅ Add profiling hooks
3. ✅ Implement distance-based LOD levels
4. ✅ Add frustum culling support
5. ⬜ Test: 100k splats at 60 FPS

### Sprint 7: World Rendering (4-6 hours)
1. ✅ Create `GaussianSplatBlock`
2. ✅ Create `GaussianSplatBlockEntity`
3. ✅ Create `GaussianSplatBlockEntityRenderer`
4. ✅ Register with `ExampleRegistry`
5. ✅ Fix crash when resource not found
6. ⬜ Test: Place splat block in world

**Total Estimate:** 40-54 hours
**Completed:** Sprint 1-7 core implementation
**Remaining:** Testing and optimization

---

## Compatibility Matrix

| Feature | Standalone | Photon | Oculus | Notes |
|---------|-----------|--------|--------|-------|
| Basic Rendering | ✅ | ✅ | ✅ | Core functionality |
| Full Shader | ✅ | ✅ | ❌ | Photon only |
| Shader Injection | ❌ | ❌ | ✅ | Oculus only |
| SSBO | ✅ | ✅ | ⚠️ | Fallback to TBO |
| Instanced Rendering | ✅ | ✅ | ✅ | GL 3.3+ |
| Node Editor | ✅ | ✅ | ✅ | All modes |
| Real-time Preview | ✅ | ✅ | ✅ | All modes |

**Legend:**
- ✅ Fully supported
- ⚠️ Supported with fallback
- ❌ Not supported
- ⬜ TODO

---

## Risk Mitigation

### Risk 1: SSBO Unavailable in Oculus
**Impact:** Cannot use efficient buffer storage
**Mitigation:**
- Fallback to TBO (like BoneMatrixTBO)
- Limit splat count to TBO size (16,384 splats max)
- Pre-sort on CPU, upload sorted data

### Risk 2: Instanced Rendering Performance
**Impact:** Low FPS with large point clouds
**Mitigation:**
- Implement LOD system
- Frustum culling
- Adaptive quality (reduce splat count dynamically)

### Risk 3: Shader Pack Conflicts
**Impact:** Injected splat code breaks shader pack
**Mitigation:**
- Detect shader pack compatibility
- Disable splat rendering if incompatible
- Provide compatibility list

### Risk 4: Alpha Blending Artifacts
**Impact:** Incorrect rendering order
**Mitigation:**
- Ensure back-to-front sorting every frame
- Use depth peeling for complex scenes (TODO)
- Disable depth writes for splats

---

## Testing Strategy

### Unit Tests
- `GaussianSplatBuffer` packing correctness
- `GaussianSplatSorter` sorting correctness
- Shader compilation success

### Integration Tests
1. **Standalone Mode:** Render splats without Photon/Oculus
2. **Photon Mode:** Compile full splat shader
3. **Oculus Mode:** Inject splat code into gbuffers
4. **LookDev Mode:** Preview splats in offscreen renderer
5. **Node Editor:** Modify splats via node graph

### Performance Tests
- 1k splats: 60+ FPS
- 10k splats: 30+ FPS
- 100k splats: 15+ FPS (with LOD)

---

## Future Enhancements (Post-MVP)

1. **Advanced Rendering:**
   - Depth peeling for order-independent transparency
   - Screen-space ambient occlusion for splats
   - HDR and tone mapping

2. **Editing Tools:**
   - Splat painting/sculpting
   - Splat merging/splitting
   - Splat animation

3. **Compression:**
   - SPZ compression support
   - Streaming for large datasets

4. **Export:**
   - Export modified splats to .ply/.splat/.spz
   - glTF export with KHR_gaussian_splatting

---

## Key Files Summary

**New Files (26 total):**
```
src/main/java/cn/minerealms/assimploader/rendering/splat/
  ├── GaussianSplatRenderer.java          [Core renderer]
  ├── GaussianSplatVBO.java               [Buffer management]
  ├── GaussianSplatBuffer.java            [Data packing]
  ├── GaussianSplatShader.java            [Shader compiler]
  ├── GaussianSplatSorter.java            [Depth sorting]
  ├── GaussianSplatRenderState.java       [GL state]
  └── data/
      ├── GaussianSplat.java              [Splat data structure]
      └── GaussianSplatData.java          [Splat container]

src/main/java/cn/minerealms/assimploader/lookdev/render/
  └── GaussianSplatPreviewRenderer.java   [Preview renderer]

src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/nodes/
  ├── GaussianSplatNode.java              [Splat input node]
  ├── SplatColorNode.java                 [Color modifier]
  └── SplatOpacityNode.java               [Opacity modifier]

src/main/java/cn/minerealms/assimploader/io/splat/
  ├── SplatLoader.java                    [Format loader]
  └── SplatResourceManager.java           [Resource management]

src/main/resources/assets/assimploader/shaders/core/
  ├── gaussian_splat.vert                 [Vertex shader]
  └── gaussian_splat.frag                 [Fragment shader]
```

**Modified Files (5 total):**
```
src/main/java/cn/minerealms/assimploader/
  ├── render/AssimpItemRenderer.java      [Add splat detection]
  ├── lookdev/render/OffscreenRenderer.java [Add splat rendering]
  ├── lookdev/ViewMode.java               [Add splat view modes]
  ├── lookdev/iris/OculusShaderInjector.java [Add splat injection]
  └── lookdev/nodeeditor/shader/DualModeShaderManager.java [Add splat compilation]
```

---

## Conclusion

This plan provides a complete bottom-up integration path for Gaussian Splatting into AssimpLoader. The architecture follows existing patterns (SkinnedMeshRenderer, VBO caching, dual-mode shaders) for consistency. Oculus compatibility is addressed via shader injection with TBO fallback. Implementation is broken into 7 sprints totaling 40-54 hours.

**Next Steps:**
1. Review and approve plan
2. Begin Sprint 1 (Foundation)
3. Iterate based on testing results
