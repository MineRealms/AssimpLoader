package cn.minerealms.assimploader.material;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Pure conversion of authored metallic/roughness inputs to LabPBR 1.3 ABGR image planes. */
public final class LabPbrPacker {
    private static final int MAX_PIXELS = 16 * 1024 * 1024;

    public record Image(int width, int height, int[] pixels) {
        public Image {
            Objects.requireNonNull(pixels, "pixels");
            int count = pixelCount(width, height);
            if (pixels.length != count) throw new IllegalArgumentException("Image pixel count does not match dimensions");
            pixels = pixels.clone();
        }

        @Override public int[] pixels() { return pixels.clone(); }
        public int pixel(int x, int y) { return pixels[y * width + x]; }
    }

    public record PackedTextures(Image normal, Image specular) {}

    private LabPbrPacker() {}

    public static PackedTextures pack(PbrMaterialInputs inputs, Function<String, Image> resolver) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(resolver, "resolver");
        Map<String, Image> images = new HashMap<>();
        Image normal = resolve(inputs.normal(), resolver, images);
        Image roughness = resolve(inputs.roughness(), resolver, images);
        Image metallic = resolve(inputs.metallic(), resolver, images);
        Image occlusion = resolve(inputs.occlusion(), resolver, images);
        Image emissive = resolve(inputs.emissive(), resolver, images);
        boolean makeNormal = normal != null || occlusion != null;
        boolean makeSpecular = inputs.workflow() != PbrMaterialInputs.Workflow.NONE
                || roughness != null || metallic != null || emissive != null
                || inputs.emissiveR() > 0 || inputs.emissiveG() > 0 || inputs.emissiveB() > 0;
        if (!makeNormal && !makeSpecular) return new PackedTextures(null, null);

        int width = 1, height = 1;
        for (Image image : images.values()) {
            if (image == null) continue;
            width = Math.max(width, image.width());
            height = Math.max(height, image.height());
        }
        int count = pixelCount(width, height);
        int[] normalPixels = makeNormal ? new int[count] : null;
        int[] specularPixels = makeSpecular ? new int[count] : null;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (makeNormal) {
                    int sample = sample(normal, x, y, width, height, 0xffff8080);
                    double nx = ((sample & 255) / 127.5 - 1) * inputs.normalScale();
                    double ny = (((sample >>> 8) & 255) / 127.5 - 1) * inputs.normalScale();
                    if (normal == null) { nx = 0; ny = 0; }
                    double length = Math.sqrt(nx * nx + ny * ny);
                    if (length > 1) { nx /= length; ny /= length; }
                    float ao = value(occlusion, inputs.occlusion(), x, y, width, height, 1f);
                    ao = 1 - inputs.occlusionStrength() * (1 - ao);
                    // LabPBR reconstructs Z: blue stores AO; input normals are OpenGL Y+.
                    normalPixels[index] = abgr(encode(nx), encode(-ny), unitByte(ao), 255);
                }
                if (makeSpecular) {
                    float rough = value(roughness, inputs.roughness(), x, y, width, height, 1f) * inputs.roughnessFactor();
                    float metal = value(metallic, inputs.metallic(), x, y, width, height, 1f) * inputs.metallicFactor();
                    int reflectance = metal >= 0.5f ? 255 : unitByte(inputs.dielectricF0());
                    int emission = emission(inputs, emissive, x, y, width, height);
                    specularPixels[index] = abgr(unitByte(1 - rough), reflectance, 0, emission);
                }
            }
        }
        return new PackedTextures(makeNormal ? new Image(width, height, normalPixels) : null,
                makeSpecular ? new Image(width, height, specularPixels) : null);
    }

    private static Image resolve(PbrMaterialInputs.TextureRef ref, Function<String, Image> resolver,
                                 Map<String, Image> images) {
        if (ref == null || ref.uvIndex() != 0) return null;
        if (!images.containsKey(ref.path())) images.put(ref.path(), resolver.apply(ref.path()));
        return images.get(ref.path());
    }

    private static float value(Image image, PbrMaterialInputs.TextureRef ref, int x, int y,
                               int width, int height, float fallback) {
        if (image == null || ref == null) return fallback;
        return ((sample(image, x, y, width, height, 0) >>> (ref.channel() * 8)) & 255) / 255f;
    }

    private static int sample(Image image, int x, int y, int width, int height, int fallback) {
        if (image == null) return fallback;
        return image.pixel((int) ((long) x * image.width() / width), (int) ((long) y * image.height() / height));
    }

    private static int emission(PbrMaterialInputs inputs, Image image, int x, int y, int width, int height) {
        int pixel = sample(image, x, y, width, height, -1);
        double r = inputs.emissiveR(), g = inputs.emissiveG(), b = inputs.emissiveB();
        if (image != null) {
            r *= srgbToLinear(pixel & 255);
            g *= srgbToLinear((pixel >>> 8) & 255);
            b *= srgbToLinear((pixel >>> 16) & 255);
        }
        // LabPBR emission is a scalar modulating albedo; colored/HDR emission is approximated.
        return (int) Math.round(Math.min(1, Math.max(r, Math.max(g, b))) * 254);
    }

    private static double srgbToLinear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static int encode(double value) { return (int) Math.round((Math.max(-1, Math.min(1, value)) + 1) * 127.5); }
    private static int unitByte(float value) { return Math.round(Math.max(0, Math.min(1, value)) * 255); }
    private static int abgr(int r, int g, int b, int a) { return a << 24 | b << 16 | g << 8 | r; }
    private static int pixelCount(int width, int height) {
        long count = (long) width * height;
        if (width < 1 || height < 1 || count > MAX_PIXELS) throw new IllegalArgumentException("PBR image exceeds 16M pixels or has invalid dimensions");
        return (int) count;
    }
}
