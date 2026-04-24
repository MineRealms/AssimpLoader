# Dual Mode System - Static vs Dynamic

## Overview

The Assimp Loader now supports two import modes for 3D models:

- **STATIC Mode**: Bakes all node transformations into vertices (default)
- **DYNAMIC Mode**: Preserves scene graph structure for runtime animation

## When to Use Each Mode

### STATIC Mode (Default)
Use for models that don't need runtime animation or node manipulation:
- Static decorative models
- Simple props and items
- Models where all transformations are final
- Better performance (fewer draw calls, pre-baked transforms)

### DYNAMIC Mode
Use for models that need runtime control:
- Animated models (rotating parts, moving components)
- Models where you need to control individual sub-objects
- Interactive models (e.g., turret barrels, doors, wheels)
- Models where node hierarchy matters at runtime

## Usage

### Loading Models

```java
// STATIC mode (default) - best for simple static models
AssimpScene scene = AssimpModelLoader.getInstance().loadModel(path);

// DYNAMIC mode (explicit) - for animated/controllable models
AssimpScene scene = AssimpModelLoader.getInstance().loadModel(path, ModelImportMode.DYNAMIC);

// AUTO-DETECT mode - let the system decide based on model characteristics
AssimpScene scene = AssimpModelLoader.getInstance().loadModel(path, true);
```

### Auto-Detection Heuristics

When using auto-detection (`loadModel(path, true)`), the system analyzes the model and chooses:

**DYNAMIC mode if:**
- Model has animations (keyframes, skeletal animation)
- Complex node hierarchy (more than 3 levels deep)
- Many nodes with transformations (more than 5 transformed nodes)

**STATIC mode if:**
- Simple flat structure
- Few or no node transformations
- No animations

**Recommendation:** For production code, explicitly specify the mode for predictable behavior. Use auto-detection during development to understand your models.

### Checking Mode at Runtime

```java
if (scene.isDynamic()) {
    // Scene graph is preserved
    AssimpNode node = scene.findNodeByName("GunBarrel");
    // Can manipulate node transformations
} else {
    // All transformations are baked
    // Direct mesh rendering
}
```

### Finding and Manipulating Nodes (DYNAMIC mode only)

```java
// Find a node by name
AssimpNode barrelNode = scene.findNodeByName("GunBarrel");

if (barrelNode != null) {
    // Get current transformation
    Matrix4f transform = barrelNode.getTransformation();
    
    // Apply rotation (example)
    Matrix4f rotation = new Matrix4f().rotateY((float) Math.toRadians(angle));
    transform.mul(rotation);
    
    // The renderer will automatically apply this transformation
}
```

## Technical Details

### STATIC Mode
- Uses `aiProcess_PreTransformVertices` flag
- Collapses scene graph into flat mesh list
- All node transformations baked into vertex positions
- Faster rendering (no matrix multiplications per frame)
- Cannot animate or modify individual parts

### DYNAMIC Mode
- Preserves original scene graph structure
- Each node retains its transformation matrix
- Renderer traverses scene graph recursively
- Applies cumulative transformations during rendering
- Slightly slower but enables runtime control

## Example: Rotating Turret Barrel

```java
public class TurretBlockEntityRenderer extends AssimpBlockEntityRenderer<TurretBlockEntity> {
    
    @Override
    protected void applyTransformations(PoseStack poseStack, TurretBlockEntity entity, float partialTick) {
        // Get the scene
        AssimpScene scene = AssimpModelLoader.getCachedModel(getModelName());
        
        if (scene != null && scene.isDynamic()) {
            // Find the barrel node
            AssimpNode barrel = scene.findNodeByName("Barrel");
            
            if (barrel != null) {
                // Calculate rotation based on entity state
                float rotation = entity.getBarrelRotation(partialTick);
                
                // Apply rotation to barrel node
                Matrix4f transform = barrel.getTransformation();
                transform.identity(); // Reset
                transform.rotateZ(rotation); // Rotate around Z axis
            }
        }
    }
    
    @Override
    protected String getModelName() {
        return "turret"; // Must be loaded with ModelImportMode.DYNAMIC
    }
}
```

## Performance Considerations

- **STATIC mode**: ~10-20% faster rendering, lower memory usage
- **DYNAMIC mode**: Slightly slower due to scene graph traversal, but enables animation
- For most static models, use STATIC mode
- Only use DYNAMIC mode when you actually need runtime control

## Migration Guide

Existing code will continue to work unchanged (defaults to STATIC mode). To enable dynamic features:

1. Load model with `ModelImportMode.DYNAMIC`
2. Use `scene.findNodeByName()` to locate nodes
3. Modify node transformations as needed
4. Renderer automatically applies transformations

## Limitations

- Node transformations in DYNAMIC mode are not persistent across frames
- You must reapply transformations each frame in `applyTransformations()`
- STATIC mode cannot be converted to DYNAMIC at runtime (must reload)
- Node names must match exactly (case-sensitive)

## Future Enhancements

Planned features for DYNAMIC mode:
- Skeletal animation support
- Keyframe interpolation
- Animation blending
- Bone-based deformation
