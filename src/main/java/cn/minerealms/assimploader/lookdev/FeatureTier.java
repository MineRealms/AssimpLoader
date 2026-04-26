package cn.minerealms.assimploader.lookdev;

/**
 * Feature tier for LookDev system.
 *
 * Determines which features are available based on loaded mods:
 * - STANDALONE: Basic node editor without shader integration (Minecraft + AssimpLoader only)
 * - FULL_INTEGRATION: Complete system with Photon shader integration (Photon/Oculus loaded)
 *
 * The tier is detected at runtime and affects:
 * - Available node types
 * - UI elements shown
 * - Shader generation and compilation
 * - Material preview rendering
 */
public enum FeatureTier {
    /**
     * Standalone mode - Basic node editor only
     *
     * Available features:
     * - Node graph editing
     * - Basic node types (input, math, output)
     * - Graph execution and value computation
     * - Save/load node graphs
     *
     * Not available:
     * - Shader code generation
     * - Real-time shader preview
     * - Photon material integration
     */
    STANDALONE("Standalone", "Basic node editor without shader integration"),

    /**
     * Full integration mode - Complete system with Photon
     *
     * Available features:
     * - All STANDALONE features
     * - GLSL shader code generation from node graphs
     * - Real-time shader compilation via Photon
     * - Live material preview on 3D models
     * - Photon material system integration
     * - Advanced shader nodes
     */
    FULL_INTEGRATION("Full Integration", "Complete system with Photon shader integration");

    private final String displayName;
    private final String description;

    FeatureTier(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if shader features are available in this tier
     */
    public boolean hasShaderSupport() {
        return this == FULL_INTEGRATION;
    }

    /**
     * Check if Photon integration is available in this tier
     */
    public boolean hasPhotonIntegration() {
        return this == FULL_INTEGRATION;
    }

    /**
     * Check if real-time preview is available in this tier
     */
    public boolean hasRealtimePreview() {
        return this == FULL_INTEGRATION;
    }
}
