package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.config.GpuBatchPolicy;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.ModelWorldBounds;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.rendering.batch.HiZPyramid;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Render-thread opaque batches. No entity mutation, server lookup or per-frame GPU readback. */
public final class GpuInstanceBatcher implements AutoCloseable {
    private static final int INSTANCE_BYTES=112;
    private record Geometry(int firstIndex,int baseVertex,int count) {}
    private record Instance(AssimpMesh mesh,ResourceLocation texture,Matrix4f view,float[] tint,
                            int light,int overlay,Matrix4f[] bones,AABB bounds) {}
    private final GpuBatchPolicy policy;
    private final Map<AssimpMesh,Geometry> geometry=new IdentityHashMap<>();
    private final List<Instance> instances=new ArrayList<>();
    private final int[] buffers=new int[8];
    private int vao,program,visibilityProgram;
    private long vertexBytes,indexBytes,frameBytes;
    private final long vertexCapacity,indexCapacity;
    private HiZPyramid hiZ;
    private long accepted,rejected,submissions,commands,frames;
    private int lastInstances,lastCommands,lastSubmissions;
    private boolean lastHiZ;
    private String lastRejection="none";
    public String lastRejection() {return lastRejection;}
    private boolean reject(String reason) {lastRejection=reason;rejected++;return false;}

    public record Statistics(long accepted,long rejected,long submissions,long commands,long frames,
                             int lastInstances,int lastCommands,int lastSubmissions,boolean lastHiZ,
                             long geometryBytes,long frameBytes,long hiZBytes) {}
    public Statistics statistics() {
        return new Statistics(accepted,rejected,submissions,commands,frames,lastInstances,lastCommands,lastSubmissions,
                lastHiZ,program==0?0:vertexCapacity+indexCapacity,program==0?0:frameBytes,hiZ==null?0:hiZ.statistics().bytes());
    }
    public static boolean available() {
        return GL.getCapabilities().OpenGL43 && GL11.glGetInteger(GL43.GL_MAX_VERTEX_SHADER_STORAGE_BLOCKS)>=2;
    }
    public GpuInstanceBatcher(GpuBatchPolicy policy) throws IOException {
        RenderSystem.assertOnRenderThread();
        if(!policy.enabled() || !available()) throw new IllegalArgumentException("GPU batching requires OpenGL 4.3");
        this.policy=policy;
        long driverLimit=GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE);
        if(policy.frameBytes()>driverLimit) throw new IllegalArgumentException("Frame budget exceeds driver SSBO limit");
        vertexCapacity=(policy.geometryBytes()*3/4/64)*64;
        indexCapacity=policy.geometryBytes()-vertexCapacity;
        try(State ignored=new State()) {
            program=link("batch_mesh.vert","batch_mesh.frag");
            visibilityProgram=link("batch_visibility.comp");
            vao=GL30.glGenVertexArrays();GL30.glBindVertexArray(vao);
            for(int i=0;i<buffers.length;i++) buffers[i]=GL15.glGenBuffers();
            allocate(buffers[0],vertexCapacity);allocate(buffers[1],indexCapacity);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[0]);
            for(int i=0;i<5;i++) GL20.glEnableVertexAttribArray(i);
            GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,64,0);
            GL20.glVertexAttribPointer(1,3,GL11.GL_FLOAT,false,64,12);
            GL20.glVertexAttribPointer(2,2,GL11.GL_FLOAT,false,64,24);
            GL30.glVertexAttribIPointer(3,4,GL11.GL_INT,64,32);
            GL20.glVertexAttribPointer(4,4,GL11.GL_FLOAT,false,64,48);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,buffers[1]);
            int[] identifiers=new int[policy.maxInstances()];
            for(int i=0;i<identifiers.length;i++) identifiers[i]=i;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[2]);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,identifiers,GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(5);GL30.glVertexAttribIPointer(5,1,GL11.GL_UNSIGNED_INT,4,0);
            GL33.glVertexAttribDivisor(5,1);
            if(policy.usesHiZ()) hiZ=new HiZPyramid(Math.min(1024,policy.hiZMaxResolution()),HiZPyramid.MAX_BYTES);
        } catch(IOException|RuntimeException failure) {close();throw failure;}
    }

    public GpuBatchPolicy policy() {return policy;}
    public void begin() {RenderSystem.assertOnRenderThread();instances.clear();frameBytes=(long)policy.maxInstances()*4+64;}

    /** Admission is atomic for a scene: a rejected scene can use the immediate renderer unchanged. */
    public boolean submit(AssimpScene scene,PoseSnapshot pose,Matrix4f modelView,int light,int overlay) {
        RenderSystem.assertOnRenderThread();
        if(program==0) return reject("closed");
        if(pose!=null&&pose.getScene()!=scene) return reject("snapshot belongs to another scene");
        if(!modelView.isFinite()) return reject("non-finite model transform");
        if(!SceneTransforms.isOpaque(scene)) return reject("transparent material");
        if(pose!=null&&pose.hasActiveMorphs()) return reject("active Morph");
        if(pose==null&&scene.getMeshes().stream().anyMatch(AssimpMesh::hasMorphTargets)) return reject("Morph defaults require a snapshot");
        var draws=pose==null?SceneTransforms.instances(scene):pose.copyMeshInstances().stream()
                .map(value->new SceneTransforms.Instance(value.mesh(),value.transform())).toList();
        if(draws.isEmpty() || instances.size()+draws.size()>policy.maxInstances()) return reject("instance capacity");
        long required=0,additionalVertices=0,additionalIndices=0;
        var missing=Collections.newSetFromMap(new IdentityHashMap<AssimpMesh,Boolean>());
        for(var draw:draws) {
            var mesh=draw.mesh();
            int bones=mesh.hasBones()?(pose==null?0:pose.getBoneCount(mesh)):0;
            if(mesh.hasBones()&&(bones<1 || bones>1024 || mesh.getSkinnedData().getBoneIndices().length>mesh.getVertexCount()*4L)
                    || mesh.getVertexCount()==0 || mesh.getIndices().length==0 || mesh.getIndices().length%3!=0)
                return reject("mesh "+mesh.getName()+" unsupported bones="+bones+" vertices="+mesh.getVertexCount()+" indices="+mesh.getIndices().length);
            required+=INSTANCE_BYTES+32+20+4+(long)bones*64;
            if(!geometry.containsKey(mesh)&&missing.add(mesh)) {
                additionalVertices+=(long)mesh.getVertexCount()*64;additionalIndices+=(long)mesh.getIndices().length*4;
            }
        }
        if(frameBytes+required>policy.frameBytes()) return reject("frame budget");
        if(vertexBytes+additionalVertices>vertexCapacity || indexBytes+additionalIndices>indexCapacity)
            return reject("geometry budget vertices="+(vertexBytes+additionalVertices)+"/"+vertexCapacity+" indices="+(indexBytes+additionalIndices)+"/"+indexCapacity);
        // Upload before accepting; allocation/driver failure cannot leave half a scene queued.
        if(!missing.isEmpty()) try(State ignored=new State()) {for(var mesh:missing) upload(mesh);}
        AABB localBounds=pose==null?SceneTransforms.bounds(scene):pose.getBounds();
        AABB eyeBounds=ModelWorldBounds.transform(localBounds,modelView,0,0,0).inflate(.01);
        for(var draw:draws) {
            var mesh=draw.mesh();
            var material=ModelTextureCache.material(scene,mesh);
            Matrix4f view=new Matrix4f(modelView);
            if(!mesh.hasBones()) view.mul(draw.transform());
            instances.add(new Instance(mesh,ModelTextureCache.getTexture(scene,mesh),view,
                    material==null?new float[]{1,1,1,1}:material.getDiffuseColor().clone(),light,overlay,
                    mesh.hasBones()?pose.getBoneTransforms(mesh):new Matrix4f[0],eyeBounds));
        }
        frameBytes+=required;accepted+=draws.size();return true;
    }

    private void upload(AssimpMesh mesh) {
        var skinned=mesh.getSkinnedData();
        int[] ids=skinned==null?new int[mesh.getVertexCount()]:skinned.getBoneIndices();
        float[] weights=skinned==null?new float[mesh.getVertexCount()]:skinned.getBoneWeights();
        ByteBuffer data=SkinnedMeshVBO.createInterleavedBuffer(mesh.getVertices(),mesh.getNormals(),mesh.getTexCoords(),ids,weights);
        int[] faces=mesh.getIndices().clone();
        for(int i=0;i<faces.length;i+=3) {
            if(faces[i]<0||faces[i]>=mesh.getVertexCount()||faces[i+1]<0||faces[i+1]>=mesh.getVertexCount()
                    ||faces[i+2]<0||faces[i+2]>=mesh.getVertexCount()) throw new IllegalArgumentException("Invalid batch triangle");
            int first=faces[i];faces[i]=faces[i+2];faces[i+2]=first;
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[0]);GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,vertexBytes,data);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffers[1]);GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER,indexBytes,faces);
        geometry.put(mesh,new Geometry((int)(indexBytes/4),(int)(vertexBytes/64),faces.length));
        vertexBytes+=data.remaining();indexBytes+=(long)faces.length*4;
    }

    /** Depth must belong to this projection, target and frame; texture 0 disables occlusion. */
    public void flush(Matrix4f projection,int depthTexture,int width,int height,int samples,long frameToken) {
        RenderSystem.assertOnRenderThread();
        if(instances.isEmpty()) {lastInstances=lastCommands=lastSubmissions=0;lastHiZ=false;return;}
        var draws=new ArrayList<BatchDrawPlan.Draw<ResourceLocation>>(instances.size());
        for(var instance:instances) {
            var range=geometry.get(instance.mesh);
            draws.add(new BatchDrawPlan.Draw<>(instance.mesh,instance.texture,range.count,range.firstIndex,range.baseVertex));
        }
        var plan=BatchDrawPlan.build(draws,policy.mode(),policy.usesHiZ(),policy.maxInstances());
        int boneCount=instances.stream().mapToInt(value->value.bones.length).sum();
        ByteBuffer instanceData=allocateCpu(instances.size()*INSTANCE_BYTES),boneData=allocateCpu(Math.max(64,boneCount*64)),
                commandData=allocateCpu(plan.commandBytes()),boundsData=allocateCpu(instances.size()*32);
        try(State ignored=new State();GpuDrawState drawState=new GpuDrawState()) {
            int offset=0;
            for(int index:plan.order()) {
                var value=instances.get(index);
                value.view.get(instanceData);instanceData.position(instanceData.position()+64);
                for(float color:value.tint) instanceData.putFloat(color);
                instanceData.putInt(value.light&65535).putInt(value.light>>>16&65535)
                        .putInt(value.overlay&65535).putInt(value.overlay>>>16&65535);
                instanceData.putInt(offset).putInt(value.bones.length).putInt(value.mesh.hasBones()?1:0).putInt(0);
                for(var bone:value.bones) {bone.get(boneData);boneData.position(boneData.position()+64);}
                offset+=value.bones.length;
                var box=value.bounds;
                boundsData.putFloat((float)box.minX).putFloat((float)box.minY).putFloat((float)box.minZ).putFloat(0)
                        .putFloat((float)box.maxX).putFloat((float)box.maxY).putFloat((float)box.maxZ).putFloat(0);
            }
            if(boneData.position()==0) {new Matrix4f().get(boneData);boneData.position(64);}
            plan.writeCommands(commandData);
            stream(buffers[3],instanceData.flip());stream(buffers[4],boneData.flip());
            stream(buffers[5],commandData.flip());stream(buffers[6],boundsData.flip());
            allocate(buffers[7],(long)instances.size()*4);
            lastHiZ=hiZ!=null&&hiZ.build(depthTexture,width,height,samples,frameToken)
                    &&hiZ.classify(buffers[6],buffers[7],instances.size(),projection,(float)policy.hiZBias(),frameToken);
            if(lastHiZ) {
                GL20.glUseProgram(visibilityProgram);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,buffers[7]);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,buffers[5]);
                GL30.glUniform1ui(GL20.glGetUniformLocation(visibilityProgram,"count"),instances.size());
                GL43.glDispatchCompute((instances.size()+63)/64,1,1);
                GL42.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT|GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            }
            drawState.prepare();GL20.glUseProgram(program);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,0,buffers[3]);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,1,buffers[4]);
            GL20.glUniformMatrix4fv(location("Projection"),false,projection.get(new float[16]));
            GL20.glUniform1i(location("Albedo"),0);GL20.glUniform1i(location("Overlay"),1);GL20.glUniform1i(location("Lightmap"),2);
            float[] color=RenderSystem.getShaderColor(),fog=RenderSystem.getShaderFogColor();
            GL20.glUniform4f(location("ColorModulator"),color[0],color[1],color[2],color[3]);
            GL20.glUniform1f(location("FogStart"),RenderSystem.getShaderFogStart());
            GL20.glUniform1f(location("FogEnd"),RenderSystem.getShaderFogEnd());
            GL20.glUniform4f(location("FogColor"),fog[0],fog[1],fog[2],fog[3]);
            GL20.glUniform1i(location("FogShape"),RenderSystem.getShaderFogShape().getIndex());
            GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,buffers[5]);
            lastSubmissions=0;
            for(var group:plan.textureGroups()) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                Minecraft.getInstance().getTextureManager().bindForSetup(group.texture());
                if(policy.mode()==GpuBatchPolicy.Mode.MDI) {
                    GL43.glMultiDrawElementsIndirect(GL11.GL_TRIANGLES,GL11.GL_UNSIGNED_INT,group.byteOffset(),group.commandCount(),20);
                    lastSubmissions++;
                } else for(int index=group.firstCommand();index<group.firstCommand()+group.commandCount();index++) {
                    var command=plan.commands().get(index);
                    GL42.glDrawElementsInstancedBaseVertexBaseInstance(GL11.GL_TRIANGLES,command.count(),GL11.GL_UNSIGNED_INT,
                            (long)command.firstIndex()*4,command.instanceCount(),command.baseVertex(),command.baseInstance());
                    lastSubmissions++;
                }
            }
            lastInstances=instances.size();lastCommands=plan.commands().size();
            submissions+=lastSubmissions;commands+=lastCommands;frames++;
        } finally {
            MemoryUtil.memFree(instanceData);MemoryUtil.memFree(boneData);MemoryUtil.memFree(commandData);MemoryUtil.memFree(boundsData);
        }
    }

    /** Explicit diagnostics only. Runtime rendering never calls glGetBufferSubData. */
    public int[] readVisibility() {
        int[] result=new int[lastInstances];
        if(!lastHiZ) {Arrays.fill(result,1);return result;}
        try(State ignored=new State()) {
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffers[7]);
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER,0,result);
        }
        return result;
    }

    private int location(String name) {return GL20.glGetUniformLocation(program,name);}
    private static ByteBuffer allocateCpu(int size) {return MemoryUtil.memAlloc(size).order(ByteOrder.nativeOrder());}
    private static void allocate(int buffer,long bytes) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,bytes,GL15.GL_DYNAMIC_DRAW);
        if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=bytes)
            throw new IllegalStateException("GPU batch allocation failed");
    }
    private static void stream(int buffer,ByteBuffer data) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,GL15.GL_STREAM_DRAW);
        if(GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE)!=data.remaining())
            throw new IllegalStateException("GPU batch streaming allocation failed");
    }
    private static int link(String... sources) throws IOException {
        int program=GL20.glCreateProgram();var shaders=new ArrayList<Integer>();
        try {
            for(String source:sources) {
                int type=source.endsWith(".vert")?GL20.GL_VERTEX_SHADER:source.endsWith(".frag")?GL20.GL_FRAGMENT_SHADER:GL43.GL_COMPUTE_SHADER;
                int shader=GL20.glCreateShader(type);shaders.add(shader);
                try(var input=Minecraft.getInstance().getResourceManager().open(new ResourceLocation("assimploader","shaders/"+source))) {
                    GL20.glShaderSource(shader,new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                GL20.glCompileShader(shader);
                if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0) throw new IOException(GL20.glGetShaderInfoLog(shader));
                GL20.glAttachShader(program,shader);
            }
            GL20.glLinkProgram(program);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0) throw new IOException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch(IOException|RuntimeException failure) {GL20.glDeleteProgram(program);throw failure;}
        finally {shaders.forEach(GL20::glDeleteShader);}
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();instances.clear();geometry.clear();
        if(hiZ!=null) hiZ.close();hiZ=null;
        if(vao!=0) GL30.glDeleteVertexArrays(vao);vao=0;
        for(int buffer:buffers) if(buffer!=0) GL15.glDeleteBuffers(buffer);
        Arrays.fill(buffers,0);
        if(program!=0) GL20.glDeleteProgram(program);program=0;
        if(visibilityProgram!=0) GL20.glDeleteProgram(visibilityProgram);visibilityProgram=0;
    }

    /** Indexed SSBO ranges must be restored with their offset and length, not just their names. */
    private static final class State implements AutoCloseable {
        private final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                indirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),ssbo=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        private final int[] indexed=new int[2];private final long[] start=new long[2],size=new long[2];
        private final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        State() {for(int i=0;i<2;i++) {
            indexed[i]=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,i);
            start[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,i);size[i]=GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,i);
        }}
        @Override public void close() {
            for(int i=0;i<2;i++) {
                if(indexed[i]!=0&&size[i]>0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,i,indexed[i],start[i],size[i]);
                else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,i,indexed[i]);
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,ssbo);GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,indirect);
            GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
            GL20.glUseProgram(program);com.mojang.blaze3d.shaders.ProgramManager.glUseProgram(program);
        }
    }
}
