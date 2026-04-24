package cn.minerealms.assimploader.lookdev.material;

import cn.minerealms.assimploader.material.PBRMaterial;

import javax.annotation.Nullable;

/**
 * Manager for non-destructive material parameter overrides.
 *
 * Manages the relationship between original materials and their runtime overrides.
 * The original material is never modified - all changes are applied to a copy.
 *
 * Usage:
 * <pre>
 * MaterialOverrideManager manager = new MaterialOverrideManager(originalMaterial);
 * manager.setRoughnessOverride(0.8f);
 * manager.disableNormalMap();
 * PBRMaterial effective = manager.getEffectiveMaterial();
 * </pre>
 */
public class MaterialOverrideManager {

    // Original material (read-only)
    private final PBRMaterial originalMaterial;

    // Current override settings
    private final MaterialOverride currentOverride;

    // Cached effective material (original + overrides)
    @Nullable
    private PBRMaterial cachedEffectiveMaterial;

    // Dirty flag for cache invalidation
    private boolean dirty = true;

    /**
     * Create a manager for the given material
     *
     * @param originalMaterial The original material (will not be modified)
     */
    public MaterialOverrideManager(PBRMaterial originalMaterial) {
        this.originalMaterial = originalMaterial;
        this.currentOverride = new MaterialOverride();
    }

    // ==================== Channel Toggle Methods ====================

    /**
     * Enable/disable base color texture
     */
    public void setBaseColorEnabled(boolean enabled) {
        if (currentOverride.enableBaseColor != enabled) {
            currentOverride.enableBaseColor = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable normal map
     */
    public void setNormalEnabled(boolean enabled) {
        if (currentOverride.enableNormal != enabled) {
            currentOverride.enableNormal = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable metallic channel
     */
    public void setMetallicEnabled(boolean enabled) {
        if (currentOverride.enableMetallic != enabled) {
            currentOverride.enableMetallic = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable roughness channel
     */
    public void setRoughnessEnabled(boolean enabled) {
        if (currentOverride.enableRoughness != enabled) {
            currentOverride.enableRoughness = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable ambient occlusion
     */
    public void setAOEnabled(boolean enabled) {
        if (currentOverride.enableAO != enabled) {
            currentOverride.enableAO = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable emissive channel
     */
    public void setEmissiveEnabled(boolean enabled) {
        if (currentOverride.enableEmissive != enabled) {
            currentOverride.enableEmissive = enabled;
            markDirty();
        }
    }

    /**
     * Enable/disable alpha channel
     */
    public void setAlphaEnabled(boolean enabled) {
        if (currentOverride.enableAlpha != enabled) {
            currentOverride.enableAlpha = enabled;
            markDirty();
        }
    }

    // ==================== Parameter Override Methods ====================

    /**
     * Set roughness override (0-1)
     */
    public void setRoughnessOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(1.0f, value));
        }
        if (!equals(currentOverride.roughnessOverride, value)) {
            currentOverride.roughnessOverride = value;
            markDirty();
        }
    }

    /**
     * Set metallic override (0-1)
     */
    public void setMetallicOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(1.0f, value));
        }
        if (!equals(currentOverride.metallicOverride, value)) {
            currentOverride.metallicOverride = value;
            markDirty();
        }
    }

    /**
     * Set emission strength override (0-10)
     */
    public void setEmissionStrengthOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(10.0f, value));
        }
        if (!equals(currentOverride.emissionStrengthOverride, value)) {
            currentOverride.emissionStrengthOverride = value;
            markDirty();
        }
    }

    /**
     * Set alpha cutoff override (0-1)
     */
    public void setAlphaCutoffOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(1.0f, value));
        }
        if (!equals(currentOverride.alphaCutoffOverride, value)) {
            currentOverride.alphaCutoffOverride = value;
            markDirty();
        }
    }

    /**
     * Set normal strength override (0-2)
     */
    public void setNormalStrengthOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(2.0f, value));
        }
        if (!equals(currentOverride.normalStrengthOverride, value)) {
            currentOverride.normalStrengthOverride = value;
            markDirty();
        }
    }

    /**
     * Set AO factor override (0-1)
     */
    public void setAOFactorOverride(@Nullable Float value) {
        if (value != null) {
            value = Math.max(0.0f, Math.min(1.0f, value));
        }
        if (!equals(currentOverride.aoFactorOverride, value)) {
            currentOverride.aoFactorOverride = value;
            markDirty();
        }
    }

    /**
     * Set alpha mode override
     */
    public void setAlphaModeOverride(@Nullable PBRMaterial.AlphaMode value) {
        if (currentOverride.alphaModeOverride != value) {
            currentOverride.alphaModeOverride = value;
            markDirty();
        }
    }

    /**
     * Set double-sided override
     */
    public void setDoubleSidedOverride(@Nullable Boolean value) {
        if (!equals(currentOverride.doubleSidedOverride, value)) {
            currentOverride.doubleSidedOverride = value;
            markDirty();
        }
    }

    // ==================== Convenience Methods ====================

    /**
     * Disable normal map (shorthand)
     */
    public void disableNormalMap() {
        setNormalEnabled(false);
    }

    /**
     * Enable normal map (shorthand)
     */
    public void enableNormalMap() {
        setNormalEnabled(true);
    }

    // ==================== Core Methods ====================

    /**
     * Get the effective material (original + overrides).
     * Result is cached until overrides change.
     *
     * @return Material with overrides applied
     */
    public PBRMaterial getEffectiveMaterial() {
        if (dirty || cachedEffectiveMaterial == null) {
            cachedEffectiveMaterial = currentOverride.applyTo(originalMaterial);
            dirty = false;
        }
        return cachedEffectiveMaterial;
    }

    /**
     * Get the original material (read-only)
     *
     * @return Original material without overrides
     */
    public PBRMaterial getOriginalMaterial() {
        return originalMaterial;
    }

    /**
     * Get the current override settings
     *
     * @return Current override object
     */
    public MaterialOverride getCurrentOverride() {
        return currentOverride;
    }

    /**
     * Reset all overrides to defaults
     */
    public void resetOverrides() {
        currentOverride.reset();
        markDirty();
    }

    /**
     * Check if any overrides are active
     *
     * @return true if overrides are active
     */
    public boolean hasOverrides() {
        return currentOverride.hasOverrides();
    }

    /**
     * Mark the cache as dirty (needs rebuild)
     */
    private void markDirty() {
        dirty = true;
    }

    /**
     * Null-safe equality check for Float/Boolean
     */
    private static boolean equals(@Nullable Object a, @Nullable Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public String toString() {
        return "MaterialOverrideManager{" +
               "original='" + originalMaterial.getName() + "'" +
               ", overrides=" + (hasOverrides() ? "active" : "none") +
               ", dirty=" + dirty +
               '}';
    }
}
