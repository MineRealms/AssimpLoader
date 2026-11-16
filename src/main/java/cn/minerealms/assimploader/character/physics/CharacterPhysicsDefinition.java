package cn.minerealms.assimploader.character.physics;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.Reader;
import java.util.*;

/** Versioned, format-independent rigid-body semantics. Dimensions are model-space metres; angles are radians. */
public record CharacterPhysicsDefinition(Vec3 gravity,double fixedStep,int maxSubsteps,int solverIterations,
        float teleportDistance,int maxWorldBoxes,List<Body> bodies,List<Joint> joints) {
    public enum Mode { FOLLOW, DYNAMIC, HYBRID }
    public enum Shape { BOX, SPHERE, CAPSULE }
    public record Vec3(float x,float y,float z) {
        public static final Vec3 ZERO=new Vec3(0,0,0);
        public Vec3 {if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)) throw new IllegalArgumentException("Non-finite physics vector");}
        public javax.vecmath.Vector3f bullet() {return new javax.vecmath.Vector3f(x,y,z);}
    }
    public record Rotation(float x,float y,float z,float w) {
        public static final Rotation IDENTITY=new Rotation(0,0,0,1);
        public Rotation {
            double length=Math.sqrt((double)x*x+(double)y*y+(double)z*z+(double)w*w);
            if(!Double.isFinite(length)||length<1e-8) throw new IllegalArgumentException("Invalid physics quaternion");
            x/=length;y/=length;z/=length;w/=length;
        }
        public Quaternionf quaternion() {return new Quaternionf(x,y,z,w);}
    }
    /** BOX size is half extents; SPHERE uses size.x radius; CAPSULE uses x radius and y cylinder height, along +Y. */
    public record Body(String id,String bone,Mode mode,Shape shape,Vec3 size,Vec3 position,Rotation rotation,
            float mass,float linearDamping,float angularDamping,float friction,float restitution,int group,int mask,
            boolean ragdoll) {
        public Body {
            name(id);if(bone!=null) name(bone);Objects.requireNonNull(mode);Objects.requireNonNull(shape);
            Objects.requireNonNull(size);Objects.requireNonNull(position);Objects.requireNonNull(rotation);
            if(size.x<.001f||size.x>32 || shape==Shape.BOX&&(size.y<.001f||size.z<.001f||size.y>32||size.z>32)
                    || shape==Shape.CAPSULE&&(size.y<0||size.y>64)) throw new IllegalArgumentException("Invalid physics shape size: "+id);
            bounded(position,64,"Body offset");range(mass,.001f,10000,"mass");
            range(linearDamping,0,1,"linearDamping");range(angularDamping,0,1,"angularDamping");
            range(friction,0,10,"friction");range(restitution,0,1,"restitution");filter(group,mask);
        }
        public Matrix4f offset() {return new Matrix4f().translation(position.x,position.y,position.z).rotate(rotation.quaternion());}
    }
    /** Independent Hooke/damping forces at joint frames; Bullet owns integration, constraints and contact solving. */
    public record Spring(Vec3 linearStiffness,Vec3 angularStiffness,Vec3 linearDamping,Vec3 angularDamping,float maxForce) {
        public Spring {
            nonnegative(linearStiffness,10000);nonnegative(angularStiffness,10000);
            nonnegative(linearDamping,1000);nonnegative(angularDamping,1000);range(maxForce,.001f,100000,"spring maxForce");
        }
    }
    /** Joint frames are local to their bodies. A lower component greater than upper explicitly frees that axis. */
    public record Joint(String id,String bodyA,String bodyB,Vec3 positionA,Rotation rotationA,Vec3 positionB,Rotation rotationB,
            Vec3 linearLower,Vec3 linearUpper,Vec3 angularLower,Vec3 angularUpper,boolean disableLinkedCollision,Spring spring) {
        public Joint {
            name(id);name(bodyA);name(bodyB);if(bodyA.equals(bodyB)) throw new IllegalArgumentException("Self joint: "+id);
            Objects.requireNonNull(rotationA);Objects.requireNonNull(rotationB);
            bounded(positionA,64,"Joint position");bounded(positionB,64,"Joint position");
            bounded(linearLower,64,"Linear limit");bounded(linearUpper,64,"Linear limit");
            bounded(angularLower,(float)Math.PI,"Angular limit");bounded(angularUpper,(float)Math.PI,"Angular limit");
        }
        public Matrix4f frameA() {return new Matrix4f().translation(positionA.x,positionA.y,positionA.z).rotate(rotationA.quaternion());}
        public Matrix4f frameB() {return new Matrix4f().translation(positionB.x,positionB.y,positionB.z).rotate(rotationB.quaternion());}
    }

    public CharacterPhysicsDefinition {
        bounded(gravity,100,"gravity");
        if(!Double.isFinite(fixedStep)||fixedStep<1d/1000||fixedStep>1d/30 || maxSubsteps<1||maxSubsteps>32
                ||solverIterations<1||solverIterations>64||maxWorldBoxes<0||maxWorldBoxes>256)
            throw new IllegalArgumentException("Invalid physics stepping/capacity");
        range(teleportDistance,.01f,256,"teleportDistance");bodies=List.copyOf(bodies);joints=List.copyOf(joints);
        if(bodies.size()>128||joints.size()>256) throw new IllegalArgumentException("Physics definition exceeds capacity");
        Set<String> ids=new HashSet<>(),bones=new HashSet<>(),jointIds=new HashSet<>();
        for(Body body:bodies) {
            if(!ids.add(body.id)) throw new IllegalArgumentException("Duplicate physics body: "+body.id);
            if(body.bone!=null&&!bones.add(body.bone)) throw new IllegalArgumentException("Multiple physics bodies write one bone: "+body.bone);
        }
        for(Joint joint:joints) if(!jointIds.add(joint.id)||!ids.contains(joint.bodyA)||!ids.contains(joint.bodyB))
            throw new IllegalArgumentException("Duplicate/unknown joint binding: "+joint.id);
    }

    public static CharacterPhysicsDefinition read(Reader source) {return parse(JsonParser.parseReader(source).getAsJsonObject());}
    public static CharacterPhysicsDefinition parse(JsonObject root) {
        keys(root,"version","gravity","fixedStep","maxSubsteps","solverIterations","teleportDistance","maxWorldBoxes","bodies","joints");
        if(number(root,"version",0)!=1) throw new IllegalArgumentException("Physics definition version must be 1");
        if(array(root,"bodies").size()>128||array(root,"joints").size()>256) throw new IllegalArgumentException("Physics definition exceeds capacity");
        List<Body> bodies=new ArrayList<>();
        for(JsonElement item:array(root,"bodies")) {
            JsonObject b=item.getAsJsonObject();keys(b,"id","bone","mode","shape","size","position","rotation","mass","linearDamping","angularDamping","friction","restitution","group","mask","ragdoll");
            bodies.add(new Body(text(b,"id",null),text(b,"bone",null),Mode.valueOf(text(b,"mode","FOLLOW")),
                    Shape.valueOf(text(b,"shape","SPHERE")),vector(b,"size",new Vec3(.1f,.1f,.1f)),vector(b,"position",Vec3.ZERO),rotation(b,"rotation"),
                    scalar(b,"mass",1),scalar(b,"linearDamping",.05f),scalar(b,"angularDamping",.1f),scalar(b,"friction",.5f),scalar(b,"restitution",0),
                    integer(b,"group",1),integer(b,"mask",65535),bool(b,"ragdoll",true)));
        }
        List<Joint> joints=new ArrayList<>();
        for(JsonElement item:array(root,"joints")) {
            JsonObject j=item.getAsJsonObject();keys(j,"id","bodyA","bodyB","positionA","rotationA","positionB","rotationB","linearLower","linearUpper","angularLower","angularUpper","disableLinkedCollision","spring");
            Spring spring=null;
            if(j.has("spring")) {
                JsonObject s=j.getAsJsonObject("spring");keys(s,"linearStiffness","angularStiffness","linearDamping","angularDamping","maxForce");
                spring=new Spring(vector(s,"linearStiffness",Vec3.ZERO),vector(s,"angularStiffness",Vec3.ZERO),
                        vector(s,"linearDamping",Vec3.ZERO),vector(s,"angularDamping",Vec3.ZERO),scalar(s,"maxForce",1000));
            }
            joints.add(new Joint(text(j,"id",null),text(j,"bodyA",null),text(j,"bodyB",null),vector(j,"positionA",Vec3.ZERO),rotation(j,"rotationA"),
                    vector(j,"positionB",Vec3.ZERO),rotation(j,"rotationB"),vector(j,"linearLower",Vec3.ZERO),vector(j,"linearUpper",Vec3.ZERO),
                    vector(j,"angularLower",Vec3.ZERO),vector(j,"angularUpper",Vec3.ZERO),bool(j,"disableLinkedCollision",true),spring));
        }
        return new CharacterPhysicsDefinition(vector(root,"gravity",new Vec3(0,-9.81f,0)),number(root,"fixedStep",1d/120),
                integer(root,"maxSubsteps",8),integer(root,"solverIterations",12),scalar(root,"teleportDistance",4),integer(root,"maxWorldBoxes",128),bodies,joints);
    }

    static void filter(int group,int mask) {if(group<1||group>65535||mask<0||mask>65535) throw new IllegalArgumentException("Collision filters require 16-bit unsigned groups/masks");}
    static void name(String value) {if(value==null||value.isBlank()||value.length()>256||value.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid physics name");}
    private static void range(float value,float min,float max,String name) {if(!Float.isFinite(value)||value<min||value>max) throw new IllegalArgumentException("Invalid "+name);}
    private static void bounded(Vec3 value,float bound,String name) {
        Objects.requireNonNull(value);if(Math.abs(value.x)>bound||Math.abs(value.y)>bound||Math.abs(value.z)>bound) throw new IllegalArgumentException("Invalid "+name);
    }
    private static void nonnegative(Vec3 value,float max) {bounded(value,max,"spring coefficient");if(value.x<0||value.y<0||value.z<0) throw new IllegalArgumentException("Negative spring coefficient");}
    private static void keys(JsonObject root,String... allowed) {Set<String> names=Set.of(allowed);for(String key:root.keySet()) if(!names.contains(key)) throw new IllegalArgumentException("Unknown physics field: "+key);}
    private static JsonArray array(JsonObject root,String key) {if(!root.has(key)) return new JsonArray();if(!root.get(key).isJsonArray()) throw new IllegalArgumentException("Expected physics array: "+key);return root.getAsJsonArray(key);}
    private static double number(JsonObject root,String key,double fallback) {if(!root.has(key)) return fallback;JsonElement v=root.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble())) throw new IllegalArgumentException("Expected finite physics number: "+key);return v.getAsDouble();}
    private static float scalar(JsonObject root,String key,float fallback) {double n=number(root,key,fallback);if(!Float.isFinite((float)n)) throw new IllegalArgumentException("Physics float overflow: "+key);return (float)n;}
    private static int integer(JsonObject root,String key,int fallback) {double n=number(root,key,fallback);if(n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE) throw new IllegalArgumentException("Expected physics integer: "+key);return (int)n;}
    private static String text(JsonObject root,String key,String fallback) {if(!root.has(key)) return fallback;JsonElement v=root.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected physics string: "+key);return v.getAsString();}
    private static boolean bool(JsonObject root,String key,boolean fallback) {if(!root.has(key)) return fallback;JsonElement v=root.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected physics boolean: "+key);return v.getAsBoolean();}
    private static Vec3 vector(JsonObject root,String key,Vec3 fallback) {if(!root.has(key)) return fallback;JsonArray v=array(root,key);if(v.size()!=3) throw new IllegalArgumentException("Physics vector needs 3 components: "+key);return new Vec3(component(v,0),component(v,1),component(v,2));}
    private static Rotation rotation(JsonObject root,String key) {if(!root.has(key)) return Rotation.IDENTITY;JsonArray v=array(root,key);if(v.size()!=4) throw new IllegalArgumentException("Physics rotation needs 4 components: "+key);return new Rotation(component(v,0),component(v,1),component(v,2),component(v,3));}
    private static float component(JsonArray values,int index) {JsonElement v=values.get(index);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Float.isFinite(v.getAsFloat())) throw new IllegalArgumentException("Invalid physics vector component");return v.getAsFloat();}
}
