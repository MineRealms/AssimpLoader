package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.transform.EditableTransform;
import cn.minerealms.assimploader.client.transform.GizmoMath;
import cn.minerealms.assimploader.compat.OculusCompat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Locale;

/** Projected world gizmos; picking and drag constraints use the same camera-relative matrices as rendering. */
@Mod.EventBusSubscriber(modid = "rigworks", value = Dist.CLIENT)
public final class EquipmentTransformGizmo {
    public enum Mode { TRANSLATE, ROTATE, SCALE }
    private static EquipmentTransformGizmo current;
    private static final int[] COLORS = {0xffff7070, 0xff70e497, 0xff78acff};
    private static final Vector3f[] BASIS = {new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)};
    private final CharacterEquipmentEditorSession session;
    private final Matrix4f combined = new Matrix4f(), inverse = new Matrix4f(), bone = new Matrix4f();
    private final Vector3f origin = new Vector3f(), wrist = new Vector3f();
    private final Vector3f[] axes = {new Vector3f(),new Vector3f(),new Vector3f()};
    private final Matrix4f dragBone = new Matrix4f(), dragInverse = new Matrix4f();
    private final Vector3f dragOrigin = new Vector3f(), dragAxis = new Vector3f();
    private Vec3 camera = Vec3.ZERO;
    private HumanoidArm hand = HumanoidArm.RIGHT;
    private Mode mode = Mode.TRANSLATE;
    private Runnable listener = () -> {};
    private boolean ready;
    private int width, height, hover = -1, dragging = -1;
    private float length = .35f, startParameter, previousAngle, totalAngle;
    private double startX, startY;
    private Vector3f startVector;
    private EditableTransform start;

    public EquipmentTransformGizmo(CharacterEquipmentEditorSession session) { this.session=session;current=this; }
    public void hand(HumanoidArm arm) {
        if (hand != arm) { EquipmentItemOutline.clear(); ready=false; }
        hand=arm;hover=-1;
    }
    public void mode(Mode mode) { if(dragging<0){if(this.mode!=mode)ready=false;this.mode=mode;hover=-1;} }
    public Mode mode() { return mode; }
    public void changeListener(Runnable callback) { listener=java.util.Objects.requireNonNull(callback); }
    public void close() { if(current==this)current=null;EquipmentItemOutline.clear();ready=false; }
    static HumanoidArm selectedHand() { return current!=null&&current.session.valid()&&CharacterWorkbenchPose.frozen()?current.hand:null; }

    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if(current==null||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||OculusCompat.isRenderingShadowPass())return;
        current.updateProjection(event);
    }

    private void updateProjection(RenderLevelStageEvent event) {
        ready=false;
        if(!session.valid()||!session.available(hand))return;
        var minecraft=Minecraft.getInstance();
        var player=minecraft.player;
        var mount=LocalPlayerCharacter.equipmentTransform(hand,event.getPartialTick());
        var wristFrame=LocalPlayerCharacter.equipmentBoneTransform(hand,event.getPartialTick());
        if(player==null||mount==null||wristFrame==null)return;
        camera=event.getCamera().getPosition();
        float partial=event.getPartialTick();
        var anchor=CharacterWorkbenchPose.position(player,partial);
        var offset=new Vector3f((float)(anchor.x-camera.x),(float)(anchor.y-camera.y),(float)(anchor.z-camera.z));
        bone.set(wristFrame);
        wristFrame.getTranslation(wrist).add(offset);
        mount.getTranslation(origin).add(offset);
        var orientation=mode==Mode.TRANSLATE?bone:mount;
        for(int i=0;i<3;i++)orientation.transformDirection(BASIS[i],axes[i]).normalize();
        width=minecraft.getWindow().getGuiScaledWidth();height=minecraft.getWindow().getGuiScaledHeight();
        combined.set(event.getProjectionMatrix()).mul(event.getPoseStack().last().pose());
        if(!combined.isFinite()||Math.abs(combined.determinant())<1e-10)return;
        inverse.set(combined).invert();
        var center=project(origin);
        if(center==null)return;
        length=Mth.clamp((float)(origin.length()*2/Math.abs(event.getProjectionMatrix().m11())*64/height),.025f,2);
        ready=true;
    }

    private GizmoMath.Point project(Vector3f p) { return GizmoMath.project(combined,p,width,height); }
    private GizmoMath.Point endpoint(int axis) { return project(new Vector3f(origin).fma(length,axes[axis])); }
    GizmoMath.Point screenHandle(int axis) { return ready?endpoint(axis):null; }
    GizmoMath.Point screenPivot() { return ready?project(origin):null; }
    GizmoMath.Point screenRing(int axis, double angle) { return ready?ring(axis,angle):null; }

    public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        if(!ready)return;
        EquipmentItemOutline.render(graphics);
        if(dragging<0)hover=pick(mouseX,mouseY);
        var center=project(origin);var bind=project(wrist);
        if(center==null)return;
        var font=Minecraft.getInstance().font;
        int wristLabelY=bind==null?0:(int)bind.y()-15;
        if(bind!=null){
            line(graphics,bind.x()-4,bind.y(),bind.x()+4,bind.y(),0xff6fe2ff,1);
            line(graphics,bind.x(),bind.y()-4,bind.x(),bind.y()+4,0xff6fe2ff,1);
            line(graphics,bind.x(),bind.y(),center.x(),center.y(),0xbb7fc8ce,1);
            graphics.drawString(font,"Wrist",(int)bind.x()-font.width("Wrist")-8,wristLabelY,0xff9eefff,true);
        }
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            line(graphics,center.x()+(float)Math.cos(a)*5,center.y()+(float)Math.sin(a)*5,
                    center.x()+(float)Math.cos(b)*5,center.y()+(float)Math.sin(b)*5,0xffffd779,2);
        }
        int pivotLabelY=(int)center.y()+8;
        if(bind!=null&&Math.abs(pivotLabelY-wristLabelY)<font.lineHeight+3)pivotLabelY=wristLabelY+font.lineHeight+3;
        graphics.drawString(font,"Pivot",(int)center.x()+9,pivotLabelY,0xffffd779,true);
        for(int axis=0;axis<3;axis++){
            int color=(dragging==axis||hover==axis)?0xffffff9a:COLORS[axis];
            if(mode==Mode.ROTATE){
                GizmoMath.Point previous=null;
                for(int step=0;step<=64;step++){
                    var point=ring(axis,step*Math.PI/32);
                    if(previous!=null&&point!=null)line(graphics,previous.x(),previous.y(),point.x(),point.y(),color,hover==axis?2:1);
                    previous=point;
                }
            }else{
                var end=endpoint(axis);if(end==null)continue;
                line(graphics,center.x(),center.y(),end.x(),end.y(),color,2);
                if(mode==Mode.TRANSLATE){
                    float dx=end.x()-center.x(),dy=end.y()-center.y(),d=(float)Math.hypot(dx,dy);
                    if(d>1){dx/=d;dy/=d;line(graphics,end.x(),end.y(),end.x()-dx*8-dy*3,end.y()-dy*8+dx*3,color,2);
                        line(graphics,end.x(),end.y(),end.x()-dx*8+dy*3,end.y()-dy*8-dx*3,color,2);}
                }else box(graphics,end.x(),end.y(),3,color);
                graphics.drawString(Minecraft.getInstance().font,"XYZ".substring(axis,axis+1),(int)end.x()+4,(int)end.y()-4,color,true);
            }
        }
        if(mode==Mode.SCALE)box(graphics,center.x(),center.y(),5,hover==3||dragging==3?0xffffff9a:0xffffd779);
    }

    private GizmoMath.Point ring(int axis,double angle){
        return project(new Vector3f(origin).fma((float)Math.cos(angle)*length,axes[(axis+1)%3])
                .fma((float)Math.sin(angle)*length,axes[(axis+2)%3]));
    }

    private int pick(double x,double y){
        var center=project(origin);if(center==null)return -1;
        if(mode==Mode.SCALE&&Math.hypot(x-center.x(),y-center.y())<=8)return 3;
        int selected=-1;float nearest=7;
        for(int axis=0;axis<3;axis++){
            if(mode==Mode.ROTATE){
                var previous=ring(axis,0);
                for(int i=1;i<=64;i++){
                    var next=ring(axis,i*Math.PI/32);
                    if(previous!=null&&next!=null){float distance=GizmoMath.segmentDistance(x,y,previous,next);if(distance<nearest){nearest=distance;selected=axis;}}
                    previous=next;
                }
            }else{
                var end=endpoint(axis);if(end==null)continue;
                // Keep the pivot distinct from the three axis constraints.
                var beginning=new GizmoMath.Point(center.x()+(end.x()-center.x())*.18f,center.y()+(end.y()-center.y())*.18f,center.depth());
                float distance=GizmoMath.segmentDistance(x,y,beginning,end);
                if(distance<nearest){nearest=distance;selected=axis;}
            }
        }
        return selected;
    }

    public boolean mouseClicked(double x,double y,int button){
        if(button!=0||!ready)return false;
        int selected=pick(x,y);if(selected<0)return false;
        dragging=selected;start=session.get(hand);startX=x;startY=y;
        dragOrigin.set(origin);dragBone.set(bone).invert();dragInverse.set(inverse);
        previousAngle=totalAngle=0;
        if(selected<3)dragAxis.set(axes[selected]);
        var ray=GizmoMath.ray(dragInverse,x,y,width,height);
        startParameter=selected<3?GizmoMath.axisParameter(ray,dragOrigin,dragAxis):0;
        if(mode==Mode.ROTATE){
            var point=GizmoMath.plane(ray,dragOrigin,dragAxis);
            if(point==null){dragging=-1;return false;}
            startVector=point.sub(dragOrigin).normalize();
        }
        return true;
    }

    public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(dragging<0||button!=0)return false;
        var ray=GizmoMath.ray(dragInverse,x,y,width,height);
        EditableTransform value=start;
        if(mode==Mode.TRANSLATE){
            float parameter=GizmoMath.axisParameter(ray,dragOrigin,dragAxis);
            float delta=parameter-startParameter;
            if(!Float.isFinite(delta)){
                var a=GizmoMath.project(new Matrix4f(dragInverse).invert(),dragOrigin,width,height);
                var b=GizmoMath.project(new Matrix4f(dragInverse).invert(),new Vector3f(dragOrigin).fma(length,dragAxis),width,height);
                if(a==null||b==null)return true;
                double sx=b.x()-a.x(),sy=b.y()-a.y(),squared=sx*sx+sy*sy;
                if(squared<4)return true;
                delta=(float)(((x-startX)*sx+(y-startY)*sy)/squared*length);
            }
            var local=dragBone.transformDirection(new Vector3f(dragAxis).mul(delta));
            value=new EditableTransform(Mth.clamp(start.x()+local.x,-10,10),Mth.clamp(start.y()+local.y,-10,10),
                    Mth.clamp(start.z()+local.z,-10,10),start.rx(),start.ry(),start.rz(),start.scale());
        }else if(mode==Mode.ROTATE){
            var point=GizmoMath.plane(ray,dragOrigin,dragAxis);if(point==null)return true;
            var vector=point.sub(dragOrigin);if(vector.lengthSquared()<1e-8)return true;
            float angle=GizmoMath.signedAngle(startVector,vector.normalize(),dragAxis);
            totalAngle+=Mth.wrapDegrees((float)Math.toDegrees(angle-previousAngle))*(float)Math.PI/180;previousAngle=angle;
            var rotation=start.quaternion().mul(new org.joml.Quaternionf().rotationAxis(totalAngle,BASIS[dragging]));
            value=EditableTransform.from(start.x(),start.y(),start.z(),rotation,start.scale());
        }else{
            float factor=(float)Math.exp(Mth.clamp((float)((x-startX)-(y-startY))*.01f,-8,8));
            value=new EditableTransform(start.x(),start.y(),start.z(),start.rx(),start.ry(),start.rz(),Mth.clamp(start.scale()*factor,.001f,10));
        }
        session.preview(hand,value);listener.run();return true;
    }

    public boolean mouseReleased(double x,double y,int button){if(dragging<0||button!=0)return false;dragging=-1;return true;}

    public String tooltip(){
        int axis=dragging>=0?dragging:hover;if(axis<0)return "";
        var value=session.get(hand);String label=axis==3?"Uniform":"XYZ".substring(axis,axis+1);
        return switch(mode){
            case TRANSLATE->String.format(Locale.ROOT,"Translate %s / bone local\n%.4f m",label,axis==0?value.x():axis==1?value.y():value.z());
            case ROTATE->dragging>=0
                    ?String.format(Locale.ROOT,"Rotate %s / item local\nDelta %.2f deg",label,(double)Math.toDegrees(totalAngle))
                    :String.format(Locale.ROOT,"Rotate %s / item local\nEuler %.2f deg",label,axis==0?value.rx():axis==1?value.ry():value.rz());
            case SCALE->String.format(Locale.ROOT,"Uniform scale\n%.4f",value.scale());
        };
    }

    static void line(GuiGraphics graphics,float x,float y,float endX,float endY,int color,int thickness){
        float length=(float)Math.hypot(endX-x,endY-y);if(!Float.isFinite(length)||length<.1||length>10000)return;
        var poses=graphics.pose();poses.pushPose();poses.translate(x,y,0);poses.mulPose(Axis.ZP.rotation((float)Math.atan2(endY-y,endX-x)));
        graphics.fill(0,-thickness/2,Math.max(1,(int)Math.ceil(length)),Math.max(1,(thickness+1)/2),color);poses.popPose();
    }
    private static void box(GuiGraphics g,float x,float y,int size,int color){
        line(g,x-size,y-size,x+size,y-size,color,2);line(g,x+size,y-size,x+size,y+size,color,2);
        line(g,x+size,y+size,x-size,y+size,color,2);line(g,x-size,y+size,x-size,y-size,color,2);
    }
}
