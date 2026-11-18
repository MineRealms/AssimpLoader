package cn.minerealms.assimploader.workbench.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchProjectStoreTest {
    @TempDir Path temp;

    @Test void gltfImportCopiesExternalBufferSavesPortableProjectAndReopensEdits() throws Exception {
        Path source=createGltf(temp.resolve("original"));
        var store=new WorkbenchProjectStore(temp.resolve("projects"));
        var imported=store.importAsset(source);
        assertEquals(1,imported.scene().getMeshCount());assertEquals(1,imported.metadata().meshes().size());
        assertEquals("assets/model.gltf",imported.document().sourcePath());assertEquals(64,imported.document().contentHash().length());
        assertTrue(Files.isRegularFile(imported.directory().resolve("assets/mesh.bin")));
        var changed=imported.document().withTransform(imported.document().transform().withScale(2,3,4));
        store.save(imported.documentPath(),changed);
        var opened=store.open(imported.documentPath());assertEquals(changed,opened.document());
        assertFalse(Files.readString(imported.documentPath()).contains(source.getParent().toString().replace("\\","\\\\")));
        assertEquals(1,store.listProjects().size());
        Path zip=store.exportZip(imported,changed,temp.resolve("export/model.zip"));
        try(var archive=new ZipFile(zip.toFile())) {
            assertNotNull(archive.getEntry(WorkbenchProjectStore.PROJECT_FILE));
            assertNotNull(archive.getEntry("assets/mesh.bin"));
            assertNotNull(archive.getEntry("assets/model.gltf"));
            String json=new String(archive.getInputStream(archive.getEntry(WorkbenchProjectStore.PROJECT_FILE)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(changed,WorkbenchCodec.read(json));
        }
        assertTrue(Files.isRegularFile(source));
    }

    @Test void editedAssetHashProducesActionableWarningInsteadOfRefusingPreview() throws Exception {
        var store=new WorkbenchProjectStore(temp.resolve("projects"));var imported=store.importAsset(createGltf(temp.resolve("source")));
        Files.writeString(imported.directory().resolve("assets/new-note.txt"),"asset changed");
        assertTrue(store.open(imported.documentPath()).metadata().issues().stream().anyMatch(issue->issue.contains("content changed")));
    }

    @Test void recoveryDraftDoesNotOverwriteSavedProject() throws Exception {
        var store=new WorkbenchProjectStore(temp.resolve("projects"));var imported=store.importAsset(createGltf(temp.resolve("source")));
        var draft=imported.document().withTransform(imported.document().transform().withPosition(3,4,5));
        store.saveDraft(imported.documentPath(),draft);
        assertEquals(imported.document(),store.open(imported.documentPath()).document());
        assertEquals(draft,store.readDraft(imported.documentPath()).orElseThrow());
        store.discardDraft(imported.documentPath());assertTrue(store.readDraft(imported.documentPath()).isEmpty());
    }

    @Test void pathEscapeAndExternalSaveAreRejected() throws Exception {
        Path project=temp.resolve("project");Files.createDirectories(project);
        for(String invalid:new String[]{"../outside.fbx","/outside.fbx","C:/outside.fbx","..\\outside.fbx"})
            assertThrows(java.io.IOException.class,()->WorkbenchProjectStore.resolvePortable(project,invalid));
        var store=new WorkbenchProjectStore(temp.resolve("projects"));var imported=store.importAsset(createGltf(temp.resolve("source")));
        assertThrows(java.io.IOException.class,()->store.save(temp.resolve("outside.json"),imported.document()));
    }

    @Test void hashIncludesRelativeFilenamesAndNotAbsoluteMachineDirectory() throws Exception {
        Path a=temp.resolve("a"),b=temp.resolve("b");Files.createDirectories(a);Files.createDirectories(b);
        Files.writeString(a.resolve("x.png"),"pixels");Files.writeString(b.resolve("x.png"),"pixels");
        assertEquals(WorkbenchProjectStore.hashFiles(a),WorkbenchProjectStore.hashFiles(b));
        Files.move(b.resolve("x.png"),b.resolve("y.png"));
        assertNotEquals(WorkbenchProjectStore.hashFiles(a),WorkbenchProjectStore.hashFiles(b));
    }

    @Test void ambiguousFolderRequiresExplicitModelChoice() throws Exception {
        Path source=createGltf(temp.resolve("source"));Files.copy(source,source.resolveSibling("another.gltf"));
        var store=new WorkbenchProjectStore(temp.resolve("projects"));
        var error=assertThrows(java.io.IOException.class,()->store.importAsset(source.getParent()));
        assertTrue(error.getMessage().contains("multiple models"));
    }

    private static Path createGltf(Path directory) throws Exception {
        Files.createDirectories(directory);ByteBuffer vertices=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for(float value:new float[]{0,0,0,1,0,0,0,1,0})vertices.putFloat(value);
        Files.write(directory.resolve("mesh.bin"),vertices.array());
        Path model=directory.resolve("model.gltf");Files.writeString(model,"""
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":36,"uri":"mesh.bin"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Model","mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """);return model;
    }
}
