package cn.minerealms.assimploader.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import cn.minerealms.assimploader.util.ResourceLocations;
import cn.minerealms.assimploader.resource.LocalModelResources;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
/** Resource-pack sidecars with deterministic parent merging and generation-scoped caching. */
public final class InstanceDefinitionResources {
    private static final Map<ResourceLocation, Optional<ModelInstanceDefinition>> CACHE = new HashMap<>();
    private static final Map<Path, Optional<ModelInstanceDefinition>> FILE_CACHE = new HashMap<>();
    private InstanceDefinitionResources() {}

    public static Optional<ModelInstanceDefinition> forScene(AssimpScene scene) {
        if (scene == null) return Optional.empty();
        return scene.getSourceResource() != null ? forModel(scene.getSourceResource()) : forFile(scene.getSourcePath());
    }

    /** Cached with the same reload lifetime as resource-pack sidecars. */
    public static Optional<ModelInstanceDefinition> forFile(Path model) {
        if (model == null) return Optional.empty();
        Path source = model.toAbsolutePath().normalize();
        return FILE_CACHE.computeIfAbsent(source, key -> {
            Path sidecar = LocalModelResources.instancePath(key);
            if (!Files.exists(sidecar, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            try {
                var resources = LocalModelResources.open(key);
                JsonObject child = resources.json(sidecar);
                if(child.has("model")&&child.get("model").isJsonPrimitive()&&child.get("model").getAsString().contains(":"))
                    resources.originalModel(ResourceLocations.parse(child.get("model").getAsString()));
                JsonObject merged = resolve(sidecar.toString(), path -> {
                    try { return resources.json(Path.of(path)); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                }, new LinkedHashSet<>(), (current, parent) -> {
                    try { return resources.resolve(parent, Path.of(current)).toString(); }
                    catch (IOException failure) { throw new UncheckedIOException(failure); }
                });
                String declared = merged.has("model") ? merged.get("model").getAsString() : null;
                if (declared == null || declared.isBlank()) throw new IOException("Instance model is required: " + sidecar);
                if (declared.contains(":")) {
                    var identity = ResourceLocations.parse(declared);
                    if (!Path.of(identity.getPath()).getFileName().equals(key.getFileName()))
                        throw new IOException("Instance model does not match file source: " + sidecar);
                    resources.originalModel(identity);
                } else if (!resources.resolve(declared, sidecar).equals(key)) {
                    throw new IOException("Instance model does not match file source: " + sidecar);
                }
                merged.addProperty("model", AssimpModelLoader.fileIdentity(key));
                return Optional.of(new ModelInstanceDefinition(merged));
            } catch (IOException failure) { throw new UncheckedIOException("Cannot read local instance " + sidecar, failure); }
        });
    }

    public static Optional<ModelInstanceDefinition> forModel(ResourceLocation model) {
        if (model == null) return Optional.empty();
        String path = model.getPath();
        int dot = path.lastIndexOf('.');
        ResourceLocation sidecar = ResourceLocations.of(model.getNamespace(), (dot < 0 ? path : path.substring(0, dot)) + ".instance.json");
        return CACHE.computeIfAbsent(sidecar, key -> {
            var manager = Minecraft.getInstance().getResourceManager();
            if (manager.getResource(key).isEmpty()) return Optional.empty();
            JsonObject merged = resolve(key.toString(), location -> {
                try (var reader = manager.openAsReader(ResourceLocations.parse(location))) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                } catch (IOException failure) { throw new UncheckedIOException("Cannot read instance " + location, failure); }
            });
            ModelInstanceDefinition definition = new ModelInstanceDefinition(merged);
            if (!definition.getModel().equals(model.toString())) throw new IllegalArgumentException("Instance model does not match sidecar source: " + key);
            return Optional.of(definition);
        });
    }

    public static JsonObject resolve(String location, Function<String, JsonObject> source) {
        return resolve(location, source, new LinkedHashSet<>(), (current, parent) -> parent);
    }

    private static JsonObject resolve(String location, Function<String, JsonObject> source, LinkedHashSet<String> active,
                                      java.util.function.BiFunction<String, String, String> parentLocation) {
        if (active.size() >= 32 || !active.add(location)) throw new IllegalArgumentException("Instance inheritance cycle/depth: " + active + " -> " + location);
        JsonObject child = java.util.Objects.requireNonNull(source.apply(location), "Missing instance " + location).deepCopy();
        JsonElement parent = child.remove("parent");
        JsonObject result = parent == null ? child : merge(resolve(parentLocation.apply(location, parent.getAsString()), source, active, parentLocation), child);
        active.remove(location);
        return result;
    }

    private static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject result = parent.deepCopy();
        child.entrySet().forEach(entry -> {
            JsonElement previous = result.get(entry.getKey());
            if (previous != null && previous.isJsonObject() && entry.getValue().isJsonObject()) {
                result.add(entry.getKey(), merge(previous.getAsJsonObject(), entry.getValue().getAsJsonObject()));
            } else result.add(entry.getKey(), entry.getValue().deepCopy());
        });
        return result;
    }

    public static void clear() { CACHE.clear(); FILE_CACHE.clear(); }
}
