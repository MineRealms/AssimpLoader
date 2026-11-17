package cn.minerealms.assimploader.rendering;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class OculusPoseComputeTest {
    @Test void discoversOculus18UnalignedTangentInsteadOfAssumingFloatAlignment() {
        var layout = OculusPoseCompute.Layout.from(entityFormat(VertexFormatElement.Type.BYTE));
        assertEquals(56, layout.stride());
        assertEquals(0, layout.position());
        assertEquals(32, layout.normal());
        assertEquals(50, layout.tangent());
        assertEquals(0, layout.positionAttribute());
    }

    @Test void rejectsUnsupportedLayoutsBeforeWritingAnyGpuBuffer() {
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.Layout.from(DefaultVertexFormat.NEW_ENTITY));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.Layout.from(entityFormat(VertexFormatElement.Type.FLOAT)));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(55, 0, 32, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 2, 32, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 0, 55, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 0, 32, 54, 0));
    }

    @Test void gpuScatterPreservesMaterialAttributesAndRebuildsAnimatedTangent() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("ASSIMPLOADER_GL_TESTS")), "Opt in to a hidden OpenGL test context");
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW unavailable");
        long window = 0;
        int program = 0;
        int[] buffers = new int[3];
        ByteBuffer packed = MemoryUtil.memAlloc(3 * 56).order(ByteOrder.nativeOrder());
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            window = GLFW.glfwCreateWindow(32, 32, "AssimpLoader compute test", 0, 0);
            Assumptions.assumeTrue(window != 0, "OpenGL 4.3 unavailable");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
            try (var stream = getClass().getResourceAsStream("/assets/assimploader/shaders/oculus_pose.comp")) {
                assertNotNull(stream);
                GL20.glShaderSource(shader, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                GL20.glCompileShader(shader);
                assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, shader);
                GL20.glLinkProgram(program);
                assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
            } finally {
                GL20.glDeleteShader(shader);
            }
            float[] geometry = {0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, -1, 0, 0, 0, 0, 1, 0, 1};
            for (int i = 0; i < packed.capacity(); i++) packed.put(i, (byte) 0x5a);
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[i]);
                if (i == 0) GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, geometry, GL15.GL_STATIC_DRAW);
                else if (i == 1) GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, new int[]{2, 1, 0}, GL15.GL_STATIC_DRAW);
                else GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, packed, GL15.GL_DYNAMIC_COPY);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, i, buffers[i]);
            }
            GL20.glUseProgram(program);
            String[] names = {"triangleCount", "vertexStride", "positionOffset", "normalOffset", "tangentOffset"};
            int[] values = {1, 56, 0, 32, 50};
            for (int i = 0; i < names.length; i++) GL20.glUniform1i(GL20.glGetUniformLocation(program, names[i]), values[i]);
            GL43.glDispatchCompute(1, 1, 1);
            GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[2]);
            GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, packed);
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            for (int vertex = 0; vertex < 3; vertex++) {
                int offset = vertex * 56;
                int source = (2 - vertex) * 8;
                for (int axis = 0; axis < 3; axis++) assertEquals(geometry[source + axis], packed.getFloat(offset + axis * 4));
                assertEquals(0, packed.get(offset + 32));
                assertEquals(0, packed.get(offset + 33));
                assertEquals(127, packed.get(offset + 34));
                // The triangle has rotated +90 degrees: its tangent must follow it.
                assertEquals(0, packed.get(offset + 50));
                assertEquals(127, packed.get(offset + 51));
                assertEquals(0, packed.get(offset + 52));
                assertEquals(-127, packed.get(offset + 53));
                for (int field = 12; field < 56; field++) {
                    if (field >= 32 && field < 35 || field >= 50 && field < 54) continue;
                    assertEquals((byte) 0x5a, packed.get(offset + field), "Material field overwritten at " + field);
                }
            }
        } finally {
            if (window != 0) {
                for (int buffer : buffers) if (buffer != 0) GL15.glDeleteBuffers(buffer);
                if (program != 0) GL20.glDeleteProgram(program);
                GL.setCapabilities(null);
                GLFW.glfwMakeContextCurrent(0);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            MemoryUtil.memFree(packed);
        }
    }

    private static VertexFormat entityFormat(VertexFormatElement.Type tangentType) {
        var elements = ImmutableMap.<String, VertexFormatElement>builder();
        elements.putAll(DefaultVertexFormat.NEW_ENTITY.getElementMapping());
        elements.put("iris_Entity", new VertexFormatElement(11, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 3));
        // Unit tests do not apply Oculus's mixin permitting nonzero GENERIC indices.
        elements.put("mc_midTexCoord", new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2));
        elements.put("at_tangent", new VertexFormatElement(0, tangentType, VertexFormatElement.Usage.GENERIC, 4));
        elements.put("Padding2", new VertexFormatElement(0, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.PADDING, 1));
        return new VertexFormat(elements.build());
    }
}
