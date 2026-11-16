package cn.minerealms.assimploader.animation;

import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Consumer;

/** An instance facade for state requests, named mounts, and effects sharing one published pose. */
public final class ModelInstance implements AutoCloseable {
    public record Attachment(String node, Matrix4f offset) {
        public Attachment { offset = new Matrix4f(offset); }
        @Override public Matrix4f offset() { return new Matrix4f(offset); }
    }
    private final AnimationState animation;
    private final Map<String, Attachment> attachments;
    private final Map<String, MaterialAnimation> materials = new java.util.LinkedHashMap<>();
    private final Map<String, AttachmentTrail> trails = new java.util.LinkedHashMap<>();

    ModelInstance(AnimationState animation, Map<String, Attachment> attachments, JsonObject definition) {
        this.animation = animation;
        this.attachments = Map.copyOf(attachments);
        ModelInstanceDefinition.object(definition, "materials").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            ModelInstanceDefinition.keys(value, "scrollU", "scrollV", "red", "green", "blue", "alpha", "pulseFrequency", "pulseDepth");
            if (!entry.getKey().equals("*") && animation.getScene().getMaterials().stream().noneMatch(material -> material.getName().equals(entry.getKey())))
                throw new IllegalArgumentException("Unknown material: " + entry.getKey());
            materials.put(entry.getKey(), new MaterialAnimation(number(value,"scrollU",0), number(value,"scrollV",0),
                    number(value,"red",1),number(value,"green",1),number(value,"blue",1),number(value,"alpha",1),
                    number(value,"pulseFrequency",0),number(value,"pulseDepth",0)));
        });
        ModelInstanceDefinition.object(definition, "trails").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            ModelInstanceDefinition.keys(value, "start", "end", "capacity", "lifetime", "color");
            String start = ModelInstanceDefinition.text(value,"start", "");
            String end = ModelInstanceDefinition.text(value,"end", "");
            if (!attachments.containsKey(start) || !attachments.containsKey(end)) throw new IllegalArgumentException("Unknown trail attachment");
            float capacity = number(value,"capacity",32);
            if (capacity != (int)capacity) throw new IllegalArgumentException("Trail capacity must be an integer");
            float[] color = {1,1,1,1};
            if (value.has("color")) {
                var values = value.getAsJsonArray("color");
                if (values.size() != 4) throw new IllegalArgumentException("Trail color requires RGBA");
                for (int i=0;i<4;i++) color[i] = ModelInstanceDefinition.finite(values.get(i));
            }
            trails.put(entry.getKey(), new AttachmentTrail(start,end,(int)capacity,number(value,"lifetime",.5f),color));
        });
        animation.setModelInstance(this);
        afterPose();
    }

    private static float number(JsonObject object, String name, float fallback) { return ModelInstanceDefinition.number(object,name,fallback); }

    public AnimationState animation() { return animation; }
    public boolean requestState(String state) { return animation.getStateMachine().request(state); }
    public Map<String, Attachment> attachments() { return attachments; }
    public Map<String, AttachmentTrail> trails() { return java.util.Collections.unmodifiableMap(trails); }
    public boolean hasMaterialAnimation() { return !materials.isEmpty(); }
    public MaterialAnimation.Sample material(String name) {
        MaterialAnimation value = materials.getOrDefault(name, materials.get("*"));
        return value == null ? null : value.sample(animation.getSimulationTime());
    }

    void afterPose() {
        trails.values().forEach(trail -> trail.sample(animation.getSimulationTime(),
                attachmentTransform(trail.startAttachment(), new Matrix4f()).getTranslation(new Vector3f()),
                attachmentTransform(trail.endAttachment(), new Matrix4f()).getTranslation(new Vector3f())));
    }
    public void resetEffects() { trails.values().forEach(AttachmentTrail::reset); }

    public Matrix4f attachmentTransform(String name, Matrix4f modelToWorld) {
        Attachment attachment = attachments.get(name);
        if (attachment == null) throw new IllegalArgumentException("Unknown attachment: " + name);
        PoseSnapshot snapshot = animation.getPoseSnapshot();
        if (snapshot == null) throw new IllegalStateException("Instance has no published pose");
        return new Matrix4f(modelToWorld).mul(snapshot.getNodeTransform(attachment.node())).mul(attachment.offset());
    }

    /** Render callbacks receive a copy and cannot change the pose shared with shadow/collision consumers. */
    public void drawAttachment(String name, Matrix4f modelToWorld, Consumer<Matrix4f> draw) {
        draw.accept(attachmentTransform(name, modelToWorld));
    }

    @Override public void close() { animation.close(); }
}
