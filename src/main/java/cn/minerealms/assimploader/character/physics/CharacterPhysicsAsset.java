package cn.minerealms.assimploader.character.physics;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.*;

import static cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition.*;

/** Blender authoring adapter: an optional joint anchor bone compiles to two canonical body-local frames. */
public final class CharacterPhysicsAsset {
    private CharacterPhysicsAsset() {}
    public static CharacterPhysicsDefinition read(Reader reader,AssimpScene scene) {
        var root=JsonParser.parseReader(reader).getAsJsonObject();
        var anchors=new HashMap<String,String>();
        if(root.has("joints"))for(var item:root.getAsJsonArray("joints")) {
            var joint=item.getAsJsonObject();var anchor=joint.remove("anchorBone");
            if(anchor!=null) {
                if(joint.has("positionA")||joint.has("positionB")||joint.has("rotationA")||joint.has("rotationB"))
                    throw new IllegalArgumentException("Joint cannot specify anchorBone and explicit frames");
                anchors.put(joint.get("id").getAsString(),anchor.getAsString());
            }
        }
        var canonical=CharacterPhysicsDefinition.parse(root);
        var graph=new PoseGraph(scene.getRootNode(),new HashMap<>());
        var bodies=new HashMap<String,Body>();canonical.bodies().forEach(body->bodies.put(body.id(),body));
        var joints=new ArrayList<Joint>();
        for(var joint:canonical.joints()) {
            String node=anchors.get(joint.id());
            if(node==null){joints.add(joint);continue;}
            if(!graph.contains(node))throw new IllegalArgumentException("Unknown joint anchor: "+node);
            Matrix4f anchor=rigid(graph.global(node));
            var a=bodyFrame(graph,bodies.get(joint.bodyA())).invert().mul(anchor);
            var b=bodyFrame(graph,bodies.get(joint.bodyB())).invert().mul(anchor);
            joints.add(new Joint(joint.id(),joint.bodyA(),joint.bodyB(),position(a),rotation(a),position(b),rotation(b),
                    joint.linearLower(),joint.linearUpper(),joint.angularLower(),joint.angularUpper(),joint.disableLinkedCollision(),joint.spring()));
        }
        return new CharacterPhysicsDefinition(canonical.gravity(),canonical.fixedStep(),canonical.maxSubsteps(),canonical.solverIterations(),
                canonical.teleportDistance(),canonical.maxWorldBoxes(),canonical.bodies(),joints);
    }
    private static Matrix4f bodyFrame(PoseGraph graph,Body body) {
        return rigid(body.bone()==null?new Matrix4f():graph.global(body.bone())).mul(body.offset());
    }
    private static Matrix4f rigid(Matrix4f matrix) {return new Matrix4f().translation(matrix.getTranslation(new Vector3f())).rotate(matrix.getUnnormalizedRotation(new Quaternionf()).normalize());}
    private static Vec3 position(Matrix4f matrix) {var v=matrix.getTranslation(new Vector3f());return new Vec3(v.x,v.y,v.z);}
    private static Rotation rotation(Matrix4f matrix) {var q=matrix.getUnnormalizedRotation(new Quaternionf());return new Rotation(q.x,q.y,q.z,q.w);}
}
