package cn.minerealms.assimploader.animation;

import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import cn.minerealms.assimploader.collision.BoneCollisionProxy;
import cn.minerealms.assimploader.collision.ModelCollision;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;

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
    private final Map<String,cn.minerealms.assimploader.animation.ik.FootPlacementModifier> feet=new java.util.LinkedHashMap<>();
    private final Matrix4f worldTransform=new Matrix4f();
    private boolean worldBound;
    private Consumer<ModelInstance> worldBinding;
    private long emittedParticles;
    private long mainRenderPasses,shadowRenderPasses;
    private boolean worldEffectsEnabled=true;
    private boolean groundPlacementEnabled=true;
    private final Map<String,java.util.Set<String>> footStates=new java.util.LinkedHashMap<>();
    private cn.minerealms.assimploader.animation.ik.GroundBodyModifier groundBody;
    private java.util.Set<String> groundBodyStates=java.util.Set.of();
    private java.util.Set<String> groundSupportStates=java.util.Set.of();

    ModelInstance(AnimationState animation, Map<String, Attachment> attachments, JsonObject definition) {
        this.animation = animation;
        this.attachments = Map.copyOf(attachments);
        JsonObject body=ModelInstanceDefinition.object(definition,"groundBody");
        if(!body.entrySet().isEmpty()) {
            ModelInstanceDefinition.keys(body,"node","feet","rise","drop","maxOffset","sole","states","supportStates","contacts");
            String node=ModelInstanceDefinition.text(body,"node","");
            ModelInstanceDefinition.requireNode(animation.getScene(),node);
            var ends=new java.util.ArrayList<String>();
            for(var element:ModelInstanceDefinition.array(body,"feet")) {
                String name=element.getAsString();ModelInstanceDefinition.requireNode(animation.getScene(),name);ends.add(name);
            }
            var graph=new PoseGraph(animation.getScene().getRootNode(),Map.of());
            for(String end:ends) if(!graph.isAncestor(node,end)) throw new IllegalArgumentException("Ground body must be ancestor of its feet");
            var states=new java.util.LinkedHashSet<String>();
            for(var element:ModelInstanceDefinition.array(body,"states")) {
                String name=element.getAsString();
                if(!animation.getStateMachine().getBindings().containsKey(name)) throw new IllegalArgumentException("Unknown ground body state: "+name);
                states.add(name);
            }
            groundBodyStates=java.util.Set.copyOf(states);
            var supportStates=new java.util.LinkedHashSet<String>();
            for(var element:ModelInstanceDefinition.array(body,"supportStates")) {
                String name=element.getAsString();
                if(!animation.getStateMachine().getBindings().containsKey(name)) throw new IllegalArgumentException("Unknown ground support state: "+name);
                supportStates.add(name);
            }
            groundSupportStates=java.util.Set.copyOf(supportStates);
            var contacts=new java.util.LinkedHashMap<String,java.util.List<Vector3f>>();
            ModelInstanceDefinition.object(body,"contacts").entrySet().forEach(entry->{
                var points=new java.util.ArrayList<Vector3f>();
                for(var value:entry.getValue().getAsJsonArray()) {
                    var point=value.getAsJsonArray();
                    if(point.size()!=3) throw new IllegalArgumentException("Ground contact requires three coordinates");
                    points.add(new Vector3f(point.get(0).getAsFloat(),point.get(1).getAsFloat(),point.get(2).getAsFloat()));
                }
                contacts.put(entry.getKey(),points);
            });
            groundBody=new cn.minerealms.assimploader.animation.ik.GroundBodyModifier(node,ends,number(body,"rise",.3f),
                    number(body,"drop",.5f),number(body,"maxOffset",.2f),number(body,"sole",.005f),contacts);
            animation.addModifier("groundBody",groundBody);
        }
        var footDefinitions=ModelInstanceDefinition.object(definition,"footPlacement");
        if(footDefinitions.size()>16) throw new IllegalArgumentException("At most 16 grounded feet per instance");
        footDefinitions.entrySet().forEach(entry -> {
            JsonObject value=entry.getValue().getAsJsonObject();
            ModelInstanceDefinition.keys(value,"chain","rise","drop","sole","weight","states");
            var chain=new java.util.ArrayList<String>();
            for(var element:ModelInstanceDefinition.array(value,"chain")) {
                String node=element.getAsString(); ModelInstanceDefinition.requireNode(animation.getScene(),node); chain.add(node);
            }
            var graph=new PoseGraph(animation.getScene().getRootNode(),Map.of());
            for(int index=1;index<chain.size();index++) if(!graph.isAncestor(chain.get(index-1),chain.get(index)))
                throw new IllegalArgumentException("Ground IK nodes must form an ordered chain");
            var foot=new cn.minerealms.assimploader.animation.ik.FootPlacementModifier(chain,null,
                    number(value,"rise",.3f),number(value,"drop",.5f),number(value,"sole",.005f),number(value,"weight",1));
            feet.put(entry.getKey(),foot);
            var states=new java.util.LinkedHashSet<String>();
            for(var element:ModelInstanceDefinition.array(value,"states")) {
                String name=element.getAsString();
                if(!animation.getStateMachine().getBindings().containsKey(name)) throw new IllegalArgumentException("Unknown foot placement state: "+name);
                states.add(name);
            }
            footStates.put(entry.getKey(),java.util.Set.copyOf(states));
            animation.addModifier("footPlacement:"+entry.getKey(),foot);
        });
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
    public InstancePerformanceSettings performance() { return animation.getPerformanceSettings(); }
    public boolean trailsEnabled() { return worldEffectsEnabled && performance().trails() && RuntimePerformancePolicy.current().trailsEnabled(); }
    public ModelCollision collision() { return collision; }
    public void setParticleSink(AttachmentParticleEmitter.Sink sink) { particleSink=sink; }
    public long emittedParticles() { return emittedParticles; }
    public record RenderStatistics(long mainPasses,long shadowPasses) {}
    public RenderStatistics renderStatistics() { return new RenderStatistics(mainRenderPasses,shadowRenderPasses); }
    public void noteRenderPass(boolean shadow) { if(shadow) shadowRenderPasses++;else mainRenderPasses++; }
    public void setWorldEffectsEnabled(boolean enabled) { if(worldEffectsEnabled!=enabled) { worldEffectsEnabled=enabled; resetEffects(); } }
    public void setGroundPlacementEnabled(boolean enabled) { groundPlacementEnabled=enabled; }
    public Map<String,cn.minerealms.assimploader.animation.ik.FootPlacementModifier> feet() { return java.util.Collections.unmodifiableMap(feet); }
    public float groundBodyOffset() { return groundBody==null?0:groundBody.lastOffset(); }
    public boolean hasWorldBinding() { return worldBinding!=null; }
    public void setWorldBinding(Consumer<ModelInstance> source) { worldBinding=source; }
    void prepareTick() { if(worldBinding!=null) worldBinding.accept(this); }
    public boolean trailsInWorldSpace() { return worldBound; }
    public Matrix4f worldTransform() { return new Matrix4f(worldTransform); }
    public void setWorldContext(Matrix4f transform,cn.minerealms.assimploader.animation.ik.FootPlacementModifier.GroundSampler sampler) {
        if(!transform.isFinite() || Math.abs(transform.determinant())<1e-10f) throw new IllegalArgumentException("Invalid instance world transform");
        boolean discontinuity=!worldBound || worldTransform.getTranslation(new Vector3f()).distanceSquared(transform.getTranslation(new Vector3f()))>16;
        worldBound=true; worldTransform.set(transform);
        String currentState=animation.getStateMachine().getCurrentStateName();
        boolean support=currentState!=null && groundSupportStates.contains(currentState);
        if(groundBody!=null) groundBody.bind(transform,groundPlacementEnabled && (groundBodyStates.isEmpty()
                || currentState!=null && groundBodyStates.contains(currentState) || support)?sampler:null,support);
        feet.forEach((name,foot)->{
            var states=footStates.get(name);
            String current=animation.getStateMachine().getCurrentStateName();
            foot.setGroundSampler(groundPlacementEnabled && (states.isEmpty() || current!=null && states.contains(current))?sampler:null);
            foot.setModelToWorld(transform);
        });
        if(discontinuity) animation.resetDynamics();
    }
    public void clearWorldContext() {
        if(worldBound) { worldBound=false; worldTransform.identity(); feet.values().forEach(foot->foot.setGroundSampler(null)); if(groundBody!=null) groundBody.bind(worldTransform,null); animation.resetDynamics(); }
        particleSink=null;
    }
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
        var policy=RuntimePerformancePolicy.current();
        var settings=performance();
        if (trailsEnabled()) {
            for (AttachmentTrail trail : trails.values()) trail.sample(animation.getSimulationTime(),
                    attachmentTransform(trail.startAttachment(), worldBound?worldTransform:new Matrix4f()).getTranslation(new Vector3f()),
                    attachmentTransform(trail.endAttachment(), worldBound?worldTransform:new Matrix4f()).getTranslation(new Vector3f()),
                    Math.min(settings.maxTrailSamples(),policy.maxTrailSamples()));
        } else trails.values().forEach(AttachmentTrail::reset);
        double time=animation.getSimulationTime();
        double delta=Math.max(0,time-effectTime); effectTime=time;
        int budget=worldEffectsEnabled && settings.particles() && policy.particlesEnabled() ? Math.min(settings.maxParticleEmissions(),policy.maxParticleEmissions()) : 0;
        for (AttachmentParticleEmitter emitter : particles) {
            if (budget==0 || particleSink==null) { emitter.reset(); continue; }
            int emitted=emitter.update(delta,attachmentTransform(emitter.attachment(),new Matrix4f()).getTranslation(new Vector3f()),particleSink,budget);
            budget-=emitted; emittedParticles+=emitted;
        }
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

    void release() { resetEffects(); particleSink=null; worldBinding=null; collision.clear(); }
    @Override public void close() { animation.close(); }
}
