package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.workbench.model.SceneMetadata;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Portable project container. All archive metadata is checked before a model reaches Assimp. */
final class WorkbenchPackageIO {
    static final String MANIFEST = "manifest.json";
    static final String FORMAT = "assimploader.asset-package";
    static final int VERSION = 1;
    static final int MAX_ENTRIES = 10000;
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_ARCHIVE_BYTES = MAX_BYTES + 16L * 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
    private static final String PROJECT = WorkbenchProjectStore.PROJECT_FILE;
    private record Check(long bytes, String sha256) {}
    private record Manifest(Map<String, Check> checks, long bytes) {}
    private record Payload(Path file, byte[] bytes) {
        InputStream open() throws IOException {
            return file == null ? new java.io.ByteArrayInputStream(bytes) : Files.newInputStream(file);
        }
        long size() throws IOException { return file == null ? bytes.length : Files.size(file); }
    }
    record Extracted(WorkbenchDocument document, boolean legacy) {}

    private WorkbenchPackageIO() {}

    static void write(WorkbenchProjectStore.Project project, WorkbenchDocument document,
                      Path output, Map<String, byte[]> extraFiles) throws IOException {
        var files = new TreeMap<String, Payload>();
        collect(project.directory(), "assets", files);
        collect(project.directory(), "extensions", files);
        WorkbenchDocument portable = portableTextures(project, document, files);
        for (var entry : extraFiles.entrySet()) {
            String name = checkedPath(entry.getKey(), false);
            if (!name.startsWith("extensions/")) throw new IOException("Extra package files must be inside extensions/");
            byte[] bytes = java.util.Objects.requireNonNull(entry.getValue(), "Extra package content");
            if (bytes.length > MAX_BYTES) throw new IOException("Package extension exceeds its size budget");
            if (!files.containsKey(name) && files.size() >= MAX_ENTRIES - 2) throw new IOException("Package exceeds 10000 entries");
            files.put(name, new Payload(null, bytes.clone()));
        }
        if (files.size() + 2 > MAX_ENTRIES) throw new IOException("Package exceeds 10000 entries");
        var names = new HashSet<String>();
        var pathCases = new LinkedHashMap<String, String>();
        var checks = new TreeMap<String, Check>();
        MessageDigest assetsHash = sha256();
        long total = 0;
        for (var entry : files.entrySet()) {
            checkedPath(entry.getKey(), false);
            rememberPath(entry.getKey(), pathCases);
            if (!names.add(entry.getKey().toLowerCase(Locale.ROOT))) throw new IOException("Case-conflicting package paths");
            long size = entry.getValue().size();
            total = addSize(total, size);
            boolean asset = entry.getKey().startsWith("assets/");
            if (asset) { assetsHash.update(entry.getKey().substring(7).getBytes(StandardCharsets.UTF_8)); assetsHash.update((byte)0); }
            try (InputStream input = entry.getValue().open()) {
                checks.put(entry.getKey(), check(input, asset ? new DigestOutputStream(OutputStream.nullOutputStream(), assetsHash) : OutputStream.nullOutputStream(), size));
            }
            if (asset) assetsHash.update((byte)0);
        }
        for (String name : files.keySet()) for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1))
            if (files.containsKey(name.substring(0, slash))) throw new IOException("Package file conflicts with a directory");
        portable = portable.withSource(portable.sourcePath(), HexFormat.of().formatHex(assetsHash.digest()));
        byte[] projectBytes = WorkbenchCodec.write(portable).getBytes(StandardCharsets.UTF_8);
        total = addSize(total, projectBytes.length);
        files.put(PROJECT, new Payload(null, projectBytes));
        checks.put(PROJECT, new Check(projectBytes.length, HexFormat.of().formatHex(sha256().digest(projectBytes))));
        var manifest = new JsonObject();
        manifest.addProperty("format", FORMAT);
        manifest.addProperty("version", VERSION);
        manifest.addProperty("project", PROJECT);
        var entries = new JsonObject();
        checks.forEach((name, check) -> {
            var value = new JsonObject();
            value.addProperty("bytes", check.bytes());
            value.addProperty("sha256", check.sha256());
            entries.add(name, value);
        });
        manifest.add("files", entries);
        byte[] encoded = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                .toJson(manifest).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_MANIFEST_BYTES) throw new IOException("Package manifest exceeds 4 MiB");
        addSize(total, encoded.length);
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            entry(zip, MANIFEST);
            zip.write(encoded); zip.closeEntry();
            for (var item : files.entrySet()) {
                entry(zip, item.getKey());
                Check actual;
                try (InputStream input = item.getValue().open()) { actual = check(input, zip, checks.get(item.getKey()).bytes()); }
                if (!actual.equals(checks.get(item.getKey()))) throw new IOException("Package source changed during export: " + item.getKey());
                zip.closeEntry();
            }
        }
    }

    private static void entry(ZipOutputStream zip, String name) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
    }

    private static void collect(Path project, String folder, Map<String, Payload> files) throws IOException {
        Path directory = project.resolve(folder);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Package folder must be a real directory: " + folder);
        Path realRoot = project.toRealPath();
        try (var paths = Files.walk(directory)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (Files.isSymbolicLink(file)) throw new IOException("Package cannot include symbolic links");
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!file.toRealPath().startsWith(realRoot)) throw new IOException("Package asset escapes project");
                if (files.size() >= MAX_ENTRIES - 2) throw new IOException("Package exceeds 10000 entries");
                String name = checkedPath(project.relativize(file).toString().replace('\\', '/'), false);
                files.put(name, new Payload(file, null));
            }
        }
    }

    private static WorkbenchDocument portableTextures(WorkbenchProjectStore.Project project, WorkbenchDocument document,
                                                     Map<String, Payload> files) throws IOException {
        Path model = WorkbenchProjectStore.resolvePortable(project.directory(), document.sourcePath());
        String source = checkedPath(document.sourcePath().replace('\\', '/'), false);
        if (!source.startsWith("assets/") || !files.containsKey(source)) throw new IOException("Package source must exist inside assets/");
        WorkbenchDocument result = document;
        for (var item : document.materials().entrySet()) {
            var material = item.getValue();
            var textures = new LinkedHashMap<String, String>();
            for (var texture : material.textures().entrySet()) {
                String reference = texture.getValue();
                if (reference.matches("\\*\\d+")) { textures.put(texture.getKey(), reference); continue; }
                Path file = SceneMetadata.resolveDependency(model, reference);
                if (file == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Missing texture for package export: " + reference);
                Path absolute = file.toAbsolutePath().normalize();
                String target;
                if (absolute.startsWith(project.directory().toAbsolutePath().normalize().resolve("assets"))) {
                    target = checkedPath(project.directory().toAbsolutePath().normalize().relativize(absolute).toString().replace('\\', '/'), false);
                } else {
                    String filename = file.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
                    if (filename.length() > 100) filename = filename.substring(filename.length() - 100);
                    Check hash;
                    try (InputStream input = Files.newInputStream(file)) { hash = check(input, OutputStream.nullOutputStream(), MAX_BYTES); }
                    target = "assets/textures/override-" + hash.sha256() + "-" + filename;
                }
                Payload old = files.get(target);
                if (old != null && !old.file().toRealPath().equals(file.toRealPath())) {
                    try (InputStream prior = old.open(); InputStream replacement = Files.newInputStream(file)) {
                        if (!check(prior, OutputStream.nullOutputStream(), MAX_BYTES).equals(check(replacement, OutputStream.nullOutputStream(), MAX_BYTES)))
                            throw new IOException("Conflicting package texture path: " + target);
                    }
                }
                files.putIfAbsent(target, new Payload(file, null));
                String rewritten = Path.of(source).getParent().relativize(Path.of(target)).toString().replace('\\', '/');
                textures.put(texture.getKey(), rewritten);
            }
            result = result.withMaterial(item.getKey(), new WorkbenchDocument.MaterialOverride(material.r(), material.g(), material.b(), material.a(),
                    material.metallic(), material.roughness(), material.emission(), material.alphaMode(), material.cutoff(), material.doubleSided(), textures));
        }
        return result;
    }

    static Extracted extract(Path input, Path staging) throws IOException {
        if (Files.size(input) > MAX_ARCHIVE_BYTES) throw new IOException("Compressed package exceeds its budget");
        try (var zip = new ZipFile(input.toFile(), StandardCharsets.UTF_8)) {
            var entries = new LinkedHashMap<String, ZipEntry>();
            var names = new HashSet<String>();
            var pathCases = new LinkedHashMap<String, String>();
            var iterator = zip.entries();
            int count = 0;
            while (iterator.hasMoreElements()) {
                if (++count > MAX_ENTRIES) throw new IOException("Package exceeds 10000 entries");
                var entry = iterator.nextElement();
                String name = checkedPath(entry.getName(), entry.isDirectory());
                rememberPath(name, pathCases);
                if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate or case-conflicting package path: " + name);
                if (!entry.isDirectory()) entries.put(name, entry);
                if (entry.getSize() > MAX_BYTES) throw new IOException("Package entry exceeds 2 GiB");
            }
            ZipEntry manifest = entries.remove(MANIFEST);
            Manifest metadata = manifest == null ? null : readManifest(zip, manifest);
            Map<String, Check> expected = metadata == null ? null : metadata.checks();
            if (expected != null && !expected.keySet().equals(entries.keySet())) throw new IOException("Package entries differ from manifest");
            if (!entries.containsKey(PROJECT)) throw new IOException("Package is missing " + PROJECT);
            for (String name : entries.keySet()) {
                if (!name.equals(PROJECT) && !name.startsWith("assets/") && !name.startsWith("extensions/"))
                    throw new IOException("Unexpected package entry: " + name);
                for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1))
                    if (entries.containsKey(name.substring(0, slash))) throw new IOException("Package file conflicts with a directory");
            }
            long total = metadata == null ? 0 : metadata.bytes();
            for (var item : entries.entrySet()) {
                interrupted();
                var entry = item.getValue();
                long remaining = MAX_BYTES - total;
                long limit = item.getKey().equals(PROJECT) ? Math.min(remaining, WorkbenchCodec.MAX_BYTES) : remaining;
                if (expected != null) limit = Math.min(limit, expected.get(item.getKey()).bytes());
                Path target = staging.resolve(item.getKey()).normalize();
                if (!target.startsWith(staging)) throw new IOException("Package path escapes staging");
                Files.createDirectories(target.getParent());
                Check actual;
                try (InputStream stream = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    actual = check(stream, out, limit);
                }
                total = addSize(total, actual.bytes());
                if (expected != null && !actual.equals(expected.get(item.getKey()))) throw new IOException("Package checksum or size mismatch: " + item.getKey());
            }
            WorkbenchDocument document;
            try { document = WorkbenchCodec.read(Files.readString(staging.resolve(PROJECT), StandardCharsets.UTF_8)); }
            catch (RuntimeException invalid) { throw new IOException("Invalid package project: " + invalid.getMessage(), invalid); }
            validateDocument(staging, document);
            return new Extracted(document, expected == null);
        }
    }

    private static Manifest readManifest(ZipFile zip, ZipEntry entry) throws IOException {
        byte[] bytes;
        try (InputStream input = zip.getInputStream(entry)) {
            var output = new ByteArrayOutputStream();
            check(input, output, MAX_MANIFEST_BYTES);
            bytes = output.toByteArray();
        }
        try (var reader = new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()))) {
            reader.setLenient(false);
            reader.beginObject();
            var seen = new HashSet<String>();
            String format = null, project = null; long version = -1;
            Map<String, Check> checks = null;
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!seen.add(key)) throw new IOException("Duplicate manifest field");
                switch (key) {
                    case "format" -> format = text(reader);
                    case "version" -> version = integer(reader);
                    case "project" -> project = text(reader);
                    case "files" -> {
                        checks = new TreeMap<>(); reader.beginObject();
                        var names = new HashSet<String>(); long total = bytes.length;
                        while (reader.hasNext()) {
                            if (checks.size() >= MAX_ENTRIES - 1) throw new IOException("Package exceeds 10000 entries");
                            String name = checkedPath(reader.nextName(), false);
                            if (name.equals(MANIFEST) || !names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate manifest path");
                            reader.beginObject(); long length = -1; String sha = null; var fields = new HashSet<String>();
                            while (reader.hasNext()) {
                                String field = reader.nextName();
                                if (!fields.add(field)) throw new IOException("Duplicate manifest entry field");
                                switch (field) {
                                    case "bytes" -> length = integer(reader);
                                    case "sha256" -> sha = text(reader);
                                    default -> throw new IOException("Unknown manifest entry field: " + field);
                                }
                            }
                            reader.endObject();
                            if (length < 0 || sha == null || !sha.matches("[a-f0-9]{64}")) throw new IOException("Invalid manifest checksum or size");
                            total = addSize(total, length); checks.put(name, new Check(length, sha));
                        }
                        reader.endObject();
                    }
                    default -> throw new IOException("Unknown package manifest field: " + key);
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || !FORMAT.equals(format) || version != VERSION || !PROJECT.equals(project) || checks == null)
                throw new IOException("Unsupported or invalid asset package manifest");
            return new Manifest(Map.copyOf(checks), bytes.length);
        } catch (IllegalStateException | NumberFormatException invalid) {
            throw new IOException("Invalid asset package manifest", invalid);
        }
    }

    private static long integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw new IOException("Manifest integer required");
        String value = reader.nextString();
        if (!value.matches("0|[1-9][0-9]{0,18}")) throw new IOException("Invalid manifest integer");
        return Long.parseLong(value);
    }

    private static String text(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw new IOException("Manifest string required");
        return reader.nextString();
    }

    private static void validateDocument(Path staging, WorkbenchDocument document) throws IOException {
        String source = checkedPath(document.sourcePath().replace('\\', '/'), false);
        if (!source.startsWith("assets/")) throw new IOException("Package source must be inside assets/");
        Path model = WorkbenchProjectStore.resolvePortable(staging, source);
        if (!Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Package model source is missing");
        for (var material : document.materials().values()) for (String texture : material.textures().values()) {
            if (texture.matches("\\*\\d+")) continue;
            Path dependency = SceneMetadata.resolveDependency(model, texture);
            if (dependency == null || !dependency.startsWith(staging.resolve("assets")) || !Files.isRegularFile(dependency, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Package texture is missing or outside assets/: " + texture);
        }
        if (source.toLowerCase(Locale.ROOT).endsWith(".gltf")) {
            if (Files.size(model) > 32L * 1024 * 1024) throw new IOException("Package glTF JSON exceeds 32 MiB");
            try {
                var json = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : Set.of("buffers", "images")) if (json.has(key)) for (var entry : json.getAsJsonArray(key)) {
                    var value = entry.getAsJsonObject();
                    if (!value.has("uri")) continue;
                    String uri = value.get("uri").getAsString();
                    if (uri.startsWith("data:")) continue;
                    Path dependency = SceneMetadata.resolveDependency(model, uri);
                    if (dependency == null || !dependency.startsWith(staging.resolve("assets")) || !Files.isRegularFile(dependency, LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("Package glTF dependency is missing or outside assets/");
                }
            } catch (RuntimeException invalid) { throw new IOException("Invalid package glTF metadata", invalid); }
        }
    }

    private static String checkedPath(String value, boolean directory) throws IOException {
        if (value == null || value.length() > 1024 || value.isBlank() || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0 || value.startsWith("/"))
            throw new IOException("Invalid package relative path");
        String name = directory && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        String[] parts = name.split("/", -1);
        if (parts.length > 32) throw new IOException("Package path is too deep");
        for (String part : parts) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.endsWith(".") || part.endsWith(" ") || part.chars().anyMatch(c -> c < 32 || c == 127 || "<>\"|?*".indexOf(c) >= 0))
                throw new IOException("Invalid package path component");
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IOException("Reserved package filename");
        }
        return name;
    }

    private static void rememberPath(String path, Map<String, String> spelling) throws IOException {
        String prefix = "";
        for (String component : path.split("/")) {
            prefix = prefix.isEmpty() ? component : prefix + "/" + component;
            String previous = spelling.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
            if (previous != null && !previous.equals(prefix)) throw new IOException("Case-conflicting package path: " + path);
        }
    }

    private static Check check(InputStream input, OutputStream output, long limit) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[65536]; long bytes = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            interrupted();
            bytes += count;
            if (bytes > limit) throw new IOException("Expanded package entry exceeds its size budget");
            digest.update(buffer, 0, count); output.write(buffer, 0, count);
        }
        return new Check(bytes, HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static long addSize(long total, long size) throws IOException {
        if (size < 0 || size > MAX_BYTES - total) throw new IOException("Expanded package exceeds 2 GiB");
        return total + size;
    }

    private static void interrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Package operation cancelled");
    }
}
