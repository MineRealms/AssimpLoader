package cn.minerealms.assimploader.core;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.assimp.Assimp.*;

class ResourceFileIOTest {
    @Test
    void callbacksUseElementCountsAndCStyleSignedSeekOffsets() {
        ResourceLocation resource = new ResourceLocation("example", "models/data.bin");
        try (ResourceFileIO io = new ResourceFileIO(resource, ignored -> new byte[]{10, 20, 30, 40, 50})) {
            ByteBuffer path = MemoryUtil.memUTF8(resource.getPath());
            ByteBuffer mode = MemoryUtil.memUTF8("rb");
            ByteBuffer output = MemoryUtil.memAlloc(8);
            try {
                long address = io.nativeIO().OpenProc().invoke(io.nativeIO().address(), MemoryUtil.memAddress(path), MemoryUtil.memAddress(mode));
                assertNotEquals(MemoryUtil.NULL, address);
                var file = org.lwjgl.assimp.AIFile.create(address);
                assertEquals(2, file.ReadProc().invoke(address, MemoryUtil.memAddress(output), 2, 4));
                assertEquals(4, file.TellProc().invoke(address));
                assertEquals(5, file.FileSizeProc().invoke(address));
                assertEquals(4, file.TellProc().invoke(address));
                assertEquals(aiReturn_SUCCESS, file.SeekProc().invoke(address, -2, aiOrigin_END));
                assertEquals(3, file.TellProc().invoke(address));
                assertEquals(aiReturn_FAILURE, file.SeekProc().invoke(address, 6, aiOrigin_SET));
                assertEquals(3, file.TellProc().invoke(address));
                io.nativeIO().CloseProc().invoke(io.nativeIO().address(), address);
            } finally {
                MemoryUtil.memFree(output);
                MemoryUtil.memFree(mode);
                MemoryUtil.memFree(path);
            }
        }
    }
}
