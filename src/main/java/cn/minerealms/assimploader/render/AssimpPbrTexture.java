package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.material.LabPbrPacker;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;

/** A material-specific albedo. Oculus owns child textures created from the immutable packed images. */
public final class AssimpPbrTexture extends DynamicTexture {
    private LabPbrPacker.PackedTextures maps;
    private boolean ready;
    private boolean closed;
    private int normalId;
    private int specularId;
    private boolean configurationKnown;
    private boolean pbrEnabled;

    public AssimpPbrTexture() {
        super(white());
    }

    private static NativeImage white() {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixelRGBA(0, 0, 0xffffffff);
        return image;
    }

    public void publish(LabPbrPacker.Image albedo, LabPbrPacker.PackedTextures packed) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        NativeImage image = nativeImage(albedo);
        int previous = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            TextureUtil.prepareImage(getId(), image.getWidth(), image.getHeight());
            setPixels(image);
            image = null;
            upload();
            setFilter(false, false);
            maps = packed;
            ready = true;
        } finally {
            if (image != null) image.close();
            GlStateManager._bindTexture(previous);
        }
    }

    public static NativeImage nativeImage(LabPbrPacker.Image source) {
        NativeImage image = new NativeImage(source.width(), source.height(), false);
        try {
            for (int y = 0; y < source.height(); y++) {
                for (int x = 0; x < source.width(); x++) image.setPixelRGBA(x, y, source.pixel(x, y));
            }
            return image;
        } catch (RuntimeException | Error failure) {
            image.close();
            throw failure;
        }
    }

    public LabPbrPacker.PackedTextures maps() { return maps; }
    public boolean isReady() { return ready; }
    public boolean hasNormalMap() { return ready && maps != null && maps.normal() != null; }
    public boolean hasSpecularMap() { return ready && maps != null && maps.specular() != null; }
    public int normalTextureId() { return normalId; }
    public int specularTextureId() { return specularId; }

    public boolean updateConfiguration(boolean enabled) {
        boolean changed = !configurationKnown || pbrEnabled != enabled;
        configurationKnown = true;
        pbrEnabled = enabled;
        return changed;
    }

    public void recordBindings(int normal, int specular) {
        normalId = normal;
        specularId = specular;
    }

    @Override
    public void close() {
        closed = true;
        ready = false;
        maps = null;
        recordBindings(0, 0);
        super.close();
    }
}
