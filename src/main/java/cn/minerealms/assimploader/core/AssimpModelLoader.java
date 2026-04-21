package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Core Assimp model loader using LWJGL native bindings.
 *
 * Supports 40+ 3D model formats including:
 * - FBX (Autodesk Filmbox)
 * - GLTF/GLB (GL Transmission Format)
 * - Collada (DAE)
 * - OBJ (Wavefront)
 * - Blender (.blend)
 * - 3DS (3D Studio)
 * - And many more...
 *
 * Post-processing flags optimize models for Minecraft rendering:
 * - Triangulation: Convert all polygons to triangles
 * - Generate normals: Calculate smooth normals if missing
 * - Calculate tangent space: For normal mapping
 * - Join identical vertices: Optimize vertex count
 * - Optimize meshes: Reduce draw calls
 */
public class AssimpModelLoader {

    // Default post-processing flags for Minecraft rendering
    private static final int DEFAULT_FLAGS =
            aiProcess_Triangulate |           // Convert to triangles
            aiProcess_GenSmoothNormals |      // Generate smooth normals
            aiProcess_CalcTangentSpace |      // Calculate tangent space for normal maps
            aiProcess_JoinIdenticalVertices | // Merge duplicate vertices
            aiProcess_OptimizeMeshes |        // Optimize mesh count
            aiProcess_OptimizeGraph |         // Optimize scene graph
            aiProcess_FlipUVs;                // Flip UV coordinates for OpenGL

    public AssimpModelLoader() {
        AssimpLoader.LOGGER.info("AssimpModelLoader initialized with LWJGL Assimp bindings");
        logSupportedFormats();
    }

    /**
     * Load a 3D model from file path
     *
     * @param filePath Path to the model file
     * @return AssimpScene containing all model data
     * @throws IOException if file cannot be read
     */
    public AssimpScene loadModel(Path filePath) throws IOException {
        return loadModel(filePath, DEFAULT_FLAGS);
    }

    /**
     * Load a 3D model with custom post-processing flags
     *
     * @param filePath Path to the model file
     * @param flags Assimp post-processing flags
     * @return AssimpScene containing all model data
     * @throws IOException if file cannot be read
     */
    public AssimpScene loadModel(Path filePath, int flags) throws IOException {
        AssimpLoader.LOGGER.info("Loading model: {}", filePath);

        if (!Files.exists(filePath)) {
            throw new IOException("Model file not found: " + filePath);
        }

        // Load file into memory
        byte[] fileData = Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();
        String extension = getFileExtension(fileName);

        // Import scene from memory
        AIScene aiScene = aiImportFileFromMemory(
                ByteBuffer.wrap(fileData),
                flags,
                extension
        );

        if (aiScene == null) {
            String error = aiGetErrorString();
            throw new IOException("Failed to load model: " + error);
        }

        AssimpLoader.LOGGER.info("Model loaded successfully: {} meshes, {} materials, {} animations",
                aiScene.mNumMeshes(),
                aiScene.mNumMaterials(),
                aiScene.mNumAnimations());

        // Convert to our internal format
        return new AssimpScene(aiScene, filePath);
    }

    /**
     * Load a model from resource location (for resource packs)
     *
     * @param resourcePath Resource path (e.g., "models/custom/mymodel.fbx")
     * @param data Raw file data
     * @return AssimpScene containing all model data
     */
    public AssimpScene loadModelFromMemory(String resourcePath, byte[] data) {
        AssimpLoader.LOGGER.info("Loading model from memory: {}", resourcePath);

        String extension = getFileExtension(resourcePath);

        AIScene aiScene = aiImportFileFromMemory(
                ByteBuffer.wrap(data),
                DEFAULT_FLAGS,
                extension
        );

        if (aiScene == null) {
            String error = aiGetErrorString();
            AssimpLoader.LOGGER.error("Failed to load model from memory: {}", error);
            return null;
        }

        AssimpLoader.LOGGER.info("Model loaded from memory: {} meshes", aiScene.mNumMeshes());

        return new AssimpScene(aiScene, null);
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * Log all supported import formats
     */
    private void logSupportedFormats() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString description = AIString.calloc(stack);
            int count = (int) aiGetImportFormatCount();

            AssimpLoader.LOGGER.info("Assimp supports {} import formats:", count);

            for (int i = 0; i < count; i++) {
                AIImporterDesc desc = aiGetImportFormatDescription(i);
                if (desc != null) {
                    String name = desc.mNameString();
                    String extensions = desc.mFileExtensionsString();
                    AssimpLoader.LOGGER.debug("  - {} ({})", name, extensions);
                }
            }
        }
    }

    /**
     * Check if a file format is supported
     *
     * @param extension File extension (e.g., "fbx", "gltf")
     * @return true if supported
     */
    public boolean isFormatSupported(String extension) {
        return aiIsExtensionSupported(extension);
    }

    /**
     * Get Assimp version string
     */
    public static String getVersion() {
        int major = aiGetVersionMajor();
        int minor = aiGetVersionMinor();
        int revision = aiGetVersionRevision();
        return String.format("%d.%d.%d", major, minor, revision);
    }
}
