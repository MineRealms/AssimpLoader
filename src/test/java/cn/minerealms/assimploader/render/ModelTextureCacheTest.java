package cn.minerealms.assimploader.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelTextureCacheTest {
    @TempDir Path directory;

    @Test
    void materialTexturesResolveRelativeToTheirOwnModelNamespace() {
        var source = new ResourceLocation("othermod", "models/vehicle/body.gltf");
        assertEquals(new ResourceLocation("othermod", "models/vehicle/textures/body.png"),
                ModelTextureCache.resolveResource(source, "textures\\body.png"));
        assertEquals(new ResourceLocation("othermod", "models/shared.png"),
                ModelTextureCache.resolveResource(source, "../shared.png"));
        assertEquals(new ResourceLocation("test", "textures/direct.png"),
                ModelTextureCache.resolveResource(source, "test:textures/direct.png"));
        assertNull(ModelTextureCache.resolveResource(source, "../../../../secret.png"));
        assertNull(ModelTextureCache.resolveResource(source, "C:/private.png"));
        assertNull(ModelTextureCache.resolveResource(source, "*0"));
    }

    @Test
    void filesystemImagesRemainWithinTheModelDirectory() throws Exception {
        Path modelDirectory = Files.createDirectory(directory.resolve("model"));
        Path image = Files.writeString(modelDirectory.resolve("body.png"), "image");
        Files.writeString(directory.resolve("outside.png"), "outside");
        Path source = modelDirectory.resolve("body.fbx");
        assertEquals(image, ModelTextureCache.resolveFile(source, "body.png"));
        assertNull(ModelTextureCache.resolveFile(source, "../outside.png"));
        assertNull(ModelTextureCache.resolveFile(source, image.toString()));
        assertNull(ModelTextureCache.resolveFile(source, "other:textures/body.png"));
        assertNull(ModelTextureCache.resolveFile(source, "*0"));
    }
}
