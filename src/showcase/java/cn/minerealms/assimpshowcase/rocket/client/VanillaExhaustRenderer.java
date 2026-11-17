package cn.minerealms.assimpshowcase.rocket.client;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL33C.*;

/** Standalone half-resolution fallback. Shader packs own their HDR pass and never use this backend. */
public final class VanillaExhaustRenderer {
    private static int volumeProgram, compositeProgram, vao, fbo, color, depthFbo, depthTexture;
    private static int width, height, depthFormat;
    private static boolean failed;
    private static ExhaustUniforms uniforms;
    private static long draws;

    private VanillaExhaustRenderer() {}

    public static long draws() { return draws; }

    public static void render(Matrix4f projection, Matrix4f view) {
        var packet = RocketExhausts.packet();
        if ((packet.count() == 0 && packet.puffCount() == 0) || failed) return;
        RenderSystem.assertOnRenderThread();
        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM), oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
        boolean oldDepth = glIsEnabled(GL_DEPTH_TEST), oldBlend = glIsEnabled(GL_BLEND), oldCull = glIsEnabled(GL_CULL_FACE);
        boolean oldMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int src = glGetInteger(GL_BLEND_SRC_RGB), dst = glGetInteger(GL_BLEND_DST_RGB);
        int srcA = glGetInteger(GL_BLEND_SRC_ALPHA), dstA = glGetInteger(GL_BLEND_DST_ALPHA);
        int eq = glGetInteger(GL_BLEND_EQUATION_RGB), eqA = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int[] bound = new int[2];
        for (int i = 0; i < 2; i++) { glActiveTexture(GL_TEXTURE0 + i); bound[i] = glGetInteger(GL_TEXTURE_BINDING_2D); }
        try {
            initialize();
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, main.getDepthTextureId());
            int sourceDepthFormat = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT);
            resize(main.width, main.height, sourceDepthFormat);
            // Never sample a depth attachment while drawing back into the framebuffer that owns it.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, main.frameBufferId);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, depthFbo);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
            glDisable(GL_DEPTH_TEST); glDepthMask(false); glDisable(GL_BLEND); glDisable(GL_CULL_FACE);
            glBindVertexArray(vao);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glViewport(0, 0, Math.max(1, width / 2), Math.max(1, height / 2));
            glUseProgram(volumeProgram);
            uniforms.upload(packet);
            matrix(volumeProgram, "ProjectionInverse", new Matrix4f(projection).invert());
            matrix(volumeProgram, "ViewInverse", new Matrix4f(view).invert());
            glUniform2f(glGetUniformLocation(volumeProgram, "FullResolution"), width, height);
            texture(volumeProgram, "DepthSampler", 0, depthTexture);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glBindFramebuffer(GL_FRAMEBUFFER, main.frameBufferId);
            glViewport(0, 0, width, height);
            glUseProgram(compositeProgram);
            texture(compositeProgram, "VolumeSampler", 0, color);
            texture(compositeProgram, "DepthSampler", 1, depthTexture);
            matrix(compositeProgram, "ProjectionInverse", new Matrix4f(projection).invert());
            glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_ONE, GL_SRC_ALPHA, GL_ZERO, GL_ONE);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            draws++;
        } catch (Exception | LinkageError failure) {
            failed = true;
            AssimpLoader.LOGGER.error("Vanilla rocket volume renderer disabled until resource reload", failure);
        } finally {
            glUseProgram(oldProgram); glBindVertexArray(oldVao);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw); glBindFramebuffer(GL_READ_FRAMEBUFFER, oldRead);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int i = 0; i < 2; i++) { glActiveTexture(GL_TEXTURE0 + i); glBindTexture(GL_TEXTURE_2D, bound[i]); }
            glActiveTexture(oldActive);
            set(GL_DEPTH_TEST, oldDepth); set(GL_BLEND, oldBlend); set(GL_CULL_FACE, oldCull);
            glDepthMask(oldMask); glBlendFuncSeparate(src, dst, srcA, dstA); glBlendEquationSeparate(eq, eqA);
        }
    }

    private static void initialize() throws IOException {
        if (volumeProgram != 0 && compositeProgram != 0) return;
        String vertex = read("shaders/core/exhaust_fullscreen.vsh");
        String volume = read("shaders/core/exhaust_volume.fsh").replace("// ASSIMP_EXHAUST_INCLUDE",
                read("shaders/include/exhaust_volume.glsl"));
        volumeProgram = link(vertex, volume);
        compositeProgram = link(vertex, read("shaders/core/exhaust_composite.fsh"));
        uniforms = new ExhaustUniforms(volumeProgram);
        vao = glGenVertexArrays();
    }

    private static void resize(int w, int h, int format) {
        if (width == w && height == h && depthFormat == format && fbo != 0) return;
        destroyTargets(); width = w; height = h; depthFormat = format;
        color = texture2d(GL_RGBA16F, Math.max(1, w / 2), Math.max(1, h / 2), GL_RGBA, GL_HALF_FLOAT);
        fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0); checkFramebuffer();
        boolean stencil = format == GL_DEPTH24_STENCIL8 || format == GL_DEPTH32F_STENCIL8;
        int depthType = format == GL_DEPTH32F_STENCIL8 ? GL_FLOAT_32_UNSIGNED_INT_24_8_REV
                : stencil ? GL_UNSIGNED_INT_24_8 : GL_FLOAT;
        depthTexture = texture2d(format, w, h, stencil ? GL_DEPTH_STENCIL : GL_DEPTH_COMPONENT, depthType);
        depthFbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, depthFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        glDrawBuffer(GL_NONE); glReadBuffer(GL_NONE); checkFramebuffer();
    }

    private static int texture2d(int format, int w, int h, int external, int type) {
        glActiveTexture(GL_TEXTURE0);
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, format, w, h, 0, external, type, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static void checkFramebuffer() {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Rocket framebuffer incomplete: " + status);
    }

    private static void texture(int program, String name, int unit, int id) {
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, id);
        glUniform1i(glGetUniformLocation(program, name), unit);
    }

    private static void matrix(int program, String name, Matrix4f value) {
        glUniformMatrix4fv(glGetUniformLocation(program, name), false, value.get(new float[16]));
    }

    private static String read(String path) throws IOException {
        try (var stream = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(new ResourceLocation(AssimpLoader.MODID, path)).open()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int link(String vertex, String fragment) {
        int vs = 0, fs = 0, program = 0;
        try {
            vs = compile(GL_VERTEX_SHADER, vertex); fs = compile(GL_FRAGMENT_SHADER, fragment);
            program = glCreateProgram(); glAttachShader(program, vs); glAttachShader(program, fs); glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException(glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException error) {
            if (program != 0) glDeleteProgram(program);
            throw error;
        } finally { if (vs != 0) glDeleteShader(vs); if (fs != 0) glDeleteShader(fs); }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error);
        }
        return shader;
    }

    private static void set(int capability, boolean enabled) { if (enabled) glEnable(capability); else glDisable(capability); }

    private static void destroyTargets() {
        if (fbo != 0) glDeleteFramebuffers(fbo);
        if (depthFbo != 0) glDeleteFramebuffers(depthFbo);
        if (color != 0) glDeleteTextures(color);
        if (depthTexture != 0) glDeleteTextures(depthTexture);
        fbo = depthFbo = color = depthTexture = 0; width = height = 0;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(VanillaExhaustRenderer::clear); return; }
        destroyTargets();
        if (volumeProgram != 0) glDeleteProgram(volumeProgram);
        if (compositeProgram != 0) glDeleteProgram(compositeProgram);
        if (vao != 0) glDeleteVertexArrays(vao);
        volumeProgram = compositeProgram = vao = 0; uniforms = null; failed = false;
    }
}
