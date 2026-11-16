package cn.minerealms.assimploader.rendering.batch;

import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Optional current-frame depth hierarchy. Unsupported or uncertain inputs leave instances visible. */
public final class HiZPyramid implements AutoCloseable {
    public static final int MAX_DIMENSION=1024,BOUNDS_STRIDE=32;
    public static final long MAX_BYTES=8L*1024*1024;
    private final int maximumDimension;
    private final long budgetBytes;
    private int reduceProgram,cullProgram,sampler,texture,width,height,levels;
    private long builtFrame=Long.MIN_VALUE,bytes,builds,classifications;

    public static boolean available() {return GL.getCapabilities().OpenGL43;}

    public HiZPyramid(int maximumDimension,long budgetBytes) throws IOException {
        RenderSystem.assertOnRenderThread();
        if(!available() || maximumDimension<1 || maximumDimension>MAX_DIMENSION || budgetBytes<4 || budgetBytes>MAX_BYTES)
            throw new IllegalArgumentException("Unsupported Hi-Z capacity");
        this.maximumDimension=maximumDimension;this.budgetBytes=budgetBytes;
        try {
            reduceProgram=compile("batch_hiz_reduce.comp");cullProgram=compile("batch_hiz_cull.comp");
            sampler=GL33.glGenSamplers();
            GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
            GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL33.glSamplerParameteri(sampler,GL14.GL_TEXTURE_COMPARE_MODE,GL11.GL_NONE);
            GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        } catch(IOException|RuntimeException failure) {close();throw failure;}
    }

    public record Statistics(int width,int height,int levels,long bytes,long builds,long classifications,long frame) {}
    public Statistics statistics() {return new Statistics(width,height,levels,bytes,builds,classifications,builtFrame);}

    /**
     * The caller guarantees this texture contains opaque depth from the same camera and frame.
     * Multisample/reversed-depth/shader-pack targets must be rejected by the caller. A failed build
     * invalidates the frame, including an earlier successful build, so stale depth is never reused.
     */
    public boolean build(int depthTexture,int sourceWidth,int sourceHeight,int samples,long frameToken) {
        RenderSystem.assertOnRenderThread();builtFrame=Long.MIN_VALUE;
        if(reduceProgram==0 || depthTexture==0 || sourceWidth<1 || sourceHeight<1 || sourceWidth>16384
                || sourceHeight>16384 || samples>1 || samples<0 || frameToken==Long.MIN_VALUE || !GL11.glIsTexture(depthTexture)) return false;
        int baseWidth=Math.min(sourceWidth,maximumDimension),baseHeight=Math.min(sourceHeight,maximumDimension);
        if(sourceWidth>baseWidth*32 || sourceHeight>baseHeight*32) return false;
        long required=requiredBytes(baseWidth,baseHeight);
        if(required>budgetBytes) return false;
        try(State ignored=new State()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depthTexture);
            if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=sourceWidth
                    || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT)!=sourceHeight
                    || !depthFormat(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT))) return false;
            if(texture==0 || width!=baseWidth || height!=baseHeight) allocate(baseWidth,baseHeight);
            GL20.glUseProgram(reduceProgram);GL33.glBindSampler(0,sampler);
            GL20.glUniform1i(GL20.glGetUniformLocation(reduceProgram,"sourceDepth"),0);
            GL42.glMemoryBarrier(GL42.GL_TEXTURE_FETCH_BARRIER_BIT|GL42.GL_FRAMEBUFFER_BARRIER_BIT);
            int previousWidth=sourceWidth,previousHeight=sourceHeight;
            for(int level=0;level<levels;level++) {
                int currentWidth=Math.max(1,width>>level),currentHeight=Math.max(1,height>>level);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,level==0?depthTexture:texture);
                GL20.glUniform1i(GL20.glGetUniformLocation(reduceProgram,"sourceLevel"),level==0?0:level-1);
                GL20.glUniform2i(GL20.glGetUniformLocation(reduceProgram,"sourceSize"),previousWidth,previousHeight);
                GL20.glUniform2i(GL20.glGetUniformLocation(reduceProgram,"destinationSize"),currentWidth,currentHeight);
                GL42.glBindImageTexture(0,texture,level,false,0,GL15.GL_WRITE_ONLY,GL30.GL_R32F);
                GL43.glDispatchCompute((currentWidth+7)/8,(currentHeight+7)/8,1);
                GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT|GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
                previousWidth=currentWidth;previousHeight=currentHeight;
            }
            builtFrame=frameToken;builds++;return true;
        }
    }

    /**
     * Bounds: tightly packed std430 {vec4 minimum;vec4 maximum;} in VP coordinates.
     * Output: one uint per instance, 1 visible / 0 occluded. No CPU readback or indirect draw mutation.
     * A false return leaves output unspecified; callers must render all instances in that case.
     */
    public boolean classify(int boundsBuffer,int visibilityBuffer,int instanceCount,Matrix4fc viewProjection,float depthBias,long frameToken) {
        RenderSystem.assertOnRenderThread();
        if(cullProgram==0 || texture==0 || builtFrame==Long.MIN_VALUE || frameToken!=builtFrame || boundsBuffer==0
                || visibilityBuffer==0 || boundsBuffer==visibilityBuffer || instanceCount<1 || instanceCount>65536
                || !Float.isFinite(depthBias) || depthBias<0 || depthBias>.1f || viewProjection==null || !viewProjection.isFinite()) return false;
        try(State ignored=new State()) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,boundsBuffer);
            if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)<(long)instanceCount*BOUNDS_STRIDE) return false;
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,visibilityBuffer);
            if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)<(long)instanceCount*4) return false;
            GL20.glUseProgram(cullProgram);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL33.glBindSampler(0,sampler);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,boundsBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,visibilityBuffer);
            GL20.glUniform1i(GL20.glGetUniformLocation(cullProgram,"hierarchy"),0);
            GL30.glUniform1ui(GL20.glGetUniformLocation(cullProgram,"instanceCount"),instanceCount);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(cullProgram,"viewProjection"),false,viewProjection.get(new float[16]));
            GL20.glUniform2i(GL20.glGetUniformLocation(cullProgram,"baseSize"),width,height);
            GL20.glUniform1i(GL20.glGetUniformLocation(cullProgram,"levelCount"),levels);
            GL20.glUniform1f(GL20.glGetUniformLocation(cullProgram,"depthBias"),depthBias);
            GL43.glDispatchCompute((instanceCount+63)/64,1,1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            classifications++;return true;
        }
    }

    public static long requiredBytes(int width,int height) {
        if(width<1 || height<1 || width>MAX_DIMENSION || height>MAX_DIMENSION) throw new IllegalArgumentException("Invalid Hi-Z size");
        long count=0;
        for(int level=0;level<32-Integer.numberOfLeadingZeros(Math.max(width,height));level++)
            count+=(long)Math.max(1,width>>level)*Math.max(1,height>>level)*4;
        return count;
    }

    private void allocate(int newWidth,int newHeight) {
        if(texture!=0) GL11.glDeleteTextures(texture);
        texture=GL11.glGenTextures();width=newWidth;height=newHeight;
        levels=32-Integer.numberOfLeadingZeros(Math.max(width,height));bytes=requiredBytes(width,height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL42.glTexStorage2D(GL11.GL_TEXTURE_2D,levels,GL30.GL_R32F,width,height);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,levels-1);
        if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH)!=width)
            throw new IllegalStateException("Hi-Z texture allocation failed");
    }

    private static boolean depthFormat(int format) {
        return format==GL11.GL_DEPTH_COMPONENT || format==GL14.GL_DEPTH_COMPONENT16 || format==GL14.GL_DEPTH_COMPONENT24
                || format==GL14.GL_DEPTH_COMPONENT32 || format==GL30.GL_DEPTH_COMPONENT32F
                || format==GL30.GL_DEPTH24_STENCIL8 || format==GL30.GL_DEPTH32F_STENCIL8;
    }

    private static int compile(String name) throws IOException {
        String source;
        try(var input=HiZPyramid.class.getResourceAsStream("/assets/assimploader/shaders/"+name)) {
            if(input==null) throw new IOException("Missing Hi-Z shader "+name);
            source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER),program=0;
        try {
            GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) throw new IOException(GL20.glGetShaderInfoLog(shader));
            program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new IOException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch(IOException|RuntimeException failure) {if(program!=0) GL20.glDeleteProgram(program);throw failure;}
        finally {GL20.glDeleteShader(shader);}
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if(texture!=0) GL11.glDeleteTextures(texture);
        if(sampler!=0) GL33.glDeleteSamplers(sampler);
        if(reduceProgram!=0) GL20.glDeleteProgram(reduceProgram);
        if(cullProgram!=0) GL20.glDeleteProgram(cullProgram);
        texture=sampler=reduceProgram=cullProgram=width=height=levels=0;bytes=0;builtFrame=Long.MIN_VALUE;
    }

    private static final class State implements AutoCloseable {
        private final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int storage=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        private final int[] buffers=new int[2];
        private final long[] starts=new long[2],sizes=new long[2];
        private final int boundTexture,boundSampler,image,level,layer,access,format;
        private final boolean layered;
        State() {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);boundTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boundSampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,0);
            image=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_NAME,0);level=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LEVEL,0);
            layer=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYER,0);layered=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_LAYERED,0)!=0;
            access=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_ACCESS,0);format=GL30.glGetIntegeri(GL42.GL_IMAGE_BINDING_FORMAT,0);
            for(int index=0;index<2;index++) {
                buffers[index]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,index);
                starts[index]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,index);
                sizes[index]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,index);
            }
        }
        @Override public void close() {
            for(int index=0;index<2;index++) {
                if(buffers[index]!=0 && sizes[index]>0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,index,buffers[index],starts[index],sizes[index]);
                else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,index,buffers[index]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,storage);
            GL42.glBindImageTexture(0,image,level,layered,layer,access==0?GL15.GL_READ_ONLY:access,format==0?GL30.GL_R32F:format);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,boundTexture);GL33.glBindSampler(0,boundSampler);
            GL13.glActiveTexture(active);GL20.glUseProgram(program);ProgramManager.glUseProgram(program);
        }
    }
}
