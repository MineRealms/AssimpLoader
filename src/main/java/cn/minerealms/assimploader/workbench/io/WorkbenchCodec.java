package cn.minerealms.assimploader.workbench.io;

import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;

import java.nio.charset.StandardCharsets;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit schema codec: record constructors validate data on every read. */
public final class WorkbenchCodec {
    public static final int MAX_BYTES=4*1024*1024;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private WorkbenchCodec() {}

    public static String write(WorkbenchDocument value) {
        String json=GSON.toJson(canonical(toJson(value)))+"\n";
        if(json.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Project JSON exceeds 4 MiB");
        return json;
    }
    public static WorkbenchDocument read(String source) {
        if(source.length()>MAX_BYTES)throw new IllegalArgumentException("Project JSON exceeds 4 MiB");
        return fromJson(JsonParser.parseString(source).getAsJsonObject());
    }
    public static JsonObject toJson(WorkbenchDocument value) {
        var root=new JsonObject();root.addProperty("version",value.version());root.addProperty("asset_id",value.assetId());
        root.addProperty("source",value.sourcePath());root.addProperty("content_hash",value.contentHash());
        root.add("transform",GSON.toJsonTree(value.transform()));
        var nodes=new JsonObject();value.nodes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry->nodes.add(entry.getKey(),GSON.toJsonTree(entry.getValue())));root.add("nodes",nodes);
        var materials=new JsonObject();value.materials().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry->materials.add(Integer.toString(entry.getKey()),GSON.toJsonTree(entry.getValue())));root.add("materials",materials);
        var sockets=new JsonObject();value.sockets().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry->sockets.add(entry.getKey(),GSON.toJsonTree(entry.getValue())));root.add("sockets",sockets);
        root.add("animation",GSON.toJsonTree(value.animation()));root.add("extensions",value.extensions());return root;
    }
    public static WorkbenchDocument fromJson(JsonObject root) {
        int version=root.has("version")?root.get("version").getAsInt():1;
        var nodes=new LinkedHashMap<String,WorkbenchDocument.NodeOverride>();
        object(root,"nodes").entrySet().forEach(entry-> {
            var value=entry.getValue().getAsJsonObject();
            nodes.put(entry.getKey(),new WorkbenchDocument.NodeOverride(transform(value.get("transform")),bool(value,"visible",true)));
        });
        var materials=new LinkedHashMap<Integer,WorkbenchDocument.MaterialOverride>();
        object(root,"materials").entrySet().forEach(entry->materials.put(Integer.parseInt(entry.getKey()),material(entry.getValue().getAsJsonObject())));
        var sockets=new LinkedHashMap<String,WorkbenchDocument.Socket>();
        object(root,"sockets").entrySet().forEach(entry->{var value=entry.getValue().getAsJsonObject();
            sockets.put(entry.getKey(),new WorkbenchDocument.Socket(value.get("node").getAsString(),transform(value.get("transform"))));});
        var animation=object(root,"animation");
        return new WorkbenchDocument(version,root.get("asset_id").getAsString(),root.get("source").getAsString(),
                root.has("content_hash")?root.get("content_hash").getAsString():"",transform(root.get("transform")),nodes,materials,sockets,
                new WorkbenchDocument.AnimationPreview(text(animation,"clip",""),number(animation,"time",0),
                        scalar(animation,"speed",1),bool(animation,"loop",true),bool(animation,"playing",false)),object(root,"extensions"));
    }
    private static WorkbenchDocument.MaterialOverride material(JsonObject value) {
        var textures=new LinkedHashMap<String,String>();object(value,"textures").entrySet().forEach(entry->textures.put(entry.getKey(),entry.getValue().getAsString()));
        return new WorkbenchDocument.MaterialOverride(scalar(value,"r",1),scalar(value,"g",1),scalar(value,"b",1),scalar(value,"a",1),
                scalar(value,"metallic",0),scalar(value,"roughness",.5f),scalar(value,"emission",0),
                WorkbenchDocument.AlphaMode.valueOf(text(value,"alphaMode","OPAQUE")),scalar(value,"cutoff",.1f),bool(value,"doubleSided",true),textures);
    }
    private static ModelTransform transform(JsonElement input) {
        if(input==null||input.isJsonNull())return ModelTransform.IDENTITY;
        var value=input.getAsJsonObject();return new ModelTransform(scalar(value,"x",0),scalar(value,"y",0),scalar(value,"z",0),
                scalar(value,"rx",0),scalar(value,"ry",0),scalar(value,"rz",0),scalar(value,"sx",1),scalar(value,"sy",1),scalar(value,"sz",1));
    }
    private static JsonObject object(JsonObject source,String name){return source.has(name)?source.getAsJsonObject(name):new JsonObject();}
    private static String text(JsonObject source,String key,String fallback){return source.has(key)?source.get(key).getAsString():fallback;}
    private static float scalar(JsonObject source,String key,float fallback){return source.has(key)?source.get(key).getAsFloat():fallback;}
    private static double number(JsonObject source,String key,double fallback){return source.has(key)?source.get(key).getAsDouble():fallback;}
    private static boolean bool(JsonObject source,String key,boolean fallback){return source.has(key)?source.get(key).getAsBoolean():fallback;}
    private static JsonElement canonical(JsonElement value) {
        if(value.isJsonObject()) {
            var sorted=new JsonObject();value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry->sorted.add(entry.getKey(),canonical(entry.getValue())));return sorted;
        }
        if(value.isJsonArray()) {var array=new JsonArray();value.getAsJsonArray().forEach(element->array.add(canonical(element)));return array;}
        return value;
    }
}
