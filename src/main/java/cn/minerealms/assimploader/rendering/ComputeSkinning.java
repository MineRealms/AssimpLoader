package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.MorphDeformation;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Optional GL 4.3 deformation. Output remains on the GPU; readback is diagnostic only. */
public final class ComputeSkinning implements AutoCloseable {
    private static final int INPUT_STRIDE=112, OUTPUT_STRIDE=32, GROUP_SIZE=128;
    private static int sharedProgram, programUsers;
    private final AssimpMesh mesh;
    private final int[] buffers=new int[4];
    private int program,vao,elements;
    private PoseSnapshot pose;
    private long dispatches,hits;

    public static boolean available() { return GL.getCapabilities().OpenGL43; }

    public static long requiredBytes(AssimpMesh mesh) {
        if(!mesh.hasBones() || mesh.getSkinnedData()==null || mesh.getVertexCount()==0
                || mesh.getMorphTargets().size()>32 || mesh.getBoneNames().size()>1024) return Long.MAX_VALUE;
        return (long)mesh.getVertexCount()*(INPUT_STRIDE+OUTPUT_STRIDE+32L*mesh.getMorphTargets().size())
                +(long)mesh.getIndices().length*4+(long)mesh.getBoneNames().size()*64;
    }

    public ComputeSkinning(AssimpMesh mesh) throws IOException {
        RenderSystem.assertOnRenderThread();
        if(!available() || requiredBytes(mesh)>128L*1024*1024) throw new IllegalArgumentException("Compute mesh exceeds capability or capacity");
        this.mesh=mesh;
        try(State ignored=new State()) {
            long limit=GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
            long[] sizes={(long)mesh.getVertexCount()*INPUT_STRIDE,(long)mesh.getBoneNames().size()*64,
                    Math.max(32L,(long)mesh.getVertexCount()*mesh.getMorphTargets().size()*32),
                    (long)mesh.getVertexCount()*OUTPUT_STRIDE};
            for(long size:sizes) if(size>limit) throw new IllegalArgumentException("Compute SSBO exceeds driver limit");
            if((mesh.getVertexCount()+127L)/128>GL30.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT,0)
                    || GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS)<GROUP_SIZE
                    || GL30.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE,0)<GROUP_SIZE
                    || GL11.glGetInteger(GL43.GL_MAX_COMPUTE_SHADER_STORAGE_BLOCKS)<4)
                throw new IllegalArgumentException("Compute workgroup limits unavailable");
            compile();
            ByteBuffer input=pack(mesh);
            try { buffers[0]=upload(input,GL15.GL_STATIC_DRAW); }
            finally { MemoryUtil.memFree(input); }
            buffers[1]=allocate(sizes[1]);
            buffers[2]=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffers[2]);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,mesh.hasMorphTargets()?MorphTargetTBO.pack(mesh):new float[8],GL15.GL_STATIC_DRAW);
            checkSize(sizes[2]);
            buffers[3]=allocate(sizes[3]);
            elements=GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,elements);
            int[] indices=mesh.getIndices().clone();
            for(int i=0;i+2<indices.length;i+=3) {int value=indices[i];indices[i]=indices[i+2];indices[i+2]=value;}
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,indices,GL15.GL_STATIC_DRAW);
            checkSize((long)indices.length*4);
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[3]);
            for(int attribute=0;attribute<3;attribute++) {
                GL20.glEnableVertexAttribArray(attribute);
                GL20.glVertexAttribPointer(attribute,attribute==2?2:3,GL11.GL_FLOAT,false,OUTPUT_STRIDE,attribute==0?0:attribute==1?12:24);
            }
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,elements);
        } catch(IOException|RuntimeException failure) {close();throw failure;}
    }

    static ByteBuffer pack(AssimpMesh mesh) {
        var data=mesh.getSkinnedData();
        return pack(mesh.getVertices(),mesh.getNormals(),mesh.getTexCoords(),data.getBoneIndices(),data.getBoneWeights());
    }

    static ByteBuffer pack(float[] positions,float[] normals,float[] uv,int[] boneIndices,float[] weights) {
        if(positions.length==0 || positions.length%3!=0) throw new IllegalArgumentException("Incomplete Compute positions");
        int vertices=positions.length/3;
        if(weights.length%vertices!=0 || boneIndices.length!=weights.length
                || normals!=null&&normals.length!=positions.length || uv!=null&&uv.length!=vertices*2)
            throw new IllegalArgumentException("Compute vertex layout mismatch");
        int stride=weights.length/vertices;
        if(stride<1 || stride>8) throw new IllegalArgumentException("Compute supports one to eight influences");
        ByteBuffer packed=MemoryUtil.memAlloc(Math.multiplyExact(vertices,INPUT_STRIDE)).order(ByteOrder.nativeOrder());
        for(int vertex=0;vertex<vertices;vertex++) {
            packed.putFloat(positions[vertex*3]).putFloat(positions[vertex*3+1]).putFloat(positions[vertex*3+2]).putFloat(1);
            packed.putFloat(normals==null?0:normals[vertex*3]).putFloat(normals==null?1:normals[vertex*3+1])
                    .putFloat(normals==null?0:normals[vertex*3+2]).putFloat(0);
            packed.putFloat(uv==null?0:uv[vertex*2]).putFloat(uv==null?0:uv[vertex*2+1]).putLong(0);
            for(int group=0;group<2;group++) {
                for(int slot=0;slot<4;slot++) {int index=group*4+slot;packed.putInt(index<stride?boneIndices[vertex*stride+index]:-1);}
                for(int slot=0;slot<4;slot++) {
                    int index=group*4+slot;
                    float weight=index<stride?weights[vertex*stride+index]:0;
                    packed.putFloat(Float.isFinite(weight)&&weight>0?weight:0);
                }
            }
        }
        return packed.flip();
    }

    public void deform(PoseSnapshot snapshot) {
        RenderSystem.assertOnRenderThread();
        if(program==0) throw new IllegalStateException("Compute buffer closed");
        if(pose==snapshot) {hits++;return;}
        var palette=snapshot.getBoneTransforms(mesh);
        if(palette==null || palette.length!=mesh.getBoneNames().size()) throw new IllegalArgumentException("Compute palette mismatch");
        try(State ignored=new State()) {
            GlStateManager._glUseProgram(program);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffers[1]);
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,SkinnedMeshRenderer.packPalette(palette));
            for(int index=0;index<4;index++) GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,index,buffers[index]);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"vertexCount"),mesh.getVertexCount());
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"boneCount"),palette.length);
            boolean morph=snapshot.hasActiveMorphs(mesh);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"morphCount"),morph?mesh.getMorphTargets().size():0);
            if(morph) GL20.glUniform1fv(GL20.glGetUniformLocation(program,"morphWeights"),MorphDeformation.effectiveWeights(mesh,snapshot.getMorphWeights(mesh)));
            GL43.glDispatchCompute((mesh.getVertexCount()+127)/128,1,1);
            GL42.glMemoryBarrier(GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT|GL42.GL_BUFFER_UPDATE_BARRIER_BIT
                    |GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            pose=snapshot;dispatches++;
        }
    }

    public void draw() {
        GL30.glBindVertexArray(vao);
        var selection = MeshDrawSelection.current();
        if (selection == null) GL11.glDrawElements(GL11.GL_TRIANGLES,mesh.getIndices().length,GL11.GL_UNSIGNED_INT,0);
        else selection.drawBound(mesh, false);
    }
    public long dispatches() {return dispatches;}
    public long hits() {return hits;}

    int outputBuffer() { return buffers[3]; }
    int indexBuffer() { return elements; }

    public CpuSkinning.Geometry readback() {
        RenderSystem.assertOnRenderThread();
        try(State ignored=new State()) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffers[3]);
            float[] packed=new float[mesh.getVertexCount()*8];
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,packed);
            float[] positions=new float[mesh.getVertexCount()*3],normals=new float[positions.length];
            for(int vertex=0;vertex<mesh.getVertexCount();vertex++) {
                System.arraycopy(packed,vertex*8,positions,vertex*3,3);
                System.arraycopy(packed,vertex*8+3,normals,vertex*3,3);
            }
            return new CpuSkinning.Geometry(positions,normals);
        }
    }

    private static int upload(ByteBuffer data,int usage) {
        int buffer=GL15.glGenBuffers();
        try {GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,usage);checkSize(data.remaining());return buffer;}
        catch(RuntimeException failure) {GL15.glDeleteBuffers(buffer);throw failure;}
    }
    private static int allocate(long bytes) {
        int buffer=GL15.glGenBuffers();
        try {GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,bytes,GL15.GL_DYNAMIC_COPY);checkSize(bytes);return buffer;}
        catch(RuntimeException failure) {GL15.glDeleteBuffers(buffer);throw failure;}
    }
    private static void checkSize(long bytes) {
        if(GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=bytes) throw new IllegalStateException("Compute allocation failed");
    }
    private void compile() throws IOException {
        if(sharedProgram!=0) {program=sharedProgram;programUsers++;return;}
        String source;
        try(var stream=ComputeSkinning.class.getResourceAsStream("/assets/assimploader/shaders/skinning.comp")) {
            if(stream==null) throw new IOException("Missing compute shader");
            source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
        int shader=GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        try {
            GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);
            if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) throw new IOException(GL20.glGetShaderInfoLog(shader));
            program=GL20.glCreateProgram();GL20.glAttachShader(program,shader);GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new IOException(GL20.glGetProgramInfoLog(program));
            sharedProgram=program;programUsers++;
        } finally {GL20.glDeleteShader(shader);}
    }
    @Override public void close() {
        for(int index=0;index<buffers.length;index++) {if(buffers[index]!=0) GL15.glDeleteBuffers(buffers[index]);buffers[index]=0;}
        if(elements!=0) GL15.glDeleteBuffers(elements);
        if(vao!=0) GL30.glDeleteVertexArrays(vao);
        if(program!=0) {
            if(program==sharedProgram) {
                if(--programUsers==0) {GL20.glDeleteProgram(program);sharedProgram=0;}
            } else GL20.glDeleteProgram(program);
        }
        elements=vao=program=0;pose=null;
    }

    static final class State implements AutoCloseable {
        private final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),storage=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        private final int[] indexed=new int[4];
        private final long[] starts=new long[4],sizes=new long[4];
        State() {
            for(int index=0;index<4;index++) {
                indexed[index]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,index);
                starts[index]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,index);
                sizes[index]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,index);
            }
        }
        @Override public void close() {
            for(int index=0;index<4;index++) {
                if(indexed[index]!=0&&sizes[index]>0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,index,indexed[index],starts[index],sizes[index]);
                else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,index,indexed[index]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,storage);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);GL30.glBindVertexArray(vao);GlStateManager._glUseProgram(program);
        }
    }
}
