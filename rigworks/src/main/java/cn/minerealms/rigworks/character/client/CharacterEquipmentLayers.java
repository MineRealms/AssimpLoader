package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.mixin.character.LivingRendererLayersAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reuses native and compatible mod equipment layers with a render-only humanoid adapter. */
public final class CharacterEquipmentLayers {
    private static Adapter standard,slim;
    private CharacterEquipmentLayers(){}
    public static void clear(){standard=null;slim=null;CharacterAttachmentFrames.clear();}

    @SuppressWarnings({"unchecked","rawtypes"})
    public static void render(AbstractClientPlayer player,CharacterProfile profile,CharacterEquipmentDefinition equipment,
                              PoseSnapshot snapshot,PoseStack poses,MultiBufferSource buffers,int light,float partialTick) {
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if(!(renderer instanceof LivingRendererLayersAccess access))return;
        boolean narrow="slim".equals(player.getModelName());
        Adapter adapter=narrow?slim:standard;
        if(adapter==null) {
            adapter=new Adapter(Minecraft.getInstance().getEntityModels().bakeLayer(narrow?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),narrow);
            if(narrow)slim=adapter;else standard=adapter;
        }
        adapter.pose(player,profile,equipment,snapshot);
        var previous=access.rigworks$getLayerModel();
        access.rigworks$setLayerModel((net.minecraft.client.model.EntityModel)adapter);
        try {
            float limb=player.walkAnimation.position(partialTick),speed=player.walkAnimation.speed(partialTick);
            float age=player.tickCount+partialTick,headYaw=Mth.rotLerp(partialTick,player.yHeadRotO,player.yHeadRot)-Mth.rotLerp(partialTick,player.yBodyRotO,player.yBodyRot);
            for(var layer:access.rigworks$getLayers()) {
                if(layer instanceof ItemInHandLayer)continue;
                poses.pushPose();
                try {
                    // These layers use literal native coordinates instead of the adapter's ModelParts.
                    Object nativeLayer=layer;
                    if(nativeLayer instanceof ElytraLayer || nativeLayer instanceof CapeLayer
                            || nativeLayer instanceof net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer
                            || nativeLayer instanceof net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer) adapter.back(poses);
                    layer.render(poses,buffers,light,player,limb,speed,partialTick,age,headYaw,player.getViewXRot(partialTick));
                } finally {poses.popPose();}
            }
        } finally {access.rigworks$setLayerModel(previous);}
    }

    private static final class Adapter extends PlayerModel<AbstractClientPlayer> {
        private Matrix4f back=new Matrix4f();
        Adapter(ModelPart root,boolean slim){super(root,slim);}
        void pose(AbstractClientPlayer player,CharacterProfile profile,CharacterEquipmentDefinition equipment,PoseSnapshot snapshot) {
            setAllVisible(true);young=false;riding=player.isPassenger();crouching=player.isCrouching();attackTime=player.getAttackAnim(1);
            var frames=CharacterAttachmentFrames.of(profile,snapshot.getScene());
            apply(head,selected(equipment,"head",frames.part(snapshot,"head"),snapshot));hat.copyFrom(head);
            Matrix4f torso=frames.body(snapshot);apply(body,torso);jacket.copyFrom(body);
            apply(leftArm,frames.part(snapshot,"left_upper_arm"));leftSleeve.copyFrom(leftArm);
            apply(rightArm,frames.part(snapshot,"right_upper_arm"));rightSleeve.copyFrom(rightArm);
            apply(leftLeg,frames.part(snapshot,"left_thigh"));leftPants.copyFrom(leftLeg);
            apply(rightLeg,frames.part(snapshot,"right_thigh"));rightPants.copyFrom(rightLeg);
            float width=fit(frames.length("left_upper_arm","right_upper_arm",.625f)/.625f);
            float height=fit(frames.length("hips","head",.75f)/.75f);
            body.xScale=width;body.yScale=height;jacket.copyFrom(body);
            leftArm.yScale=fit(frames.length("left_upper_arm","left_hand",.75f)/.75f);leftSleeve.copyFrom(leftArm);
            rightArm.yScale=fit(frames.length("right_upper_arm","right_hand",.75f)/.75f);rightSleeve.copyFrom(rightArm);
            leftLeg.yScale=fit(frames.length("left_thigh","left_foot",.75f)/.75f);leftPants.copyFrom(leftLeg);
            rightLeg.yScale=fit(frames.length("right_thigh","right_foot",.75f)/.75f);rightPants.copyFrom(rightLeg);
            back=selected(equipment,"back",torso,snapshot);if(back==null)back=new Matrix4f();
        }
        private static Matrix4f selected(CharacterEquipmentDefinition equipment,String name,Matrix4f fallback,PoseSnapshot snapshot) {
            var socket=equipment.socket(name);return socket==null?fallback:socket.transform(snapshot);
        }
        void back(PoseStack stack){stack.mulPoseMatrix(back);stack.last().normal().mul(new org.joml.Matrix3f(back).invert().transpose());}
        private static float fit(float value){return Mth.clamp(value,.1f,8);}
        private static void apply(ModelPart part,Matrix4f frame) {
            part.resetPose();part.visible=frame!=null;
            if(frame==null)return;
            var position=frame.getTranslation(new Vector3f());var angles=frame.getEulerAnglesZYX(new Vector3f());
            part.setPos(position.x*16,position.y*16,position.z*16);part.setRotation(angles.x,angles.y,angles.z);
            part.xScale=1;part.yScale=1;part.zScale=1;
        }
    }
}
