package cn.minerealms.assimploader.lookdev.material;

import cn.minerealms.assimploader.material.PBRMaterial;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * Material parameter override container.
 *
 * Provides non-destructive runtime overrides for PBR material parameters.
 * Original material data is never modified - overrides are applied to a copy.
 *
 * Usage:
 * <pre>
 * MaterialOverride override = new MaterialOverride();
 * override.enableNormal = false;  // Disable normal map
 * override.roughnessOverride = 0.8f;  // Override roughness
 * PBRMaterial result = override.applyTo(originalMaterial);
 * </pre>
 */
public class MaterialOverride {

    // ==================== Channel Toggles ====================

    /**
     * Enable/disable base color texture.
     * When disabled, uses solid white (1,1,1,1) for lighting calculations.
     */
    public boolean enableBaseColor = true;

    /**
     * Enable/disable normal map.
     * When disabled, uses flat normals (no bump mapping).
     */
    public boolean enableNormal = true;

    /**
     * Enable/disable metallic channel.
     * When disabled, treats surface as fully dielectric (metallic = 0).
     */
    public boolean enableMetallic = true;

    /**
     * Enable/disable roughness channel.
     * When disabled, uses default roughness (0.5).
     */
    public boolean enableRoughness = true;

    /**
     * Enable/disable ambient occlusion.
     * When disabled, no AO shadowing is applied.
     */
    public boolean enableAO = true;

    /**
     * Enable/disable emissive channel.
     * When disabled, no self-illumination.
     */
    public boolean enableEmissive = true;

    /**
     * Enable/disable alpha/opacity.
     * When disabled, treats as fully opaque.
     */
    public boolean enableAlpha = true;

    // ==================== Parameter Overrides ====================

    /**
     * Override roughness factor (0-1).
     * null = use original value
     */
    @Nullable
    public Float roughnessOverride = null;

    /**
     * Override metallic factor (0-1).
     * null = use original value
     */
    @Nullable
    public Float metallicOverride = null;

    /**
     * Override emission strength multiplier (0-10).
     * null = use original value
     */
    @Nullable
    public Float emissionStrengthOverride = null;

    /**
     * Override alpha cutoff for MASK mode (0-1).
     * null = use original value
     */
    @Nullable
    public Float alphaCutoffOverride = null;

    /**
     * Override normal map strength (0-2).
     * null = use original value
     */
    @Nullable
    public Float normalStrengthOverride = null;

    /**
     * Override AO factor (0-1).
     * null = use original value
     */
    @Nullable
    public Float aoFactorOverride = null;

    // ==================== RenderType Override ====================

    /**
     * Override alpha mode.
     * null = use original value
     */
    @Nullable
    public PBRMaterial.AlphaMode alphaModeOverride = null;

    /**
     * Override double-sided rendering.
     * null = use original value
     */
    @Nullable
    public Boolean doubleSidedOverride = null;

    // ==================== Apply Method ====================

    /**
     * Apply overrides to a material, creating a modified copy.
     *
     * The original material is never modified.
     *
     * @param original Original material (read-only)
     * @return New material with overrides applied
     */
    public PBRMaterial applyTo(PBRMaterial original) {
        // Create a copy of the original material
        PBRMaterial result = copyMaterial(original);

        // Apply channel toggles
        applyChannelToggles(result);

        // Apply parameter overrides
        applyParameterOverrides(result);

        return result;
    }

    /**
     * Create a deep copy of a PBRMaterial
     */
    private PBRMaterial copyMaterial(PBRMaterial original) {
        PBRMaterial copy = new PBRMaterial(original.getName() + "_override");

        // Copy base color
        copy.setBaseColor(new Vector4f(original.getBaseColor()));
        copy.setBaseColorTexture(original.getBaseColorTexture());

        // Copy metallic/roughness
        copy.setMetallicFactor(original.getMetallicFactor());
        copy.setRoughnessFactor(original.getRoughnessFactor());
        copy.setMetallicRoughnessTexture(original.getMetallicRoughnessTexture());

        // Copy normal
        copy.setNormalTexture(original.getNormalTexture());
        copy.setNormalScale(original.getNormalScale());

        // Copy emissive
        copy.setEmissiveFactor(new Vector3f(original.getEmissiveFactor()));
        copy.setEmissiveTexture(original.getEmissiveTexture());

        // Copy AO
        copy.setAoFactor(original.getAoFactor());
        copy.setAoTexture(original.getAoTexture());

        // Copy height
        copy.setHeightTexture(original.getHeightTexture());
        copy.setHeightScale(original.getHeightScale());

        // Copy alpha
        copy.setAlphaMode(original.getAlphaMode());
        copy.setAlphaCutoff(original.getAlphaCutoff());

        // Copy other properties
        copy.setDoubleSided(original.isDoubleSided());
        copy.setSpecularColor(new Vector3f(original.getSpecularColor()));
        copy.setShininess(original.getShininess());

        return copy;
    }

    /**
     * Apply channel toggle overrides
     */
    private void applyChannelToggles(PBRMaterial material) {
        // Disable base color: use white texture
        if (!enableBaseColor) {
            material.setBaseColorTexture(null);
            material.setBaseColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Disable normal map
        if (!enableNormal) {
            material.setNormalTexture(null);
            material.setNormalScale(0.0f);
        }

        // Disable metallic: treat as dielectric
        if (!enableMetallic) {
            material.setMetallicFactor(0.0f);
        }

        // Disable roughness: use default
        if (!enableRoughness) {
            material.setRoughnessFactor(0.5f);
        }

        // Disable AO
        if (!enableAO) {
            material.setAoTexture(null);
            material.setAoFactor(1.0f);
        }

        // Disable emissive
        if (!enableEmissive) {
            material.setEmissiveTexture(null);
            material.setEmissiveFactor(0.0f, 0.0f, 0.0f);
        }

        // Disable alpha: force opaque
        if (!enableAlpha) {
            material.setAlphaMode(PBRMaterial.AlphaMode.OPAQUE);
            material.getBaseColor().w = 1.0f;
        }
    }

    /**
     * Apply parameter overrides
     */
    private void applyParameterOverrides(PBRMaterial material) {
        // Roughness override
        if (roughnessOverride != null) {
            material.setRoughnessFactor(roughnessOverride);
        }

        // Metallic override
        if (metallicOverride != null) {
            material.setMetallicFactor(metallicOverride);
        }

        // Emission strength override
        if (emissionStrengthOverride != null) {
            Vector3f emissive = material.getEmissiveFactor();
            emissive.mul(emissionStrengthOverride);
            material.setEmissiveFactor(emissive);
        }

        // Alpha cutoff override
        if (alphaCutoffOverride != null) {
            material.setAlphaCutoff(alphaCutoffOverride);
        }

        // Normal strength override
        if (normalStrengthOverride != null) {
            material.setNormalScale(normalStrengthOverride);
        }

        // AO factor override
        if (aoFactorOverride != null) {
            material.setAoFactor(aoFactorOverride);
        }

        // Alpha mode override
        if (alphaModeOverride != null) {
            material.setAlphaMode(alphaModeOverride);
        }

        // Double-sided override
        if (doubleSidedOverride != null) {
            material.setDoubleSided(doubleSidedOverride);
        }
    }

    /**
     * Reset all overrides to defaults
     */
    public void reset() {
        // Reset channel toggles
        enableBaseColor = true;
        enableNormal = true;
        enableMetallic = true;
        enableRoughness = true;
        enableAO = true;
        enableEmissive = true;
        enableAlpha = true;

        // Reset parameter overrides
        roughnessOverride = null;
        metallicOverride = null;
        emissionStrengthOverride = null;
        alphaCutoffOverride = null;
        normalStrengthOverride = null;
        aoFactorOverride = null;
        alphaModeOverride = null;
        doubleSidedOverride = null;
    }

    /**
     * Check if any overrides are active
     */
    public boolean hasOverrides() {
        // Check channel toggles
        if (!enableBaseColor || !enableNormal || !enableMetallic ||
            !enableRoughness || !enableAO || !enableEmissive || !enableAlpha) {
            return true;
        }

        // Check parameter overrides
        return roughnessOverride != null ||
               metallicOverride != null ||
               emissionStrengthOverride != null ||
               alphaCutoffOverride != null ||
               normalStrengthOverride != null ||
               aoFactorOverride != null ||
               alphaModeOverride != null ||
               doubleSidedOverride != null;
    }

    @Override
    public String toString() {
        return "MaterialOverride{" +
               "channels=[BC:" + enableBaseColor +
               ",N:" + enableNormal +
               ",M:" + enableMetallic +
               ",R:" + enableRoughness +
               ",AO:" + enableAO +
               ",E:" + enableEmissive +
               ",A:" + enableAlpha + "]" +
               ", overrides=" + (hasOverrides() ? "active" : "none") +
               '}';
    }
}
