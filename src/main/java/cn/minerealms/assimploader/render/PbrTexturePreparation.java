package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.material.LabPbrPacker;
import cn.minerealms.assimploader.material.PbrMaterialInputs;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Pure image preparation. Conventional sibling maps are already LabPBR and bypass conversion. */
public final class PbrTexturePreparation {
    private static final LabPbrPacker.Image WHITE = new LabPbrPacker.Image(1, 1, new int[]{0xffffffff});

    private PbrTexturePreparation() {}

    public static Prepared prepare(String albedoPath, String siblingBase, PbrMaterialInputs inputs,
                                   Function<String, LabPbrPacker.Image> source) {
        Map<String, LabPbrPacker.Image> images = new HashMap<>();
        Function<String, LabPbrPacker.Image> resolve = path -> {
            if (path == null || path.isBlank()) return null;
            if (!images.containsKey(path)) images.put(path, source.apply(path));
            return images.get(path);
        };
        LabPbrPacker.Image albedo = resolve.apply(albedoPath);
        String normalPath = OculusPBRSupport.getNormalMapPath(siblingBase);
        String specularPath = OculusPBRSupport.getSpecularMapPath(siblingBase);
        LabPbrPacker.Image normal = resolve.apply(normalPath);
        LabPbrPacker.Image specular = resolve.apply(specularPath);
        String normalSource = normal == null ? "none" : "prepacked:" + normalPath;
        String specularSource = specular == null ? "none" : "prepacked:" + specularPath;
        if (inputs != null && inputs.hasPbrInputs() && (normal == null || specular == null)) {
            LabPbrPacker.PackedTextures converted = LabPbrPacker.pack(inputs, resolve);
            if (normal == null && converted.normal() != null) {
                normal = converted.normal();
                normalSource = "converted:" + inputs.workflow();
            }
            if (specular == null && converted.specular() != null) {
                specular = converted.specular();
                specularSource = "converted:" + inputs.workflow();
            }
        }
        return new Prepared(albedo == null ? WHITE : albedo,
                new LabPbrPacker.PackedTextures(normal, specular), normalSource, specularSource);
    }

    public record Prepared(LabPbrPacker.Image albedo, LabPbrPacker.PackedTextures maps,
                           String normalSource, String specularSource) {}
}
