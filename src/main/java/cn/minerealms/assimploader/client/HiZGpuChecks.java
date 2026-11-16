package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.rendering.batch.HiZPyramid;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

import java.util.Arrays;

/** Small readbacks confined to the explicit GPU acceptance harness. */
final class HiZGpuChecks {
    private HiZGpuChecks() {}
    static void verify() throws Exception {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),textureBinding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),
                storage=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int texture=GL11.glGenTextures(),bounds=GL15.glGenBuffers(),visibility=GL15.glGenBuffers();
        try(var pyramid=new HiZPyramid(64,1024*1024)) {
            int width=63,height=37;float[] depth=new float[width*height];Arrays.fill(depth,1);
            for(int y=0;y<height;y++) for(int x=0;x<width/2;x++) depth[y*width+x]=.25f;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,width,height,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            // Hidden, in front, sky, wall edge, near plane, screen edge, far plane, invalid.
            float[] boxes={
                    -.8f,-.2f,.5f,0,-.6f,.2f,.7f,0,
                    -.8f,-.2f,-.8f,0,-.6f,.2f,-.6f,0,
                    .5f,-.2f,.5f,0,.7f,.2f,.7f,0,
                    -.1f,-.2f,.5f,0,.1f,.2f,.7f,0,
                    -.8f,-.2f,-1.1f,0,-.6f,.2f,.7f,0,
                    -1.1f,-.2f,.5f,0,-.7f,.2f,.7f,0,
                    -.8f,-.2f,.5f,0,-.6f,.2f,1.1f,0,
                    Float.NaN,-.2f,.5f,0,-.6f,.2f,.7f,0};
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,bounds);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,boxes,GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,visibility);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,32,GL15.GL_DYNAMIC_DRAW);
            require(pyramid.build(texture,width,height,0,42),"NPOT depth build failed");
            require(pyramid.classify(bounds,visibility,8,new Matrix4f(),.0005f,42),"classification failed");
            int[] actual=new int[8];GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,visibility);GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,actual);
            require(Arrays.equals(new int[]{0,1,1,1,1,1,1,1},actual),"conservative edge mismatch "+Arrays.toString(actual));
            require(!pyramid.classify(bounds,visibility,8,new Matrix4f(),0,43),"stale depth accepted");
            require(!pyramid.build(texture,width,height,4,43),"multisample depth accepted");
            require(!pyramid.classify(bounds,visibility,8,new Matrix4f(),0,42),"failed build retained previous frame");
            require(pyramid.build(texture,width,height,0,44),"rebuild failed");
            Arrays.fill(depth,1);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,37,63,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);
            require(pyramid.build(texture,37,63,0,45),"resize failed");
            require(pyramid.classify(bounds,visibility,8,new Matrix4f(),0,45),"resized classification failed");
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,visibility);GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,actual);
            require(Arrays.stream(actual).allMatch(value->value==1),"empty depth hid geometry");
            AssimpLoader.LOGGER.info("[BatchSmoke] HiZ GPU NPOT, wall/sky, front/near/far/screen/nonfinite, stale token, failed build and resize checks passed");
        } finally {
            GL15.glDeleteBuffers(bounds);GL15.glDeleteBuffers(visibility);GL11.glDeleteTextures(texture);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,storage);GL13.glActiveTexture(active);GL11.glBindTexture(GL11.GL_TEXTURE_2D,textureBinding);
        }
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"HiZ checks leaked a GL error");
    }
    private static void require(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
}
