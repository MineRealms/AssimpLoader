package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Immutable mesh buffers are uploaded once; only instance matrices and lighting change per draw. */
public final class CachedMeshRenderer {
    private record MeshBuffer(int vao, int vertices, int indices, int count) {
        void close() {
            GL30.glDeleteVertexArrays(vao);
            GL15.glDeleteBuffers(vertices);
            GL15.glDeleteBuffers(indices);
        }
    }
    private static final Map<AssimpMesh, MeshBuffer> BUFFERS = new IdentityHashMap<>();
    private static int program;
    private static boolean failed;
    private static int mvpLocation, modelViewLocation, normalLocation, tintLocation, lightLocation, overlayLocation;
    private static int colorLocation, fogStartLocation, fogEndLocation, fogColorLocation, fogShapeLocation;
    private static final String VERTEX = """
            #version 330 core
            layout(location=0) in vec3 Position;
            layout(location=1) in vec3 Normal;
            layout(location=2) in vec2 UV;
            uniform mat4 MVP;
            uniform mat4 ModelView;
            uniform mat3 NormalMatrix;
            uniform int FogShape;
            out vec2 texCoord;
            out float diffuse;
            out float vertexDistance;
            void main() {
                gl_Position = MVP * vec4(Position, 1.0);
                vec3 position = (ModelView * vec4(Position, 1.0)).xyz;
                vertexDistance = FogShape == 0 ? length(position) : max(length(position.xz), abs(position.y));
                texCoord = UV;
                vec3 n = NormalMatrix * Normal;
                n = length(n) > 0.000001 ? normalize(n) : vec3(0,1,0);
                diffuse = min(1.0, 0.6 + 0.4 * max(dot(n, normalize(vec3(0.2,1,0.3))), 0.0));
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            uniform sampler2D Albedo;
            uniform sampler2D Lightmap;
            uniform sampler2D Overlay;
            uniform vec4 Tint;
            uniform vec4 ColorModulator;
            uniform ivec2 LightUV;
            uniform ivec2 OverlayUV;
            uniform float FogStart;
            uniform float FogEnd;
            uniform vec4 FogColor;
            in vec2 texCoord;
            in float diffuse;
            in float vertexDistance;
            out vec4 color;
            void main() {
                color = texture(Albedo, texCoord) * Tint * ColorModulator;
                if (color.a < 0.1) discard;
                vec4 overlay = texelFetch(Overlay, OverlayUV, 0);
                color.rgb = mix(overlay.rgb, color.rgb, overlay.a);
                color *= texelFetch(Lightmap, clamp(LightUV / 16, ivec2(0), ivec2(15)), 0);
                color.rgb *= diffuse;
                if (vertexDistance > FogStart) {
                    float amount = vertexDistance >= FogEnd ? 1.0 : smoothstep(FogStart, FogEnd, vertexDistance);
                    color.rgb = mix(color.rgb, FogColor.rgb, amount * FogColor.a);
                }
            }
            """;

    private CachedMeshRenderer() {}

    public static boolean render(AssimpScene scene, List<SceneTransforms.Instance> instances, PoseStack poses,
                                  MultiBufferSource buffers, int light, int overlay) {
        // Shader packs must retain control of their entity programs and pass outputs.
        if (OculusCompat.shouldAvoidCustomShaders() || scene.hasSkeleton() || failed) return false;
        if (!GL.getCapabilities().OpenGL33) return false;
        if (AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                AssimpLoaderConfig.OptimizationLevel.AUTO) == AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        // Transparent meshes keep Minecraft's own blending/order path.
        if (instances.stream().anyMatch(instance -> {
            var material = ModelTextureCache.material(scene, instance.mesh());
            return material != null && material.isTransparent();
        })) return false;
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
        try (GpuDrawState drawState = new GpuDrawState()) {
            drawState.prepare();
            initialize();
            GL20.glUseProgram(program);
            GL20.glUniform2i(lightLocation, light & 0xffff, light >>> 16 & 0xffff);
            GL20.glUniform2i(overlayLocation, overlay & 0xffff, overlay >>> 16 & 0xffff);
            float[] color = RenderSystem.getShaderColor(), fog = RenderSystem.getShaderFogColor();
            GL20.glUniform4f(colorLocation, color[0], color[1], color[2], color[3]);
            GL20.glUniform1f(fogStartLocation, RenderSystem.getShaderFogStart());
            GL20.glUniform1f(fogEndLocation, RenderSystem.getShaderFogEnd());
            GL20.glUniform4f(fogColorLocation, fog[0], fog[1], fog[2], fog[3]);
            GL20.glUniform1i(fogShapeLocation, RenderSystem.getShaderFogShape().getIndex());
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matrix = stack.mallocFloat(16), normalBuffer = stack.mallocFloat(9);
                Matrix4f mvp = new Matrix4f(), modelView = new Matrix4f();
                Matrix3f normal = new Matrix3f();
                for (SceneTransforms.Instance instance : instances) {
                    AssimpMesh mesh = instance.mesh();
                    MeshBuffer meshBuffer = BUFFERS.computeIfAbsent(mesh, CachedMeshRenderer::upload);
                    modelView.set(view).mul(instance.transform());
                    mvp.set(RenderSystem.getProjectionMatrix()).mul(modelView);
                    mvp.get(matrix);
                    GL20.glUniformMatrix4fv(mvpLocation, false, matrix);
                    modelView.get(matrix);
                    GL20.glUniformMatrix4fv(modelViewLocation, false, matrix);
                    normal.set(modelView);
                    if (Math.abs(normal.determinant()) > 1.0e-12f) normal.invert().transpose();
                    else normal.identity();
                    normal.get(normalBuffer);
                    GL20.glUniformMatrix3fv(normalLocation, false, normalBuffer);
                    var material = ModelTextureCache.material(scene, mesh);
                    if (material != null && material.isDoubleSided()) RenderSystem.disableCull();
                    else RenderSystem.enableCull();
                    float[] tint = material == null ? new float[]{1,1,1,1} : material.getRenderColor();
                    GL20.glUniform4f(tintLocation, tint[0], tint[1], tint[2], tint[3]);
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    Minecraft.getInstance().getTextureManager().bindForSetup(ModelTextureCache.getTexture(scene, mesh));
                    GL30.glBindVertexArray(meshBuffer.vao());
                    GL11.glDrawElements(GL11.GL_TRIANGLES, meshBuffer.count(), GL11.GL_UNSIGNED_INT, 0);
                }
            }
            return true;
        } catch (RuntimeException exception) {
            release();
            failed = true;
            cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Cached GPU rendering unavailable; using vanilla geometry", exception);
            return false;
        }
    }

    private static MeshBuffer upload(AssimpMesh mesh) {
        float[] vertices = mesh.getVertices(), normals = mesh.getNormals(), uv = mesh.getTexCoords();
        int[] indices = mesh.getIndices();
        FloatBuffer data = MemoryUtil.memAllocFloat(vertices.length / 3 * 8);
        IntBuffer faces = MemoryUtil.memAllocInt(indices.length);
        int vao = GL30.glGenVertexArrays(), vbo = GL15.glGenBuffers(), ebo = GL15.glGenBuffers();
        try {
            for (int i = 0; i < vertices.length / 3; i++) {
                data.put(vertices[i * 3]).put(vertices[i * 3 + 1]).put(vertices[i * 3 + 2]);
                data.put(normals == null ? 0 : normals[i * 3]).put(normals == null ? 1 : normals[i * 3 + 1])
                        .put(normals == null ? 0 : normals[i * 3 + 2]);
                data.put(uv == null ? 0 : uv[i * 2]).put(uv == null ? 0 : uv[i * 2 + 1]);
            }
            for (int i = 0; i + 2 < indices.length; i += 3) {
                int count = vertices.length / 3;
                if (indices[i] < 0 || indices[i] >= count || indices[i + 1] < 0 || indices[i + 1] >= count
                        || indices[i + 2] < 0 || indices[i + 2] >= count) continue;
                faces.put(indices[i + 2]).put(indices[i + 1]).put(indices[i]);
            }
            data.flip(); faces.flip();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, faces, GL15.GL_STATIC_DRAW);
            for (int attribute = 0; attribute < 3; attribute++) GL20.glEnableVertexAttribArray(attribute);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 32, 0);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 32, 12);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 32, 24);
            return new MeshBuffer(vao, vbo, ebo, faces.limit());
        } catch (RuntimeException e) {
            GL30.glDeleteVertexArrays(vao); GL15.glDeleteBuffers(vbo); GL15.glDeleteBuffers(ebo);
            throw e;
        } finally {
            MemoryUtil.memFree(data); MemoryUtil.memFree(faces);
        }
    }

    private static void initialize() {
        if (program != 0) return;
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), fragment = 0, linked = 0;
        try {
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, vertex); GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            program = linked;
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Albedo"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Overlay"), 1);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Lightmap"), 2);
            mvpLocation = GL20.glGetUniformLocation(program, "MVP");
            modelViewLocation = GL20.glGetUniformLocation(program, "ModelView");
            normalLocation = GL20.glGetUniformLocation(program, "NormalMatrix");
            tintLocation = GL20.glGetUniformLocation(program, "Tint");
            lightLocation = GL20.glGetUniformLocation(program, "LightUV");
            overlayLocation = GL20.glGetUniformLocation(program, "OverlayUV");
            colorLocation = GL20.glGetUniformLocation(program, "ColorModulator");
            fogStartLocation = GL20.glGetUniformLocation(program, "FogStart");
            fogEndLocation = GL20.glGetUniformLocation(program, "FogEnd");
            fogColorLocation = GL20.glGetUniformLocation(program, "FogColor");
            fogShapeLocation = GL20.glGetUniformLocation(program, "FogShape");
        } catch (RuntimeException e) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            program = 0;
            throw e;
        } finally {
            GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader); GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    public static void clear() {
        release();
        failed = false;
    }

    private static void release() {
        BUFFERS.values().forEach(MeshBuffer::close);
        BUFFERS.clear();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
    }
}
