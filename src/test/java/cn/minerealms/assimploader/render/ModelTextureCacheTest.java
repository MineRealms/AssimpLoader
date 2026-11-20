package cn.minerealms.assimploader.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import cn.minerealms.assimploader.util.ResourceLocations;
class ModelTextureCacheTest {
    @org.junit.jupiter.api.Test void portableAliasesResolveTexturesInsideTheAssetBundle()throws Exception{
        Path project=java.nio.file.Files.createDirectories(directory.resolve("portable"));
        java.nio.file.Files.writeString(project.resolve("project.workbench.json"),"{}");
        Path assets=java.nio.file.Files.createDirectories(project.resolve("assets"));
        Path model=java.nio.file.Files.createDirectories(assets.resolve("demo/models")).resolve("model.fbx");
        java.nio.file.Files.writeString(model,"source");
        Path texture=java.nio.file.Files.createDirectories(assets.resolve("demo/textures")).resolve("body.png");
        java.nio.file.Files.writeString(texture,"pixels");
        java.nio.file.Files.writeString(model.resolveSibling("model.resources.json"),"{\"version\":1,\"model\":\"demo:models/model.fbx\",\"resources\":{\"demo:textures/body.png\":\"demo/textures/body.png\"}}");
        assertEquals(texture,ModelTextureCache.resolveFile(model,"demo:textures/body.png"));
        assertEquals(texture,ModelTextureCache.resolveFile(model,"../textures/body.png"));
        assertThrows(java.io.IOException.class,()->ModelTextureCache.resolveFile(model,"../../../outside.png"));
    }
    @TempDir Path directory;

    @Test
    void materialTexturesResolveRelativeToTheirOwnModelNamespace() {
        var source = ResourceLocations.of("othermod", "models/vehicle/body.gltf");
        assertEquals(ResourceLocations.of("othermod", "models/vehicle/textures/body.png"),
                ModelTextureCache.resolveResource(source, "textures\\body.png"));
        assertEquals(ResourceLocations.of("othermod", "models/shared.png"),
                ModelTextureCache.resolveResource(source, "../shared.png"));
        assertEquals(ResourceLocations.of("test", "textures/direct.png"),
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

    @Test void explicitEditorOverrideCanReadAnAbsoluteAuthorFileWithoutOpeningSceneTraversal() throws Exception {
        Path model=Files.createDirectories(directory.resolve("model")).resolve("model.fbx");
        Path chosen=Files.writeString(directory.resolve("selected.png"),"selected");
        assertEquals(chosen,ModelTextureCache.resolveFile(model,chosen.toString(),true));
        assertNull(ModelTextureCache.resolveFile(model,chosen.toString(),false));
        assertNull(ModelTextureCache.resolveFile(model,"../selected.png",true));
        assertNull(ModelTextureCache.resolveFile(model,"missing.png",true));
    }
}
