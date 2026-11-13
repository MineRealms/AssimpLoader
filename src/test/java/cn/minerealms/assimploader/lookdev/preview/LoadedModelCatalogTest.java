package cn.minerealms.assimploader.lookdev.preview;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoadedModelCatalogTest {
    @Test
    void aliasesAreDeduplicatedBySceneIdentityWithStableCanonicalNames() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/assets/assimploader/models/showcase/probe_no_uv.fbx")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        var loader = new AssimpModelLoader();
        AssimpScene first = loader.loadModelFromMemory("probe.fbx", bytes, ModelImportMode.DYNAMIC);
        AssimpScene second = loader.loadModelFromMemory("probe.fbx", bytes, ModelImportMode.STATIC);
        Map<String, AssimpScene> cache = new LinkedHashMap<>();
        cache.put("probe", first);
        cache.put("assimploader:models/showcase/probe:DYNAMIC", first);
        cache.put("assimploader:models/showcase/probe", first);
        cache.put("other:models/probe", second);
        List<LoadedModelCatalog.Entry> entries = LoadedModelCatalog.snapshot(cache);
        assertEquals(2, entries.size());
        assertEquals("assimploader:models/showcase/probe", entries.get(0).name());
        assertSame(first, entries.get(0).scene());
        assertEquals(1, LoadedModelCatalog.filter(entries, "SHOWCASE").size());
        assertTrue(LoadedModelCatalog.filter(entries, "absent").isEmpty());
        cache.clear();
        assertEquals(2, entries.size(), "Snapshot must not change with the source cache");
    }
}
