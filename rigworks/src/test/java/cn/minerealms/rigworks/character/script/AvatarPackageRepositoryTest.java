package cn.minerealms.rigworks.character.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AvatarPackageRepositoryTest {
    @TempDir Path temp;

    @Test void scanFindsAvatarSourcesSkipsCacheAndReloadsWithoutReplacingOtherBundles() throws Exception {
        var repository=AvatarPackageRepository.forGame(temp);Files.createDirectories(repository.directory());
        writeAvatar(repository.directory().resolve("one"),"demo:one","one.js");writeAvatar(repository.directory().resolve("two"),"demo:two","two.js");
        Files.createDirectories(repository.directory().resolve("cache"));Files.writeString(repository.directory().resolve("cache/deadbeef.json"),"{}");
        assertEquals(2,repository.refresh().size());var original=repository.find("demo:two").orElseThrow();
        var old=repository.find("demo:one").orElseThrow();Files.writeString(repository.directory().resolve("one/one.js"),"RigWorks.register('demo:controller', function(f){f.defaultMotion();});");
        var updated=repository.reload("demo:one");assertTrue(updated.valid(),updated.error());assertNotEquals(old.hash(),updated.bundle().hash());
        assertSame(original,repository.find("demo:two").orElseThrow());
        assertEquals(2,repository.forProfile("demo:models/profile.json").orElseThrow().files().size());
    }

    @Test void invalidReloadPreservesLastPublishedBundleAndReportsFailure() throws Exception {
        var repository=AvatarPackageRepository.forGame(temp);writeAvatar(repository.directory().resolve("one"),"demo:one","one.js");repository.refresh();
        var old=repository.find("demo:one").orElseThrow();Files.writeString(repository.directory().resolve("one/manifest.json"),"{");
        assertFalse(repository.reload("demo:one").valid());assertSame(old,repository.find("demo:one").orElseThrow());
        var refreshed=repository.refresh();assertTrue(refreshed.get(0).valid());assertFalse(refreshed.get(0).error().isEmpty());
        assertSame(old,repository.find("demo:one").orElseThrow());
    }

    @Test void hashIsDeterministicRegardlessOfMapInsertionAndRejectsTraversalOrOversizedFiles() {
        var first=new LinkedHashMap<String,String>();first.put("controller.js","RigWorks.register('demo:controller',function(){});");first.put("mappings.json","{}");
        var second=new LinkedHashMap<String,String>();second.put("mappings.json","{}");second.put("controller.js",first.get("controller.js"));
        assertEquals(bundle(first).hash(),bundle(second).hash());
        assertThrows(IllegalArgumentException.class,()->bundle(Map.of("../escape.js","x")));
        assertThrows(IllegalArgumentException.class,()->bundle(Map.of("controller.js","x".repeat(65537))));
        assertThrows(IllegalArgumentException.class,()->bundle(Map.of("controller.js","x","manifest.json","{\"scripts\":[\"missing.js\"]}")));
    }
    private static AvatarScriptBundle bundle(Map<String,String> files){return new AvatarScriptBundle("test","demo:models/profile.json","demo:controller",files);}
    private static void writeAvatar(Path folder,String id,String script) throws Exception {
        Files.createDirectories(folder);Files.writeString(folder.resolve("manifest.json"),"{\"version\":1,\"id\":\""+id+"\",\"profile\":\"demo:models/profile.json\",\"controller\":\"demo:controller\",\"scripts\":[\""+script+"\"]}");
        Files.writeString(folder.resolve(script),"RigWorks.register('demo:controller',function(f){f.defaultAnimation();});");
    }
}
