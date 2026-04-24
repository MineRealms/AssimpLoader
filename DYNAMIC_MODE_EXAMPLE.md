# Dynamic Mode Example: Gatling Gun Barrel Rotation

This example demonstrates how to use DYNAMIC mode to create a rotating gatling gun barrel.

## Step 1: Export FBX with Proper Node Structure

In Maya/Blender, structure your model like this:

```
RootNode
├─ GunBody (static mesh)
├─ BarrelAssembly (parent node)
│  └─ Barrels (rotating mesh - 6 barrels arranged in circle)
└─ Mount (static mesh)
```

**Important**: Name the rotating part clearly (e.g., "Barrels" or "BarrelAssembly")

Export settings:
- FBX 2014/2015 format
- Embed Media: ON
- Triangulate: ON
- Preserve node hierarchy

## Step 2: Load Model in DYNAMIC Mode

```java
public class ModModels {
    public static void loadModels() {
        try {
            // Load gatling gun in DYNAMIC mode to preserve scene graph
            Path modelPath = Paths.get("models/blocks/gatling_gun.fbx");
            AssimpScene scene = AssimpModelLoader.getInstance()
                .loadModel(modelPath, ModelImportMode.DYNAMIC);
            
            AssimpModelLoader.cacheModel("gatling_gun", scene);
            
            AssimpLoader.LOGGER.info("Gatling gun loaded in DYNAMIC mode");
            
            // Verify the barrel node exists
            AssimpNode barrels = scene.findNodeByName("Barrels");
            if (barrels != null) {
                AssimpLoader.LOGGER.info("Found barrel node: {}", barrels.getName());
            } else {
                AssimpLoader.LOGGER.warn("Barrel node not found! Check FBX node names.");
            }
            
        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to load gatling gun model", e);
        }
    }
}
```

## Step 3: Create Block Entity

```java
public class GatlingGunBlockEntity extends BlockEntity {
    
    private float barrelRotation = 0.0f;
    private float barrelRotationSpeed = 0.0f;
    private boolean isFiring = false;
    
    public GatlingGunBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GATLING_GUN.get(), pos, state);
    }
    
    public static void tick(Level level, BlockPos pos, BlockState state, 
                           GatlingGunBlockEntity entity) {
        if (level.isClientSide) {
            entity.clientTick();
        }
    }
    
    private void clientTick() {
        // Spin up/down logic
        if (isFiring) {
            // Accelerate to max speed
            barrelRotationSpeed = Math.min(barrelRotationSpeed + 0.5f, 30.0f);
        } else {
            // Decelerate
            barrelRotationSpeed = Math.max(barrelRotationSpeed - 0.2f, 0.0f);
        }
        
        // Update rotation
        barrelRotation += barrelRotationSpeed;
        if (barrelRotation >= 360.0f) {
            barrelRotation -= 360.0f;
        }
    }
    
    public void startFiring() {
        this.isFiring = true;
    }
    
    public void stopFiring() {
        this.isFiring = false;
    }
    
    public float getBarrelRotation(float partialTick) {
        return barrelRotation + barrelRotationSpeed * partialTick;
    }
}
```

## Step 4: Create Renderer with Dynamic Transformation

```java
public class GatlingGunRenderer extends AssimpBlockEntityRenderer<GatlingGunBlockEntity> {
    
    @Override
    protected String getModelName() {
        return "gatling_gun";
    }
    
    @Override
    protected void applyTransformations(PoseStack poseStack, 
                                       GatlingGunBlockEntity entity, 
                                       float partialTick) {
        // Get the scene
        AssimpScene scene = AssimpModelLoader.getCachedModel(getModelName());
        
        if (scene == null || !scene.isDynamic()) {
            AssimpLoader.LOGGER.warn("Gatling gun scene not loaded or not in DYNAMIC mode!");
            return;
        }
        
        // Find the barrel node
        AssimpNode barrels = scene.findNodeByName("Barrels");
        
        if (barrels == null) {
            // Try alternative names
            barrels = scene.findNodeByName("BarrelAssembly");
        }
        
        if (barrels != null) {
            // Get interpolated rotation
            float rotation = entity.getBarrelRotation(partialTick);
            float rotationRadians = (float) Math.toRadians(rotation);
            
            // Create rotation matrix
            Matrix4f transform = barrels.getTransformation();
            
            // Reset to identity (clear any previous rotation)
            transform.identity();
            
            // Apply rotation around Z axis (assuming barrels face forward)
            // Adjust axis based on your model's orientation:
            // - rotateZ() if barrels face along X/Y
            // - rotateX() if barrels face along Y/Z
            // - rotateY() if barrels face along X/Z
            transform.rotateZ(rotationRadians);
            
            // Optional: Add offset if rotation pivot is not at origin
            // transform.translate(offsetX, offsetY, offsetZ);
            // transform.rotateZ(rotationRadians);
            // transform.translate(-offsetX, -offsetY, -offsetZ);
        }
    }
}
```

## Step 5: Register Renderer

```java
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(
                ModBlockEntities.GATLING_GUN.get(),
                GatlingGunRenderer::new
            );
        });
    }
}
```

## Troubleshooting

### Barrels not rotating
1. Check node name matches exactly (case-sensitive)
2. Verify model loaded in DYNAMIC mode: `scene.isDynamic()`
3. Check rotation axis (X/Y/Z) matches your model's orientation
4. Add debug logging to verify `getBarrelRotation()` returns changing values

### Barrels rotating around wrong point
The rotation pivot might not be at the origin. Use this pattern:

```java
// Get the original transformation (contains the pivot offset)
Matrix4f originalTransform = new Matrix4f(barrels.getTransformation());

// Extract translation (pivot point)
Vector3f pivot = originalTransform.getTranslation(new Vector3f());

// Build new transform: translate to origin, rotate, translate back
transform.identity();
transform.translate(pivot);
transform.rotateZ(rotationRadians);
transform.translate(pivot.negate());
```

### Performance issues
- Ensure only the rotating part is in DYNAMIC mode
- Consider using STATIC mode for the gun body
- Limit rotation updates to when actually firing

## Advanced: Multiple Rotating Parts

For complex models with multiple rotating parts:

```java
@Override
protected void applyTransformations(PoseStack poseStack, 
                                   ComplexTurretBlockEntity entity, 
                                   float partialTick) {
    AssimpScene scene = AssimpModelLoader.getCachedModel(getModelName());
    
    if (scene != null && scene.isDynamic()) {
        // Rotate turret base (yaw)
        AssimpNode base = scene.findNodeByName("TurretBase");
        if (base != null) {
            float yaw = entity.getYaw(partialTick);
            base.getTransformation().identity().rotateY((float) Math.toRadians(yaw));
        }
        
        // Rotate barrel assembly (pitch)
        AssimpNode barrel = scene.findNodeByName("BarrelAssembly");
        if (barrel != null) {
            float pitch = entity.getPitch(partialTick);
            barrel.getTransformation().identity().rotateX((float) Math.toRadians(pitch));
        }
        
        // Rotate gatling barrels (spin)
        AssimpNode barrels = scene.findNodeByName("Barrels");
        if (barrels != null) {
            float spin = entity.getBarrelSpin(partialTick);
            barrels.getTransformation().identity().rotateZ((float) Math.toRadians(spin));
        }
    }
}
```

## Notes

- Transformations are applied every frame, not persistent
- Must reapply transformations in `applyTransformations()` each render
- Node transformations are relative to parent node
- Use `scene.getRootNode()` to traverse entire hierarchy if needed
