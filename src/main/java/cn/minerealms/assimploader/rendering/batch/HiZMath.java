package cn.minerealms.assimploader.rendering.batch;

import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** CPU reference for the conservative, conventional OpenGL depth hierarchy. */
public final class HiZMath {
    private HiZMath() {}

    public record Level(int width,int height,float[] depth) {
        public Level {
            if(width<1 || height<1 || depth.length!=Math.multiplyExact(width,height))
                throw new IllegalArgumentException("Invalid Hi-Z level");
        }
    }

    /** All source texels overlapping a destination footprint participate, including NPOT edges. */
    public static Level reduce(Level source,int width,int height) {
        if(width<1 || height<1 || width>source.width || height>source.height)
            throw new IllegalArgumentException("Hi-Z reduction must shrink");
        float[] depth=new float[Math.multiplyExact(width,height)];
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int x0=(int)((long)x*source.width/width),x1=(int)(((long)(x+1)*source.width+width-1)/width);
            int y0=(int)((long)y*source.height/height),y1=(int)(((long)(y+1)*source.height+height-1)/height);
            float maximum=0;
            for(int sy=y0;sy<y1;sy++) for(int sx=x0;sx<x1;sx++) {
                float value=source.depth[sy*source.width+sx];
                maximum=Math.max(maximum,Float.isFinite(value)&&value>=0&&value<=1?value:1);
            }
            depth[y*width+x]=maximum;
        }
        return new Level(width,height,depth);
    }

    public static Level[] pyramid(Level base) {
        int count=32-Integer.numberOfLeadingZeros(Math.max(base.width,base.height));
        Level[] levels=new Level[count];levels[0]=base;
        for(int i=1;i<count;i++) levels[i]=reduce(levels[i-1],Math.max(1,base.width>>i),Math.max(1,base.height>>i));
        return levels;
    }

    /** False means uncertain/visible. Bounds and VP must use the same coordinate origin. */
    public static boolean occluded(float minX,float minY,float minZ,float maxX,float maxY,float maxZ,
            Matrix4fc viewProjection,Level[] levels,float bias) {
        if(levels==null || levels.length==0 || !Float.isFinite(bias) || bias<0 || bias>.1f
                || !finite(minX,minY,minZ,maxX,maxY,maxZ) || minX>maxX || minY>maxY || minZ>maxZ) return false;
        float u0=1,v0=1,u1=0,v1=0,nearest=1;
        Vector4f point=new Vector4f();
        for(int corner=0;corner<8;corner++) {
            point.set((corner&1)==0?minX:maxX,(corner&2)==0?minY:maxY,(corner&4)==0?minZ:maxZ,1);
            viewProjection.transform(point);
            if(!finite(point.x,point.y,point.z,point.w) || point.w<=.00001f
                    || point.z<=-point.w || point.z>=point.w
                    || point.x<=-point.w || point.x>=point.w || point.y<=-point.w || point.y>=point.w) return false;
            float u=point.x/point.w*.5f+.5f,v=point.y/point.w*.5f+.5f;
            u0=Math.min(u0,u);v0=Math.min(v0,v);u1=Math.max(u1,u);v1=Math.max(v1,v);
            nearest=Math.min(nearest,point.z/point.w*.5f+.5f);
        }
        Level base=levels[0];
        u0-=1f/base.width;u1+=1f/base.width;v0-=1f/base.height;v1+=1f/base.height;
        if(u0<=0 || v0<=0 || u1>=1 || v1>=1) return false;
        float extent=Math.max((u1-u0)*base.width,(v1-v0)*base.height);
        int mip=Math.min(levels.length-1,Math.max(0,(int)Math.floor(Math.log(Math.max(1,extent))/Math.log(2))));
        Level level=levels[mip];float maximum=0;
        int x0=Math.max(0,(int)Math.floor(u0*level.width)),x1=Math.min(level.width-1,(int)Math.floor(u1*level.width));
        int y0=Math.max(0,(int)Math.floor(v0*level.height)),y1=Math.min(level.height-1,(int)Math.floor(v1*level.height));
        for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
            float value=level.depth[y*level.width+x];
            if(!Float.isFinite(value) || value<0 || value>1) return false;
            maximum=Math.max(maximum,value);
        }
        return nearest>maximum+bias;
    }

    private static boolean finite(float... values) {for(float value:values) if(!Float.isFinite(value)) return false;return true;}
}
