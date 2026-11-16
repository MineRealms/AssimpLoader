package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.model.AssimpMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/** Immutable shared position/normal deltas. Only instance weights change between draws. */
final class MorphTargetTBO {
    static final int MAX_TARGETS=32;
    static final long MAX_BYTES=128L*1024*1024;
    private int buffer,texture;

    static long texels(AssimpMesh mesh) { return (long)mesh.getVertexCount()*mesh.getMorphTargets().size()*2; }
    static boolean fits(AssimpMesh mesh,long maximumTexels) {
        return mesh.hasMorphTargets() && mesh.getMorphTargets().size()<=MAX_TARGETS
                && texels(mesh)<=maximumTexels && texels(mesh)*16<=MAX_BYTES;
    }

    MorphTargetTBO(AssimpMesh mesh) {
        try {
            float[] packed=pack(mesh);
            buffer=GL15.glGenBuffers(); texture=GL11.glGenTextures();
            RenderSystem.activeTexture(GL13.GL_TEXTURE4);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER,buffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER,packed,GL15.GL_STATIC_DRAW);
            if(GL15.glGetBufferParameteri(GL31.GL_TEXTURE_BUFFER,GL15.GL_BUFFER_SIZE)!=(long)packed.length*Float.BYTES) {
                throw allocationFailure("buffer allocation");
            }
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_RGBA32F,buffer);
            if(GL11.glGetTexLevelParameteri(GL31.GL_TEXTURE_BUFFER,0,GL31.GL_TEXTURE_BUFFER_DATA_STORE_BINDING)!=buffer) {
                throw allocationFailure("texture attachment");
            }
        } catch(RuntimeException failure) { delete(); throw failure; }
    }

    private static IllegalStateException allocationFailure(String operation) {
        int error=GL11.glGetError();
        return new IllegalStateException("Morph GPU " + operation + " failed (GL error " + error + ")");
    }

    static float[] pack(AssimpMesh mesh) {
        if(!fits(mesh,Integer.MAX_VALUE)) throw new IllegalArgumentException("Morph GPU data exceeds bounded capacity");
        float[] result=new float[Math.toIntExact(texels(mesh)*4)];
        int vertices=mesh.getVertexCount();
        for(int target=0;target<mesh.getMorphTargets().size();target++) {
            var morph=mesh.getMorphTargets().get(target);
            float[] positions=morph.copyPositionDeltas(),normals=morph.copyNormalDeltas();
            for(int vertex=0;vertex<vertices;vertex++) {
                int destination=(target*vertices+vertex)*8;
                if(positions!=null) System.arraycopy(positions,vertex*3,result,destination,3);
                if(normals!=null) System.arraycopy(normals,vertex*3,result,destination+4,3);
            }
        }
        return result;
    }
    void bind() { RenderSystem.activeTexture(GL13.GL_TEXTURE4); GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,texture); }
    void delete() {
        if(buffer!=0) GL15.glDeleteBuffers(buffer);
        if(texture!=0) GL11.glDeleteTextures(texture);
        buffer=0; texture=0;
    }
}
