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

/** Resource-pack sidecars with deterministic parent merging and generation-scoped caching. */
public final class InstanceDefinitionResources {
    private static final Map<ResourceLocation, Optional<ModelInstanceDefinition>> CACHE = new HashMap<>();
    private InstanceDefinitionResources() {}

    public static Optional<ModelInstanceDefinition> forModel(ResourceLocation model) {
        if (model == null) return Optional.empty();
        String path = model.getPath();
        int dot = path.lastIndexOf('.');
        ResourceLocation sidecar = new ResourceLocation(model.getNamespace(), (dot < 0 ? path : path.substring(0, dot)) + ".instance.json");
        return CACHE.computeIfAbsent(sidecar, key -> {
            var manager = Minecraft.getInstance().getResourceManager();
            if (manager.getResource(key).isEmpty()) return Optional.empty();
            JsonObject merged = resolve(key.toString(), location -> {
                try (var reader = manager.openAsReader(new ResourceLocation(location))) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                } catch (IOException failure) { throw new UncheckedIOException("Cannot read instance " + location, failure); }
            });
            ModelInstanceDefinition definition = new ModelInstanceDefinition(merged);
            if (!definition.getModel().equals(model.toString())) throw new IllegalArgumentException("Instance model does not match sidecar source: " + key);
            return Optional.of(definition);
        });
    }

    public static JsonObject resolve(String location, Function<String, JsonObject> source) {
        return resolve(location, source, new LinkedHashSet<>());
    }

    private static JsonObject resolve(String location, Function<String, JsonObject> source, LinkedHashSet<String> active) {
        if (active.size() >= 32 || !active.add(location)) throw new IllegalArgumentException("Instance inheritance cycle/depth: " + active + " -> " + location);
        JsonObject child = java.util.Objects.requireNonNull(source.apply(location), "Missing instance " + location).deepCopy();
        JsonElement parent = child.remove("parent");
        JsonObject result = parent == null ? child : merge(resolve(parent.getAsString(), source, active), child);
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

    public static void clear() { CACHE.clear(); }
}
