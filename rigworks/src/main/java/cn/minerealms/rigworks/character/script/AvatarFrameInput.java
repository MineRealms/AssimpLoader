package cn.minerealms.rigworks.character.script;

import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Builds plain JSON observations for a local or remote owner. Never passes the player to JavaScript. */
public final class AvatarFrameInput {
    private static final Gson JSON=new Gson();
    private long lastSwing=-1,lastAttack=-1;
    public JsonObject capture(AbstractClientPlayer player,CharacterProfile profile,PlayerVisualInput input) {
        var values=new LinkedHashMap<String,Object>();
        values.put("tick",input.sequence());values.put("time",input.timeSeconds());values.put("delta",.05);
        values.put("forwardVelocity",input.forwardVelocity());values.put("strafeVelocity",input.strafeVelocity());
        values.put("verticalVelocity",input.verticalVelocity());values.put("speed",input.horizontalSpeed());
        values.put("bodyYaw",input.bodyYawDegrees());values.put("headYaw",input.headYawDegrees());values.put("headPitch",input.headPitchDegrees());
        values.put("headYawRelative",net.minecraft.util.Mth.wrapDegrees(input.headYawDegrees()-input.bodyYawDegrees()));
        values.put("onGround",input.onGround());values.put("crouching",input.crouching());values.put("sprinting",input.sprinting());
        values.put("jumping",input.jumping());values.put("falling",input.falling());values.put("swimming",input.swimming());
        values.put("flying",input.flying());values.put("elytra",input.elytra());values.put("riding",input.riding());
        values.put("sleeping",input.sleeping());values.put("dead",input.dead());values.put("usingItem",input.usingItem());
        values.put("climbing",player.onClimbable());values.put("inWater",player.isInWater());values.put("underWater",player.isUnderWater());values.put("inLava",player.isInLava());
        var action=input.actionState();values.put("pose",action.pose());values.put("airPhase",action.airPhaseName());
        values.put("crawling",input.crawling());values.put("riptide",action.riptide());values.put("frozen",action.frozen());
        values.put("takingOff",action.airPhase()==cn.minerealms.rigworks.character.core.PlayerActionState.AirPhase.TAKEOFF);
        values.put("landing",action.airPhase()==cn.minerealms.rigworks.character.core.PlayerActionState.AirPhase.LANDING);
        values.put("primaryArm",lower(player.getMainArm().name()));
        var swing=input.swingAction();
        if(swing!=null&&swing.active()&&swing.generation()!=lastSwing){lastSwing=swing.generation();lastAttack=input.sequence();}
        values.put("swingGeneration",swing==null?0:swing.generation());values.put("swingActive",swing!=null&&swing.active());values.put("swingProgress",swing==null?0:swing.progress());
        var swingHand=player.swingingArm==null?InteractionHand.MAIN_HAND:player.swingingArm;
        values.put("swingHand",swingHand==InteractionHand.MAIN_HAND?"main":"off");values.put("swingArm",arm(player,swingHand));
        values.put("ticksAfterAttack",lastAttack<0?1_000_000:Math.max(0,input.sequence()-lastAttack));
        var useHand=player.getUsedItemHand();values.put("useHand",useHand==InteractionHand.MAIN_HAND?"main":"off");values.put("useArm",arm(player,useHand));
        values.put("useTicks",player.getTicksUsingItem());values.put("useRemainingTicks",player.getUseItemRemainingTicks());values.put("useAnimation",lower(player.getUseItem().getUseAnimation().name()));
        values.put("mainItem",item(player.getMainHandItem()));values.put("offItem",item(player.getOffhandItem()));values.put("swingItem",item(player.getItemInHand(swingHand)));values.put("useItem",item(player.getUseItem()));
        values.put("attackStrength",player.getAttackStrengthScale(0));values.put("fallDistance",player.fallDistance);values.put("hurtTime",player.hurtTime);
        values.put("x",player.getX());values.put("y",player.getY());values.put("z",player.getZ());
        var bones=new LinkedHashMap<String,String>();profile.humanoid().forEach((role,bone)->bones.put(role,bone.node()));
        values.put("profile",Map.of("model",profile.model(),"scale",profile.scale(),"modelYawDegrees",profile.modelYawDegrees(),"animations",profile.animations(),"bones",bones));
        return JSON.toJsonTree(values).getAsJsonObject();
    }
    public void reset(){lastSwing=-1;lastAttack=-1;}
    private static String arm(AbstractClientPlayer player,InteractionHand hand){return lower((hand==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite()).name());}
    private static String lower(String value){return value.toLowerCase(Locale.ROOT);}
    private static Map<String,Object> item(ItemStack stack){return Map.of("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"empty",stack.isEmpty(),"count",stack.getCount(),
            "attackType",stack.isEmpty()?"fist":stack.getItem() instanceof SwordItem?"sword":"tool","useAnimation",lower(stack.getUseAnimation().name()),
            "chargedCrossbow",stack.getItem() instanceof CrossbowItem&&CrossbowItem.isCharged(stack),"tags",stack.getTags().map(tag->tag.location().toString()).sorted().toList());}
}
