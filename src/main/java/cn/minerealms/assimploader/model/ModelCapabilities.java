package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.material.PbrMaterialInputs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Immutable import inventory. A missing adapter field never proves that the source asset lacked the feature. */
public final class ModelCapabilities {
    private static final Map<AssimpScene, Map<String, Object>> CACHE = new WeakHashMap<>();
    private ModelCapabilities() {}

    public static Map<String, Object> of(AssimpScene scene) {
        java.util.Objects.requireNonNull(scene);
        synchronized (CACHE) { return CACHE.computeIfAbsent(scene, ModelCapabilities::inspect); }
    }

    private static Map<String, Object> inspect(AssimpScene scene) {
        var meshes = new ArrayList<Map<String, Object>>();
        var morphMeshes = new ArrayList<Map<String, Object>>();
        var bones = new LinkedHashSet<String>();
        int skinned = 0, targets = 0;
        long vertices = 0, triangles = 0;
        for (AssimpMesh mesh : scene.getMeshes()) {
            vertices += mesh.getVertexCount(); triangles += mesh.getFaceCount();
            if (mesh.hasBones()) { skinned++; bones.addAll(mesh.getBoneNames()); }
            meshes.add(Map.of("index", mesh.getIndex(), "name", mesh.getName(), "vertices", mesh.getVertexCount(),
                    "triangles", mesh.getFaceCount(), "material", mesh.getMaterialIndex(), "normals", mesh.hasNormals(),
                    "uv0", mesh.hasTexCoords(), "tangents", mesh.hasTangentsAndBitangents(), "skinned", mesh.hasBones(),
                    "morphTargets", mesh.getMorphTargets().size()));
            if (mesh.hasMorphTargets()) {
                targets += mesh.getMorphTargets().size();
                var defaults = new ArrayList<Float>();
                for (float weight : mesh.getDefaultMorphWeights()) defaults.add(weight);
                morphMeshes.add(Map.of("index", mesh.getIndex(), "name", mesh.getName(), "method", mesh.getMorphMethod(),
                        "targets", mesh.getMorphTargets().stream().map(target -> target.getName()).toList(),
                        "defaultWeights", List.copyOf(defaults)));
            }
        }
        var clips = new ArrayList<Map<String, Object>>();
        int morphChannels = 0;
        for (AssimpAnimation clip : scene.getAnimations()) {
            morphChannels += clip.getMorphChannels().size();
            clips.add(Map.of("name", clip.getName(), "durationTicks", clip.getDuration(), "ticksPerSecond", clip.getTicksPerSecond(),
                    "boneChannels", clip.getChannels().size(), "morphChannels", clip.getMorphChannels().size(),
                    "bakingProvenance", "unknown"));
        }
        var materials = new ArrayList<Map<String, Object>>();
        int pbrCount = 0;
        boolean unsupportedUv = false;
        for (AssimpMaterial material : scene.getMaterials()) {
            var inputs = material.getPbrInputs();
            if (inputs.hasPbrInputs()) pbrCount++;
            unsupportedUv |= inputs.hasUnsupportedUv();
            var textures = new ArrayList<Map<String, Object>>();
            texture(textures, "normal", inputs.normal()); texture(textures, "roughness", inputs.roughness());
            texture(textures, "metallic", inputs.metallic()); texture(textures, "occlusion", inputs.occlusion());
            texture(textures, "emissive", inputs.emissive());
            materials.add(Map.of("index", material.getIndex(), "name", material.getName(), "workflow", inputs.workflow().name(),
                    "hasPbrInputs", inputs.hasPbrInputs(), "textures", List.copyOf(textures),
                    "roughnessFactor", inputs.roughnessFactor(), "metallicFactor", inputs.metallicFactor(),
                    "factorProvenance", "imported_or_adapter_default", "unsupportedUv", inputs.hasUnsupportedUv()));
        }
        var source = new LinkedHashMap<String, Object>();
        source.put("format", known(scene.getSourceFormat())); source.put("generator", known(scene.getSourceGenerator()));
        source.put("importMode", scene.getImportMode().name());
        source.put("hierarchy", scene.isDynamic() ? "preserved" : "pretransformed");
        source.put("originalFeaturePresence", "not_inspected");
        if (scene.getSourceResource() != null) source.put("resource", scene.getSourceResource().toString());
        else if (scene.getSourcePath() != null) source.put("path", scene.getSourcePath().toString());
        var inventory = new LinkedHashMap<String, Object>();
        inventory.put("apiVersion", 1);
        inventory.put("source", Collections.unmodifiableMap(source));
        inventory.put("mesh", Map.of("imported", imported(meshes.size()), "count", meshes.size(), "vertices", vertices,
                "triangles", triangles, "items", List.copyOf(meshes), "attributeProvenance", "imported_or_importer_generated"));
        inventory.put("skin", Map.of("imported", imported(skinned), "meshCount", skinned, "boneCount", bones.size(),
                "bones", List.copyOf(bones), "runtime", scene.isDynamic() ? "supported_if_imported" : "requires_dynamic_import",
                "gpuEligibility", "not_evaluated", "sourcePresence", "unknown"));
        inventory.put("clips", Map.of("imported", imported(clips.size()), "count", clips.size(), "items", List.copyOf(clips),
                "runtime", scene.isDynamic() ? "supported_if_imported" : "requires_dynamic_import", "sourcePresence", "unknown"));
        inventory.put("morph", Map.of("imported", imported(targets), "targetCount", targets, "channelCount", morphChannels,
                "meshes", List.copyOf(morphMeshes), "runtime", scene.isDynamic() ? "supported_if_imported" : "requires_dynamic_import",
                "sourcePresence", "unknown"));
        inventory.put("pbr", Map.of("imported", imported(pbrCount), "materialCount", pbrCount, "items", List.copyOf(materials),
                "runtime", "supported_uv0_inputs_with_render_fallback", "unsupportedUv", unsupportedUv,
                "textureResolution", "not_evaluated", "activeShader", "not_evaluated"));
        inventory.put("authoring", Map.of(
                "fbxConstraints", authoring("sidecar_ik_append_and_limits"),
                "fbxPhysics", authoring("sidecar_rigid_bodies_joints_and_springs"),
                "bakedConstraintRecovery", "not_available_from_sampled_transforms"));
        inventory.put("scope", "imported_asset_inventory_not_instance_activation_or_render_validation");
        return Collections.unmodifiableMap(inventory);
    }

    private static Map<String, Object> authoring(String alternative) {
        return Map.of("sourcePresence", "unknown", "imported", "not_exposed_by_adapter",
                "runtime", "source_graph_not_supported", "alternative", alternative,
                "alternativeDefinition", "required_not_inferred");
    }

    private static void texture(List<Map<String, Object>> textures, String semantic, PbrMaterialInputs.TextureRef value) {
        if (value != null) textures.add(Map.of("semantic", semantic, "path", value.path(), "channel", value.channel(),
                "uvIndex", value.uvIndex(), "runtimeUvSupported", value.uvIndex() == 0));
    }
    private static String imported(int count) { return count > 0 ? "present" : "no_imported_data"; }
    private static String known(String value) { return value == null || value.isBlank() ? "unknown" : value; }
}
