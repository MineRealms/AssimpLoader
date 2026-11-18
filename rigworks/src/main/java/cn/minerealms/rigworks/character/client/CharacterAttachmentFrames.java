package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Canonical vanilla part frames in custom model space; imported bone scale never sizes equipment. */
public final class CharacterAttachmentFrames {
    private static final Map<AssimpScene,Map<CharacterProfile,CharacterAttachmentFrames>> CACHE=new WeakHashMap<>();
    private final CharacterProfile profile;
    private final Map<String,Matrix4f> bind=new LinkedHashMap<>();
    private final Quaternionf axes;
    private CharacterAttachmentFrames(CharacterProfile profile,AssimpScene scene) {
        this.profile=profile;
        var locals=new LinkedHashMap<>(SkinBindPose.locals(scene));
        profile.humanoid().values().forEach(bone->{var local=locals.get(bone.node());if(local!=null)locals.put(bone.node(),VanillaPoseRetargeter.calibratedLocal(local,bone.nativeRotation()));});
        var graph=new PoseGraph(scene.getRootNode(),locals);
        profile.humanoid().forEach((role,bone)->{if(graph.contains(bone.node()))bind.put(role,normalized(graph.global(bone.node())));});
        axes=new Quaternionf().rotationY((float)Math.toRadians(-profile.modelYawDegrees())).rotateX((float)Math.PI);
    }
    public static CharacterAttachmentFrames of(CharacterProfile profile,AssimpScene scene) {
        return CACHE.computeIfAbsent(scene,ignored->new WeakHashMap<>()).computeIfAbsent(profile,ignored->new CharacterAttachmentFrames(profile,scene));
    }
    public static Matrix4f frame(CharacterProfile profile,PoseSnapshot snapshot,String role) {
        return of(profile,snapshot.getScene()).part(snapshot,role);
    }
    public static void clear(){CACHE.clear();}
    public Matrix4f arm(PoseSnapshot snapshot,boolean left) {
        String shoulder=left?"left_upper_arm":"right_upper_arm",elbow=left?"left_forearm":"right_forearm";
        var frame=part(snapshot,shoulder);var start=bind.get(shoulder);var end=bind.get(elbow);
        if(frame==null||start==null||end==null)return frame;
        var direction=end.getTranslation(new Vector3f()).sub(start.getTranslation(new Vector3f()));
        if(direction.lengthSquared()<1e-10f)return frame;
        new Quaternionf(axes).invert().transform(direction).normalize();
        // Imported reference arms can point sideways; native first-person geometry extends along +Y.
        return frame.rotate(new Quaternionf().rotationTo(new Vector3f(0,1,0),direction));
    }
    public float armScale(boolean left) {
        String side=left?"left_":"right_";
        float reach=length(side+"upper_arm",side+"forearm",0)+length(side+"forearm",side+"hand",0);
        if(reach<1e-5f)reach=length(side+"upper_arm",side+"hand",.75f);
        return reach<1e-5f?1:.75f/reach;
    }
    public Matrix4f part(PoseSnapshot snapshot,String role) {
        var bone=profile.bone(role);var reference=bind.get(role);
        if(bone==null||reference==null)return null;
        var current=snapshot.getNodeTransform(bone.node());if(current==null)return null;
        Quaternionf delta=normalized(current).getNormalizedRotation(new Quaternionf()).mul(reference.getNormalizedRotation(new Quaternionf()).invert());
        return new Matrix4f().translationRotate(current.getTranslation(new Vector3f()),delta.mul(axes).normalize());
    }
    public Matrix4f body(PoseSnapshot snapshot) {
        String role=bind.containsKey("chest")?"chest":bind.containsKey("spine")?"spine":"hips";
        var reference=bind.get(role);var head=bind.get("head");var bone=profile.bone(role);
        if(reference==null||head==null||bone==null)return null;
        var current=snapshot.getNodeTransform(bone.node());if(current==null)return null;
        Quaternionf delta=normalized(current).getNormalizedRotation(new Quaternionf()).mul(reference.getNormalizedRotation(new Quaternionf()).invert());
        Vector3f position=delta.transform(head.getTranslation(new Vector3f()).sub(reference.getTranslation(new Vector3f()))).add(current.getTranslation(new Vector3f()));
        return new Matrix4f().translationRotate(position,delta.mul(axes).normalize());
    }
    public float length(String a,String b,float fallback) {
        var first=bind.get(a);var second=bind.get(b);if(first==null||second==null)return fallback;
        return first.getTranslation(new Vector3f()).distance(second.getTranslation(new Vector3f()));
    }
    public static Matrix4f normalized(Matrix4f frame) {
        if(!frame.isFinite()||Math.abs(frame.determinant3x3())<1e-12)throw new IllegalArgumentException("Singular attachment frame");
        return new Matrix4f().translationRotate(frame.getTranslation(new Vector3f()),frame.getUnnormalizedRotation(new Quaternionf()).normalize());
    }
}
