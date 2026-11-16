package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.character.client.LocalPlayerCharacter;
import cn.minerealms.assimploader.character.core.CharacterProfile;
import cn.minerealms.assimploader.character.core.PlayerAnimationController;
import cn.minerealms.assimploader.character.core.PlayerVisualInput;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tick-scoped controls and immutable observations. Script state belongs to the current player/model binding. */
public final class CharacterActionFrame {
    private final LocalPlayer player;
    private final CharacterProfile profile;
    private final PlayerAnimationController controller;
    private final PlayerVisualInput observation;
    private final AnimationState animation;
    private final CharacterRigDefinition.Installed rig;
    private final Map<String,Object> state,input;
    private boolean valid=true;

    public CharacterActionFrame(LocalPlayer player,CharacterProfile profile,PlayerAnimationController controller,
                                PlayerVisualInput observation,AnimationState animation,CharacterRigDefinition.Installed rig,
                                Map<String,Object> state,long ticksAfterAttack) {
        this.player=player;this.profile=profile;this.controller=controller;this.observation=observation;
        this.animation=animation;this.rig=rig;this.state=state;input=snapshot(player,observation,ticksAfterAttack);
    }
    public Map<String,Object> input(){check();return input;}
    public Map<String,Object> state(){check();return state;}
    public AssimpModelHandle model(){check();return new AssimpModelHandle(animation);}
    public String bone(String role){check();var value=profile.bone(role);return value==null?null:value.node();}
    public Map<String,String> bones(){check();var result=new LinkedHashMap<String,String>();profile.humanoid().forEach((role,bone)->result.put(role,bone.node()));return Map.copyOf(result);}
    public String clip(String intent){check();return profile.animation(intent);}
    public Map<String,Object> profile(){check();return Map.of("model",profile.model(),"scale",profile.scale(),"modelYawDegrees",profile.modelYawDegrees(),"animations",profile.animations(),"bones",bones());}

    /** Retains built-in locomotion cadence and head aim while scripts provide the action overlay. */
    public void defaultMotion() {
        check();var i=observation;
        controller.update(new PlayerVisualInput(i.sequence(),i.timeSeconds(),i.forwardVelocity(),i.strafeVelocity(),i.verticalVelocity(),
                i.bodyYawDegrees(),i.headYawDegrees(),i.headPitchDegrees(),i.onGround(),i.crouching(),i.sprinting(),i.jumping(),
                i.falling(),i.swimming(),i.flying(),i.elytra(),i.riding(),i.sleeping(),i.dead(),false,false,
                i.swingAction()==null?null:new PlayerVisualInput.SwingAction(i.swingAction().generation(),false,0)));
    }
    public void locomotion(){defaultMotion();}
    public void defaultAnimation(){check();controller.update(observation);}
    public List<String> morphs(){check();return rig==null?List.of():new ArrayList<>(rig.morphs().names());}
    public void morph(String name,double weight){checkRig();rig.morphs().setWeight(name,number(weight));}
    public List<String> ikChains(){check();return rig==null?List.of():new ArrayList<>(rig.ik().keySet());}
    public void ik(String name,boolean enabled){checkRig();var value=rig.ik().get(name);if(value==null)throw new IllegalArgumentException("Unknown character IK: "+name);value.setEnabled(enabled);}
    public void ikTarget(String name,double x,double y,double z){checkRig();var value=rig.ik().get(name);if(value==null)throw new IllegalArgumentException("Unknown character IK: "+name);value.setTarget(new Vector3f(number(x),number(y),number(z)));}
    public Map<String,Object> ikStatus(String name){checkRig();var value=rig.ik().get(name);if(value==null)throw new IllegalArgumentException("Unknown character IK: "+name);var p=value.target();return Map.of("enabled",value.isEnabled(),"error",value.lastError(),"x",p.x,"y",p.y,"z",p.z);}
    public List<String> motions(){check();return rig==null?List.of():new ArrayList<>(rig.motions().names());}
    public void motion(String name,boolean loop){checkRig();if(!name.equals(rig.motions().currentName()))rig.motions().play(name,loop);}
    public void stopMotion(){checkRig();rig.motions().reset();}
    public void seekMotion(double seconds){checkRig();rig.motions().seek(seconds);}
    public void pauseMotion(boolean paused){checkRig();if(paused)rig.motions().pause();else rig.motions().resume();}
    public void ragdoll(boolean enabled){check();LocalPlayerCharacter.setRagdollOverride(enabled);var physics=LocalPlayerCharacter.currentPhysics();if(physics!=null)physics.setRagdollEnabled(enabled);}
    public void automaticRagdoll(){check();LocalPlayerCharacter.setRagdollOverride(null);}
    public Map<String,Object> physics(){check();var physics=LocalPlayerCharacter.currentPhysics();if(physics==null)return Map.of("available",false);var s=physics.statistics();return Map.of("available",true,"bodies",s.bodies(),"joints",s.joints(),"worldBoxes",s.worldBoxes(),"ragdoll",s.ragdoll(),"lastSubsteps",s.lastSubsteps(),"droppedSeconds",s.droppedSeconds());}
    public List<String> physicsBodies(){check();var physics=LocalPlayerCharacter.currentPhysics();return physics==null?List.of():physics.definition().bodies().stream().map(value->value.id()).toList();}
    public Map<String,Object> body(String id){check();var physics=LocalPlayerCharacter.currentPhysics();if(physics==null)throw new IllegalStateException("Character has no physics");var position=physics.bodyTransform(id).getTranslation(new Vector3f());var velocity=physics.linearVelocity(id);return Map.of("x",position.x,"y",position.y,"z",position.z,"vx",velocity.x,"vy",velocity.y,"vz",velocity.z);}
    public void resetPhysics(){check();var physics=LocalPlayerCharacter.currentPhysics();if(physics!=null)physics.reset();}
    public void impulse(String body,double x,double y,double z){check();var physics=LocalPlayerCharacter.currentPhysics();if(physics==null)throw new IllegalStateException("Character has no physics");physics.applyImpulse(body,new Vector3f(number(x),number(y),number(z)));}
    public void torqueImpulse(String body,double x,double y,double z){check();var physics=LocalPlayerCharacter.currentPhysics();if(physics==null)throw new IllegalStateException("Character has no physics");physics.applyTorqueImpulse(body,new Vector3f(number(x),number(y),number(z)));}
    public void expire(){valid=false;}
    public static void clearControls(AnimationState animation){
        new AssimpModelHandle(animation).clearScriptControls();
        animation.resume();
        var player=animation.getCurrentAnimation();
        if(player!=null) {
            var binding=animation.getStateMachine().getBindings().get(animation.getStateMachine().getCurrentStateName());
            player.setLooping(binding==null || !binding.clip().equals(player.getAnimation().getName()) || binding.looping());
            player.play();
        }
    }
    private void check(){CharacterScriptApi.checkThread();if(!valid||animation.isClosed())throw new IllegalStateException("Character action frame expired; retain state values, not the frame");}
    private void checkRig(){check();if(rig==null)throw new IllegalStateException("Character has no solver rig");}
    private static float number(double value){if(!Double.isFinite(value)||!Float.isFinite((float)value))throw new IllegalArgumentException("Expected finite float");return (float)value;}

    private static Map<String,Object> snapshot(LocalPlayer p,PlayerVisualInput i,long ticksAfterAttack) {
        var out=new LinkedHashMap<String,Object>();
        out.put("tick",i.sequence());out.put("time",i.timeSeconds());out.put("delta",.05);
        out.put("forwardVelocity",i.forwardVelocity());out.put("strafeVelocity",i.strafeVelocity());out.put("verticalVelocity",i.verticalVelocity());out.put("speed",i.horizontalSpeed());
        out.put("bodyYaw",i.bodyYawDegrees());out.put("headYaw",i.headYawDegrees());out.put("headPitch",i.headPitchDegrees());
        out.put("headYawRelative",net.minecraft.util.Mth.wrapDegrees(i.headYawDegrees()-i.bodyYawDegrees()));
        out.put("onGround",i.onGround());out.put("crouching",i.crouching());out.put("sprinting",i.sprinting());
        out.put("jumping",i.jumping());out.put("falling",i.falling());out.put("swimming",i.swimming());out.put("flying",i.flying());out.put("elytra",i.elytra());
        out.put("riding",i.riding());out.put("sleeping",i.sleeping());out.put("dead",i.dead());out.put("usingItem",i.usingItem());
        out.put("climbing",p.onClimbable());out.put("inWater",p.isInWater());out.put("underWater",p.isUnderWater());out.put("inLava",p.isInLava());
        out.put("passengerLiving",p.getVehicle() instanceof net.minecraft.world.entity.LivingEntity);
        out.put("vehicleType",p.getVehicle()==null?"":BuiltInRegistries.ENTITY_TYPE.getKey(p.getVehicle().getType()).toString());
        out.put("primaryArm",lower(p.getMainArm().name()));
        var swing=i.swingAction();var swingHand=p.swingingArm==null?InteractionHand.MAIN_HAND:p.swingingArm;
        out.put("swingGeneration",swing==null?0:swing.generation());out.put("swingActive",swing!=null&&swing.active());out.put("swingProgress",swing==null?0f:swing.progress());
        out.put("swingHand",swingHand==InteractionHand.MAIN_HAND?"main":"off");out.put("swingArm",arm(p,swingHand));out.put("ticksAfterAttack",ticksAfterAttack);
        var useHand=p.getUsedItemHand();
        out.put("useHand",useHand==InteractionHand.MAIN_HAND?"main":"off");out.put("useArm",arm(p,useHand));
        out.put("useTicks",p.getTicksUsingItem());out.put("useRemainingTicks",p.getUseItemRemainingTicks());
        out.put("useAnimation",lower(p.getUseItem().getUseAnimation().name()));
        out.put("mainItem",item(p.getMainHandItem()));out.put("offItem",item(p.getOffhandItem()));
        out.put("swingItem",item(p.getItemInHand(swingHand)));out.put("useItem",item(p.getUseItem()));
        out.put("attackStrength",p.getAttackStrengthScale(0));out.put("fallDistance",p.fallDistance);out.put("hurtTime",p.hurtTime);
        out.put("x",p.getX());out.put("y",p.getY());out.put("z",p.getZ());
        return Map.copyOf(out);
    }
    private static String arm(LocalPlayer p,InteractionHand hand){HumanoidArm arm=hand==InteractionHand.MAIN_HAND?p.getMainArm():p.getMainArm().getOpposite();return lower(arm.name());}
    private static String lower(String value){return value.toLowerCase(Locale.ROOT);}
    private static Map<String,Object> item(ItemStack stack){return Map.of("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"empty",stack.isEmpty(),"count",stack.getCount(),"attackType",stack.isEmpty()?"fist":stack.getItem() instanceof SwordItem?"sword":"tool","useAnimation",lower(stack.getUseAnimation().name()),"tags",stack.getTags().map(tag->tag.location().toString()).sorted().toList());}
}
