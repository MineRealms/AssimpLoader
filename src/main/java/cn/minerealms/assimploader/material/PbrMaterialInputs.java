package cn.minerealms.assimploader.material;

import java.util.Objects;

/** Detached source material values. Texture channels are numeric data unless explicitly noted. */
public record PbrMaterialInputs(
        Workflow workflow,
        TextureRef normal, TextureRef roughness, TextureRef metallic,
        TextureRef occlusion, TextureRef emissive,
        float roughnessFactor, float metallicFactor, float normalScale,
        float occlusionStrength, float emissiveR, float emissiveG, float emissiveB,
        float dielectricF0) {

    public enum Workflow { NONE, METALLIC_ROUGHNESS, BLENDER_FBX }

    /** channel: 0=R, 1=G, 2=B, 3=A; normal and emissive use RGB. */
    public record TextureRef(String path, int channel, int uvIndex) {
        public TextureRef {
            Objects.requireNonNull(path, "path");
            if (path.isBlank() || channel < 0 || channel > 3 || uvIndex < 0) {
                throw new IllegalArgumentException("Invalid material texture reference");
            }
        }
    }

    public PbrMaterialInputs {
        Objects.requireNonNull(workflow, "workflow");
        roughnessFactor = unit(roughnessFactor, 0.5f);
        metallicFactor = unit(metallicFactor, 0f);
        normalScale = nonnegative(normalScale, 1f);
        occlusionStrength = unit(occlusionStrength, 1f);
        emissiveR = nonnegative(emissiveR, 0f);
        emissiveG = nonnegative(emissiveG, 0f);
        emissiveB = nonnegative(emissiveB, 0f);
        dielectricF0 = Math.min(229f / 255f, unit(dielectricF0, 0.04f));
    }

    public boolean hasPbrInputs() {
        return workflow != Workflow.NONE || normal != null || occlusion != null || emissive != null
                || emissiveR > 0 || emissiveG > 0 || emissiveB > 0;
    }

    public boolean hasUnsupportedUv() {
        return unsupported(normal) || unsupported(roughness) || unsupported(metallic)
                || unsupported(occlusion) || unsupported(emissive);
    }

    private static boolean unsupported(TextureRef ref) { return ref != null && ref.uvIndex() != 0; }
    private static float unit(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
    private static float nonnegative(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0, value) : fallback;
    }
}
