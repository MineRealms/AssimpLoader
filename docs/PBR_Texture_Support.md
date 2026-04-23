# PBR Texture Support for Oculus Integration

## Overview

AssimpLoader now supports PBR (Physically Based Rendering) textures when Oculus shaders are active. This enables FBX models to use normal maps, specular maps, and other advanced material properties for realistic lighting and surface detail.

## Texture Naming Conventions

PBR textures follow standard naming conventions:

- **Albedo/Diffuse**: `texture.png` (base color)
- **Normal Map**: `texture_n.png` (tangent-space normals)
- **Specular Map**: `texture_s.png` (specular/metallic/roughness)
- **Emissive Map**: `texture_e.png` (self-illumination, future support)

## How It Works

### 1. Automatic Detection

The system automatically detects if Oculus is loaded and if the active shader pack supports PBR:

```java
if (OculusPBRSupport.isPBRAvailable()) {
    // PBR textures will be registered
}
```

### 2. Texture Registration

When rendering a model, the system:
1. Checks if the material has explicit normal/specular texture paths
2. If not, generates conventional paths based on the albedo texture
3. Registers these textures with Oculus PBRTextureManager
4. Oculus automatically loads and binds them during rendering

### 3. Material Properties

FBX materials can specify PBR textures in two ways:

**Explicit Paths** (from FBX material):
```java
material.getNormalTexture()   // Returns path from aiTextureType_NORMALS
material.getSpecularTexture()  // Returns path from aiTextureType_SPECULAR
```

**Conventional Paths** (auto-generated):
```java
material.getNormalMapPath()    // Generates texture_n.png from texture.png
material.getSpecularMapPath()  // Generates texture_s.png from texture.png
```

## Usage in FBX Models

### Option 1: Explicit Material Properties

In your 3D modeling software (Blender, Maya, etc.), assign normal and specular maps to the material:
- Normal Map → Normal/Bump channel
- Specular Map → Specular/Metallic channel

When exported to FBX, these will be detected automatically.

### Option 2: Conventional File Naming

Place PBR textures alongside your FBX model with conventional names:

```
models/block/
├── cnc.fbx
├── cnc.png          (albedo/diffuse)
├── cnc_n.png        (normal map)
└── cnc_s.png        (specular map)
```

The system will automatically find and register them.

## Shader Pack Compatibility

PBR support requires:
1. Oculus mod installed
2. A shader pack that supports PBR (e.g., BSL, Complementary, Seus PTGI)
3. Shader pack must be active (not disabled)

If any of these conditions are not met, the system gracefully falls back to vanilla rendering.

## Implementation Details

### OculusPBRSupport Class

Located at: `src/main/java/cn/minerealms/assimploader/compat/OculusPBRSupport.java`

Key methods:
- `isPBRAvailable()` - Check if PBR system is available
- `registerPBRTextures(albedo, material)` - Register PBR textures for a material
- `getNormalMapPath(texturePath)` - Generate conventional normal map path
- `getSpecularMapPath(texturePath)` - Generate conventional specular map path

### Reflection-Based API Access

Like OculusCompat, OculusPBRSupport uses reflection to access Oculus APIs:

```java
Class<?> pbrManagerClass = Class.forName("net.irisshaders.iris.pbr.PBRTextureManager");
java.lang.reflect.Field instanceField = pbrManagerClass.getField("INSTANCE");
pbrTextureManager = instanceField.get(null);
```

This ensures no hard dependency on Oculus.

### Integration Points

PBR registration happens in two places:

1. **AssimpBlockEntityRenderer** (line ~110):
```java
if (textureLocation != null && material != null) {
    OculusPBRSupport.registerPBRTextures(textureLocation, material);
}
```

2. **AssimpItemRenderer** (line ~135):
```java
if (textureLocation != null && material != null) {
    OculusPBRSupport.registerPBRTextures(textureLocation, material);
}
```

## Testing

### Test Cases

1. **Without Oculus**:
   - Models render normally with vanilla lighting
   - No PBR textures loaded
   - No errors or warnings

2. **With Oculus, Shaders Disabled**:
   - Models render normally with vanilla lighting
   - PBR system not initialized
   - No performance impact

3. **With Oculus, Non-PBR Shader Pack**:
   - Models render with shader effects
   - PBR textures not used (shader pack doesn't support them)
   - Graceful degradation

4. **With Oculus, PBR Shader Pack**:
   - Models render with full PBR effects
   - Normal maps provide surface detail
   - Specular maps control reflectivity
   - Realistic lighting and materials

### Creating Test Models

To test PBR support:

1. Create a simple FBX model in Blender
2. Create three textures:
   - `test.png` - Base color (e.g., brick pattern)
   - `test_n.png` - Normal map (blue-purple tangent-space normals)
   - `test_s.png` - Specular map (grayscale, white = shiny, black = rough)
3. Place in `models/block/` directory
4. Load with a PBR shader pack (BSL, Complementary)
5. Observe realistic lighting and surface detail

## Performance Considerations

- PBR texture registration is lightweight (only happens once per texture)
- Reflection-based API access is cached after first use
- No performance impact when Oculus is not loaded
- Minimal overhead when shaders are disabled

## Future Enhancements

Potential additions for Phase 5+:
- Emissive map support (self-illumination)
- Height/displacement map support
- Ambient occlusion map support
- Roughness/metallic separation (separate maps instead of packed)
- Custom shader uniforms for advanced materials

## Troubleshooting

**PBR textures not appearing:**
1. Check that Oculus is installed and shaders are active
2. Verify shader pack supports PBR (check shader pack documentation)
3. Ensure texture files exist and follow naming convention
4. Check logs for PBR initialization messages

**Performance issues:**
1. Reduce texture resolution (PBR textures can be large)
2. Use texture compression (DXT5 for normal maps)
3. Disable PBR in shader pack settings if not needed

**Compatibility issues:**
1. Update Oculus to latest version
2. Try different shader packs
3. Check for conflicting mods that modify rendering
