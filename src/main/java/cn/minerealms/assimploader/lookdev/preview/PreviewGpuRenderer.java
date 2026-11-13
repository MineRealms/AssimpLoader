package cn.minerealms.assimploader.lookdev.preview;

import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Render-thread-owned GPU resources for exactly one selected model. No framebuffer readback. */
public final class PreviewGpuRenderer implements AutoCloseable {
    private final AssimpScene scene;
    private final PreviewSceneData data;
    private final Map<Integer, GpuMesh> meshes = new HashMap<>();
    private final Map<Integer, ResourceLocation> textureLocations = new HashMap<>();
    private DynamicTexture white;
    private int program;
    private int mvpUniform;
    private int normalUniform;
    private int colorUniform;
    private int textureUniform;
    private int whiteTexture;

    public PreviewGpuRenderer(AssimpScene scene, PreviewSceneData data) throws IOException {
        RenderSystem.assertOnRenderThread();
        this.scene = scene;
        this.data = data;
        try (State ignored = new State()) {
            try {
                program = createProgram();
                mvpUniform = GL20.glGetUniformLocation(program, "Mvp");
                normalUniform = GL20.glGetUniformLocation(program, "NormalMatrix");
                colorUniform = GL20.glGetUniformLocation(program, "MaterialColor");
                textureUniform = GL20.glGetUniformLocation(program, "Albedo");
                NativeImage whiteImage = new NativeImage(1, 1, false);
                whiteImage.setPixelRGBA(0, 0, 0xffffffff);
                white = new DynamicTexture(whiteImage);
                white.setFilter(false, false);
                whiteTexture = white.getId();
                for (PreviewSceneData.Draw draw : data.draws()) {
                    if (!meshes.containsKey(draw.meshIndex())) {
                        AssimpMesh mesh = scene.getMeshes().get(draw.meshIndex());
                        meshes.put(draw.meshIndex(), new GpuMesh(mesh));
                        AssimpMaterial material = material(mesh);
                        if (material != null && material.hasTexture()) {
                            textureLocations.put(draw.meshIndex(), ModelTextureCache.getTexture(scene, mesh));
                        }
                    }
                }
            } catch (IOException | RuntimeException failure) {
                close();
                throw failure;
            }
        }
    }

    public void render(PreviewCamera camera, int x, int y, int width, int height) {
        if (program == 0 || width <= 0 || height <= 0) return;
        RenderSystem.assertOnRenderThread();
        try (State ignored = new State(); MemoryStack stack = MemoryStack.stackPush()) {
            RenderSystem.viewport(x, y, width, height);
            RenderSystem.enableScissor(x, y, width, height);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            GL11.glClearDepth(1);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GL20.glUseProgram(program);
            GL20.glUniform1i(textureUniform, 0);
            Matrix4f view = camera.view();
            Matrix4f projection = camera.projection((float) width / height);
            Vector3f center = data.center();
            Matrix4f centeredView = new Matrix4f(view).translate(-center.x, -center.y, -center.z);
            FloatBuffer matrix = stack.mallocFloat(16);
            FloatBuffer normal = stack.mallocFloat(9);
            Matrix4f modelView = new Matrix4f();
            Matrix4f mvp = new Matrix4f();
            Matrix3f normalMatrix = new Matrix3f();
            for (boolean transparentPass : new boolean[]{false, true}) {
                RenderSystem.depthMask(!transparentPass);
                for (PreviewSceneData.Draw draw : data.draws()) {
                    AssimpMesh mesh = scene.getMeshes().get(draw.meshIndex());
                    AssimpMaterial material = material(mesh);
                    if ((material != null && material.isTransparent()) != transparentPass) continue;
                    modelView.set(centeredView).mul(draw.transform());
                    mvp.set(projection).mul(modelView).get(matrix);
                    modelView.normal(normalMatrix).get(normal);
                    GL20.glUniformMatrix4fv(mvpUniform, false, matrix);
                    GL20.glUniformMatrix3fv(normalUniform, false, normal);
                    float[] color = material == null ? null : material.getDiffuseColor();
                    GL20.glUniform4f(colorUniform, color == null ? 1 : color[0], color == null ? 1 : color[1],
                            color == null ? 1 : color[2], color == null ? 1 : color[3]);
                    ResourceLocation texture = textureLocations.get(draw.meshIndex());
                    RenderSystem.bindTexture(texture == null ? whiteTexture
                            : Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
                    meshes.get(draw.meshIndex()).draw();
                }
            }
        }
    }

    private AssimpMaterial material(AssimpMesh mesh) {
        return mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterialCount()
                ? scene.getMaterials().get(mesh.getMaterialIndex()) : null;
    }

    private static int createProgram() throws IOException {
        int vertex = 0;
        int fragment = 0;
        int linked = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, "preview.vert");
            fragment = compile(GL20.GL_FRAGMENT_SHADER, "preview.frag");
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glBindAttribLocation(linked, 0, "Position");
            GL20.glBindAttribLocation(linked, 1, "Normal");
            GL20.glBindAttribLocation(linked, 2, "UV");
            GL20.glBindAttribLocation(linked, 3, "Color");
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IOException("LookDev shader link failed: " + GL20.glGetProgramInfoLog(linked, 1024));
            }
            int result = linked;
            linked = 0;
            return result;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
            if (linked != 0) GL20.glDeleteProgram(linked);
        }
    }

    private static int compile(int type, String name) throws IOException {
        ResourceLocation location = new ResourceLocation("assimploader", "shaders/lookdev/" + name);
        String source;
        try (var input = Minecraft.getInstance().getResourceManager().open(location)) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            GL20.glDeleteShader(shader);
            throw new IOException("LookDev " + name + ": " + log);
        }
        return shader;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        for (GpuMesh mesh : meshes.values()) mesh.close();
        meshes.clear();
        if (white != null) white.close();
        white = null;
        textureLocations.clear();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
    }

    private static final class GpuMesh implements AutoCloseable {
        private int vao;
        private int vertexBuffer;
        private int indexBuffer;
        private int indexCount;

        private GpuMesh(AssimpMesh mesh) {
            float[] positions = mesh.getVertices();
            float[] normals = mesh.getNormals();
            float[] uv = mesh.getTexCoords();
            float[] colors = mesh.getVertexColors();
            int[] indices = mesh.getIndices();
            FloatBuffer vertices = MemoryUtil.memAllocFloat(mesh.getVertexCount() * 12);
            IntBuffer elements = MemoryUtil.memAllocInt(indices.length);
            try {
                for (int v = 0; v < mesh.getVertexCount(); v++) {
                    vertices.put(positions, v * 3, 3);
                    if (normals != null && v * 3 + 2 < normals.length) vertices.put(normals, v * 3, 3);
                    else vertices.put(0).put(1).put(0);
                    if (uv != null && v * 2 + 1 < uv.length) vertices.put(uv, v * 2, 2);
                    else vertices.put(0).put(0);
                    if (colors != null && v * 4 + 3 < colors.length) vertices.put(colors, v * 4, 4);
                    else vertices.put(1).put(1).put(1).put(1);
                }
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    if (valid(indices[i], mesh) && valid(indices[i + 1], mesh) && valid(indices[i + 2], mesh)) {
                        elements.put(indices[i]).put(indices[i + 1]).put(indices[i + 2]);
                    }
                }
                vertices.flip();
                elements.flip();
                indexCount = elements.remaining();
                vao = GL30.glGenVertexArrays();
                GL30.glBindVertexArray(vao);
                vertexBuffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
                indexBuffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, elements, GL15.GL_STATIC_DRAW);
                int[] sizes = {3, 3, 2, 4};
                int offset = 0;
                for (int attribute = 0; attribute < sizes.length; attribute++) {
                    GL20.glEnableVertexAttribArray(attribute);
                    GL20.glVertexAttribPointer(attribute, sizes[attribute], GL11.GL_FLOAT, false, 12 * Float.BYTES,
                            (long) offset * Float.BYTES);
                    offset += sizes[attribute];
                }
            } catch (RuntimeException failure) {
                close();
                throw failure;
            } finally {
                MemoryUtil.memFree(vertices);
                MemoryUtil.memFree(elements);
            }
        }

        private static boolean valid(int index, AssimpMesh mesh) { return index >= 0 && index < mesh.getVertexCount(); }
        private void draw() {
            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L);
        }
        @Override public void close() {
            if (vertexBuffer != 0) GL15.glDeleteBuffers(vertexBuffer);
            if (indexBuffer != 0) GL15.glDeleteBuffers(indexBuffer);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            vertexBuffer = indexBuffer = vao = 0;
        }
    }

    /** Preserve both GL values and Minecraft's cached state through the matching RenderSystem APIs. */
    private static final class State implements AutoCloseable {
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final int texture;
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

        private State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        @Override public void close() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            RenderSystem.bindTexture(texture);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            if (!scissorEnabled) RenderSystem.disableScissor();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthMask);
            GL11.glClearDepth(clearDepth);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        }
    }
}
