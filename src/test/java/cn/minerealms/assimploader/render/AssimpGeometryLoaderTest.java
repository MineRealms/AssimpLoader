package cn.minerealms.assimploader.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssimpGeometryLoaderTest {
    @TempDir
    Path directory;

    @Test
    void externalModelsMustStayInsideConfiguredDirectory() throws Exception {
        Path root = Files.createDirectory(directory.resolve("models"));
        Path inside = Files.write(root.resolve("asset.glb"), new byte[]{1});
        Path outside = Files.write(directory.resolve("outside.glb"), new byte[]{2});

        assertEquals(inside.toRealPath(), AssimpGeometryLoader.AssimpGeometry.resolveExternalPath(root, "asset.glb"));
        assertEquals(inside.toRealPath(), AssimpGeometryLoader.AssimpGeometry.resolveExternalPath(root, "sub/../asset.glb"));
        assertNull(AssimpGeometryLoader.AssimpGeometry.resolveExternalPath(root, "../outside.glb"));
        assertNull(AssimpGeometryLoader.AssimpGeometry.resolveExternalPath(root, outside.toString()));
        assertNull(AssimpGeometryLoader.AssimpGeometry.resolveExternalPath(root, "missing.glb"));
    }
}
