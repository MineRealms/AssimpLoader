package cn.minerealms.rigworks.character.script;

import cn.minerealms.rigworks.character.core.CharacterAppearance;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.CharacterVisualPackage;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Source-only avatar behavior. No Java objects, model bytes or texture bytes are included. */
public final class AvatarScriptBundle {
    private final String id,profile,controller,hash;
    private final Map<String,String> files;
    private final JsonObject manifest,mappings;
    private final List<String> scripts;

    public AvatarScriptBundle(String id,String profile,String controller,Map<String,String> files) {
        if(id==null||!id.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Invalid avatar ID");
        new CharacterAppearance(true,profile,"SCRIPTED",false,0);
        if(controller==null||!controller.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))throw new IllegalArgumentException("Expected namespaced controller ID");
        if(files.size()>CharacterVisualPackage.MAX_FILES)throw new IllegalArgumentException("Avatar package exceeds 32 files");
        var sorted=new TreeMap<String,String>();int total=0;
        for(var entry:files.entrySet()) {
            String path=entry.getKey(),source=entry.getValue();
            if(path==null||path.length()>192||!path.matches("[a-zA-Z0-9_.-]+(/[a-zA-Z0-9_.-]+)*\\.(json|js)")
                    ||java.util.Arrays.stream(path.split("/")).anyMatch(part->part.equals(".")||part.equals("..")))
                throw new IllegalArgumentException("Invalid avatar package file path");
            if(source==null||source.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid avatar source");
            int bytes=source.getBytes(StandardCharsets.UTF_8).length;
            if(bytes>CharacterVisualPackage.MAX_FILE_BYTES)throw new IllegalArgumentException("Avatar file exceeds 64 KiB");
            total+=bytes;if(total>CharacterVisualPackage.MAX_BYTES)throw new IllegalArgumentException("Avatar bundle exceeds 512 KiB");
            if(path.endsWith(".json"))CharacterVisualConfig.parseBounded(source);
            sorted.put(path,source);
        }
        this.id=id;this.profile=profile;this.controller=controller;this.files=Map.copyOf(sorted);
        manifest=sorted.containsKey("manifest.json")?CharacterVisualConfig.parseBounded(sorted.get("manifest.json")).getAsJsonObject():new JsonObject();
        if(manifest.has("version")&&manifest.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported avatar manifest version");
        if(manifest.has("profile")&&!profile.equals(manifest.get("profile").getAsString()))throw new IllegalArgumentException("Avatar manifest profile differs from selected profile");
        if(manifest.has("controller")&&!controller.equals(manifest.get("controller").getAsString()))throw new IllegalArgumentException("Avatar manifest controller differs from selected controller");
        if(manifest.has("scripts")) {
            var paths=manifest.getAsJsonArray("scripts");
            if(paths.size()>CharacterVisualPackage.MAX_FILES)throw new IllegalArgumentException("Too many avatar entry scripts");
            var ordered=new java.util.ArrayList<String>();
            for(var value:paths) {
                String path=value.getAsString();
                if(!path.endsWith(".js")||!sorted.containsKey(path)||ordered.contains(path))throw new IllegalArgumentException("Invalid or duplicate avatar entry script: "+path);
                ordered.add(path);
            }
            scripts=List.copyOf(ordered);
        } else scripts=sorted.keySet().stream().filter(path->path.endsWith(".js")).toList();
        if(scripts.isEmpty())throw new IllegalArgumentException("Avatar package has no entry scripts");
        if(manifest.has("mappings")) {
            String path=manifest.get("mappings").getAsString();
            if(!path.endsWith(".json")||!sorted.containsKey(path))throw new IllegalArgumentException("Avatar mappings file is missing");
            mappings=CharacterVisualConfig.parseBounded(sorted.get(path)).getAsJsonObject();
        } else mappings=sorted.containsKey("mappings.json")?CharacterVisualConfig.parseBounded(sorted.get("mappings.json")).getAsJsonObject():new JsonObject();
        var json=new JsonObject();json.addProperty("id",id);json.addProperty("profile",profile);json.addProperty("controller",controller);
        var sources=new JsonObject();sorted.forEach(sources::addProperty);json.add("files",sources);
        hash=CharacterVisualConfig.hash(json.toString().getBytes(StandardCharsets.UTF_8));
    }
    public String id(){return id;} public String profile(){return profile;} public String controller(){return controller;}
    public String hash(){return hash;} public Map<String,String> files(){return files;} public List<String> scripts(){return scripts;}
    public JsonObject manifest(){return manifest.deepCopy();} public JsonObject mappings(){return mappings.deepCopy();}
    public CharacterVisualPackage toPackage(CharacterVisualConfig visual){return new CharacterVisualPackage(profile,controller,visual,files);}
    public static AvatarScriptBundle fromPackage(CharacterVisualPackage value) {
        String id=value.profile();
        if(value.files().containsKey("manifest.json")) {
            var manifest=CharacterVisualConfig.parseBounded(value.files().get("manifest.json")).getAsJsonObject();
            if(manifest.has("id"))id=manifest.get("id").getAsString();
        }
        return new AvatarScriptBundle(id,value.profile(),value.controller(),value.files());
    }
}
