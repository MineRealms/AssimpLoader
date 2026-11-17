package cn.minerealms.assimploader.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VertexBuffer.class)
public interface VertexBufferSelectionAccess {
    @Accessor("indexCount") int assimploader$getIndexCount();
    @Accessor("indexCount") void assimploader$setIndexCount(int value);
    @Accessor("indexType") VertexFormat.IndexType assimploader$getIndexType();
    @Accessor("indexType") void assimploader$setIndexType(VertexFormat.IndexType value);
    @Accessor("sequentialIndices") RenderSystem.AutoStorageIndexBuffer assimploader$getSequentialIndices();
    @Accessor("sequentialIndices") void assimploader$setSequentialIndices(RenderSystem.AutoStorageIndexBuffer value);
}
