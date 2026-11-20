package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.util.ResourceLocations;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Read-only resource aliases for a copied model. Aliases never grant access outside its asset root. */
public final class LocalModelResources {
    public static final int MAX_JSON_BYTES = 1024 * 1024;
    private final Path model, root;
    private final Map<ResourceLocation, Path> aliases = new LinkedHashMap<>();
    private ResourceLocation originalModel;

    private LocalModelResources(Path model) throws IOException {
        this.model = model.toAbsolutePath().normalize();
        this.root = assetRoot(this.model);
        checked(this.model);
        Path index = indexPath(this.model);
        if (Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
            JsonObject json = readJson(index);
            if (!json.has("version") || json.get("version").getAsInt() != 1)
                throw new IOException("Unsupported portable model resource index");
            if (json.has("model")) originalModel = ResourceLocations.parse(json.get("model").getAsString());
            var entries = json.getAsJsonObject("resources");
            if (entries == null || entries.size() > 4096) throw new IOException("Invalid portable model resource index");
            for (var entry : entries.entrySet()) {
                String reference = entry.getValue().getAsString();
                Path relative = relative(reference);
                if (relative.startsWith("..")) throw new IOException("Resource alias escapes model assets");
                aliases.put(ResourceLocations.parse(entry.getKey()), checked(root.resolve(relative)));
            }
        }
    }

    public static LocalModelResources open(Path model) throws IOException { return new LocalModelResources(model); }
    public Path model() { return model; }
    public Path root() { return root; }
    public Optional<ResourceLocation> originalModel() { return Optional.ofNullable(originalModel); }
    public Map<ResourceLocation, Path> aliases() { return Map.copyOf(aliases); }

    public static Path indexPath(Path model) { return sibling(model, ".resources.json"); }
    public static Path instancePath(Path model) { return sibling(model, ".instance.json"); }
    private static Path sibling(Path model, String suffix) {
        String name = model.getFileName().toString(); int dot = name.lastIndexOf('.');
        return model.resolveSibling((dot < 0 ? name : name.substring(0, dot)) + suffix);
    }

    /** Explicit workbench projects may reference siblings below assets/; loose FBXs stay in their own folder. */
    private static Path assetRoot(Path model) {
        Path parent = model.getParent();
        for (Path candidate = parent; candidate != null; candidate = candidate.getParent()) {
            if (candidate.getFileName() != null && candidate.getFileName().toString().equals("assets")
                    && candidate.getParent() != null
                    && Files.isRegularFile(candidate.getParent().resolve("project.workbench.json"), LinkOption.NOFOLLOW_LINKS))
                return candidate;
        }
        return parent;
    }

    public Optional<Path> find(ResourceLocation resource) throws IOException {
        Path mapped = aliases.get(resource);
        if (mapped != null) return Optional.of(checked(mapped));
        if (originalModel != null && originalModel.getNamespace().equals(resource.getNamespace())) {
            Path source = Path.of(originalModel.getPath()).getParent();
            Path relative = (source == null ? Path.of("") : source).relativize(Path.of(resource.getPath()));
            Path candidate = checked(model.getParent().resolve(relative));
            if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public Path resolve(String reference, Path referringFile) throws IOException {
        if (reference == null || reference.isBlank()) throw new IOException("Empty model resource reference");
        if (reference.contains(":")) {
            ResourceLocation resource;
            try { resource = ResourceLocations.parse(reference); }
            catch (RuntimeException invalid) { throw new IOException("Invalid resource reference: " + reference, invalid); }
            return find(resource).orElseThrow(() -> new IOException("Portable model resource is missing: " + resource));
        }
        return checked(referringFile.toAbsolutePath().normalize().getParent().resolve(relative(reference)));
    }

    public JsonObject json(Path path) throws IOException { return readJson(checked(path)); }

    /** Allows a loose sidecar to describe its original resource ID without making that ID a disk path. */
    public void originalModel(ResourceLocation model) throws IOException {
        if (originalModel != null && !originalModel.equals(model)) throw new IOException("Portable model identity differs from sidecar");
        originalModel = model;
    }

    private Path checked(Path path) throws IOException {
        Path value = path.toAbsolutePath().normalize();
        if (!value.startsWith(root)) throw new IOException("Model resource escapes asset directory: " + path);
        if (Files.exists(value, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(value)
                    || !value.toRealPath().startsWith(root.toRealPath()))
                throw new IOException("Model resource is not a confined regular file: " + path);
        }
        return value;
    }

    private static Path relative(String reference) throws IOException {
        String normalized = reference.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":") || normalized.indexOf('\0') >= 0)
            throw new IOException("Expected a portable relative resource path");
        Path path = Path.of(normalized);
        if (path.isAbsolute()) throw new IOException("Expected a relative resource path");
        return path;
    }

    public static JsonObject readJson(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_JSON_BYTES)
            throw new IOException("Model JSON must be a regular file below 1 MiB: " + file);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES) throw new IOException("Model JSON exceeds 1 MiB: " + file);
            try { return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException invalid) { throw new IOException("Invalid model JSON: " + file, invalid); }
        }
    }
}
