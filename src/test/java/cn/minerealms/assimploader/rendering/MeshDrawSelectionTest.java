package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MeshDrawSelectionTest {
    @Test void masksAreIsolatedFromCallerMutationAndNestedConsumers() throws Exception {
        var scene = new AssimpModelLoader().loadModel(Path.of("src/showcase/resources/assets/assimploader/models/validation/highbone_probe.glb"), ModelImportMode.DYNAMIC);
        var mesh = scene.getMeshes().get(0);
        int[] source = {0, 2};
        var left = new MeshDrawSelection(Map.of(mesh, source));
        var right = new MeshDrawSelection(Map.of(mesh, new int[]{1}));
        source[0] = 1000;
        assertArrayEquals(new int[]{0, 2}, left.triangles(mesh));
        var exposed = left.triangles(mesh); exposed[0] = 900;
        assertArrayEquals(new int[]{0, 2}, left.triangles(mesh));
        assertNull(MeshDrawSelection.current());
        try (var first = left.activate()) {
            assertSame(left, MeshDrawSelection.current());
            try (var second = right.activate()) { assertSame(right, MeshDrawSelection.current()); }
            assertSame(left, MeshDrawSelection.current());
        }
        assertNull(MeshDrawSelection.current());
        assertThrows(IllegalArgumentException.class, () -> new MeshDrawSelection(Map.of(mesh, new int[]{0, 0})));
        assertThrows(IllegalArgumentException.class, () -> new MeshDrawSelection(Map.of(mesh, new int[]{mesh.getIndices().length / 3})));
    }
}
