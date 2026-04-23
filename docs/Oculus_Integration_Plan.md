# Oculus Integration Plan for AssimpLoader

## Overview

This document outlines the integration strategy for Oculus (Iris Shaders for Forge) support in AssimpLoader. The integration will be implemented as a **soft dependency** - AssimpLoader will work with or without Oculus, providing enhanced shader-based rendering when available.

## Goals

1. **Soft Dependency**: No hard dependency on Oculus - graceful degradation when absent
2. **Shader Compatibility**: FBX models render correctly with shader packs active
3. **PBR Support**: Leverage Oculus PBR system for normal/specular maps
4. **No Crashes**: Zero crashes whether Oculus is loaded or not
5. **Performance**: Minimal overhead when shaders disabled

## Architecture Analysis

### Oculus Rendering Pipeline

```
IrisRenderingPipeline (Main Pipeline)
├── beginLevelRendering() - Setup, clear buffers, uniforms
├── renderShadows() - Shadow map generation
├── beginTranslucents() - Deferred rendering phase
├── finalizeLevelRendering() - Composite passes
└── finalizeGameRendering() - Post-processing

Rendering Phases:
- NONE, SKY, TERRAIN, TRANSLUCENT, HAND, WEATHER, PARTICLES, OUTLINE, SHADOW
```

### Key Integration Points

1. **IrisApi (v0)** - Public API for mod compatibility
   - `isShaderPackInUse()` - Detect active shaders
   - `isRenderingShadowPass()` - Shadow pass detection
   
2. **WorldRenderingPipeline** - Core rendering interface
   - Access to render targets (colortex0-15)
   - Shadow map access
   - Phase management

3. **PBRTextureManager** - Material texture system
   - Normal map loading
   - Specular map loading
   - Default texture fallbacks

4. **IrisSamplers** - Texture unit management
   - Reserved units: 0 (albedo), 1 (overlay), 2 (lightmap)
   - PBR samplers for normal/specular
   - Custom texture binding

## Implementation Strategy

### Phase 1: Soft Dependency Detection

**File**: `src/main/java/cn/minerealms/assimploader/compat/OculusCompat.java`

```java
public class OculusCompat {
    private static Boolean OCULUS_LOADED = null;
    private static IrisApi irisApi = null;
    
    public static boolean isOculusLoaded() {
        if (OCULUS_LOADED == null) {
            OCULUS_LOADED = LoadingModList.get()
                .getModFileById("oculus") != null;
        }
        return OCULUS_LOADED;
    }
    
    public static boolean isShadersActive() {
        if (!isOculusLoaded()) return false;
        try {
            if (irisApi == null) {
                irisApi = IrisApi.getInstance();
            }
            return irisApi.isShaderPackInUse();
        } catch (Exception e) {
            return false;
        }
    }
    
    public static boolean isRenderingShadows() {
        if (!isShadersActive()) return false;
        try {
            return irisApi.isRenderingShadowPass();
        } catch (Exception e) {
            return false;
        }
    }
}
```

**Dependencies in mods.toml**:
```toml
# No changes needed - soft dependency via runtime detection
# Oculus is completely optional
```

### Phase 2: Shader-Aware Rendering

**Modify**: `AssimpBlockEntityRenderer.java`

```java
protected RenderType getRenderType(AssimpMaterial material, 
                                   ResourceLocation texture) {
    // Check if Oculus shaders are active
    if (OculusCompat.isShadersActive()) {
        // Use shader-compatible render types
        if (material != null && material.isTransparent()) {
            return RenderType.entityTranslucent(texture);
        } else {
            // Use cutout for better shader compatibility
            return RenderType.entityCutout(texture);
        }
    } else {
        // Vanilla rendering
        if (material != null && material.isTransparent()) {
            return RenderType.entityTranslucent(texture);
        } else {
            return RenderType.entityCutout(texture);
        }
    }
}
```

**Shadow Pass Handling**:
```java
@Override
public void render(T blockEntity, float partialTick, PoseStack poseStack,
                  MultiBufferSource bufferSource, int packedLight, 
                  int packedOverlay) {
    
    // Skip certain rendering during shadow pass
    if (OculusCompat.isRenderingShadows()) {
        // Simplified shadow rendering
        renderShadowGeometry(blockEntity, poseStack, bufferSource);
        return;
    }
    
    // Normal rendering
    renderNormal(blockEntity, poseStack, bufferSource, 
                packedLight, packedOverlay);
}
```

### Phase 3: PBR Texture Support

**New Class**: `src/main/java/cn/minerealms/assimploader/compat/OculusPBRSupport.java`

```java
public class OculusPBRSupport {
    private static PBRTextureManager pbrManager = null;
    
    public static void bindPBRTextures(AssimpMaterial material, 
                                      int albedoTextureId) {
        if (!OculusCompat.isShadersActive()) return;
        
        try {
            if (pbrManager == null) {
                pbrManager = PBRTextureManager.INSTANCE;
            }
            
            // Load normal and specular maps for this texture
            PBRTextureHolder holder = pbrManager
                .getOrLoadHolder(albedoTextureId);
            
            if (holder != null) {
                // Bind normal map to appropriate texture unit
                // Bind specular map to appropriate texture unit
                // Oculus handles this automatically via samplers
            }
        } catch (Exception e) {
            // Graceful degradation
        }
    }
}
```

**Material Extension**:
```java
// In AssimpMaterial.java
public String getNormalMapPath() {
    // Extract normal map from FBX material
    // Convention: texture_n.png for normal map
}

public String getSpecularMapPath() {
    // Extract specular map from FBX material
    // Convention: texture_s.png for specular map
}
```

### Phase 4: Extended Vertex Format

**For PBR Support**: Add tangent/bitangent to vertex data

```java
// In AssimpBlockEntityRenderer.renderMesh()
if (OculusCompat.isShadersActive()) {
    // Use extended vertex format with tangents
    // Required for normal mapping in shaders
    
    Vector3f tangent = calculateTangent(v0, v1, v2, 
                                       uv0, uv1, uv2);
    Vector3f bitangent = normal.cross(tangent);
    
    // Add to vertex data (if supported by RenderType)
}
```

### Phase 5: Custom Shader Uniforms (Optional)

**For Advanced Materials**:

```java
public class AssimpShaderUniforms {
    public static void setupUniforms(AssimpMaterial material) {
        if (!OculusCompat.isShadersActive()) return;
        
        // Set custom uniforms for FBX materials
        // - Metallic factor
        // - Roughness factor
        // - Emissive color
        // - Alpha cutoff
        
        // These would be read by custom shader packs
    }
}
```

## Testing Strategy

### Test Cases

1. **Without Oculus**:
   - ✓ FBX models render normally
   - ✓ No crashes or errors
   - ✓ All features work as before

2. **With Oculus (Shaders Disabled)**:
   - ✓ FBX models render normally
   - ✓ No performance impact
   - ✓ Graceful detection

3. **With Oculus (Shaders Enabled)**:
   - ✓ FBX models render with shader effects
   - ✓ Lighting works correctly
   - ✓ Shadows render properly
   - ✓ Translucency works

4. **With Oculus + PBR Shader Pack**:
   - ✓ Normal maps applied correctly
   - ✓ Specular highlights work
   - ✓ PBR materials render properly

### Test Models

- **Opaque Model**: CNC machine (existing)
- **Translucent Model**: CCB block (existing)
- **PBR Model**: New test model with normal/specular maps

## File Structure

```
src/main/java/cn/minerealms/assimploader/
├── compat/
│   ├── OculusCompat.java          # Soft dependency detection
│   ├── OculusPBRSupport.java      # PBR texture integration
│   └── OculusShaderUniforms.java  # Custom uniform support
├── example/
│   └── AssimpBlockEntityRenderer.java  # Modified for shader support
└── render/
    └── AssimpItemRenderer.java    # Modified for shader support

docs/
├── Oculus_Integration_Plan.md     # This document
└── Oculus_API_Reference.md        # API usage examples
```

## Implementation Timeline

1. **Phase 1** (1-2 hours): Soft dependency detection
2. **Phase 2** (2-3 hours): Shader-aware rendering
3. **Phase 3** (3-4 hours): PBR texture support
4. **Phase 4** (2-3 hours): Extended vertex format
5. **Phase 5** (1-2 hours): Testing and refinement

**Total**: ~10-14 hours

## Risks and Mitigation

### Risk 1: Oculus API Changes
- **Mitigation**: Use public IrisApi v0, avoid internal APIs
- **Fallback**: Graceful degradation on API errors

### Risk 2: Performance Impact
- **Mitigation**: Lazy initialization, caching, minimal overhead
- **Monitoring**: Profile with and without shaders

### Risk 3: Shader Pack Compatibility
- **Mitigation**: Test with popular shader packs (BSL, Complementary, Seus)
- **Documentation**: Document known issues

### Risk 4: Vertex Format Limitations
- **Mitigation**: Check RenderType capabilities before using extended formats
- **Fallback**: Use standard format if extended not supported

## Success Criteria

- ✓ Zero crashes with or without Oculus
- ✓ FBX models render correctly in all scenarios
- ✓ PBR materials work with compatible shader packs
- ✓ Performance impact < 5% when shaders disabled
- ✓ No hard dependency on Oculus
- ✓ Clean, maintainable code

## Future Enhancements

1. **Custom Shader Pack**: Create AssimpLoader-specific shader pack
2. **Advanced PBR**: Support for more PBR maps (AO, height, etc.)
3. **Shader Uniforms**: Expose FBX material properties to shaders
4. **Animation Support**: Shader-based skeletal animation
5. **Particle Effects**: Shader-enhanced particle systems

## References

- Oculus GitHub: https://github.com/Asek3/Oculus
- Iris Shaders: https://irisshaders.net/
- Oculus API: `net.irisshaders.iris.api.v0.IrisApi`
- Shader Pack Format: https://github.com/IrisShaders/ShaderDoc
