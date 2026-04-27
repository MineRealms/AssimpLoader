# Skeletal Skinning System - TODO

## Phase 8: GLSL-Transformer Integration (Future Enhancement)

### Current Status
- ✅ Basic GLSL shaders implemented (skinned_mesh.vert/frag)
- ❌ GLSL-Transformer not yet integrated
- ❌ No shader preprocessing/optimization

### Why GLSL-Transformer?
GLSL-Transformer provides:
1. **Shader preprocessing** - Conditional compilation, includes
2. **Optimization** - Dead code elimination, constant folding
3. **Cross-platform compatibility** - GLSL version translation
4. **Shader variants** - Generate multiple shader permutations

### Integration Plan

#### 1. Add GLSL-Transformer Dependency
```gradle
dependencies {
    implementation 'io.github.douira:glsl-transformer:2.0.0-pre13'
}
```

#### 2. Create Shader Preprocessor
```java
public class SkinnedShaderPreprocessor {
    private final GLSLParser parser;
    private final ShaderTransformer transformer;
    
    public String preprocess(String source, Map<String, String> defines) {
        // Parse GLSL
        // Apply transformations
        // Optimize
        // Return processed shader
    }
}
```

#### 3. Shader Variants
Create shader variants for different scenarios:
- `SKINNED_MESH_BASIC` - Basic bone skinning
- `SKINNED_MESH_NORMAL_MAP` - With normal mapping
- `SKINNED_MESH_PBR` - With PBR materials
- `SKINNED_MESH_MORPH` - With morph targets

#### 4. Shader Template System
```glsl
// skinned_mesh.vert.template
#version 330 core

#ifdef NORMAL_MAPPING
in vec3 a_Tangent;
in vec3 a_Bitangent;
#endif

#ifdef MORPH_TARGETS
uniform vec3 u_MorphTargets[8];
uniform float u_MorphWeights[8];
#endif

// ... rest of shader
```

#### 5. Runtime Shader Compilation
```java
public class SkinnedMeshShader {
    public void compile(ShaderVariant variant) {
        Map<String, String> defines = new HashMap<>();
        
        if (variant.hasNormalMapping()) {
            defines.put("NORMAL_MAPPING", "1");
        }
        
        if (variant.hasMorphTargets()) {
            defines.put("MORPH_TARGETS", "1");
        }
        
        String vertexSource = preprocessor.preprocess(
            loadTemplate("skinned_mesh.vert.template"),
            defines
        );
        
        // Compile shader...
    }
}
```

### Benefits
1. **Performance** - Optimized shaders, no runtime branching
2. **Flexibility** - Easy to add new features via defines
3. **Maintainability** - Single shader template, multiple variants
4. **Compatibility** - Automatic GLSL version translation

### Implementation Priority
- Priority: **Medium** (current shaders work, but optimization would help)
- Estimated time: 4-6 hours
- Dependencies: None (can be done independently)

### Notes
- Current implementation uses raw GLSL for simplicity
- GLSL-Transformer integration is optional enhancement
- Should be done after basic skinning is tested and working
