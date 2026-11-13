// Rosetta method body: metadata reads only; no Minecraft client or GL calls.
Class<?> loader = Class.forName("cn.minerealms.assimploader.core.AssimpModelLoader");
Map<?, ?> models = (Map<?, ?>) loader.getMethod("getLoadedModels").invoke(null);
if (models.isEmpty()) throw new IllegalStateException("No Assimp models loaded");
Map<String, Object> result = new TreeMap<>();
for (Map.Entry<?, ?> entry : models.entrySet()) {
    Object scene = entry.getValue();
    Class<?> type = scene.getClass();
    List<?> materials = (List<?>) type.getMethod("getMaterials").invoke(scene);
    List<?> meshes = (List<?>) type.getMethod("getMeshes").invoke(scene);
    int normals = 0, roughness = 0, metallic = 0, transparent = 0;
    for (Object material : materials) {
        Object inputs = material.getClass().getMethod("getPbrInputs").invoke(material);
        Class<?> pbr = inputs.getClass();
        if (pbr.getMethod("normal").invoke(inputs) != null) normals++;
        if (pbr.getMethod("roughness").invoke(inputs) != null) roughness++;
        if (pbr.getMethod("metallic").invoke(inputs) != null) metallic++;
        if (Boolean.TRUE.equals(material.getClass().getMethod("isTransparent").invoke(material))) transparent++;
    }
    result.put(String.valueOf(entry.getKey()), "meshes=" + meshes.size() + ", materials=" + materials.size()
            + ", normalMaps=" + normals + ", roughnessMaps=" + roughness + ", metalMaps=" + metallic
            + ", transparent=" + transparent);
}
Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
Object mods = modList.getMethod("get").invoke(null);
boolean f3Tweaker = Boolean.TRUE.equals(modList.getMethod("isLoaded", String.class).invoke(mods, "f3tweaker"));
return "readOnly=true; thread=" + Thread.currentThread().getName() + "; f3TweakerLoaded=" + f3Tweaker
        + "; models=" + result;
