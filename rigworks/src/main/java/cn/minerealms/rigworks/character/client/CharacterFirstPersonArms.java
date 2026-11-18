package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.MeshDrawSelection;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.RigWorksConfig;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Replaces only native arm geometry. Native map, equip, use and item animation retain their original clock. */
@Mod.EventBusSubscriber(modid=RigWorks.MODID,value=Dist.CLIENT)
public final class CharacterFirstPersonArms {
    private static final BaseAssimpRenderer RENDERER=new BaseAssimpRenderer(){@Override protected String getModelName(){return "rigworks:first_person_arm";}};
    private static final Map<AssimpScene,Map<CharacterProfile,EnumMap<HumanoidArm,Selection>>> CACHE=new WeakHashMap<>();
    private record Selection(MeshDrawSelection draw,boolean empty,int triangles){}
    private static long generation=-1;
    private static long leftDraws,rightDraws;
    private static int leftTriangles,rightTriangles;
    private CharacterFirstPersonArms(){}

    public static boolean render(AbstractClientPlayer player,HumanoidArm arm,PoseStack poses,MultiBufferSource buffers,int light) {
        var minecraft=Minecraft.getInstance();
        if(player!=minecraft.player||player.isInvisible()||!AssimpLoaderConfig.getOrDefault(RigWorksConfig.CLIENT.firstPerson::get,true))return false;
        var view=LocalPlayerCharacter.view(player,minecraft.getFrameTime());if(view==null)return false;
        if(generation!=AssimpModelLoader.getCacheGeneration()){clear();generation=AssimpModelLoader.getCacheGeneration();}
        var arms=CACHE.computeIfAbsent(view.scene(),ignored->new WeakHashMap<>()).computeIfAbsent(view.profile(),ignored->new EnumMap<>(HumanoidArm.class));
        var selection=arms.computeIfAbsent(arm,ignored->selection(view.scene(),view.profile(),arm));
        if(selection.empty())return false;
        var frames=CharacterAttachmentFrames.of(view.profile(),view.scene());
        var frame=frames.arm(view.snapshot(),arm==HumanoidArm.LEFT);
        if(frame==null)return false;
        Matrix4f placement=placement(frame,frames.armScale(arm==HumanoidArm.LEFT),arm==HumanoidArm.LEFT);
        poses.pushPose();
        try {
            poses.mulPoseMatrix(placement);poses.last().normal().mul(new org.joml.Matrix3f(placement).invert().transpose());
            RENDERER.renderSelected(view.scene(),view.animation(),view.snapshot(),selection.draw(),poses,buffers,light,OverlayTexture.NO_OVERLAY,minecraft.getFrameTime());
            if(arm==HumanoidArm.LEFT){leftDraws++;leftTriangles=selection.triangles();}
            else{rightDraws++;rightTriangles=selection.triangles();}
        } finally {poses.popPose();}
        return true;
    }

    /** Native PlayerRenderer arm origins are (-/+5,2,0) pixels, with +Y pointing toward the hand. */
    public static Matrix4f placement(Matrix4f customShoulder,double scale,boolean left) {
        if(!Double.isFinite(scale)||scale<=0)throw new IllegalArgumentException("Invalid arm scale");
        return new Matrix4f().translation(left?5f/16:-5f/16,2f/16,0).scale((float)scale).mul(new Matrix4f(customShoulder).invert());
    }

    private static Selection selection(AssimpScene scene,CharacterProfile profile,HumanoidArm arm) {
        String role=arm==HumanoidArm.LEFT?"left_upper_arm":"right_upper_arm";
        var bone=profile.bone(role);var names=new HashSet<String>();
        if(bone!=null){var root=scene.findNodeByName(bone.node());if(root!=null)descendants(root,names);}
        var selected=new IdentityHashMap<AssimpMesh,int[]>();boolean empty=true;int count=0;
        for(var mesh:scene.getMeshes()) {
            int[] triangles=selectTriangles(mesh,names);
            if(triangles.length>0){selected.put(mesh,triangles);empty=false;count+=triangles.length;}
        }
        return new Selection(new MeshDrawSelection(selected),empty,count);
    }
    private static void descendants(AssimpNode node,Set<String> names){names.add(node.getName());node.getChildren().forEach(child->descendants(child,names));}

    /** All three corners must primarily belong to the selected arm; this excludes torso and opposite limbs. */
    public static int[] selectTriangles(AssimpMesh mesh,Set<String> names) {
        var data=mesh.getSkinnedData();if(data==null||names.isEmpty())return new int[0];
        int vertices=mesh.getVertexCount();if(vertices==0)return new int[0];
        var bones=mesh.getBoneNames();int[] indices=data.getBoneIndices();float[] weights=data.getBoneWeights();
        int stride=weights.length/vertices;boolean[] include=new boolean[vertices];
        for(int vertex=0;vertex<vertices;vertex++) {
            float weight=0;
            for(int slot=0;slot<stride;slot++) {
                int index=indices[vertex*stride+slot];
                if(index>=0&&index<bones.size()&&names.contains(bones.get(index)))weight+=weights[vertex*stride+slot];
            }
            include[vertex]=weight>=.5f;
        }
        int[] source=mesh.getIndices(),out=new int[source.length/3];int count=0;
        for(int triangle=0;triangle<out.length;triangle++) {
            int a=source[triangle*3],b=source[triangle*3+1],c=source[triangle*3+2];
            if(a>=0&&b>=0&&c>=0&&a<vertices&&b<vertices&&c<vertices&&include[a]&&include[b]&&include[c])out[count++]=triangle;
        }
        return java.util.Arrays.copyOf(out,count);
    }

    public static void clear() {
        if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(CharacterFirstPersonArms::clear);return;}
        CACHE.values().forEach(profiles->profiles.values().forEach(arms->arms.values().forEach(value->value.draw().close())));CACHE.clear();
    }
    public static Map<String,Object> diagnostics() {
        return Map.of("leftDraws",leftDraws,"rightDraws",rightDraws,"leftTriangles",leftTriangles,"rightTriangles",rightTriangles);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
}
