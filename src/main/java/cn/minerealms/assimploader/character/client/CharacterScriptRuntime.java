package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.character.core.CharacterProfile;
import cn.minerealms.assimploader.character.core.PlayerAnimationController;
import cn.minerealms.assimploader.character.core.PlayerVisualInput;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.assimploader.compat.kubejs.CharacterActionFrame;
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
    private CharacterScriptRuntime() {}

    public static synchronized void register(String id,Consumer<CharacterActionFrame> controller) {
        name(id);Objects.requireNonNull(controller);
        if(!CONTROLLERS.containsKey(id)&&CONTROLLERS.size()>=32)throw new IllegalArgumentException("At most 32 character controllers");
        CONTROLLERS.put(id,controller);active=id;failure="";revision++;
    }
    public static synchronized void unregister(String id) {if(CONTROLLERS.remove(id)!=null){if(id.equals(active))active=null;revision++;}}
    public static synchronized void clear() {CONTROLLERS.clear();active=null;failure="";revision++;}
    public static synchronized void use(String id) {
        if(!CONTROLLERS.containsKey(id))throw new IllegalArgumentException("Unknown character controller: "+id);
        if(!id.equals(active)){active=id;failure="";revision++;}
    }
    public static synchronized void useDefault() {if(active!=null){active=null;failure="";revision++;}}
    public static synchronized List<String> controllers() {return new ArrayList<>(CONTROLLERS.keySet());}
    public static String active() {return active;}
    public static String failure() {return failure;}
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
