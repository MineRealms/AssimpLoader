package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Local author preferences. Received network packages are stored only by the binary package cache. */
final class CharacterVisualOverrides {
    private static final Path ROOT=FMLPaths.CONFIGDIR.get().resolve("rigworks").toAbsolutePath().normalize();
    private static final Map<String,CharacterVisualConfig> CONFIGS=new HashMap<>();
    private static final Set<String> READ=new HashSet<>();
    private static final Map<String,Path> ASSETS=new HashMap<>();
    private static boolean assetsRead;
    private CharacterVisualOverrides(){}
    static CharacterVisualConfig load(String profile) {
        new ResourceLocation(profile);
        if(!READ.add(profile))return CONFIGS.get(profile);
        Path file=profileFile(profile);
        try {
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
                confined(file);
                CONFIGS.put(profile,CharacterVisualConfig.parse(read(file)));
            }
        }catch(IOException|RuntimeException error){AssimpLoader.LOGGER.warn("Invalid local character configuration {}",file,error);}
        return CONFIGS.get(profile);
    }
    static void save(String profile,CharacterVisualConfig config)throws IOException {
        new ResourceLocation(profile);write(profileFile(profile),config.json());
        CONFIGS.put(profile,config);READ.add(profile);
    }
    static synchronized Path asset(String id) {readAssets();return ASSETS.get(id);}
    static synchronized void asset(String id,Path source)throws IOException {
        new ResourceLocation(id);readAssets();
        if(ASSETS.size()>=128&&!ASSETS.containsKey(id))throw new IOException("At most 128 local character assets");
        Path path=source.toAbsolutePath().normalize();
        if(!Files.isRegularFile(path))throw new IOException("Character asset does not exist: "+path);
        var next=new HashMap<>(ASSETS);next.put(id,path);
        var json=new JsonObject();next.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->json.addProperty(e.getKey(),e.getValue().toString()));
        write(ROOT.resolve("assets.json"),json.toString());ASSETS.clear();ASSETS.putAll(next);
    }
    private static void readAssets() {
        if(assetsRead)return;assetsRead=true;Path file=ROOT.resolve("assets.json");
        try {
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return;
            confined(file);var json=CharacterVisualConfig.parseBounded(read(file)).getAsJsonObject();
            if(json.size()>128)throw new IOException("Too many local character assets");
            var replacement=new HashMap<String,Path>();
            for(var e:json.entrySet()){new ResourceLocation(e.getKey());replacement.put(e.getKey(),Path.of(e.getValue().getAsString()).toAbsolutePath().normalize());}
            ASSETS.putAll(replacement);
        }catch(IOException|RuntimeException error){AssimpLoader.LOGGER.warn("Invalid local character asset registry {}",file,error);}
    }
    private static Path profileFile(String profile){return ROOT.resolve("characters").resolve(CharacterVisualConfig.hash(profile.getBytes(StandardCharsets.UTF_8))+".json");}
    private static String read(Path file)throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>CharacterVisualConfig.MAX_BYTES)throw new IOException("Local character JSON exceeds 128 KiB");
        return Files.readString(file,StandardCharsets.UTF_8);
    }
    private static void write(Path file,String json)throws IOException {
        if(json.getBytes(StandardCharsets.UTF_8).length>CharacterVisualConfig.MAX_BYTES)throw new IOException("Local character JSON exceeds 128 KiB");
        Files.createDirectories(ROOT);confined(file);Files.createDirectories(file.getParent());confined(file);
        Path temporary=Files.createTempFile(file.getParent(),".character-",".tmp");
        try {Files.writeString(temporary,json,StandardCharsets.UTF_8);Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temporary);}
    }
    private static void confined(Path file)throws IOException {
        if(!file.startsWith(ROOT))throw new IOException("Character preference escapes config directory");
        Path current=ROOT;
        if(Files.isSymbolicLink(current))throw new IOException("Character preferences contain a symbolic link");
        for(Path part:ROOT.relativize(file)){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new IOException("Character preferences contain a symbolic link");}
    }
}
