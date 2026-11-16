package cn.minerealms.assimploader.animation;

import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import cn.minerealms.assimploader.collision.BoneCollisionProxy;
import cn.minerealms.assimploader.collision.ModelCollision;

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
    private final ModelCollision collision;
    private final java.util.List<AttachmentParticleEmitter> particles=new java.util.ArrayList<>();
    private AttachmentParticleEmitter.Sink particleSink;
    private double effectTime;

    ModelInstance(AnimationState animation, Map<String, Attachment> attachments, JsonObject definition) {
        this.animation = animation;
        this.attachments = Map.copyOf(attachments);
        var proxies = new java.util.ArrayList<BoneCollisionProxy>();
        ModelInstanceDefinition.object(definition,"hitboxes").entrySet().forEach(entry -> {
            JsonObject value=entry.getValue().getAsJsonObject();
            ModelInstanceDefinition.keys(value,"node","start","end","radius");
            String node=ModelInstanceDefinition.text(value,"node","");
            ModelInstanceDefinition.requireNode(animation.getScene(),node);
            proxies.add(new BoneCollisionProxy(node,ModelInstanceDefinition.vector(value,"start",new Vector3f()),
                    ModelInstanceDefinition.vector(value,"end",new Vector3f(0,1,0)),number(value,"radius",.1f)));
        });
        collision=new ModelCollision(proxies);
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
        ModelInstanceDefinition.object(definition,"particles").entrySet().forEach(entry -> {
            JsonObject value=entry.getValue().getAsJsonObject();
            ModelInstanceDefinition.keys(value,"attachment","particle","rate","velocity");
            String attachment=ModelInstanceDefinition.text(value,"attachment","");
            if(!attachments.containsKey(attachment)) throw new IllegalArgumentException("Unknown particle attachment: "+attachment);
            particles.add(new AttachmentParticleEmitter(attachment,ModelInstanceDefinition.text(value,"particle","minecraft:smoke"),
                    number(value,"rate",10),ModelInstanceDefinition.vector(value,"velocity",new Vector3f())));
        });
        effectTime=animation.getSimulationTime();
        animation.setModelInstance(this);
        afterPose();
    }

    private static float number(JsonObject object, String name, float fallback) { return ModelInstanceDefinition.number(object,name,fallback); }

    public AnimationState animation() { return animation; }
    public ModelCollision collision() { return collision; }
    public void setParticleSink(AttachmentParticleEmitter.Sink sink) { particleSink=sink; }
    public boolean requestState(String state) { return animation.getStateMachine().request(state); }
    public Map<String, Attachment> attachments() { return attachments; }
    public Map<String, AttachmentTrail> trails() { return java.util.Collections.unmodifiableMap(trails); }
    public boolean hasMaterialAnimation() { return !materials.isEmpty(); }
    public void setMaterialAnimation(String material, MaterialAnimation animation) {
        if (this.animation.isClosed()) throw new IllegalStateException("Instance is closed");
        if (!material.equals("*") && this.animation.getScene().getMaterials().stream().noneMatch(value -> value.getName().equals(material)))
            throw new IllegalArgumentException("Unknown material: " + material);
        if (animation == null) materials.remove(material); else materials.put(material,animation);
    }
    public MaterialAnimation.Sample material(String name) {
        MaterialAnimation value = materials.getOrDefault(name, materials.get("*"));
        return value == null ? null : value.sample(animation.getSimulationTime());
    }

    void afterPose() {
        trails.values().forEach(trail -> trail.sample(animation.getSimulationTime(),
                attachmentTransform(trail.startAttachment(), new Matrix4f()).getTranslation(new Vector3f()),
                attachmentTransform(trail.endAttachment(), new Matrix4f()).getTranslation(new Vector3f())));
        double time=animation.getSimulationTime();
        double delta=Math.max(0,time-effectTime); effectTime=time;
        particles.forEach(emitter -> emitter.update(delta,attachmentTransform(emitter.attachment(),new Matrix4f()).getTranslation(new Vector3f()),particleSink));
    }
    public void resetEffects() { trails.values().forEach(AttachmentTrail::reset); particles.forEach(AttachmentParticleEmitter::reset); effectTime=animation.getSimulationTime(); }

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

    void release() { resetEffects(); particleSink=null; collision.clear(); }
    @Override public void close() { animation.close(); }
}
