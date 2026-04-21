package cn.minerealms.assimploader.resource;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents a location for an Assimp model resource.
 * Extends Minecraft's ResourceLocation with model-specific metadata.
 */
public class ModelResourceLocation {
    private final ResourceLocation location;
    private final String variant;
    private final boolean animated;

    public ModelResourceLocation(ResourceLocation location) {
        this(location, "default", false);
    }

    public ModelResourceLocation(ResourceLocation location, String variant) {
        this(location, variant, false);
    }

    public ModelResourceLocation(ResourceLocation location, String variant, boolean animated) {
        this.location = location;
        this.variant = variant;
        this.animated = animated;
    }

    public ResourceLocation getLocation() {
        return location;
    }

    public String getVariant() {
        return variant;
    }

    public boolean isAnimated() {
        return animated;
    }

    /**
     * Converts to a file path for loading from resource packs.
     * Example: "assimploader:models/entity/zombie.gltf"
     */
    public String toFilePath() {
        return "models/" + location.getPath();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ModelResourceLocation)) return false;
        ModelResourceLocation other = (ModelResourceLocation) obj;
        return location.equals(other.location) &&
               variant.equals(other.variant) &&
               animated == other.animated;
    }

    @Override
    public int hashCode() {
        int result = location.hashCode();
        result = 31 * result + variant.hashCode();
        result = 31 * result + (animated ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return location + "#" + variant + (animated ? "[animated]" : "");
    }
}
