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
    @Test void discoversActualEntityOffsetsInsteadOfAssumingFloatAlignment() {
        var layout = OculusPoseCompute.Layout.from(entityFormat(VertexFormatElement.Type.BYTE));
        //? if forge {
        assertEquals(56, layout.stride());
        //?}
        //? if neoforge {
        /*
        assertEquals(54, layout.stride());
        *///?}
        assertEquals(0, layout.position());
        assertEquals(32, layout.normal());
        assertEquals(50, layout.tangent());
        assertEquals(0, layout.positionAttribute());
    }

    @Test void rejectsUnsupportedLayoutsBeforeWritingAnyGpuBuffer() {
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.Layout.from(DefaultVertexFormat.NEW_ENTITY));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.Layout.from(entityFormat(VertexFormatElement.Type.FLOAT)));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(53, 0, 32, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 45, 32, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 0, 55, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new OculusPoseCompute.Layout(56, 0, 32, 54, 0));
    }

    @Test void groupsTrianglesSoSeparateInvocationsNeverShareAWord() {
        for (int stride : new int[]{54, 55, 56, 57}) {
            int triangles = OculusPoseCompute.trianglesPerInvocation(stride);
            assertEquals(0, triangles * 3 * stride % 4);
            for (int smaller = 1; smaller < triangles; smaller++)
                assertNotEquals(0, smaller * 3 * stride % 4);
            assertEquals(1, OculusPoseCompute.workGroups(triangles * 64, stride));
            assertEquals(2, OculusPoseCompute.workGroups(triangles * 64 + 1, stride));
        }
        assertDoesNotThrow(() -> new OculusPoseCompute.Layout(54, 0, 32, 50, 0));
        assertDoesNotThrow(() -> new OculusPoseCompute.Layout(56, 2, 32, 50, 0));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.workGroups(0, 54));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.trianglesPerInvocation(0));
    }

    @Test void includesTheLastPartialStorageWordWithoutAddingAVertex() {
        assertEquals(164, OculusPoseCompute.storageBytes(3L * 54));
        assertEquals(324, OculusPoseCompute.storageBytes(6L * 54));
        assertEquals(168, OculusPoseCompute.storageBytes(3L * 56));
        assertEquals(168, OculusPoseCompute.storageBytes(3L * 55));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.storageBytes(0));
        assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.storageBytes(Long.MAX_VALUE));
    }

    @Test void gpuScatterPreservesMaterialAttributesAndRebuildsAnimatedTangent() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("ASSIMPLOADER_GL_TESTS")), "Opt in to a hidden OpenGL test context");
        Assumptions.assumeTrue(GLFW.glfwInit(), "GLFW unavailable");
        long window = 0;
        int program = 0;
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
            for (int stride : new int[]{56, 54, 55})
                for (int triangles : new int[]{1, 2, 3, 129, 257})
                    for (boolean guard : new boolean[]{false, true})
                        assertScatter(program, new OculusPoseCompute.Layout(stride, 0, 32, 50, 0), triangles, guard);
            assertScatter(program, new OculusPoseCompute.Layout(56, 2, 32, 50, 0), 129, true);
        } finally {
            if (window != 0) {
                if (program != 0) GL20.glDeleteProgram(program);
                GL.setCapabilities(null);
                GLFW.glfwMakeContextCurrent(0);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
        }
    }

    private static void assertScatter(int program, OculusPoseCompute.Layout layout, int triangles, boolean guard) {
        int vertices = triangles * 3;
        int bytes = vertices * layout.stride();
        int storage = (int) OculusPoseCompute.storageBytes(bytes);
        int uploaded = guard ? storage + 8 : bytes;
        ByteBuffer packed = MemoryUtil.memAlloc(uploaded).order(ByteOrder.nativeOrder());
        ByteBuffer result = MemoryUtil.memAlloc(guard ? uploaded : storage).order(ByteOrder.nativeOrder());
        int[] buffers = new int[3];
        int vao = 0;
        try {
            float[] geometry = new float[vertices * 8];
            int[] indices = new int[vertices];
            for (int triangle = 0; triangle < triangles; triangle++) {
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = triangle * 3 + corner;
                    int offset = vertex * 8;
                    geometry[offset] = triangle * 0.125f - (corner == 2 ? 1 : 0);
                    geometry[offset + 1] = -triangle * 0.25f + (corner == 1 ? 1 : 0);
                    geometry[offset + 2] = triangle * 0.03125f;
                    geometry[offset + 5] = 1;
                    geometry[offset + 6] = corner == 1 ? 1 : 0;
                    geometry[offset + 7] = corner == 2 ? 1 : 0;
                    indices[vertex] = triangle * 3 + 2 - corner;
                }
            }
            for (int i = 0; i < uploaded; i++) packed.put(i, sentinel(i));
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = GL15.glGenBuffers();
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffers[i]);
                if (i == 0) GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, geometry, GL15.GL_STATIC_DRAW);
                else if (i == 1) GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, indices, GL15.GL_STATIC_DRAW);
                else GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, packed, GL15.GL_DYNAMIC_COPY);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, i, buffers[i]);
            }
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffers[2]);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, layout.stride(), layout.position());
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER, buffers[0]);
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER, buffers[1]);
            OculusPoseCompute.prepareDestination(buffers[2], bytes);
            assertEquals(buffers[0], GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER));
            assertEquals(buffers[1], GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER));
            assertEquals(buffers[2], GL20.glGetVertexAttribi(0, GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING));
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, buffers[2]);
            assertEquals(result.capacity(), GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_SIZE));
            assertEquals(GL15.GL_DYNAMIC_COPY, GL15.glGetBufferParameteri(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_BUFFER_USAGE));
            GL20.glUseProgram(program);
            String[] names = {"triangleCount", "vertexStride", "positionOffset", "normalOffset", "tangentOffset"};
            int[] values = {triangles, layout.stride(), layout.position(), layout.normal(), layout.tangent()};
            for (int i = 0; i < names.length; i++) GL20.glUniform1i(GL20.glGetUniformLocation(program, names[i]), values[i]);
            for (int repeat = 0; repeat < 4; repeat++) {
                GL43.glDispatchCompute(OculusPoseCompute.workGroups(triangles, layout.stride()), 1, 1);
                GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, result);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                for (int vertex = 0; vertex < vertices; vertex++) {
                    int offset = vertex * layout.stride();
                    int source = indices[vertex] * 8;
                    for (int axis = 0; axis < 3; axis++)
                        assertEquals(geometry[source + axis], result.getFloat(offset + layout.position() + axis * 4),
                                "Position stride=" + layout.stride() + " vertex=" + vertex);
                    assertEquals(0, result.get(offset + layout.normal()));
                    assertEquals(0, result.get(offset + layout.normal() + 1));
                    assertEquals(127, result.get(offset + layout.normal() + 2));
                    assertEquals(0, result.get(offset + layout.tangent()));
                    assertEquals(127, result.get(offset + layout.tangent() + 1));
                    assertEquals(0, result.get(offset + layout.tangent() + 2));
                    assertEquals(-127, result.get(offset + layout.tangent() + 3));
                    for (int field = 0; field < layout.stride(); field++) {
                        if (field >= layout.position() && field < layout.position() + 12
                                || field >= layout.normal() && field < layout.normal() + 3
                                || field >= layout.tangent() && field < layout.tangent() + 4) continue;
                        assertEquals(sentinel(offset + field), result.get(offset + field),
                                "Material overwritten stride=" + layout.stride() + " vertex=" + vertex + " field=" + field);
                    }
                }
                for (int i = bytes; i < result.capacity(); i++)
                    assertEquals(guard ? sentinel(i) : (byte) 0, result.get(i), "Storage tail overwritten at " + i);
            }
            assertThrows(IllegalArgumentException.class, () -> OculusPoseCompute.prepareDestination(buffers[2], result.capacity() + 1L));
            assertEquals(buffers[0], GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER));
            assertEquals(buffers[1], GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER));
        } finally {
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            for (int buffer : buffers) if (buffer != 0) GL15.glDeleteBuffers(buffer);
            MemoryUtil.memFree(result);
            MemoryUtil.memFree(packed);
        }
    }

    private static byte sentinel(int offset) { return (byte) (0x5a + offset * 37); }

    private static VertexFormat entityFormat(VertexFormatElement.Type tangentType) {
        //? if forge {
        var elements = ImmutableMap.<String, VertexFormatElement>builder();
        elements.putAll(DefaultVertexFormat.NEW_ENTITY.getElementMapping());
        elements.put("iris_Entity", new VertexFormatElement(11, VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 3));
        // Unit tests do not apply Oculus's mixin permitting nonzero GENERIC indices.
        elements.put("mc_midTexCoord", new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2));
        elements.put("at_tangent", new VertexFormatElement(0, tangentType, VertexFormatElement.Usage.GENERIC, 4));
        elements.put("Padding2", new VertexFormatElement(0, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.PADDING, 1));
        return new VertexFormat(elements.build());
        //?}
        //? if neoforge {
        /*
        var builder = VertexFormat.builder();
        for (var element : DefaultVertexFormat.NEW_ENTITY.getElements()) {
            builder.add(DefaultVertexFormat.NEW_ENTITY.getElementName(element), element);
        }
        builder.padding(1);
        builder.add("iris_Entity", EntityElements.IDS);
        builder.add("mc_midTexCoord", EntityElements.MID_UV);
        builder.add("at_tangent", tangentType == VertexFormatElement.Type.BYTE
                ? EntityElements.TANGENT : EntityElements.FLOAT_TANGENT);
        return builder.build();
        *///?}
    }

    //? if neoforge {
    /*
    private static final class EntityElements {
        private static final VertexFormatElement IDS = element(VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 3);
        private static final VertexFormatElement MID_UV = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
        private static final VertexFormatElement TANGENT = element(VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4);
        private static final VertexFormatElement FLOAT_TANGENT = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 4);

        private static VertexFormatElement element(VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
            return VertexFormatElement.register(VertexFormatElement.findNextId(), 0, type, usage, count);
        }
    }
    *///?}
}
