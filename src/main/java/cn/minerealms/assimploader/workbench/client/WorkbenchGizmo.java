package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.client.transform.GizmoMath;
import cn.minerealms.assimploader.workbench.model.ModelTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.function.Consumer;

/** Projected handles and picking share the viewport's exact projection and model reference frame. */
public final class WorkbenchGizmo {
    public enum Mode { MOVE, ROTATE, SCALE }
    private static final int[] COLORS={0xffff7777,0xff7de5a4,0xff82b4ff};
    private static final Vector3f[] AXES={new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)};
    private final Matrix4f projection=new Matrix4f(),base=new Matrix4f(),inverse=new Matrix4f(),dragBase=new Matrix4f();
    private final Vector3f pivot=new Vector3f(),dragPivot=new Vector3f(),axis=new Vector3f();
    private final Vector3f rotationStart=new Vector3f();
    private final Quaternionf startRotation=new Quaternionf();
    private final Vector3f[] axes={new Vector3f(),new Vector3f(),new Vector3f()};
    private Mode mode=Mode.MOVE;
    private int x,y,width,height,drag=-1,hover=-1;
    private float length,parameter;
    private double startX,startY;
    private ModelTransform value,start;
    private Consumer<ModelTransform> apply;
    private Runnable begin=()->{},commit=()->{};

    public void mode(Mode mode){if(drag<0)this.mode=mode;}
    public Mode mode(){return mode;}
    public void update(Matrix4f projection,Matrix4f base,ModelTransform value,int x,int y,int width,int height,
                       float length,Consumer<ModelTransform> apply,Runnable begin,Runnable commit) {
        this.projection.set(projection);this.base.set(base);this.value=value;this.x=x;this.y=y;
        this.width=width;this.height=height;this.length=length;this.apply=apply;this.begin=begin;this.commit=commit;
        Matrix4f frame=new Matrix4f(base).mul(value.matrix());frame.getTranslation(pivot);
        for(int i=0;i<3;i++)(mode==Mode.MOVE?base:frame).transformDirection(AXES[i],axes[i]).normalize();
    }
    private GizmoMath.Point project(Vector3f p){return GizmoMath.project(projection,p,width,height);}
    private GizmoMath.Point endpoint(int i){return project(new Vector3f(pivot).fma(length,axes[i]));}
    private GizmoMath.Point ring(int axis,double angle){return project(new Vector3f(pivot).fma((float)Math.cos(angle)*length,axes[(axis+1)%3]).fma((float)Math.sin(angle)*length,axes[(axis+2)%3]));}
    private int pick(double mx,double my){
        var p=project(pivot);if(p==null)return -1;
        if(mode==Mode.SCALE&&Math.hypot(mx-p.x(),my-p.y())<7)return 3;
        int chosen=-1;float distance=7;
        for(int i=0;i<3;i++)if(mode==Mode.ROTATE){
            var a=ring(i,0);
            for(int s=1;s<=48;s++){var b=ring(i,s*Math.PI/24);if(a!=null&&b!=null){float d=GizmoMath.segmentDistance(mx,my,a,b);if(d<distance){distance=d;chosen=i;}}a=b;}
        }else{var e=endpoint(i);if(e!=null){float d=GizmoMath.segmentDistance(mx,my,p,e);if(d<distance){distance=d;chosen=i;}}}
        return chosen;
    }
    public void render(GuiGraphics graphics,int mx,int my){
        if(value==null)return;
        if(drag<0)hover=pick(mx-x,my-y);
        var p=project(pivot);if(p==null)return;
        for(int i=0;i<3;i++){
            int color=hover==i||drag==i?0xffffed99:COLORS[i];
            if(mode==Mode.ROTATE){var a=ring(i,0);for(int s=1;s<=48;s++){var b=ring(i,s*Math.PI/24);if(a!=null&&b!=null)line(graphics,x+a.x(),y+a.y(),x+b.x(),y+b.y(),color);a=b;}}
            else{var e=endpoint(i);if(e!=null){line(graphics,x+p.x(),y+p.y(),x+e.x(),y+e.y(),color);graphics.fill(x+(int)e.x()-3,y+(int)e.y()-3,x+(int)e.x()+4,y+(int)e.y()+4,color);graphics.drawString(Minecraft.getInstance().font,"XYZ".substring(i,i+1),x+(int)e.x()+5,y+(int)e.y()-4,color,true);}}
        }
        graphics.fill(x+(int)p.x()-3,y+(int)p.y()-3,x+(int)p.x()+4,y+(int)p.y()+4,0xffffdc80);
    }
    public boolean click(double mx,double my,int button){
        if(button!=0||value==null)return false;
        drag=pick(mx-x,my-y);if(drag<0)return false;
        start=value;startX=mx;startY=my;dragPivot.set(pivot);dragBase.set(base).invert();inverse.set(projection).invert();
        if(drag<3)axis.set(axes[drag]);
        var ray=GizmoMath.ray(inverse,mx-x,my-y,width,height);
        parameter=drag<3?GizmoMath.axisParameter(ray,dragPivot,axis):0;
        if(mode==Mode.ROTATE){
            var point=GizmoMath.plane(ray,dragPivot,axis);
            if(point==null||point.sub(dragPivot).lengthSquared()<1e-8f){drag=-1;return false;}
            rotationStart.set(point).normalize();
            startRotation.rotationXYZ((float)Math.toRadians(start.rx()),(float)Math.toRadians(start.ry()),(float)Math.toRadians(start.rz()));
        }
        begin.run();return true;
    }
    public boolean drag(double mx,double my){
        if(drag<0)return false;
        if(mode==Mode.MOVE){
            float delta=GizmoMath.axisParameter(GizmoMath.ray(inverse,mx-x,my-y,width,height),dragPivot,axis)-parameter;
            if(!Float.isFinite(delta))return true;
            Vector3f local=dragBase.transformDirection(new Vector3f(axis).mul(delta));
            apply.accept(start.withPosition(start.x()+local.x,start.y()+local.y,start.z()+local.z));
        }else if(mode==Mode.ROTATE){
            var point=GizmoMath.plane(GizmoMath.ray(inverse,mx-x,my-y,width,height),dragPivot,axis);
            if(point==null||point.sub(dragPivot).lengthSquared()<1e-8f)return true;
            float angle=GizmoMath.signedAngle(rotationStart,point.normalize(),axis);
            var euler=new Quaternionf(startRotation).rotateAxis(angle,AXES[drag]).getEulerAnglesXYZ(new Vector3f());
            apply.accept(start.withRotation((float)Math.toDegrees(euler.x),(float)Math.toDegrees(euler.y),(float)Math.toDegrees(euler.z)));
        }else{
            float factor=(float)Math.exp(Math.max(-8,Math.min(8,((mx-startX)-(my-startY))*.012)));
            apply.accept(start.withScale(clamp(start.sx()*(drag==0||drag==3?factor:1)),clamp(start.sy()*(drag==1||drag==3?factor:1)),clamp(start.sz()*(drag==2||drag==3?factor:1))));
        }
        return true;
    }
    public boolean release(){if(drag<0)return false;drag=-1;commit.run();return true;}
    public void cancel(){drag=-1;}
    public String tooltip(){int i=drag>=0?drag:hover;return i<0?"":(i==3?"Uniform":"XYZ".substring(i,i+1))+" "+mode.name().toLowerCase(java.util.Locale.ROOT);}
    private static float clamp(float v){return Math.copySign(Math.max(.0001f,Math.min(10000,Math.abs(v))),v);}
    public static void line(GuiGraphics g,float x1,float y1,float x2,float y2,int color){
        int count=Math.max(1,(int)Math.ceil(Math.hypot(x2-x1,y2-y1)));
        if(count>8192)return;
        for(int i=0;i<=count;i++){int x=Math.round(x1+(x2-x1)*i/count),y=Math.round(y1+(y2-y1)*i/count);g.fill(x,y,x+1,y+1,color);}
    }
}
