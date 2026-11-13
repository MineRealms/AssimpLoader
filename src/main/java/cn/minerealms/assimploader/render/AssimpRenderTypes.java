package cn.minerealms.assimploader.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Standard entity shaders with real triangles, preserving Oculus' smooth-normal tangent path. */
public final class AssimpRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> CUTOUT = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT = new HashMap<>();

    private AssimpRenderTypes() {
        super("assimploader_unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                256, false, false, () -> {}, () -> {});
    }

    public static RenderType entity(ResourceLocation texture, boolean translucent) {
        return (translucent ? TRANSLUCENT : CUTOUT).computeIfAbsent(texture,
                location -> createEntity(location, translucent));
    }

    private static RenderType createEntity(ResourceLocation texture, boolean translucent) {
        CompositeState state = CompositeState.builder()
                .setShaderState(translucent ? RENDERTYPE_ENTITY_TRANSLUCENT_SHADER : RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                .setCullState(translucent ? NO_CULL : CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);
        // Minecraft's built-in upload sorting is for quads; triangle transparency retains submission order.
        return create(translucent ? "assimploader_entity_translucent" : "assimploader_entity_cutout",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES, 1536, true, false, state);
    }

    public static void clear() {
        CUTOUT.clear();
        TRANSLUCENT.clear();
    }
}
