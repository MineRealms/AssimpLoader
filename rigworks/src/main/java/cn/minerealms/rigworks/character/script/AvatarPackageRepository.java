package cn.minerealms.rigworks.character.script;

import cn.minerealms.rigworks.character.core.CharacterVisualConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Explicit per-avatar reloads. No KubeJS global manager reload and no downloaded-file writes. */
public final class AvatarPackageRepository {
    public record Entry(String id,Path directory,AvatarScriptBundle bundle,String error) {
        public boolean valid(){return bundle!=null;}
    }
    private final Path directory;
    private volatile Map<String,Entry> entries=Map.of();
    private volatile long revision;
    public AvatarPackageRepository(Path directory){this.directory=directory.toAbsolutePath().normalize();}
    public static AvatarPackageRepository forGame(Path gameDirectory){return new AvatarPackageRepository(gameDirectory.resolve("kubejs/rigworks"));}
    public Path directory(){return directory;} public long revision(){return revision;}
    public List<Entry> entries(){return entries.values().stream().sorted(java.util.Comparator.comparing(Entry::id)).toList();}
    public Optional<AvatarScriptBundle> find(String id){var value=entries.get(id);return Optional.ofNullable(value==null?null:value.bundle());}
    public Optional<AvatarScriptBundle> forProfile(String profile){return entries().stream().filter(Entry::valid).map(Entry::bundle).filter(bundle->bundle.profile().equals(profile)).findFirst();}
    public synchronized List<Entry> refresh() throws IOException {
        Files.createDirectories(directory);var replacement=new LinkedHashMap<String,Entry>();
        try(var paths=Files.list(directory)) {
            for(Path folder:paths.filter(path->Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
                    .filter(path->!path.getFileName().toString().startsWith(".")&&!path.getFileName().toString().equals("cache")).sorted().toList()) {
                if(replacement.size()>=128)break;
                Entry entry=read(folder);
                if(!entry.valid()) {
                    Entry previous=entries.values().stream().filter(old->old.directory().equals(folder)&&old.valid()).findFirst().orElse(null);
                    if(previous!=null)entry=new Entry(previous.id(),folder,previous.bundle(),entry.error());
                }
                if(replacement.containsKey(entry.id()))entry=new Entry(folder.getFileName().toString(),folder,null,"Duplicate avatar ID: "+entry.id());
                replacement.put(entry.id(),entry);
            }
        }
        entries=Map.copyOf(replacement);revision++;return entries();
    }
    public synchronized Entry reload(String id) throws IOException {
        Entry existing=entries.get(id);
        if(existing==null)throw new IOException("Unknown avatar package: "+id);
        Entry updated=read(existing.directory());
        if(!updated.valid())return updated;
        if(!updated.id().equals(id)&&entries.containsKey(updated.id()))throw new IOException("Duplicate avatar ID: "+updated.id());
        var next=new LinkedHashMap<>(entries);next.remove(id);next.put(updated.id(),updated);entries=Map.copyOf(next);revision++;return updated;
    }
    private Entry read(Path folder) {
        String fallback=folder.getFileName().toString();
        try {
            Path realRoot=directory.toRealPath();
            if(!folder.toRealPath().startsWith(realRoot))throw new IOException("Avatar package escapes source folder");
            Path manifestFile=folder.resolve("manifest.json");
            String manifestSource=readFile(manifestFile);
            var manifest=CharacterVisualConfig.parseBounded(manifestSource).getAsJsonObject();
            String id=manifest.has("id")?manifest.get("id").getAsString():fallback;
            String profile=manifest.get("profile").getAsString();String controller=manifest.get("controller").getAsString();
            var files=new LinkedHashMap<String,String>();
            try(var paths=Files.walk(folder,8)) {
                for(Path path:paths.filter(file->Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                    String name=path.getFileName().toString();if(!name.endsWith(".js")&&!name.endsWith(".json"))continue;
                    if(files.size()>=32)throw new IOException("At most 32 JSON/JS files per avatar");
                    if(!path.toRealPath().startsWith(folder.toRealPath()))throw new IOException("Avatar source escapes through symbolic link");
                    files.put(folder.relativize(path).toString().replace('\\','/'),readFile(path));
                }
            }
            return new Entry(id,folder,new AvatarScriptBundle(id,profile,controller,files),"");
        } catch(IOException|RuntimeException failure){return new Entry(fallback,folder,null,shortError(failure));}
    }
    private static String readFile(Path path) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Avatar source must be a regular file: "+path.getFileName());
        if(Files.size(path)>65536)throw new IOException("Avatar file exceeds 64 KiB: "+path.getFileName());
        return Files.readString(path,StandardCharsets.UTF_8);
    }
    private static String shortError(Throwable error){String text=error.getMessage();return text==null?error.getClass().getSimpleName():text.substring(0,Math.min(text.length(),512));}
}
