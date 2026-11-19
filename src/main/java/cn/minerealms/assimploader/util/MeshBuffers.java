package cn.minerealms.assimploader.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Version-neutral immediate-mode mesh entry points.
 * <p>
 * 1.20.1 obtains a long-lived {@link BufferBuilder} from {@link Tesselator#getBuilder()} and then
 * starts a batch with {@code begin(mode, format)}, finishing it with {@code end()}. 1.21 deleted
 * both: {@code Tesselator#begin(mode, format)} now returns a fresh builder that is finished with
 * {@code build()}. Routing the three call sites through this helper keeps them free of
 * {@code //?} conditions.
 */
public final class MeshBuffers {
    private MeshBuffers() {}

    /** Starts a batch and returns the builder to write vertices into. */
    public static BufferBuilder begin(Tesselator tesselator, VertexFormat.Mode mode, VertexFormat format) {
        //? if forge {
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(mode, format);
        return builder;
        //?}
        //? if neoforge {
        /*
        return tesselator.begin(mode, format);
        *///?}
    }

    /** Draws the batch held by {@code builder} with its current shader. */
    public static void draw(BufferBuilder builder) {
        //? if forge {
        BufferUploader.drawWithShader(builder.end());
        //?}
        //? if neoforge {
        /*
        BufferUploader.drawWithShader(builder.build());
        *///?}
    }
}
