package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lod.LODManager;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * BlockEntity for CircleBlock with automatic animation playback.
 *
 * Features:
 * - Automatically detects and plays the first animation in the FBX file
 * - Loops animation continuously
 * - Independent animation state per block instance
 */
public class CircleBlockEntity extends BlockEntity {

    // Static registry to track all instances for hot-reload
    private static final Set<CircleBlockEntity> ALL_INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    private AnimationState animationState;
    private boolean initialized = false;
    private AssimpScene lastScene = null; // Track scene reference for hot-reload detection
    private int tickCounter = 0; // For LOD-based animation update frequency

    public CircleBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.CIRCLE_BLOCK_ENTITY.get(), pos, state);
        ALL_INSTANCES.add(this);
    }

    /**
     * Initialize animation state (lazy initialization)
     */
    private void initializeAnimation() {
        if (!initialized) {
            // Model name matches the FBX filename (without extension)
            String modelName = "circle";

            DebugManager.info(DebugCategory.ANIMATION, "Initializing CircleBlock animation at {}", worldPosition);

            animationState = new AnimationState(modelName);
            animationState.initialize();

            // Save scene reference for hot-reload detection
            lastScene = animationState.getScene();

            if (animationState.isInitialized()) {
                // Get available animations
                String[] animations = animationState.getAvailableAnimations();

                if (animations.length > 0) {
                    // Play the first animation found (loop enabled by default)
                    String firstAnimation = animations[0];
                    animationState.playAnimation(firstAnimation);

                    DebugManager.info(DebugCategory.ANIMATION,
                        "CircleBlock playing animation: '{}' (total animations: {})",
                        firstAnimation, animations.length);

                    // Log all available animations
                    if (animations.length > 1) {
                        DebugManager.info(DebugCategory.ANIMATION, "Available animations: {}",
                            String.join(", ", animations));
                    }
                } else {
                    DebugManager.warn(DebugCategory.ANIMATION,
                        "CircleBlock: No animations found in model '{}'", modelName);
                }
            } else {
                DebugManager.error(DebugCategory.ANIMATION,
                    "CircleBlock: Failed to initialize animation state for model '{}'", modelName);
            }

            initialized = true;
        }
    }

    /**
     * Tick method - update animation every tick (with LOD-based frequency)
     */
    public void tick() {
        if (!initialized) {
            initializeAnimation();
        }

        if (animationState != null && animationState.isInitialized()) {
            // LOD: Calculate distance and determine update frequency
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 entityPos = new Vec3(
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5
                );
                LODManager.LODLevel lodLevel = LODManager.getLODLevel(cameraPos, entityPos);
                int updateFrequency = lodLevel.getAnimationUpdateFrequency();

                // Update animation only at the appropriate frequency
                tickCounter++;
                if (tickCounter >= updateFrequency) {
                    // Update animation (1 tick = 0.05 seconds at 20 TPS)
                    // Multiply deltaTime by tickCounter to maintain correct animation speed
                    animationState.update(0.05f * tickCounter);
                    tickCounter = 0;
                }
            } else {
                // Fallback: update every tick if no player
                animationState.update(0.05f);
            }
        }
    }

    /**
     * Get animation state for rendering
     */
    public AnimationState getAnimationState() {
        if (!initialized) {
            initializeAnimation();
        }
        return animationState;
    }

    /**
     * Called by hot-reload system to reinitialize animation with new model
     */
    public void onModelReloaded() {
        DebugManager.info(DebugCategory.ANIMATION,
            "CircleBlockEntity at {}: Model reloaded, reinitializing animation", worldPosition);
        markForReinitialization();
        initializeAnimation();
    }

    /**
     * Notify all CircleBlockEntity instances to reload (called by hot-reload system)
     */
    public static int notifyAllReload() {
        int count = 0;
        for (CircleBlockEntity entity : ALL_INSTANCES) {
            entity.onModelReloaded();
            count++;
        }
        return count;
    }

    /**
     * Mark for reinitialization (called on hot-reload)
     */
    public void markForReinitialization() {
        DebugManager.info(DebugCategory.ANIMATION, "CircleBlockEntity at {} marked for reinitialization", worldPosition);
        initialized = false;
        animationState = null;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Load animation state if needed (currently not persisted)
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Save animation state if needed (currently not persisted)
    }
}
