package cn.minerealms.assimploader.material;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * PBR (Physically Based Rendering) material data structure.
 *
 * Supports modern PBR workflow with:
 * - Metallic/Roughness workflow (standard)
 * - Specular/Glossiness workflow (legacy)
 *
 * Texture channels:
 * - BaseColor/Albedo: RGB color + Alpha
 * - Normal: Tangent-space normal map
 * - Metallic: Metallic factor (0=dielectric, 1=metal)
 * - Roughness: Surface roughness (0=smooth, 1=rough)
 * - Emissive: Self-illumination
 * - AO (Ambient Occlusion): Ambient shadowing
 * - Height/Displacement: Surface detail
 *
 * References:
 * - https://learnopengl.com/PBR/Theory
 * - https://github.com/KhronosGroup/glTF/blob/master/specification/2.0/README.md#materials
 */
public class PBRMaterial {

    // Material name
    private String name;

    // Base color (albedo)
    private Vector4f baseColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f); // RGBA
    @Nullable
    private ResourceLocation baseColorTexture;

    // Metallic-Roughness workflow
    private float metallicFactor = 0.0f; // 0 = dielectric, 1 = metal
    private float roughnessFactor = 0.5f; // 0 = smooth, 1 = rough
    @Nullable
    private ResourceLocation metallicRoughnessTexture; // R=unused, G=roughness, B=metallic

    // Normal mapping
    @Nullable
    private ResourceLocation normalTexture;
    private float normalScale = 1.0f;

    // Emissive
    private Vector3f emissiveFactor = new Vector3f(0.0f, 0.0f, 0.0f);
    @Nullable
    private ResourceLocation emissiveTexture;

    // Ambient Occlusion
    private float aoFactor = 1.0f;
    @Nullable
    private ResourceLocation aoTexture;

    // Height/Displacement
    @Nullable
    private ResourceLocation heightTexture;
    private float heightScale = 1.0f;

    // Alpha mode
    private AlphaMode alphaMode = AlphaMode.OPAQUE;
    private float alphaCutoff = 0.5f;

    // Double-sided rendering
    private boolean doubleSided = false;

    // Legacy properties (for non-PBR materials)
    private Vector3f specularColor = new Vector3f(1.0f, 1.0f, 1.0f);
    private float shininess = 32.0f;

    public enum AlphaMode {
        OPAQUE,      // Fully opaque
        MASK,        // Alpha test with cutoff
        BLEND        // Alpha blending
    }

    public PBRMaterial(String name) {
        this.name = name;
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Vector4f getBaseColor() {
        return baseColor;
    }

    public void setBaseColor(Vector4f baseColor) {
        this.baseColor = baseColor;
    }

    public void setBaseColor(float r, float g, float b, float a) {
        this.baseColor.set(r, g, b, a);
    }

    @Nullable
    public ResourceLocation getBaseColorTexture() {
        return baseColorTexture;
    }

    public void setBaseColorTexture(@Nullable ResourceLocation baseColorTexture) {
        this.baseColorTexture = baseColorTexture;
    }

    public float getMetallicFactor() {
        return metallicFactor;
    }

    public void setMetallicFactor(float metallicFactor) {
        this.metallicFactor = Math.max(0.0f, Math.min(1.0f, metallicFactor));
    }

    public float getRoughnessFactor() {
        return roughnessFactor;
    }

    public void setRoughnessFactor(float roughnessFactor) {
        this.roughnessFactor = Math.max(0.0f, Math.min(1.0f, roughnessFactor));
    }

    @Nullable
    public ResourceLocation getMetallicRoughnessTexture() {
        return metallicRoughnessTexture;
    }

    public void setMetallicRoughnessTexture(@Nullable ResourceLocation metallicRoughnessTexture) {
        this.metallicRoughnessTexture = metallicRoughnessTexture;
    }

    @Nullable
    public ResourceLocation getNormalTexture() {
        return normalTexture;
    }

    public void setNormalTexture(@Nullable ResourceLocation normalTexture) {
        this.normalTexture = normalTexture;
    }

    public float getNormalScale() {
        return normalScale;
    }

    public void setNormalScale(float normalScale) {
        this.normalScale = normalScale;
    }

    public Vector3f getEmissiveFactor() {
        return emissiveFactor;
    }

    public void setEmissiveFactor(Vector3f emissiveFactor) {
        this.emissiveFactor = emissiveFactor;
    }

    public void setEmissiveFactor(float r, float g, float b) {
        this.emissiveFactor.set(r, g, b);
    }

    @Nullable
    public ResourceLocation getEmissiveTexture() {
        return emissiveTexture;
    }

    public void setEmissiveTexture(@Nullable ResourceLocation emissiveTexture) {
        this.emissiveTexture = emissiveTexture;
    }

    public float getAoFactor() {
        return aoFactor;
    }

    public void setAoFactor(float aoFactor) {
        this.aoFactor = Math.max(0.0f, Math.min(1.0f, aoFactor));
    }

    @Nullable
    public ResourceLocation getAoTexture() {
        return aoTexture;
    }

    public void setAoTexture(@Nullable ResourceLocation aoTexture) {
        this.aoTexture = aoTexture;
    }

    @Nullable
    public ResourceLocation getHeightTexture() {
        return heightTexture;
    }

    public void setHeightTexture(@Nullable ResourceLocation heightTexture) {
        this.heightTexture = heightTexture;
    }

    public float getHeightScale() {
        return heightScale;
    }

    public void setHeightScale(float heightScale) {
        this.heightScale = heightScale;
    }

    public AlphaMode getAlphaMode() {
        return alphaMode;
    }

    public void setAlphaMode(AlphaMode alphaMode) {
        this.alphaMode = alphaMode;
    }

    public float getAlphaCutoff() {
        return alphaCutoff;
    }

    public void setAlphaCutoff(float alphaCutoff) {
        this.alphaCutoff = Math.max(0.0f, Math.min(1.0f, alphaCutoff));
    }

    public boolean isDoubleSided() {
        return doubleSided;
    }

    public void setDoubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
    }

    public Vector3f getSpecularColor() {
        return specularColor;
    }

    public void setSpecularColor(Vector3f specularColor) {
        this.specularColor = specularColor;
    }

    public float getShininess() {
        return shininess;
    }

    public void setShininess(float shininess) {
        this.shininess = Math.max(0.0f, shininess);
    }

    /**
     * Check if material has any textures
     */
    public boolean hasTextures() {
        return baseColorTexture != null ||
               metallicRoughnessTexture != null ||
               normalTexture != null ||
               emissiveTexture != null ||
               aoTexture != null ||
               heightTexture != null;
    }

    /**
     * Check if material is transparent
     */
    public boolean isTransparent() {
        return alphaMode == AlphaMode.BLEND || (alphaMode == AlphaMode.MASK && baseColor.w < 1.0f);
    }

    /**
     * Check if material is emissive
     */
    public boolean isEmissive() {
        return emissiveFactor.lengthSquared() > 0.0f || emissiveTexture != null;
    }

    @Override
    public String toString() {
        return String.format("PBRMaterial{name='%s', baseColor=%s, metallic=%.2f, roughness=%.2f, alpha=%s}",
                name, baseColor, metallicFactor, roughnessFactor, alphaMode);
    }
}
