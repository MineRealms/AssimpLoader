package cn.minerealms.assimploader.lookdev;

/**
 * View modes for the LookDev viewer.
 *
 * Controls what is displayed in the preview area.
 */
public enum ViewMode {
    /**
     * Full PBR rendering with all channels
     */
    FULL("Full Render"),

    /**
     * Base color only (albedo)
     */
    BASE_COLOR("Base Color"),

    /**
     * Normal map visualization
     */
    NORMAL("Normal Map"),

    /**
     * Metallic channel only
     */
    METALLIC("Metallic"),

    /**
     * Roughness channel only
     */
    ROUGHNESS("Roughness"),

    /**
     * Ambient occlusion only
     */
    AO("Ambient Occlusion"),

    /**
     * Emissive channel only
     */
    EMISSIVE("Emissive"),

    /**
     * Alpha/opacity channel
     */
    ALPHA("Alpha"),

    /**
     * Wireframe overlay
     */
    WIREFRAME("Wireframe");

    private final String displayName;

    ViewMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
