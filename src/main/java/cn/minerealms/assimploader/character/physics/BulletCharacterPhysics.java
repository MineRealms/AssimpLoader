package cn.minerealms.assimploader.character.physics;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.*;
import com.bulletphysics.collision.shapes.*;
import com.bulletphysics.dynamics.*;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition.*;

/**
 * One Bullet world per avatar. Simulation coordinates use world axes relative to a double world anchor.
 * FOLLOW follows animation, DYNAMIC writes its bone, HYBRID keeps animated translation and writes rotation.
 * No game world, entity, GPU resource or server authority is owned by this class.
 */
public final class BulletCharacterPhysics implements PoseModifier {
    private final CharacterPhysicsDefinition definition;
    private final AssimpNode root;
    private final float modelScale;
    private final Thread owner=Thread.currentThread();
    private final DiscreteDynamicsWorld world;
    private final Map<String,BodyState> bodies=new LinkedHashMap<>();
    private final List<JointState> joints=new ArrayList<>();
    private final Map<String,WorldState> worldBoxes=new LinkedHashMap<>();
    private final List<BodyState> feedback=new ArrayList<>();
    private final Matrix4f frame=new Matrix4f();
    private boolean closed,initialized,resetPending=true,ragdoll,hasAnchor;
    private double anchorX,anchorY,anchorZ,accumulator,droppedSeconds;
    private int lastSubsteps;
    private long totalSubsteps,resets;

    private static final class BodyState {
        final Body definition;final RigidBody body;final javax.vecmath.Vector3f inertia;
        final Matrix4f inverseOffset;final int depth;
        Transform target=new Transform();
        BodyState(Body definition,RigidBody body,javax.vecmath.Vector3f inertia,int depth,float scale) {
            this.definition=definition;this.body=body;this.inertia=inertia;this.depth=depth;inverseOffset=scaled(definition.offset(),scale).invert();
        }
    }
    private record JointState(Joint definition,Generic6DofConstraint constraint,BodyState a,BodyState b) {}
    private record WorldState(WorldBox definition,RigidBody body) {}

    /** Positions use world axes relative to the currently supplied anchor; boxes are visual-only collision proxies. */
    public record WorldBox(String id,Vec3 center,Vec3 halfExtents,Rotation rotation,int group,int mask) {
        public WorldBox {
            CharacterPhysicsDefinition.name(id);Objects.requireNonNull(center);Objects.requireNonNull(halfExtents);Objects.requireNonNull(rotation);
            if(Math.abs(center.x())>512||Math.abs(center.y())>512||Math.abs(center.z())>512
                    ||halfExtents.x()<.001f||halfExtents.y()<.001f||halfExtents.z()<.001f
                    ||halfExtents.x()>64||halfExtents.y()>64||halfExtents.z()>64) throw new IllegalArgumentException("World box outside visual physics capacity");
            CharacterPhysicsDefinition.filter(group,mask);
        }
    }
    public record Statistics(int bodies,int joints,int worldBoxes,int lastSubsteps,long totalSubsteps,
            double pendingSeconds,double droppedSeconds,long resets,boolean ragdoll,boolean closed) {}

    public BulletCharacterPhysics(CharacterPhysicsDefinition definition,AssimpScene scene) {this(definition,Objects.requireNonNull(scene).getRootNode(),1);}
    public BulletCharacterPhysics(CharacterPhysicsDefinition definition,AssimpScene scene,float scale) {this(definition,Objects.requireNonNull(scene).getRootNode(),scale);}
    public BulletCharacterPhysics(CharacterPhysicsDefinition definition,AssimpNode root) {this(definition,root,1);}
    private BulletCharacterPhysics(CharacterPhysicsDefinition definition,AssimpNode root,float scale) {
        if(!Float.isFinite(scale)||scale<.001f||scale>100)throw new IllegalArgumentException("Invalid physics model scale");
        modelScale=scale;
        this.definition=Objects.requireNonNull(definition);this.root=root;
        var configuration=new DefaultCollisionConfiguration();
        world=new DiscreteDynamicsWorld(new CollisionDispatcher(configuration),new DbvtBroadphase(),new SequentialImpulseConstraintSolver(),configuration);
        world.setGravity(definition.gravity().bullet());world.getSolverInfo().numIterations=definition.solverIterations();
        var graph=new PoseGraph(root,new LinkedHashMap<>());
        try {
            for(Body value:definition.bodies()) {
                if(value.bone()!=null&&!graph.contains(value.bone())) throw new IllegalArgumentException("Unknown physics bone: "+value.bone());
                CollisionShape shape=shape(value,scale);var inertia=new javax.vecmath.Vector3f();shape.calculateLocalInertia(value.mass(),inertia);
                Matrix4f initial=scaled(rigidBone(value.bone()==null?new Matrix4f():graph.global(value.bone())).mul(value.offset()),scale);
                Transform transform=bullet(initial);var info=new RigidBodyConstructionInfo(value.mode()==Mode.FOLLOW?0:value.mass(),new DefaultMotionState(transform),shape,
                        value.mode()==Mode.FOLLOW?new javax.vecmath.Vector3f():inertia);
                info.linearDamping=value.linearDamping();info.angularDamping=value.angularDamping();info.friction=value.friction();info.restitution=value.restitution();
                RigidBody rigid=new RigidBody(info);BodyState state=new BodyState(value,rigid,inertia,depth(root,value.bone(),0),scale);state.target=transform;
                if(value.mode()==Mode.FOLLOW) makeKinematic(rigid);
                bodies.put(value.id(),state);world.addRigidBody(rigid,(short)value.group(),(short)value.mask());
                if(value.bone()!=null) feedback.add(state);
            }
            feedback.sort(Comparator.comparingInt(state->state.depth));
            for(Joint value:definition.joints()) {
                BodyState a=bodies.get(value.bodyA()),b=bodies.get(value.bodyB());
                Generic6DofConstraint joint=new Generic6DofConstraint(a.body,b.body,bullet(scaled(value.frameA(),scale)),bullet(scaled(value.frameB(),scale)),true);
                var lower=value.linearLower().bullet();lower.scale(scale);var upper=value.linearUpper().bullet();upper.scale(scale);
                joint.setLinearLowerLimit(lower);joint.setLinearUpperLimit(upper);
                joint.setAngularLowerLimit(value.angularLower().bullet());joint.setAngularUpperLimit(value.angularUpper().bullet());
                world.addConstraint(joint,value.disableLinkedCollision());joints.add(new JointState(value,joint,a,b));
            }
        } catch(RuntimeException failure) {close();throw failure;}
    }

    @Override public Category category() {return Category.PHYSICS;}
    @Override public Set<String> controlledBones() {return Set.copyOf(feedback.stream().map(value->value.definition.bone()).toList());}
    public CharacterPhysicsDefinition definition() {return definition;}
    public boolean isClosed() {return closed;}
    public Statistics statistics() {return new Statistics(bodies.size(),joints.size(),worldBoxes.size(),lastSubsteps,totalSubsteps,accumulator,droppedSeconds,resets,ragdoll,closed);}

    /** Root is rigid model-to-anchor space; uniform visual scale is supplied at construction. */
    public void setFrame(Matrix4fc modelToAnchor,double worldAnchorX,double worldAnchorY,double worldAnchorZ) {
        requireOpen();rigid(modelToAnchor);
        if(!Double.isFinite(worldAnchorX)||!Double.isFinite(worldAnchorY)||!Double.isFinite(worldAnchorZ)) throw new IllegalArgumentException("Non-finite physics anchor");
        if(hasAnchor) {
            double dx=worldAnchorX-anchorX,dy=worldAnchorY-anchorY,dz=worldAnchorZ-anchorZ;
            if(Math.sqrt(dx*dx+dy*dy+dz*dz)>definition.teleportDistance()) resetPending=true;
            else if(initialized&&(dx!=0||dy!=0||dz!=0)) {
                var shift=new javax.vecmath.Vector3f((float)-dx,(float)-dy,(float)-dz);
                for(BodyState state:bodies.values()) shift(state.body,shift);
                for(WorldState state:worldBoxes.values()) shift(state.body,shift);
            }
        } else resetPending=true;
        frame.set(modelToAnchor);anchorX=worldAnchorX;anchorY=worldAnchorY;anchorZ=worldAnchorZ;hasAnchor=true;
    }

    public void setRagdollEnabled(boolean enabled) {
        requireOpen();if(ragdoll==enabled) return;ragdoll=enabled;
        for(BodyState state:bodies.values()) if(state.definition.mode()==Mode.FOLLOW&&state.definition.ragdoll()) {
            world.removeRigidBody(state.body);
            if(enabled) {
                state.body.setCollisionFlags(state.body.getCollisionFlags()&~(CollisionFlags.STATIC_OBJECT|CollisionFlags.KINEMATIC_OBJECT));
                state.body.setMassProps(state.definition.mass(),state.inertia);state.body.updateInertiaTensor();state.body.forceActivationState(CollisionObject.ACTIVE_TAG);
            } else {state.body.setMassProps(0,new javax.vecmath.Vector3f());makeKinematic(state.body);}
            zero(state.body);world.addRigidBody(state.body,(short)state.definition.group(),(short)state.definition.mask());
        }
        // Leaving ragdoll snaps every body to the current animation on the next evaluation.
        if(!enabled) resetPending=true;
    }

    /** Reuses unchanged box shapes; replacement is bounded and validated before removing any old proxies. */
    public void setWorldBoxes(List<WorldBox> boxes) {
        requireOpen();Objects.requireNonNull(boxes);if(boxes.size()>definition.maxWorldBoxes()) throw new IllegalArgumentException("Too many visual world boxes");
        Map<String,WorldBox> desired=new LinkedHashMap<>();
        for(WorldBox box:boxes) if(desired.put(Objects.requireNonNull(box).id(),box)!=null) throw new IllegalArgumentException("Duplicate world box: "+box.id());
        boolean changed=false;
        var iterator=worldBoxes.entrySet().iterator();
        while(iterator.hasNext()) {
            var old=iterator.next();WorldBox next=desired.get(old.getKey());
            if(next==null||!next.halfExtents().equals(old.getValue().definition.halfExtents())||next.group()!=old.getValue().definition.group()||next.mask()!=old.getValue().definition.mask()) {
                world.removeRigidBody(old.getValue().body);old.getValue().body.destroy();iterator.remove();
                changed=true;
            }
        }
        for(WorldBox box:boxes) {
            Transform transform=bullet(new Matrix4f().translation(box.center.x(),box.center.y(),box.center.z()).rotate(box.rotation.quaternion()));
            WorldState state=worldBoxes.get(box.id());
            if(state==null) {
                var rigid=new RigidBody(0,new DefaultMotionState(transform),new BoxShape(box.halfExtents.bullet()));rigid.setFriction(.7f);
                world.addRigidBody(rigid,(short)box.group,(short)box.mask);worldBoxes.put(box.id,new WorldState(box,rigid));
                changed=true;
            } else if(!box.equals(state.definition)) {place(state.body,transform,true);world.updateSingleAabb(state.body);worldBoxes.put(box.id,new WorldState(box,state.body));changed=true;}
        }
        if(changed) for(BodyState state:bodies.values()) if(!follows(state)) state.body.activate(true);
    }

    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> localOverrides,double deltaSeconds) {
        requireOpen();if(scene.getRootNode()!=root) throw new IllegalArgumentException("Physics actor belongs to another skeleton");
        advance(localOverrides,deltaSeconds);
    }

    /** Numerical/service entry without Minecraft objects; modifies only this evaluation's local pose. */
    public void advance(Map<String,Matrix4f> localOverrides,double deltaSeconds) {
        requireOpen();if(!Double.isFinite(deltaSeconds)||deltaSeconds<0) throw new IllegalArgumentException("Invalid physics dt");
        PoseGraph graph=new PoseGraph(root,localOverrides);Map<String,Matrix4f> animated=new HashMap<>();
        for(BodyState state:bodies.values()) {
            Matrix4f bone=state.definition.bone()==null?new Matrix4f():graph.global(state.definition.bone());
            if(state.definition.bone()!=null) animated.put(state.definition.bone(),new Matrix4f(bone));
            state.target=bullet(new Matrix4f(frame).mul(scaled(rigidBone(bone).mul(state.definition.offset()),modelScale)));
        }
        if(resetPending||!initialized) {
            for(BodyState state:bodies.values()) {
                place(state.body,state.target,true);world.updateSingleAabb(state.body);
                world.getPairCache().cleanProxyFromPairs(state.body.getBroadphaseProxy(),world.getDispatcher());
            }
            for(JointState joint:joints) {
                joint.constraint.getTranslationalLimitMotor().accumulatedImpulse.set(0,0,0);
                for(int axis=0;axis<3;axis++) joint.constraint.getRotationalLimitMotor(axis).accumulatedImpulse=0;
            }
            accumulator=0;initialized=true;resetPending=false;resets++;
        }
        double capacity=definition.fixedStep()*definition.maxSubsteps(),accepted=Math.min(deltaSeconds,capacity);
        droppedSeconds+=deltaSeconds-accepted;accumulator+=accepted;
        if(accumulator>capacity) {droppedSeconds+=accumulator-capacity;accumulator=capacity;}
        int planned=Math.min(definition.maxSubsteps(),(int)Math.floor((accumulator+definition.fixedStep()*1e-7)/definition.fixedStep()));
        Map<BodyState,Transform> starts=new IdentityHashMap<>();
        for(BodyState state:bodies.values()) if(follows(state)||hybridPinned(state))
            starts.put(state,state.body.getCenterOfMassTransform(new Transform()));
        lastSubsteps=0;
        while(accumulator+definition.fixedStep()*1e-7>=definition.fixedStep()&&lastSubsteps<definition.maxSubsteps()) {
            for(BodyState state:bodies.values()) {
                if(follows(state)) state.body.getMotionState().setWorldTransform(interpolate(starts.get(state),state.target,(lastSubsteps+1f)/planned));
                else if(hybridPinned(state)) {
                    Transform current=state.body.getCenterOfMassTransform(new Transform());
                    current.origin.interpolate(starts.get(state).origin,state.target.origin,(lastSubsteps+1f)/planned);
                    place(state.body,current,false);state.body.setLinearVelocity(new javax.vecmath.Vector3f());world.updateSingleAabb(state.body);
                }
            }
            for(JointState joint:joints) if(joint.definition.spring()!=null) spring(joint);
            world.stepSimulation((float)definition.fixedStep(),0,(float)definition.fixedStep());
            accumulator=Math.max(0,accumulator-definition.fixedStep());lastSubsteps++;totalSubsteps++;
        }
        // Kinematic bodies must follow even while paused or when no fixed substep was due.
        for(BodyState state:bodies.values()) if(follows(state)) {place(state.body,state.target,lastSubsteps==0);world.updateSingleAabb(state.body);}
        Matrix4f inverseFrame=new Matrix4f(frame).invert();
        for(BodyState state:feedback) if(!follows(state)) {
            Matrix4f result=new Matrix4f(inverseFrame).mul(joml(state.body.getCenterOfMassTransform(new Transform()))).mul(state.inverseOffset);
            result.setTranslation(result.getTranslation(new Vector3f()).div(modelScale));
            Matrix4f original=animated.get(state.definition.bone());Vector3f scale=original.getScale(new Vector3f());
            Vector3f position=hybridPinned(state)?original.getTranslation(new Vector3f()):result.getTranslation(new Vector3f());
            graph.setGlobal(state.definition.bone(),new Matrix4f().translationRotateScale(position,result.getNormalizedRotation(new Quaternionf()),scale));
        }
    }

    public Matrix4f bodyTransform(String id) {requireOpen();return joml(requireBody(id).body.getCenterOfMassTransform(new Transform()));}
    public record DebugShape(Shape shape,Vector3f size,Matrix4f transform,boolean world) {}
    public org.joml.Vector3d debugAnchor(){requireOpen();return new org.joml.Vector3d(anchorX,anchorY,anchorZ);}
    public List<DebugShape> debugShapes(boolean includeWorld) {
        requireOpen();var result=new ArrayList<DebugShape>();
        for(var state:bodies.values()) {
            var size=state.definition.size();
            result.add(new DebugShape(state.definition.shape(),new Vector3f(size.x(),size.y(),size.z()).mul(modelScale),
                    joml(state.body.getCenterOfMassTransform(new Transform())),false));
        }
        if(includeWorld)for(var state:worldBoxes.values()) {
            var size=state.definition.halfExtents();
            result.add(new DebugShape(Shape.BOX,new Vector3f(size.x(),size.y(),size.z()),
                    joml(state.body.getCenterOfMassTransform(new Transform())),true));
        }
        return List.copyOf(result);
    }
    public Vector3f linearVelocity(String id) {requireOpen();var value=requireBody(id).body.getLinearVelocity(new javax.vecmath.Vector3f());return new Vector3f(value.x,value.y,value.z);}
    public void applyImpulse(String id,Vector3f impulse) {requireOpen();if(impulse==null||!impulse.isFinite()||impulse.lengthSquared()>1e10f) throw new IllegalArgumentException("Invalid impulse");var body=requireBody(id).body;body.activate(true);body.applyCentralImpulse(new javax.vecmath.Vector3f(impulse.x,impulse.y,impulse.z));}
    public void applyTorqueImpulse(String id,Vector3f impulse) {requireOpen();if(impulse==null||!impulse.isFinite()||impulse.lengthSquared()>1e10f) throw new IllegalArgumentException("Invalid torque impulse");var body=requireBody(id).body;body.activate(true);body.applyTorqueImpulse(new javax.vecmath.Vector3f(impulse.x,impulse.y,impulse.z));}
    @Override public void reset() {requireOpen();resetPending=true;accumulator=0;for(BodyState state:bodies.values()) zero(state.body);}
    @Override public void close() {
        if(closed) return;requireThread();
        for(JointState joint:joints) world.removeConstraint(joint.constraint);joints.clear();
        for(BodyState state:bodies.values()) {world.removeRigidBody(state.body);state.body.destroy();}bodies.clear();feedback.clear();
        for(WorldState state:worldBoxes.values()) {world.removeRigidBody(state.body);state.body.destroy();}worldBoxes.clear();
        world.destroy();closed=true;accumulator=0;
    }

    private void spring(JointState state) {
        Spring spring=state.definition.spring();
        Matrix4f a=joml(state.a.body.getCenterOfMassTransform(new Transform())).mul(scaled(state.definition.frameA(),modelScale));
        Matrix4f b=joml(state.b.body.getCenterOfMassTransform(new Transform())).mul(scaled(state.definition.frameB(),modelScale));
        Vector3f pa=a.getTranslation(new Vector3f()),pb=b.getTranslation(new Vector3f());
        var localA=new javax.vecmath.Vector3f(pa.x,pa.y,pa.z);localA.sub(state.a.body.getCenterOfMassPosition(new javax.vecmath.Vector3f()));
        var localB=new javax.vecmath.Vector3f(pb.x,pb.y,pb.z);localB.sub(state.b.body.getCenterOfMassPosition(new javax.vecmath.Vector3f()));
        var va=state.a.body.getVelocityInLocalPoint(localA,new javax.vecmath.Vector3f());
        var vb=state.b.body.getVelocityInLocalPoint(localB,new javax.vecmath.Vector3f());
        Quaternionf orientation=a.getNormalizedRotation(new Quaternionf()),inverse=new Quaternionf(orientation).conjugate();
        Vector3f displacement=inverse.transform(new Vector3f(pb).sub(pa));
        Vector3f relativeVelocity=inverse.transform(new Vector3f(vb.x-va.x,vb.y-va.y,vb.z-va.z));
        Vector3f force=coefficients(displacement,relativeVelocity,spring.linearStiffness(),spring.linearDamping(),spring.maxForce());orientation.transform(force);
        state.b.body.applyForce(new javax.vecmath.Vector3f(force.x,force.y,force.z),localB);
        state.a.body.applyForce(new javax.vecmath.Vector3f(-force.x,-force.y,-force.z),localA);
        Quaternionf difference=new Quaternionf(inverse).mul(b.getNormalizedRotation(new Quaternionf())).normalize();
        if(difference.w<0) difference.mul(-1);
        float sine=(float)Math.sqrt(difference.x*difference.x+difference.y*difference.y+difference.z*difference.z);
        Vector3f angle=sine<1e-7f?new Vector3f():new Vector3f(difference.x,difference.y,difference.z).mul((float)(2*Math.atan2(sine,difference.w))/sine);
        var wa=state.a.body.getAngularVelocity(new javax.vecmath.Vector3f());var wb=state.b.body.getAngularVelocity(new javax.vecmath.Vector3f());
        Vector3f omega=inverse.transform(new Vector3f(wb.x-wa.x,wb.y-wa.y,wb.z-wa.z));
        Vector3f torque=coefficients(angle,omega,spring.angularStiffness(),spring.angularDamping(),spring.maxForce());orientation.transform(torque);
        state.b.body.applyTorque(new javax.vecmath.Vector3f(torque.x,torque.y,torque.z));state.a.body.applyTorque(new javax.vecmath.Vector3f(-torque.x,-torque.y,-torque.z));
        if(force.lengthSquared()+torque.lengthSquared()>1e-10f) {state.a.body.activate();state.b.body.activate();}
    }
    private static Vector3f coefficients(Vector3f error,Vector3f velocity,Vec3 stiffness,Vec3 damping,float bound) {
        Vector3f result=new Vector3f(-error.x*stiffness.x()-velocity.x*damping.x(),-error.y*stiffness.y()-velocity.y*damping.y(),-error.z*stiffness.z()-velocity.z*damping.z());
        if(result.lengthSquared()>bound*bound) result.normalize(bound);return result;
    }
    private boolean follows(BodyState state) {return state.definition.mode()==Mode.FOLLOW&&!(ragdoll&&state.definition.ragdoll());}
    private boolean hybridPinned(BodyState state) {return state.definition.mode()==Mode.HYBRID&&!(ragdoll&&state.definition.ragdoll());}
    private BodyState requireBody(String id) {BodyState state=bodies.get(id);if(state==null) throw new IllegalArgumentException("Unknown physics body: "+id);return state;}
    private void requireThread() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Physics actor used from another thread");}
    private void requireOpen() {requireThread();if(closed) throw new IllegalStateException("Physics actor is closed");}
    private void shift(RigidBody body,javax.vecmath.Vector3f delta) {Transform transform=body.getCenterOfMassTransform(new Transform());transform.origin.add(delta);place(body,transform,false);world.updateSingleAabb(body);}
    private static void makeKinematic(RigidBody body) {body.setCollisionFlags((body.getCollisionFlags()&~CollisionFlags.STATIC_OBJECT)|CollisionFlags.KINEMATIC_OBJECT);body.forceActivationState(CollisionObject.DISABLE_DEACTIVATION);}
    private static void zero(RigidBody body) {body.clearForces();body.setLinearVelocity(new javax.vecmath.Vector3f());body.setAngularVelocity(new javax.vecmath.Vector3f());body.setInterpolationLinearVelocity(new javax.vecmath.Vector3f());body.setInterpolationAngularVelocity(new javax.vecmath.Vector3f());}
    private static void place(RigidBody body,Transform transform,boolean clearVelocity) {body.setCenterOfMassTransform(transform);body.setInterpolationWorldTransform(transform);if(body.getMotionState()!=null) body.getMotionState().setWorldTransform(transform);if(clearVelocity) zero(body);body.activate(true);}
    private static Matrix4f scaled(Matrix4f value,float scale) {return value.setTranslation(value.getTranslation(new Vector3f()).mul(scale));}
    private static Matrix4f rigidBone(Matrix4f value) {
        return new Matrix4f().translation(value.getTranslation(new Vector3f())).rotate(value.getUnnormalizedRotation(new Quaternionf()).normalize());
    }
    private static CollisionShape shape(Body value,float scale) {
        var size=value.size().bullet();size.scale(scale);
        return switch(value.shape()) {case BOX->new BoxShape(size);case SPHERE->new SphereShape(size.x);case CAPSULE->new CapsuleShape(size.x,size.y);};
    }
    private static Transform bullet(Matrix4fc matrix) {
        Vector3f position=matrix.getTranslation(new Vector3f());Quaternionf rotation=matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
        if(!position.isFinite()||!rotation.isFinite()) throw new IllegalArgumentException("Invalid physics target transform");
        Transform result=new Transform();result.setIdentity();result.origin.set(position.x,position.y,position.z);result.setRotation(new javax.vecmath.Quat4f(rotation.x,rotation.y,rotation.z,rotation.w));return result;
    }
    private static Matrix4f joml(Transform value) {javax.vecmath.Quat4f q=value.getRotation(new javax.vecmath.Quat4f());return new Matrix4f().translation(value.origin.x,value.origin.y,value.origin.z).rotate(new Quaternionf(q.x,q.y,q.z,q.w));}
    private static Transform interpolate(Transform from,Transform to,float alpha) {
        Transform result=new Transform();result.setIdentity();result.origin.interpolate(from.origin,to.origin,alpha);
        var a=from.getRotation(new javax.vecmath.Quat4f());var b=to.getRotation(new javax.vecmath.Quat4f());a.interpolate(b,alpha);a.normalize();result.setRotation(a);return result;
    }
    private static void rigid(Matrix4fc matrix) {
        if(matrix==null||!matrix.isFinite()||Math.abs(matrix.m03())>1e-6||Math.abs(matrix.m13())>1e-6||Math.abs(matrix.m23())>1e-6||Math.abs(matrix.m33()-1)>1e-6
                ||Math.abs(matrix.determinant3x3()-1)>.001f) throw new IllegalArgumentException("Physics frame must be a rigid transform without scale/reflection");
        var scale=matrix.getScale(new Vector3f());if(Math.abs(scale.x-1)>.001f||Math.abs(scale.y-1)>.001f||Math.abs(scale.z-1)>.001f) throw new IllegalArgumentException("Physics frame scale must be baked into the definition");
    }
    private static int depth(AssimpNode node,String name,int current) {if(node==null||name==null) return 0;if(node.getName().equals(name)) return current;for(AssimpNode child:node.getChildren()) {int value=depth(child,name,current+1);if(value>=0) return value;}return -1;}
}
