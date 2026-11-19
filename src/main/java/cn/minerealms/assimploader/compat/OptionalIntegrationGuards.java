package cn.minerealms.assimploader.compat;

import java.util.List;
import java.util.function.Predicate;

/** String/resource-only checks are safe before optional target classes can be initialized. */
public final class OptionalIntegrationGuards {
    private static final String MIXINS = "cn.minerealms.assimploader.mixin.photon.";

    //? if forge {
    private static final String MESH_TARGET = "com.lowdragmc.photon.gui.editor.MeshesResource";
    private static final String MATERIAL_TARGET = "com.lowdragmc.photon.gui.editor.MaterialsResource";
    private static final List<String> COMMON = List.of(
            "com.lowdragmc.lowdraglib.gui.editor.ui.Editor",
            "com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel",
            "com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer",
            "com.lowdragmc.lowdraglib.gui.util.TreeBuilder$Menu",
            "com.lowdragmc.lowdraglib.gui.widget.DialogWidget");
    private static final List<String> MESH = List.of(MESH_TARGET,
            "com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData");
    private static final List<String> MATERIAL = List.of(MATERIAL_TARGET,
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial",
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial",
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial");
    //?}
    //? if neoforge {
    /*
    // Photon 2.2.11 dropped the editor `Resource` subclasses and moved the importer entry points
    // onto LDLib2's `Resource`, which also renamed every package to `lowdraglib2`.
    private static final String MESH_TARGET = "com.lowdragmc.photon.gui.editor.resource.MeshResource";
    private static final String MATERIAL_TARGET = "com.lowdragmc.photon.gui.editor.resource.MaterialResource";
    private static final List<String> COMMON = List.of(
            "com.lowdragmc.lowdraglib2.editor.resource.Resource",
            "com.lowdragmc.lowdraglib2.editor.resource.ResourceImportContext");
    private static final List<String> MESH = List.of(MESH_TARGET,
            "com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData",
            "com.lowdragmc.photon.client.gameobject.emitter.data.model.IModelSource");
    private static final List<String> MATERIAL = List.of(MATERIAL_TARGET,
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.IMaterial",
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial",
            "com.lowdragmc.photon.client.gameobject.emitter.data.material.CustomShaderMaterial");
    *///?}

    private OptionalIntegrationGuards() {}

    public static boolean shouldApplyPhotonMixin(String target, String mixin, Predicate<String> classPresent) {
        List<String> required;
        if (mixin.equals(MIXINS + "MeshesResourceMixin") && target.equals(MESH_TARGET)) required = MESH;
        else if (mixin.equals(MIXINS + "MaterialsResourceMixin") && target.equals(MATERIAL_TARGET)) required = MATERIAL;
        else return false;
        return COMMON.stream().allMatch(classPresent) && required.stream().allMatch(classPresent);
    }

    public static boolean photonEditorAvailable(Predicate<String> classPresent) {
        return COMMON.stream().allMatch(classPresent) && MESH.stream().allMatch(classPresent) && MATERIAL.stream().allMatch(classPresent);
    }

    public static boolean requiredPhotonMods(Predicate<String> modPresent) {
        //? if forge {
        return modPresent.test("photon") && modPresent.test("ldlib");
        //?}
        //? if neoforge {
        /*
        return modPresent.test("photon") && modPresent.test("ldlib2");
        *///?}
    }

    public static boolean classResourcePresent(ClassLoader loader, String className) {
        return loader.getResource(className.replace('.', '/') + ".class") != null;
    }
}
