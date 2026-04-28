package cn.minerealms.assimploader.lod;

import net.minecraft.world.phys.Vec3;

/**
 * LOD (Level of Detail) manager for distance-based mesh quality selection.
 * Reduces rendering complexity for distant objects.
 */
public class LODManager {

    /**
     * LOD levels based on distance from camera
     */
    public enum LODLevel {
        HIGH(0, 16),      // 0-16 blocks: Full detail
        MEDIUM(16, 48),   // 16-48 blocks: Medium detail
        LOW(48, 96),      // 48-96 blocks: Low detail
        CULLED(96, Double.MAX_VALUE); // 96+ blocks: Don't render

        private final double minDistance;
        private final double maxDistance;

        LODLevel(double minDistance, double maxDistance) {
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
        }

        public double getMinDistance() {
            return minDistance;
        }

        public double getMaxDistance() {
            return maxDistance;
        }

        /**
         * Get mesh quality multiplier (1.0 = full quality, 0.5 = half quality)
         */
        public float getQualityMultiplier() {
            return switch (this) {
                case HIGH -> 1.0f;
                case MEDIUM -> 0.6f;
                case LOW -> 0.3f;
                case CULLED -> 0.0f;
            };
        }

        /**
         * Get animation update frequency (1 = every frame, 2 = every 2 frames, etc.)
         */
        public int getAnimationUpdateFrequency() {
            return switch (this) {
                case HIGH -> 1;
                case MEDIUM -> 2;
                case LOW -> 4;
                case CULLED -> Integer.MAX_VALUE;
            };
        }
    }

    /**
     * Get LOD level based on distance from camera to entity
     *
     * @param cameraPos Camera position
     * @param entityPos Entity position
     * @return Appropriate LOD level
     */
    public static LODLevel getLODLevel(Vec3 cameraPos, Vec3 entityPos) {
        double distance = cameraPos.distanceTo(entityPos);
        return getLODLevel(distance);
    }

    /**
     * Get LOD level based on distance
     *
     * @param distance Distance in blocks
     * @return Appropriate LOD level
     */
    public static LODLevel getLODLevel(double distance) {
        for (LODLevel level : LODLevel.values()) {
            if (distance >= level.minDistance && distance < level.maxDistance) {
                return level;
            }
        }
        return LODLevel.CULLED;
    }

    /**
     * Check if entity should be rendered at this distance
     *
     * @param distance Distance in blocks
     * @return true if should render, false if culled
     */
    public static boolean shouldRender(double distance) {
        return getLODLevel(distance) != LODLevel.CULLED;
    }

    /**
     * Get recommended vertex count for LOD level
     *
     * @param originalVertexCount Original mesh vertex count
     * @param lodLevel LOD level
     * @return Recommended vertex count
     */
    public static int getRecommendedVertexCount(int originalVertexCount, LODLevel lodLevel) {
        return (int) (originalVertexCount * lodLevel.getQualityMultiplier());
    }
}
