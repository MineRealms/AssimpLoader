package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.opengl.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Deforms an Oculus ENTITY VBO without reading animated vertices back to the CPU. */
final class OculusPoseCompute implements AutoCloseable {
    private static int sharedProgram, programUsers;
    private final ComputeSkinning skinning;
    private final Layout layout;
    private final int destination, triangles;
    private int program;
    private int triangleUniform, strideUniform, positionUniform, normalUniform, tangentUniform;
    private PoseSnapshot pose;
    private long dispatches;

    record Layout(int stride, int position, int normal, int tangent, int positionAttribute) {
        Layout {
            if (stride <= 0 || stride % 4 != 0 || position < 0 || position % 4 != 0
                    || position + 12 > stride || normal < 0 || normal + 3 > stride
                    || tangent < 0 || tangent + 4 > stride || positionAttribute < 0)
                throw new IllegalArgumentException("Unsupported Oculus compute vertex layout");
        }

        static Layout from(VertexFormat format) {
            int offset = 0, attribute = 0, position = -1, normal = -1, tangent = -1, positionAttribute = -1;
            for (var entry : format.getElementMapping().entrySet()) {
                var element = entry.getValue();
                switch (entry.getKey()) {
                    case "Position" -> {
                        require(element, VertexFormatElement.Type.FLOAT, 3);
                        position = offset;
                        positionAttribute = attribute;
                    }
                    case "Normal" -> {
                        require(element, VertexFormatElement.Type.BYTE, 3);
                        normal = offset;
                    }
                    case "at_tangent" -> {
                        require(element, VertexFormatElement.Type.BYTE, 4);
                        tangent = offset;
                    }
                }
                offset += element.getByteSize();
                attribute++;
            }
            return new Layout(format.getVertexSize(), position, normal, tangent, positionAttribute);
        }

        private static void require(VertexFormatElement element, VertexFormatElement.Type type, int count) {
            if (element.getType() != type || element.getCount() != count)
                throw new IllegalArgumentException("Unsupported Oculus compute attribute: " + element);
        }
    }

    static boolean supports(AssimpMesh mesh) {
        return mesh.getIndices().length > 0 && mesh.getIndices().length % 3 == 0
                && ComputeSkinning.requiredBytes(mesh) <= 128L * 1024 * 1024;
    }

    OculusPoseCompute(AssimpMesh mesh, VertexFormat format) throws IOException {
        layout = Layout.from(format);
        triangles = mesh.getIndices().length / 3;
        for (int index : mesh.getIndices()) if (index < 0 || index >= mesh.getVertexCount())
            throw new IllegalArgumentException("Invalid Oculus compute triangle index");
        destination = GL20.glGetVertexAttribi(layout.positionAttribute(), GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
        if (destination == 0) throw new IllegalStateException("Oculus VBO is not bound");
        skinning = new ComputeSkinning(mesh);
        try (ComputeSkinning.State ignored = new ComputeSkinning.State()) {
            if ((triangles + 63L) / 64 > GL30.glGetIntegeri(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT, 0))
                throw new IllegalArgumentException("Oculus compute triangle count exceeds driver limit");
            compile();
            triangleUniform = GL20.glGetUniformLocation(program, "triangleCount");
            strideUniform = GL20.glGetUniformLocation(program, "vertexStride");
            positionUniform = GL20.glGetUniformLocation(program, "positionOffset");
            normalUniform = GL20.glGetUniformLocation(program, "normalOffset");
            tangentUniform = GL20.glGetUniformLocation(program, "tangentOffset");
        } catch (IOException | RuntimeException failure) {
            close();
            throw failure;
        }
    }

    void update(PoseSnapshot snapshot) {
        if (pose == snapshot) return;
        skinning.deform(snapshot);
        try (ComputeSkinning.State ignored = new ComputeSkinning.State()) {
            com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, skinning.outputBuffer());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, skinning.indexBuffer());
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, destination);
            GL20.glUniform1i(triangleUniform, triangles);
            GL20.glUniform1i(strideUniform, layout.stride());
            GL20.glUniform1i(positionUniform, layout.position());
            GL20.glUniform1i(normalUniform, layout.normal());
            GL20.glUniform1i(tangentUniform, layout.tangent());
            GL43.glDispatchCompute((triangles + 63) / 64, 1, 1);
            GL42.glMemoryBarrier(GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            pose = snapshot;
            dispatches++;
        }
    }

    long dispatches() { return dispatches; }

    private void compile() throws IOException {
        if (sharedProgram != 0) {
            program = sharedProgram;
            programUsers++;
            return;
        }
        String source;
        try (var stream = OculusPoseCompute.class.getResourceAsStream("/assets/assimploader/shaders/oculus_pose.comp")) {
            if (stream == null) throw new IOException("Missing Oculus pose compute shader");
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                throw new IOException(GL20.glGetShaderInfoLog(shader));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, shader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IOException(GL20.glGetProgramInfoLog(program));
            sharedProgram = program;
            programUsers++;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }

    @Override public void close() {
        skinning.close();
        if (program != 0) {
            if (program == sharedProgram) {
                if (--programUsers == 0) {
                    GL20.glDeleteProgram(program);
                    sharedProgram = 0;
                }
            } else GL20.glDeleteProgram(program);
        }
        program = 0;
        pose = null;
    }
}
