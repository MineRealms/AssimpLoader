package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.PlayerAnimationController;
import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.rigworks.compat.kubejs.CharacterActionFrame;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** One callback per character tick. Java remains the sole owner of simulation and pose evaluation. */
public final class CharacterScriptRuntime {
    private static final Map<String,Consumer<CharacterActionFrame>> CONTROLLERS=new LinkedHashMap<>();
    private static volatile String active,failure="";
    private static volatile long revision;
    private static volatile String reloadFailure="";
    private static final ThreadLocal<PendingRegistration> PENDING=new ThreadLocal<>();
    private CharacterScriptRuntime() {}

    public static synchronized void register(String id,Consumer<CharacterActionFrame> controller) {
        name(id);Objects.requireNonNull(controller);
        var pending=PENDING.get();var registry=pending==null?CONTROLLERS:pending.controllers;
        if(!registry.containsKey(id)&&registry.size()>=32)throw new IllegalArgumentException("At most 32 character controllers");
        registry.put(id,controller);
        if(pending==null){active=id;failure="";revision++;}else pending.active=id;
    }
    public static synchronized void unregister(String id) {
        var pending=PENDING.get();var registry=pending==null?CONTROLLERS:pending.controllers;
        if(registry.remove(id)!=null){if(pending==null){if(id.equals(active))active=null;revision++;}else if(id.equals(pending.active))pending.active=null;}
    }
    public static synchronized void clear() {
        var pending=PENDING.get();if(pending!=null){pending.controllers.clear();pending.active=null;return;}
        CONTROLLERS.clear();active=null;failure="";reloadFailure="";revision++;
    }
    public static synchronized void use(String id) {
        var pending=PENDING.get();var registry=pending==null?CONTROLLERS:pending.controllers;
        if(!registry.containsKey(id))throw new IllegalArgumentException("Unknown character controller: "+id);
        if(pending!=null)pending.active=id;
        else if(!id.equals(active)){active=id;failure="";revision++;}
    }
    public static synchronized void useDefault() {var pending=PENDING.get();if(pending!=null)pending.active=null;else if(active!=null){active=null;failure="";revision++;}}
    public static synchronized List<String> controllers() {var pending=PENDING.get();return new ArrayList<>((pending==null?CONTROLLERS:pending.controllers).keySet());}
    public static String active() {var pending=PENDING.get();return pending==null?active:pending.active;}
    public static String failure() {return failure;}
    public static long revision(){return revision;}
    public static String reloadFailure(){return reloadFailure;}

    /** Unwrapped managers retain the legacy clear-on-load contract; wrapped loads own staging. */
    public static synchronized void prepareClientBindings() {
        if(PENDING.get()==null)clear();
    }

    /** Staging belongs to the loading thread; live sessions keep the published registry until commit. */
    public static synchronized RegistrationReload beginRegistrationReload() {
        var pending=PENDING.get();
        if(pending==null){pending=new PendingRegistration(revision);PENDING.set(pending);}
        pending.depth++;
        return new RegistrationReload(pending);
    }
    public static void recordReloadError(String message) {
        var pending=PENDING.get();if(pending!=null){pending.failed=true;pending.error=message==null?"Client script reload failed":message.substring(0,Math.min(message.length(),512));}
    }
    static synchronized Consumer<CharacterActionFrame> publishedController(String id){return CONTROLLERS.get(id);}

    private static final class PendingRegistration {
        final Map<String,Consumer<CharacterActionFrame>> controllers=new LinkedHashMap<>();
        final long baseRevision;
        int depth;
        String active,error="Client script reload did not complete";
        boolean failed;
        PendingRegistration(long baseRevision){this.baseRevision=baseRevision;}
    }
    public static final class RegistrationReload implements AutoCloseable {
        private final PendingRegistration pending;
        private boolean complete,closed;
        private RegistrationReload(PendingRegistration pending){this.pending=pending;}
        public void complete(){if(closed||PENDING.get()!=pending)throw new IllegalStateException("Reload scope is not active on this thread");complete=true;}
        @Override public void close(){
            synchronized(CharacterScriptRuntime.class){
                if(closed)return;
                if(PENDING.get()!=pending)throw new IllegalStateException("Reload scope belongs to another thread");
                closed=true;if(!complete)pending.failed=true;
                if(--pending.depth!=0)return;
                PENDING.remove();
                if(pending.failed){reloadFailure=pending.error;return;}
                if(revision!=pending.baseRevision){reloadFailure="Controllers changed on another thread during reload";return;}
                CONTROLLERS.clear();CONTROLLERS.putAll(pending.controllers);active=pending.active;
                failure="";reloadFailure="";revision++;
            }
        }
    }
    private static void name(String id) {
        if(id==null||!id.matches("[a-zA-Z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Invalid character controller ID");
    }

    /** Owned by one model binding and discarded when the model, player or world changes. */
    public static final class Session implements AutoCloseable {
        private final AnimationState animation;
        private final CharacterRigDefinition.Installed rig;
        private final Map<String,Object> state=new LinkedHashMap<>();
        private final Map<String,Boolean> originalIk=new LinkedHashMap<>();
        private final Map<String,org.joml.Vector3f> originalTargets=new LinkedHashMap<>();
        private final Map<String,Float> originalMorphs=new LinkedHashMap<>();
        private long seenRevision=-1,lastSwing=-1,lastAttackTick=Long.MIN_VALUE;
        private Boolean originalRagdoll;
        private boolean controlled,closed;

        public Session(AnimationState animation,CharacterRigDefinition.Installed rig) {this.animation=animation;this.rig=rig;}
        public boolean run(LocalPlayer player,CharacterProfile profile,PlayerAnimationController controller,PlayerVisualInput input) {
            if(closed)return false;
            Consumer<CharacterActionFrame> callback;long startedRevision;String selected;
            synchronized(CharacterScriptRuntime.class) {
                startedRevision=revision;selected=active;callback=CONTROLLERS.get(selected);
            }
            if(seenRevision!=startedRevision){reset();controller.reset();seenRevision=startedRevision;}
            if(callback==null)return false;
            if(!controlled) {
                originalRagdoll=LocalPlayerCharacter.ragdollOverride();
                if(rig!=null) {
                    rig.ik().forEach((id,ik)->{originalIk.put(id,ik.isEnabled());originalTargets.put(id,ik.target());});
                    originalMorphs.putAll(rig.morphs().inputWeights());
                }
                controlled=true;
            }
            var swing=input.swingAction();
            if(swing!=null&&swing.active()&&swing.generation()!=lastSwing){lastSwing=swing.generation();lastAttackTick=input.sequence();}
            long elapsed=lastAttackTick==Long.MIN_VALUE?1_000_000:Math.max(0,input.sequence()-lastAttackTick);
            var frame=new CharacterActionFrame(player,profile,controller,input,animation,rig,state,elapsed);
            animation.beginPoseEdit();
            try {
                callback.accept(frame);
                if(startedRevision!=revision){reset();controller.reset();return false;}
                return true;
            } catch(RuntimeException | LinkageError exception) {
                synchronized(CharacterScriptRuntime.class) {
                    failure="Controller "+selected+": "+exception;
                    if(startedRevision==revision){active=null;revision++;seenRevision=revision;}
                }
                AssimpLoader.LOGGER.warn("Character script failed; restoring the built-in controller: {}",selected,exception);
                reset();controller.reset();return false;
            } finally {
                frame.expire();
                // The animation manager evaluates the complete command set once at its normal tick.
                animation.endPoseEdit(false);
            }
        }
        public void reset() {
            if(controlled&&!animation.isClosed()) {
                CharacterActionFrame.clearControls(animation);
                if(rig!=null) {
                    rig.motions().reset();rig.morphs().reset();
                    originalMorphs.forEach(rig.morphs()::setWeight);
                    originalIk.forEach((id,value)->rig.ik().get(id).setEnabled(value));
                    originalTargets.forEach((id,value)->rig.ik().get(id).setTarget(value));
                }
                LocalPlayerCharacter.setRagdollOverride(originalRagdoll);
            }
            controlled=false;state.clear();originalIk.clear();originalTargets.clear();originalMorphs.clear();
            originalRagdoll=null;
            lastSwing=-1;lastAttackTick=Long.MIN_VALUE;
        }
        @Override public void close(){if(!closed){reset();closed=true;}}
    }
}
