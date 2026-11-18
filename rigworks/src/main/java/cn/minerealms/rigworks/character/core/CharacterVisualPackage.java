package cn.minerealms.rigworks.character.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Content-addressed declarative configuration and isolated script sources; never an asset archive. */
public final class CharacterVisualPackage {
    public static final int MAX_BYTES=524288,MAX_FILE_BYTES=65536,MAX_FILES=32;
    private final String profile,controller,json,hash;
    private final CharacterVisualConfig visual;
    private final Map<String,String> files;
    public CharacterVisualPackage(String profile,String controller,CharacterVisualConfig visual,Map<String,String> files) {
        new CharacterAppearance(true,profile,"SCRIPTED",false,0);
        if(controller!=null&&!controller.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))throw new IllegalArgumentException("Invalid package controller ID");
        if(files.size()>MAX_FILES)throw new IllegalArgumentException("At most 32 character package files");
        var sorted=new TreeMap<String,String>();
        files.forEach((path,source)-> {
            if(path==null||path.length()>192||!path.matches("[a-zA-Z0-9_.-]+(/[a-zA-Z0-9_.-]+)*\\.(json|js)")
                    ||java.util.Arrays.stream(path.split("/")).anyMatch(p->p.equals(".")||p.equals("..")))
                throw new IllegalArgumentException("Invalid package relative file path");
            if(source==null||source.indexOf('\0')>=0||source.getBytes(StandardCharsets.UTF_8).length>MAX_FILE_BYTES)
                throw new IllegalArgumentException("Character package file exceeds 64 KiB");
            if(path.endsWith(".json"))CharacterVisualConfig.parseBounded(source);
            sorted.put(path,source);
        });
        this.profile=profile;this.controller=controller;this.visual=java.util.Objects.requireNonNull(visual);this.files=Map.copyOf(sorted);
        JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("profile",profile);
        if(controller!=null)root.addProperty("controller",controller);
        root.addProperty("visual",visual.json());JsonObject entries=new JsonObject();sorted.forEach(entries::addProperty);root.add("files",entries);
        json=new GsonBuilder().disableHtmlEscaping().create().toJson(root);
        if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Character package exceeds 512 KiB");
        hash=CharacterVisualConfig.hash(json.getBytes(StandardCharsets.UTF_8));
    }
    public String profile(){return profile;}
    public String controller(){return controller;}
    public CharacterVisualConfig visual(){return visual;}
    public Map<String,String> files(){return files;}
    public String json(){return json;}
    public String hash(){return hash;}
    public boolean hasScripts(){return files.keySet().stream().anyMatch(p->p.endsWith(".js"));}
    public JsonObject manifest() {
        JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("hash",hash);root.addProperty("profile",profile);
        root.addProperty("modelHash",visual.modelHash());root.addProperty("visualHash",visual.digest());
        if(controller!=null)root.addProperty("controller",controller);
        JsonObject entries=new JsonObject();new TreeMap<>(files).forEach((path,source)->entries.addProperty(path,CharacterVisualConfig.hash(source.getBytes(StandardCharsets.UTF_8))));
        root.add("files",entries);return root;
    }
    public static CharacterVisualPackage parse(String source) {
        if(source==null||source.length()>MAX_BYTES||source.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Character package exceeds 512 KiB");
        try(JsonReader reader=new JsonReader(new StringReader(source))) {
            reader.setLenient(false);reader.beginObject();Set<String> seen=new java.util.HashSet<>();
            String profile=null,controller=null,visual=null;int version=0;Map<String,String> files=new TreeMap<>();
            while(reader.hasNext()) {
                String key=reader.nextName();if(!seen.add(key))throw new IllegalArgumentException("Duplicate package field");
                switch(key) {
                    case "version" -> version=reader.nextInt();
                    case "profile" -> profile=string(reader,256);
                    case "controller" -> controller=string(reader,256);
                    case "visual" -> visual=string(reader,CharacterVisualConfig.MAX_BYTES);
                    case "files" -> {
                        reader.beginObject();while(reader.hasNext()) {
                            String path=reader.nextName();if(files.size()>=MAX_FILES||files.containsKey(path))throw new IllegalArgumentException("Package file count or duplicate path");
                            files.put(path,string(reader,MAX_FILE_BYTES));
                        }reader.endObject();
                    }
                    default -> throw new IllegalArgumentException("Unknown character package field: "+key);
                }
            }
            reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT||version!=1||visual==null)throw new IllegalArgumentException("Invalid character package schema");
            return new CharacterVisualPackage(profile,controller,CharacterVisualConfig.parse(visual),files);
        }catch(IOException|IllegalStateException e){throw new IllegalArgumentException("Invalid character package",e);}
    }
    private static String string(JsonReader reader,int limit)throws IOException {
        if(reader.peek()!=JsonToken.STRING)throw new IllegalArgumentException("Expected package string");
        String value=reader.nextString();if(value.length()>limit)throw new IllegalArgumentException("Package string limit");return value;
    }
}
