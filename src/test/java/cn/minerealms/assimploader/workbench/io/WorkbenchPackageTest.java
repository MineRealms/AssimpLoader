package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchPackageTest {
    @TempDir Path temp;

    @Test void compressedRoundTripPreservesEditingAndUnknownConsumerData() throws Exception {
        var source = new WorkbenchProjectStore(temp.resolve("source-projects"));
        var imported = source.importAsset(gltf(temp.resolve("source")));
        var rig = new JsonObject(); rig.addProperty("profileId", "demo:character");
        var future = new JsonObject(); future.add("opaque", JsonParser.parseString("{\"array\":[1,true,{\"future\":\"value\"}]}"));
        var edited = imported.document().withTransform(new ModelTransform(1, 2, 3, 10, 20, 30, 2, 3, 4))
                .withNode("root", new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY.withScale(2, 2, 2), false))
                .withSocket("muzzle", new WorkbenchDocument.Socket("root", ModelTransform.IDENTITY))
                .withAnimation(new WorkbenchDocument.AnimationPreview("", .25, .5f, false, false))
                .withExtension("rigworks", rig).withExtension("other.future", future);
        Path extensions = imported.directory().resolve("extensions/custom");
        Files.createDirectories(extensions); Files.writeString(extensions.resolve("configuration.json"), "{\"setting\":true}");
        Path zip = source.exportZip(imported, edited, temp.resolve("character.rig.zip"),
                Map.of("extensions/rigworks/behavior/main.js", "const idle = 1;".getBytes(StandardCharsets.UTF_8)));
        try (var archive = new ZipFile(zip.toFile())) {
            var manifest = JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, manifest.get("version").getAsInt());
            assertEquals(WorkbenchPackageIO.FORMAT, manifest.get("format").getAsString());
            assertNotNull(manifest.getAsJsonObject("files").get("extensions/rigworks/behavior/main.js"));
            assertEquals(ZipEntry.DEFLATED, archive.getEntry("assets/mesh.bin").getMethod());
        }
        var target = new WorkbenchProjectStore(temp.resolve("target-projects"));
        var restored = target.importAsset(zip);
        assertEquals(edited, restored.document());
        assertEquals(1, restored.scene().getMeshCount());
        assertEquals("const idle = 1;", Files.readString(restored.directory().resolve("extensions/rigworks/behavior/main.js")));
        assertTrue(Files.isRegularFile(restored.directory().resolve("extensions/custom/configuration.json")));
        assertEquals(edited, target.open(restored.documentPath()).document());
        assertEquals(edited, target.open(zip).document());
        assertEquals(imported.document(), source.read(imported.documentPath()));
        assertTrue(target.open(restored.directory()).metadata().issues().stream().noneMatch(issue -> issue.contains("content changed")));
    }

    @Test void externalTextureOverridesBecomePortableWithoutChangingOriginalProject() throws Exception {
        var store = new WorkbenchProjectStore(temp.resolve("projects"));
        var imported = store.importAsset(gltf(temp.resolve("source")));
        Path external = temp.resolve("replacement.png"); Files.write(external, new byte[]{1, 2, 3, 4});
        var material = imported.document().materials().get(0);
        var edited = imported.document().withMaterial(0, new WorkbenchDocument.MaterialOverride(material.r(), material.g(), material.b(), material.a(),
                material.metallic(), material.roughness(), material.emission(), material.alphaMode(), material.cutoff(), material.doubleSided(), Map.of("albedo", external.toString())));
        Path zip = store.exportZip(imported, edited, temp.resolve("portable.zip"));
        var restored = new WorkbenchProjectStore(temp.resolve("restored")).open(zip);
        String reference = restored.document().materials().get(0).textures().get("albedo");
        assertTrue(reference.startsWith("textures/override-"));
        assertArrayEquals(Files.readAllBytes(external), Files.readAllBytes(restored.directory().resolve("assets").resolve(reference)));
        assertEquals(external.toString(), edited.materials().get(0).textures().get("albedo"));
        assertNotEquals(edited.contentHash(), restored.document().contentHash());
        assertEquals(WorkbenchProjectStore.hashFiles(restored.directory().resolve("assets")), restored.document().contentHash());
    }

    @Test void namedEmbeddedImagesExportAsPortableEmbeddedIndices() throws Exception {
        Path model=gltf(temp.resolve("embedded-source"));
        var json=JsonParser.parseString(Files.readString(model)).getAsJsonObject();
        json.add("images",JsonParser.parseString("[{\"name\":\"named-albedo.png\",\"uri\":\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+afoIAAAAASUVORK5CYII=\"}]"));
        json.add("textures",JsonParser.parseString("[{\"source\":0}]"));
        json.add("materials",JsonParser.parseString("[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}}}]"));
        json.getAsJsonArray("meshes").get(0).getAsJsonObject().getAsJsonArray("primitives").get(0).getAsJsonObject().addProperty("material",0);
        Files.writeString(model,json.toString());
        var store=new WorkbenchProjectStore(temp.resolve("projects"));var project=store.importAsset(model);
        assertEquals(1,project.scene().getTextureCount());
        String name=project.scene().getTextures().get(0).getFilename();assertFalse(name.isBlank());
        var material=project.document().materials().get(0);var paths=new LinkedHashMap<>(material.textures());paths.put("albedo",name);
        var legacy=project.document().withMaterial(0,new WorkbenchDocument.MaterialOverride(material.r(),material.g(),material.b(),material.a(),material.metallic(),material.roughness(),material.emission(),material.alphaMode(),material.cutoff(),material.doubleSided(),paths));
        Path archive=store.exportZip(project,legacy,temp.resolve("embedded.zip"));
        var restored=store.importZip(archive);
        assertEquals("*0",restored.document().materials().get(0).textures().get("albedo"));
        assertEquals(1,restored.scene().getTextureCount());
    }

    @Test void ownedDirectorySnapshotDropsDeletedScriptsAndRetainsOtherExtensionData() throws Exception {
        var store = new WorkbenchProjectStore(temp.resolve("projects"));
        var project = store.importAsset(gltf(temp.resolve("source")));
        extensionFile(project, "rigworks/behavior/controller.js", "old controller");
        extensionFile(project, "rigworks/behavior/deleted.js", "deleted");
        extensionFile(project, "rigworks/behavior/nested/deleted.js", "nested deleted");
        extensionFile(project, "rigworks/behavior-backup/keep.js", "same-module sibling");
        extensionFile(project, "future/keep.js", "unknown consumer");
        var snapshot = Map.of("extensions/rigworks/behavior/", new byte[0],
                "extensions/rigworks/behavior/controller.js", "new controller".getBytes(StandardCharsets.UTF_8));
        Path output = store.exportZip(project, project.document(), temp.resolve("snapshot.zip"), snapshot);
        try (var zip = new ZipFile(output.toFile())) {
            assertNull(zip.getEntry("extensions/rigworks/behavior/"), "The ownership marker is metadata, not archive content");
            assertNull(zip.getEntry("extensions/rigworks/behavior/deleted.js"));
            assertNull(zip.getEntry("extensions/rigworks/behavior/nested/deleted.js"));
            assertEquals("new controller", new String(zip.getInputStream(zip.getEntry("extensions/rigworks/behavior/controller.js")).readAllBytes(), StandardCharsets.UTF_8));
            assertNotNull(zip.getEntry("extensions/rigworks/behavior-backup/keep.js"));
            assertNotNull(zip.getEntry("extensions/future/keep.js"));
            var manifest = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(manifest.getAsJsonObject("files").has("extensions/rigworks/behavior/"));
            assertFalse(manifest.getAsJsonObject("files").has("extensions/rigworks/behavior/deleted.js"));
        }
        var restored = store.importZip(output);
        assertFalse(Files.exists(restored.directory().resolve("extensions/rigworks/behavior/deleted.js")));
        assertEquals("new controller", Files.readString(restored.directory().resolve("extensions/rigworks/behavior/controller.js")));
        assertEquals("unknown consumer", Files.readString(restored.directory().resolve("extensions/future/keep.js")));
        assertTrue(Files.isRegularFile(project.directory().resolve("extensions/rigworks/behavior/deleted.js")), "Export must not delete original author files");
    }

    @Test void explicitEmptySnapshotClearsOnlyItsDirectoryAndEmptyMapKeepsLegacyFiles() throws Exception {
        var store = new WorkbenchProjectStore(temp.resolve("projects"));
        var project = store.importAsset(gltf(temp.resolve("source")));
        extensionFile(project, "rigworks/behavior/deleted.js", "deleted");
        extensionFile(project, "rigworks/notes.json", "{}");
        extensionFile(project, "future/keep.js", "unknown consumer");
        Path empty = store.exportZip(project, project.document(), temp.resolve("empty-snapshot.zip"),
                Map.of("extensions/rigworks/behavior/", new byte[0]));
        try (var zip = new ZipFile(empty.toFile())) {
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().startsWith("extensions/rigworks/behavior/")));
            assertNotNull(zip.getEntry("extensions/rigworks/notes.json"));
            assertNotNull(zip.getEntry("extensions/future/keep.js"));
        }
        Path merged = store.exportZip(project, project.document(), temp.resolve("legacy-merge.zip"), Map.of());
        try (var zip = new ZipFile(merged.toFile())) {
            assertNotNull(zip.getEntry("extensions/rigworks/behavior/deleted.js"));
        }
        var restored = store.importZip(empty);
        Path repacked = store.exportZip(restored, restored.document(), temp.resolve("repacked.zip"), Map.of());
        try (var zip = new ZipFile(repacked.toFile())) {
            assertNull(zip.getEntry("extensions/rigworks/behavior/deleted.js"));
            assertNotNull(zip.getEntry("extensions/future/keep.js"));
        }
    }

    @Test void directorySnapshotMarkersRejectGlobalClaimsInvalidPathsAndPayloads() throws Exception {
        var store = new WorkbenchProjectStore(temp.resolve("projects"));
        var project = store.importAsset(gltf(temp.resolve("source")));
        Path destination = temp.resolve("keep.zip"); byte[] original = {1, 2, 3}; Files.write(destination, original);
        for (var invalid : java.util.List.of(
                Map.of("extensions/", new byte[0]),
                Map.of("assets/", new byte[0]),
                Map.of("extensions/rigworks/../future/", new byte[0]),
                Map.of("extensions/rigworks/behavior/", new byte[]{1}),
                Map.of("extensions/rigworks/behavior/", new byte[0], "extensions/rigworks/behavior", new byte[0]),
                Map.of("extensions/rigworks/behavior/", new byte[0], "extensions/RigWorks/behavior/main.js", new byte[0]))) {
            assertThrows(IOException.class, () -> store.exportZip(project, project.document(), destination, invalid));
            assertArrayEquals(original, Files.readAllBytes(destination));
        }
    }

    private static void extensionFile(WorkbenchProjectStore.Project project, String relative, String contents) throws IOException {
        Path path = project.directory().resolve("extensions").resolve(relative);
        Files.createDirectories(path.getParent()); Files.writeString(path, contents, StandardCharsets.UTF_8);
    }

    @Test void legacyZipReopensSavedDocumentAndReportsMissingManifest() throws Exception {
        var entries = validEntries();
        Path archive = archive("legacy.zip", entries);
        var imported = new WorkbenchProjectStore(temp.resolve("legacy-projects")).open(archive);
        assertEquals(WorkbenchCodec.read(new String(entries.get(WorkbenchProjectStore.PROJECT_FILE), StandardCharsets.UTF_8)), imported.document());
        assertTrue(imported.metadata().issues().stream().anyMatch(issue -> issue.contains("Legacy ZIP")));
    }

    @Test void unsupportedVersionAndChecksumTamperingDoNotPublishAnything() throws Exception {
        var entries = validEntries();
        byte[] manifest = manifest(entries, 99);
        var badVersion = new LinkedHashMap<>(entries); badVersion.put("manifest.json", manifest);
        rejected("bad-version.zip", badVersion);
        var tampered = new LinkedHashMap<>(entries); tampered.put("manifest.json", manifest(entries, 1));
        tampered.put("assets/mesh.bin", new byte[]{1});
        rejected("tampered.zip", tampered);
    }

    @Test void manifestMissingEntriesAndUnlistedEntriesAreRejected() throws Exception {
        var entries = validEntries();
        var missing = new LinkedHashMap<>(entries); missing.put("manifest.json", manifest(entries, 1)); missing.remove("assets/mesh.bin");
        rejected("missing.zip", missing);
        var unexpected = new LinkedHashMap<>(entries); unexpected.put("manifest.json", manifest(entries, 1)); unexpected.put("assets/extra.bin", new byte[]{2});
        rejected("unexpected.zip", unexpected);
    }

    @Test void duplicateManifestKeysAndDuplicateArchiveNamesAreRejected() throws Exception {
        var entries = validEntries();
        var manifest = new String(manifest(entries, 1), StandardCharsets.UTF_8).replace("\"version\":1", "\"version\":1,\"version\":1");
        var duplicateMetadata = new LinkedHashMap<>(entries); duplicateMetadata.put("manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
        rejected("duplicate-metadata.zip", duplicateMetadata);
        entries.put("assets/a.bin", new byte[]{1}); entries.put("assets/b.bin", new byte[]{1});
        Path duplicate = archive("duplicate-names.zip", entries);
        byte[] bytes = Files.readAllBytes(duplicate);
        byte[] from = "assets/b.bin".getBytes(StandardCharsets.UTF_8), to = "assets/a.bin".getBytes(StandardCharsets.UTF_8);
        for (int offset = 0; offset <= bytes.length - from.length; offset++) {
            boolean matches = true;
            for (int i = 0; i < from.length && matches; i++) matches = bytes[offset + i] == from[i];
            if (matches) System.arraycopy(to, 0, bytes, offset, to.length);
        }
        Files.write(duplicate, bytes);
        var store = new WorkbenchProjectStore(temp.resolve("duplicate-projects"));
        assertThrows(IOException.class, () -> store.open(duplicate));
        assertTrue(store.listProjects().isEmpty());
        try (var files = Files.list(store.root())) { assertEquals(0, files.count()); }
    }

    @Test void unsafePathsAndCaseCollisionsAreRejectedBeforeLoadingModel() throws Exception {
        for (String path : new String[]{"../outside.txt", "/outside.txt", "C:/outside.txt", "assets/../outside.txt", "assets\\outside.txt", "assets/NUL.png"}) {
            var entries = validEntries(); entries.put(path, new byte[]{1}); rejected("unsafe-" + Math.abs(path.hashCode()) + ".zip", entries);
        }
        var cases = validEntries(); cases.put("assets/Texture.png", new byte[]{1}); cases.put("assets/texture.png", new byte[]{1});
        rejected("case.zip", cases);
        var folders = validEntries(); folders.put("assets/First/a.png", new byte[]{1}); folders.put("assets/first/b.png", new byte[]{1});
        rejected("case-folder.zip", folders);
        var conflict = validEntries(); conflict.put("assets/conflict", new byte[]{1}); conflict.put("assets/conflict/nested.bin", new byte[]{1});
        rejected("file-folder.zip", conflict);
    }

    @Test void missingOrEscapingSourceAndTextureReferencesAreRejected() throws Exception {
        var entries = validEntries();
        var doc = WorkbenchCodec.read(new String(entries.get(WorkbenchProjectStore.PROJECT_FILE), StandardCharsets.UTF_8));
        var missing = new LinkedHashMap<>(entries); missing.put(WorkbenchProjectStore.PROJECT_FILE, WorkbenchCodec.write(doc.withSource("assets/missing.gltf", "")).getBytes(StandardCharsets.UTF_8));
        rejected("missing-source.zip", missing);
        var escape = new LinkedHashMap<>(entries); escape.put(WorkbenchProjectStore.PROJECT_FILE, WorkbenchCodec.write(doc.withSource("../outside.gltf", "")).getBytes(StandardCharsets.UTF_8));
        rejected("escape-source.zip", escape);
        var textures = new LinkedHashMap<>(entries);
        var material = new WorkbenchDocument.MaterialOverride(1, 1, 1, 1, 0, 1, 0, WorkbenchDocument.AlphaMode.OPAQUE, .1f, true, Map.of("normal", "../../outside.png"));
        textures.put(WorkbenchProjectStore.PROJECT_FILE, WorkbenchCodec.write(doc.withMaterial(0, material)).getBytes(StandardCharsets.UTF_8));
        rejected("escape-texture.zip", textures);
        var buffer = new LinkedHashMap<>(entries);
        buffer.put("assets/model.gltf", new String(buffer.get("assets/model.gltf"), StandardCharsets.UTF_8).replace("mesh.bin", "../../outside.bin").getBytes(StandardCharsets.UTF_8));
        rejected("escape-buffer.zip", buffer);
    }

    @Test void manifestBudgetAndActualExpandedEntryLengthAreEnforced() throws Exception {
        var entries = validEntries();
        var metadata = JsonParser.parseString(new String(manifest(entries, 1), StandardCharsets.UTF_8)).getAsJsonObject();
        metadata.getAsJsonObject("files").getAsJsonObject("assets/mesh.bin").addProperty("bytes", WorkbenchPackageIO.MAX_BYTES + 1);
        var tooLarge = new LinkedHashMap<>(entries); tooLarge.put("manifest.json", metadata.toString().getBytes(StandardCharsets.UTF_8));
        rejected("declared-too-large.zip", tooLarge);
        metadata = JsonParser.parseString(new String(manifest(entries, 1), StandardCharsets.UTF_8)).getAsJsonObject();
        metadata.getAsJsonObject("files").getAsJsonObject("assets/mesh.bin").addProperty("bytes", 1);
        var expands = new LinkedHashMap<>(entries); expands.put("manifest.json", metadata.toString().getBytes(StandardCharsets.UTF_8));
        rejected("expanded-too-large.zip", expands);
    }

    @Test void excessiveEntryCountIsRejectedAndExportFailureKeepsPreviousDestination() throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        for (int i = 0; i <= WorkbenchPackageIO.MAX_ENTRIES; i++) entries.put("assets/" + i, new byte[0]);
        rejected("too-many.zip", entries);
        var store = new WorkbenchProjectStore(temp.resolve("source-projects"));
        var project = store.importAsset(gltf(temp.resolve("source")));
        Path destination = temp.resolve("keep.zip"); byte[] before = {3, 4, 5}; Files.write(destination, before);
        assertThrows(IOException.class, () -> store.exportZip(project, project.document(), destination, Map.of("../bad.js", new byte[0])));
        assertArrayEquals(before, Files.readAllBytes(destination));
    }

    private void rejected(String name, Map<String, byte[]> entries) throws Exception {
        var store = new WorkbenchProjectStore(temp.resolve("reject-projects"));
        Files.createDirectories(store.root());
        Path existing = store.root().resolve("existing.txt"); Files.writeString(existing, "preserved");
        assertThrows(IOException.class, () -> store.importAsset(archive(name, entries)), name);
        assertEquals("preserved", Files.readString(existing));
        try (var files = Files.list(store.root())) { assertEquals(1, files.count(), name); }
        assertFalse(Files.exists(temp.resolve("outside.txt")));
    }

    private Map<String, byte[]> validEntries() throws Exception {
        Path model = gltf(temp.resolve("fixture-" + System.nanoTime()));
        var document = new WorkbenchDocument(1, "test:package", "assets/model.gltf", "", ModelTransform.IDENTITY,
                Map.of(), Map.of(), Map.of(), WorkbenchDocument.AnimationPreview.DEFAULT, new JsonObject());
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put(WorkbenchProjectStore.PROJECT_FILE, WorkbenchCodec.write(document).getBytes(StandardCharsets.UTF_8));
        entries.put("assets/model.gltf", Files.readAllBytes(model));
        entries.put("assets/mesh.bin", Files.readAllBytes(model.resolveSibling("mesh.bin")));
        return entries;
    }

    private byte[] manifest(Map<String, byte[]> entries, int version) throws Exception {
        var json = new JsonObject(); json.addProperty("format", WorkbenchPackageIO.FORMAT); json.addProperty("version", version);
        json.addProperty("project", WorkbenchProjectStore.PROJECT_FILE); var files = new JsonObject();
        for (var entry : entries.entrySet()) {
            var check = new JsonObject(); check.addProperty("bytes", entry.getValue().length);
            check.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entry.getValue())));
            files.add(entry.getKey(), check);
        }
        json.add("files", files); return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Path archive(String name, Map<String, byte[]> entries) throws Exception {
        Path file = temp.resolve(name);
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return file;
    }

    private Path gltf(Path directory) throws Exception {
        Files.createDirectories(directory); ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) vertices.putFloat(value);
        Files.write(directory.resolve("mesh.bin"), vertices.array());
        Path model = directory.resolve("model.gltf"); Files.writeString(model, """
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":36,"uri":"mesh.bin"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"root","mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """); return model;
    }
}
