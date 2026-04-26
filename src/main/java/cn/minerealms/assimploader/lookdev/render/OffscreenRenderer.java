package cn.minerealms.assimploader.lookdev.render;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.camera.OrbitCamera;
import cn.minerealms.assimploader.material.PBRMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Offscreen renderer for LookDev preview.
 *
 * Renders 3D models to a framebuffer on the render thread,
 * then provides the texture ID for UI thread to display.
 */
public class OffscreenRenderer {

    private int framebufferId = -1;
    private int colorTextureId = -1;
    private int depthRenderbufferId = -1;
    private int width;
    private int height;

    private AssimpScene currentModel;
    private PBRMaterial currentMaterial;
    private OrbitCamera camera;

    private boolean needsRender = true;

    // Texture cache for embedded textures
    private final Map<String, Map<Integer, ResourceLocation>> textureCache = new HashMap<>();

    // Pixel buffer for UI thread access
    private int[] pixelBuffer = null;
    private volatile boolean pixelBufferReady = false;

    public OffscreenRenderer(int width, int height) {
        this.width = width;
        this.height = height;

        // Framebuffer will be created on render thread
        RenderSystem.recordRenderCall(() -> {
            try {
                createFramebuffer();
                DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Framebuffer created: {}x{}", width, height);
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Failed to create framebuffer", e);
            }
        });
    }

    private void createFramebuffer() {
        // Delete old framebuffer if exists
        if (framebufferId != -1) {
            GL30.glDeleteFramebuffers(framebufferId);
            GL11.glDeleteTextures(colorTextureId);
            GL30.glDeleteRenderbuffers(depthRenderbufferId);
        }

        // Create color texture
        colorTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Create depth renderbuffer
        depthRenderbufferId = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbufferId);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT, width, height);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);

        // Create framebuffer
        framebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTextureId, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbufferId);

        // Check framebuffer status
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Framebuffer incomplete: {}", status);
        } else {
            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Framebuffer created: {}x{}, FBO={}, Tex={}",
                width, height, framebufferId, colorTextureId);
        }

        // Unbind
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void setModel(AssimpScene model) {
        // Clear texture cache when model changes to prevent texture pollution
        if (this.currentModel != model) {
            clearTextureCache();
        }

        this.currentModel = model;
        this.needsRender = true;
        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Model set, needs render");
    }

    /**
     * Clear texture cache (called when model changes)
     */
    private void clearTextureCache() {
        RenderSystem.recordRenderCall(() -> {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            for (Map<Integer, ResourceLocation> modelTextures : textureCache.values()) {
                for (ResourceLocation texLoc : modelTextures.values()) {
                    textureManager.release(texLoc);
                }
            }
            textureCache.clear();
            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Texture cache cleared");
        });
    }

    public void setMaterial(PBRMaterial material) {
        this.currentMaterial = material;
        this.needsRender = true;
        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Material set, needs render");
    }

    public void setCamera(OrbitCamera camera) {
        this.camera = camera;
        this.needsRender = true;
    }

    public void markDirty() {
        this.needsRender = true;
    }

    /**
     * Request a render on the render thread.
     * This is safe to call from any thread.
     */
    public void requestRender() {
        if (!needsRender) {
            DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] requestRender called but needsRender=false");
            return;
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] requestRender called, scheduling render (FBO={}, Tex={})", framebufferId, colorTextureId);

        RenderSystem.recordRenderCall(() -> {
            try {
                renderToFramebuffer();
                needsRender = false;
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Render failed", e);
            }
        });
    }

    /**
     * Render immediately (must be called from render thread).
     * This is used when we're already on the render thread and don't need recordRenderCall.
     */
    public void renderNow() {
        if (!needsRender) {
            return;
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] renderNow called (FBO={}, Tex={})", framebufferId, colorTextureId);

        try {
            renderToFramebuffer();
            needsRender = false;
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Render failed", e);
        }
    }

    private void renderToFramebuffer() {
        if (framebufferId == -1) {
            DebugManager.warn(DebugCategory.LOOKDEV, "[OffscreenRenderer] Framebuffer not initialized");
            return;
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Rendering to framebuffer FBO={}", framebufferId);

        // Save current framebuffer
        int prevFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        // Bind our framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);

        // Setup viewport
        GL11.glViewport(0, 0, width, height);

        // Clear to dark gray background
        GL11.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Setup OpenGL state
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);

        // Disable blending to prevent color accumulation
        GL11.glDisable(GL11.GL_BLEND);

        // Enable backface culling
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);

        // Render model if available
        if (currentModel != null && camera != null) {
            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Rendering model: {} meshes", currentModel.getMeshes().size());
            try {
                renderModel();
                DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Model rendered successfully");
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Render error", e);
            }
        } else {
            DebugManager.warn(DebugCategory.LOOKDEV, "[OffscreenRenderer] Cannot render: model={}, camera={}",
                currentModel != null, camera != null);
        }

        // Read pixels to buffer for UI thread
        readPixelsToBuffer();

        // Restore previous framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebuffer);

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Render complete, texture ID: {}", colorTextureId);
    }

    /**
     * Read framebuffer pixels to CPU buffer for UI thread access
     */
    private synchronized void readPixelsToBuffer() {
        if (pixelBuffer == null || pixelBuffer.length != width * height) {
            pixelBuffer = new int[width * height];
            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Allocated pixel buffer: {}x{}", width, height);
        }

        // Read pixels from framebuffer (RGBA format)
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.allocateDirect(width * height * 4);
        byteBuffer.order(java.nio.ByteOrder.nativeOrder());

        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, byteBuffer);

        // Convert RGBA bytes to ARGB ints (ModernUI format)
        byteBuffer.rewind();
        for (int i = 0; i < width * height; i++) {
            int r = byteBuffer.get() & 0xFF;
            int g = byteBuffer.get() & 0xFF;
            int b = byteBuffer.get() & 0xFF;
            int a = byteBuffer.get() & 0xFF;

            // Flip Y coordinate (OpenGL bottom-left origin -> top-left origin)
            int y = i / width;
            int x = i % width;
            int flippedY = height - 1 - y;
            int flippedIndex = flippedY * width + x;

            pixelBuffer[flippedIndex] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        pixelBufferReady = true;
        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Pixel buffer ready, first pixel: 0x{}",
            Integer.toHexString(pixelBuffer[0]));
    }

    /**
     * Get pixel buffer for UI thread rendering.
     * Returns null if not ready.
     */
    public synchronized int[] getPixelBuffer() {
        return pixelBufferReady ? pixelBuffer : null;
    }

    /**
     * Render the current model with camera and material
     */
    private void renderModel() {
        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] renderModel() called");

        // Load embedded textures if not cached
        String modelKey = "current_model";
        if (!textureCache.containsKey(modelKey) && currentModel.hasEmbeddedTextures()) {
            loadEmbeddedTextures(currentModel, modelKey);
        }

        // Setup matrices
        Matrix4f projectionMatrix = camera.getProjectionMatrix(width, height, 0.1f, 100.0f);
        Matrix4f viewMatrix = camera.getViewMatrix();
        Matrix4f modelMatrix = new Matrix4f(); // Identity for now

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Camera distance: {}", camera.getDistance());

        // Set projection matrix via RenderSystem
        RenderSystem.setProjectionMatrix(projectionMatrix, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        // Reset shader color to white (important: prevents color accumulation)
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Create PoseStack and apply view and model transforms
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(viewMatrix);  // Use set() instead of mul() to avoid accumulation
        poseStack.last().pose().mul(modelMatrix);

        // Setup shader
        RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);

        // Render all meshes
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Rendering {} meshes", currentModel.getMeshes().size());

        for (int meshIndex = 0; meshIndex < currentModel.getMeshes().size(); meshIndex++) {
            AssimpMesh mesh = currentModel.getMeshes().get(meshIndex);

            // Get material
            AssimpMaterial material = null;
            ResourceLocation textureLocation = null;

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < currentModel.getMaterials().size()) {
                material = currentModel.getMaterials().get(mesh.getMaterialIndex());

                // Priority 1: Use embedded textures from cache
                if (currentModel.hasEmbeddedTextures() && textureCache.containsKey(modelKey)) {
                    Map<Integer, ResourceLocation> modelTextures = textureCache.get(modelKey);

                    // Try to get texture by material index first
                    if (modelTextures.containsKey(mesh.getMaterialIndex())) {
                        textureLocation = modelTextures.get(mesh.getMaterialIndex());
                        DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using embedded texture for material {}", mesh.getMaterialIndex());
                    } else if (!modelTextures.isEmpty()) {
                        // Fallback to first texture
                        textureLocation = modelTextures.values().iterator().next();
                        DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using first embedded texture as fallback");
                    }
                }

                // Priority 2: Use currentMaterial texture (from LookDev UI) if no embedded texture
                if (textureLocation == null && currentMaterial != null && currentMaterial.getBaseColorTexture() != null) {
                    textureLocation = currentMaterial.getBaseColorTexture();
                    DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using PBRMaterial texture: {}", textureLocation);
                }

                // Priority 3: Use material's diffuse texture path
                if (textureLocation == null && material != null && material.getDiffuseTexture() != null) {
                    // Try to use the material's texture path directly
                    String texPath = material.getDiffuseTexture();
                    DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] Material has diffuse texture: {}", texPath);
                }
            }

            // Bind texture
            if (textureLocation != null) {
                RenderSystem.setShaderTexture(0, textureLocation);
            } else {
                // Use white texture as fallback
                RenderSystem.setShaderTexture(0, new ResourceLocation("minecraft", "textures/block/white_concrete.png"));
            }

            // Render mesh
            renderMesh(mesh, material, poseStack, bufferBuilder, 0xF000F0, 0);
        }

        DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] renderModel() complete");
    }

    /**
     * Load embedded textures from scene
     */
    private void loadEmbeddedTextures(AssimpScene scene, String modelKey) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureCache.put(modelKey, new HashMap<>());

        for (int i = 0; i < scene.getTextures().size(); i++) {
            AssimpTexture texture = scene.getTextures().get(i);

            ResourceLocation textureLoc = new ResourceLocation("assimploader",
                "lookdev/embedded_" + i);

            try {
                NativeImage image = texture.loadAsNativeImage();
                DynamicTexture dynamicTexture = new DynamicTexture(image);

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.get(modelKey).put(i, textureLoc);

                DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Loaded embedded texture {}", i);
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Failed to load texture {}", i, e);
            }
        }
    }

    /**
     * Render a single mesh (adapted from AssimpItemRenderer)
     */
    private void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                           BufferBuilder bufferBuilder, int packedLight, int packedOverlay) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        // Use PBRMaterial's baseColor if available (includes overrides), otherwise fallback to AssimpMaterial
        Vector4f baseColor;
        float roughness = 0.5f;
        float metallic = 0.0f;
        Vector3f emissive = new Vector3f(0.0f, 0.0f, 0.0f);

        if (currentMaterial != null) {
            baseColor = currentMaterial.getBaseColor();
            roughness = currentMaterial.getRoughnessFactor();
            metallic = currentMaterial.getMetallicFactor();
            emissive = currentMaterial.getEmissiveFactor();

            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using PBRMaterial - baseColor: ({}, {}, {}, {}), roughness: {}, metallic: {}, emissive: ({}, {}, {})",
                baseColor.x, baseColor.y, baseColor.z, baseColor.w, roughness, metallic, emissive.x, emissive.y, emissive.z);
        } else {
            float[] diffuseColor = material != null ? material.getDiffuseColor() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};
            baseColor = new Vector4f(diffuseColor[0], diffuseColor[1], diffuseColor[2], diffuseColor[3]);
            DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using AssimpMaterial diffuseColor: ({}, {}, {}, {})",
                baseColor.x, baseColor.y, baseColor.z, baseColor.w);
        }

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        // Fixed light direction (from camera, slightly above)
        Vector3f lightDir = new Vector3f(0.0f, 0.3f, 1.0f).normalize();

        // Ambient light
        float ambientStrength = 0.4f;

        // Begin drawing
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);

        // Render triangles with reversed winding order
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];

            int vIdx0 = idx0 * 3;
            int vIdx1 = idx1 * 3;
            int vIdx2 = idx2 * 3;

            int tIdx0 = idx0 * 2;
            int tIdx1 = idx1 * 2;
            int tIdx2 = idx2 * 2;

            // Vertices
            Vector3f v0 = new Vector3f(vertices[vIdx0], vertices[vIdx0 + 1], vertices[vIdx0 + 2]);
            Vector3f v1 = new Vector3f(vertices[vIdx1], vertices[vIdx1 + 1], vertices[vIdx1 + 2]);
            Vector3f v2 = new Vector3f(vertices[vIdx2], vertices[vIdx2 + 1], vertices[vIdx2 + 2]);

            // Normals (transform to world space)
            Vector3f n0, n1, n2;
            if (normals != null && vIdx0 + 2 < normals.length && vIdx1 + 2 < normals.length && vIdx2 + 2 < normals.length) {
                n0 = new Vector3f(normals[vIdx0], normals[vIdx0 + 1], normals[vIdx0 + 2]);
                n1 = new Vector3f(normals[vIdx1], normals[vIdx1 + 1], normals[vIdx1 + 2]);
                n2 = new Vector3f(normals[vIdx2], normals[vIdx2 + 1], normals[vIdx2 + 2]);

                // Transform normals to world space
                normalMatrix.transform(n0);
                normalMatrix.transform(n1);
                normalMatrix.transform(n2);
                n0.normalize();
                n1.normalize();
                n2.normalize();
            } else {
                n0 = n1 = n2 = new Vector3f(0.0f, 1.0f, 0.0f);
            }

            // Calculate lighting for each vertex
            float light0 = calculateLighting(n0, lightDir, ambientStrength, roughness);
            float light1 = calculateLighting(n1, lightDir, ambientStrength, roughness);
            float light2 = calculateLighting(n2, lightDir, ambientStrength, roughness);

            // Apply lighting to base color
            Vector4f color0 = new Vector4f(
                Math.min(1.0f, baseColor.x * light0 + emissive.x),
                Math.min(1.0f, baseColor.y * light0 + emissive.y),
                Math.min(1.0f, baseColor.z * light0 + emissive.z),
                baseColor.w
            );
            Vector4f color1 = new Vector4f(
                Math.min(1.0f, baseColor.x * light1 + emissive.x),
                Math.min(1.0f, baseColor.y * light1 + emissive.y),
                Math.min(1.0f, baseColor.z * light1 + emissive.z),
                baseColor.w
            );
            Vector4f color2 = new Vector4f(
                Math.min(1.0f, baseColor.x * light2 + emissive.x),
                Math.min(1.0f, baseColor.y * light2 + emissive.y),
                Math.min(1.0f, baseColor.z * light2 + emissive.z),
                baseColor.w
            );

            // UV coordinates
            float u0 = texCoords != null && tIdx0 + 1 < texCoords.length ? texCoords[tIdx0] : 0.0f;
            float vt0 = texCoords != null && tIdx0 + 1 < texCoords.length ? texCoords[tIdx0 + 1] : 0.0f;
            float u1 = texCoords != null && tIdx1 + 1 < texCoords.length ? texCoords[tIdx1] : 0.0f;
            float vt1 = texCoords != null && tIdx1 + 1 < texCoords.length ? texCoords[tIdx1 + 1] : 0.0f;
            float u2 = texCoords != null && tIdx2 + 1 < texCoords.length ? texCoords[tIdx2] : 0.0f;
            float vt2 = texCoords != null && tIdx2 + 1 < texCoords.length ? texCoords[tIdx2 + 1] : 0.0f;

            // Reverse winding order: v2, v1, v0, v0 (degenerate quad)
            bufferBuilder.vertex(pose, v2.x, v2.y, v2.z)
                .uv(u2, vt2)
                .color(color2.x, color2.y, color2.z, color2.w)
                .normal(normalMatrix, n2.x, n2.y, n2.z)
                .endVertex();

            bufferBuilder.vertex(pose, v1.x, v1.y, v1.z)
                .uv(u1, vt1)
                .color(color1.x, color1.y, color1.z, color1.w)
                .normal(normalMatrix, n1.x, n1.y, n1.z)
                .endVertex();

            bufferBuilder.vertex(pose, v0.x, v0.y, v0.z)
                .uv(u0, vt0)
                .color(color0.x, color0.y, color0.z, color0.w)
                .normal(normalMatrix, n0.x, n0.y, n0.z)
                .endVertex();

            // Duplicate last vertex for degenerate quad
            bufferBuilder.vertex(pose, v0.x, v0.y, v0.z)
                .uv(u0, vt0)
                .color(color0.x, color0.y, color0.z, color0.w)
                .normal(normalMatrix, n0.x, n0.y, n0.z)
                .endVertex();
        }

        // Draw
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /**
     * Calculate simple diffuse lighting with roughness
     * @param normal Surface normal (normalized)
     * @param lightDir Light direction (normalized)
     * @param ambient Ambient light strength
     * @param roughness Surface roughness (0=smooth, 1=rough)
     * @return Light intensity [0, 1]
     */
    private float calculateLighting(Vector3f normal, Vector3f lightDir, float ambient, float roughness) {
        // Lambertian diffuse
        float diffuse = Math.max(0.0f, normal.dot(lightDir));

        // Roughness affects how diffuse the surface is
        // Smooth surfaces (low roughness) have sharper highlights
        // Rough surfaces (high roughness) scatter light more evenly
        float diffuseStrength = 0.6f + (roughness * 0.4f); // Map roughness to diffuse contribution

        return ambient + (diffuse * diffuseStrength);
    }

    private void renderPlaceholder() {
        if (framebufferId == -1) return;

        int prevFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        GL11.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebuffer);
    }

    /**
     * Get the texture ID of the rendered image.
     * Safe to call from UI thread.
     */
    public int getTextureId() {
        return colorTextureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void resize(int newWidth, int newHeight) {
        if (newWidth == width && newHeight == height) return;

        this.width = newWidth;
        this.height = newHeight;

        RenderSystem.recordRenderCall(() -> {
            try {
                createFramebuffer();
                needsRender = true;
                DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Resized to {}x{}", newWidth, newHeight);
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Resize failed", e);
            }
        });
    }

    public void destroy() {
        RenderSystem.recordRenderCall(() -> {
            try {
                if (framebufferId != -1) {
                    GL30.glDeleteFramebuffers(framebufferId);
                    framebufferId = -1;
                }
                if (colorTextureId != -1) {
                    GL11.glDeleteTextures(colorTextureId);
                    colorTextureId = -1;
                }
                if (depthRenderbufferId != -1) {
                    GL30.glDeleteRenderbuffers(depthRenderbufferId);
                    depthRenderbufferId = -1;
                }

                // Clean up texture cache to prevent texture pollution
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                for (Map<Integer, ResourceLocation> modelTextures : textureCache.values()) {
                    for (ResourceLocation texLoc : modelTextures.values()) {
                        textureManager.release(texLoc);
                        DebugManager.debug(DebugCategory.LOOKDEV, "[OffscreenRenderer] Released texture: {}", texLoc);
                    }
                }
                textureCache.clear();

                DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Destroyed");
            } catch (Exception e) {
                DebugManager.error(DebugCategory.LOOKDEV, "[OffscreenRenderer] Destroy failed", e);
            }
        });
    }
}
