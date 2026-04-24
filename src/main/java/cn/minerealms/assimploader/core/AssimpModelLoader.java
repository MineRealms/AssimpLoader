package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // Hot-reload model cache
    private static final Map<String, AssimpScene> modelCache = new ConcurrentHashMap<>();

    // Base post-processing flags (common to both modes)
    private static final int BASE_FLAGS =
            aiProcess_Triangulate |           // Convert to triangles
            aiProcess_GenSmoothNormals |      // Generate smooth normals for better shading
            aiProcess_FlipUVs;                // Flip UV coordinates for OpenGL

    // Static mode: Bake transformations into vertices
    private static final int STATIC_MODE_FLAGS =
            BASE_FLAGS |
            aiProcess_PreTransformVertices;   // Bake node transformations into vertices

    // Dynamic mode: Preserve scene graph
    private static final int DYNAMIC_MODE_FLAGS =
            BASE_FLAGS;                       // Keep scene graph intact

    // Default mode
    private static final int DEFAULT_FLAGS = STATIC_MODE_FLAGS;

    // Full flags (may cause crashes with some files)
    private static final int FULL_FLAGS =
            aiProcess_Triangulate |
            aiProcess_GenSmoothNormals |
            aiProcess_CalcTangentSpace |
            aiProcess_JoinIdenticalVertices |
            aiProcess_OptimizeMeshes |
            aiProcess_OptimizeGraph |
            aiProcess_FlipUVs |
            aiProcess_MakeLeftHanded |
            aiProcess_FlipWindingOrder;

    private boolean autoConvertCoordinates = true;

    public AssimpModelLoader() {
        DebugManager.info(DebugCategory.CORE, "AssimpModelLoader initialized with LWJGL Assimp bindings");
        // logSupportedFormats(); // Temporarily disabled - requires LWJGL Assimp natives
    }

    /**
     * Load a 3D model from file path
     *
     * @param filePath Path to the model file
     * @return AssimpScene containing all model data
     * @throws IOException if file cannot be read
     */
    public AssimpScene loadModel(Path filePath) throws IOException {
        return loadModel(filePath, ModelImportMode.STATIC);
    }

    /**
     * Load a 3D model with automatic mode detection.
     * Analyzes the scene to determine if STATIC or DYNAMIC mode is more appropriate.
     *
     * Detection heuristics:
     * - Has animations -> DYNAMIC
     * - Complex node hierarchy (>3 levels) -> DYNAMIC
     * - Many transformed nodes (>5) -> DYNAMIC
     * - Otherwise -> STATIC
     *
     * @param filePath Path to the model file
     * @param autoDetect Set to true to enable automatic mode detection
     * @return AssimpScene containing all model data
     * @throws IOException if file cannot be read
     */
    public AssimpScene loadModel(Path filePath, boolean autoDetect) throws IOException {
        if (!autoDetect) {
            return loadModel(filePath, ModelImportMode.STATIC);
        }

        DebugManager.info(DebugCategory.CORE, "Loading model with auto-detection: {}", filePath);

        if (!Files.exists(filePath)) {
            throw new IOException("Model file not found: " + filePath);
        }

        // Load with minimal flags first to analyze structure
        String absolutePath = filePath.toAbsolutePath().toString();
        AIScene aiScene = aiImportFile(absolutePath, aiProcess_Triangulate);

        if (aiScene == null) {
            String error = aiGetErrorString();
            throw new IOException("Failed to load model for analysis: " + error);
        }

        // Detect best mode
        ModelImportMode detectedMode = ModelImportMode.detectFromScene(aiScene);
        DebugManager.info(DebugCategory.CORE, "Auto-detected import mode: {} for {}", detectedMode, filePath.getFileName());

        // Release the analysis scene
        Assimp.aiReleaseImport(aiScene);

        // Load with proper mode
        return loadModel(filePath, detectedMode);
    }

    /**
     * Load a 3D model with specified import mode
     *
     * @param filePath Path to the model file
     * @param mode Import mode (STATIC or DYNAMIC)
     * @return AssimpScene containing all model data
     * @throws IOException if file cannot be read
     */
    public AssimpScene loadModel(Path filePath, ModelImportMode mode) throws IOException {
        int flags = mode == ModelImportMode.STATIC ? STATIC_MODE_FLAGS : DYNAMIC_MODE_FLAGS;
        return loadModelInternal(filePath, flags, mode);
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
        // Infer mode from flags
        ModelImportMode mode = (flags & aiProcess_PreTransformVertices) != 0
            ? ModelImportMode.STATIC
            : ModelImportMode.DYNAMIC;
        return loadModelInternal(filePath, flags, mode);
    }

    private AssimpScene loadModelInternal(Path filePath, int flags, ModelImportMode mode) throws IOException {
        DebugManager.info(DebugCategory.CORE, "Loading model: {} with mode: {}", filePath.getFileName(), mode);

        if (!Files.exists(filePath)) {
            throw new IOException("Model file not found: " + filePath);
        }

        // Use aiImportFile directly for file paths (more stable than memory import)
        String absolutePath = filePath.toAbsolutePath().toString();
        DebugManager.info(DebugCategory.CORE, "Importing from file: {}", absolutePath);

        AIScene aiScene = aiImportFile(absolutePath, flags);

        if (aiScene == null) {
            String error = aiGetErrorString();
            throw new IOException("Failed to load model: " + error);
        }

        DebugManager.info(DebugCategory.CORE, "Model loaded successfully: {} meshes, {} materials, {} animations",
                aiScene.mNumMeshes(),
                aiScene.mNumMaterials(),
                aiScene.mNumAnimations());

        // Detect source coordinate system
        String fileName = filePath.getFileName().toString();
        String fileExtension = getFileExtension(fileName);
        CoordinateSystemConverter.CoordinateSystem sourceSystem =
                CoordinateSystemConverter.detectCoordinateSystem(fileExtension);

        DebugManager.debug(DebugCategory.CORE, "Detected coordinate system: {} for file type: {}",
                sourceSystem, fileExtension);

        // Log scene graph structure for DYNAMIC mode
        if (mode == ModelImportMode.DYNAMIC && aiScene.mRootNode() != null) {
            DebugManager.info(DebugCategory.CORE, "Scene graph structure (DYNAMIC mode):");
            logNodeHierarchy(aiScene.mRootNode(), 0, 3);  // Log up to 3 levels
        }

        // Convert to our internal format
        return new AssimpScene(aiScene, filePath, sourceSystem, mode);
    }

    /**
     * Log node hierarchy for debugging (limited depth to avoid spam)
     */
    private void logNodeHierarchy(org.lwjgl.assimp.AINode node, int depth, int maxDepth) {
        if (node == null || depth > maxDepth) {
            return;
        }

        String indent = "  ".repeat(depth);
        String nodeName = node.mName().dataString();
        int numMeshes = node.mNumMeshes();
        int numChildren = node.mNumChildren();

        DebugManager.info(DebugCategory.CORE, "{}├─ {} (meshes: {}, children: {})",
            indent, nodeName, numMeshes, numChildren);

        // Recursively log children
        org.lwjgl.PointerBuffer children = node.mChildren();
        if (children != null && depth < maxDepth) {
            for (int i = 0; i < numChildren; i++) {
                org.lwjgl.assimp.AINode child = org.lwjgl.assimp.AINode.create(children.get(i));
                logNodeHierarchy(child, depth + 1, maxDepth);
            }
        }
    }

    /**
     * Load a model from resource location (for resource packs)
     *
     * @param resourcePath Resource path (e.g., "models/custom/mymodel.fbx")
     * @param data Raw file data
     * @return AssimpScene containing all model data
     */
    public AssimpScene loadModelFromMemory(String resourcePath, byte[] data) {
        DebugManager.info(DebugCategory.CORE, "Loading model from memory: {}, size: {} bytes", resourcePath, data.length);

        // Validate input
        if (data == null || data.length == 0) {
            DebugManager.error(DebugCategory.CORE, "Invalid input: data is null or empty");
            return null;
        }

        String extension = getFileExtension(resourcePath);
        DebugManager.info(DebugCategory.CORE, "Detected file extension: {}", extension);

        // Validate extension
        if (extension == null || extension.isEmpty()) {
            DebugManager.error(DebugCategory.CORE, "Invalid file extension from path: {}", resourcePath);
            return null;
        }

        AIScene aiScene = null;
        try {
            // Test if Assimp is available before attempting import
            try {
                // Use aiGetVersionMajor() instead of aiGetVersionString()
                int major = aiGetVersionMajor();
                int minor = aiGetVersionMinor();
                int revision = aiGetVersionRevision();
                DebugManager.info(DebugCategory.CORE, "Assimp version check: {}.{}.{}", major, minor, revision);
            } catch (Throwable t) {
                DebugManager.error(DebugCategory.CORE, "Assimp native library not accessible: {}", t.getMessage());
                return null;
            }

            DebugManager.info(DebugCategory.CORE, "Calling aiImportFileFromMemory with flags: {} (0x{}) for extension: {}",
                    DEFAULT_FLAGS, Integer.toHexString(DEFAULT_FLAGS), extension);

            // IMPORTANT: Use direct ByteBuffer for native code
            // Wrapped arrays may cause issues with JNI
            ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(data.length);
            buffer.put(data);
            buffer.flip();
            DebugManager.info(DebugCategory.CORE, "Direct ByteBuffer created: capacity={}, position={}, limit={}, isDirect={}",
                    buffer.capacity(), buffer.position(), buffer.limit(), buffer.isDirect());

            // Try to import with reduced flags first (safer)
            aiScene = aiImportFileFromMemory(buffer, DEFAULT_FLAGS, extension);

            if (aiScene == null) {
                String error = aiGetErrorString();
                DebugManager.error(DebugCategory.CORE, "Assimp import failed with reduced flags: {}", error);
                DebugManager.info(DebugCategory.CORE, "Trying with minimal flags (triangulate only)...");

                // Try with absolute minimal flags
                buffer.rewind();
                aiScene = aiImportFileFromMemory(buffer, aiProcess_Triangulate, extension);

                if (aiScene == null) {
                    error = aiGetErrorString();
                    DebugManager.error(DebugCategory.CORE, "Assimp import failed even with minimal flags: {}", error);
                    return null;
                }
            }

            DebugManager.info(DebugCategory.CORE, "Assimp import successful: {} meshes, {} materials, {} animations",
                    aiScene.mNumMeshes(), aiScene.mNumMaterials(), aiScene.mNumAnimations());

        } catch (Exception e) {
            DebugManager.error(DebugCategory.CORE, "Exception during Assimp import: {}", e.getMessage(), e);
            return null;
        } catch (Error e) {
            // Catch native errors (like UnsatisfiedLinkError or JVM crashes)
            DebugManager.error(DebugCategory.CORE, "Native error during Assimp import: {}", e.getMessage(), e);
            return null;
        }

        if (aiScene == null) {
            DebugManager.error(DebugCategory.CORE, "Failed to load model from memory: aiScene is null");
            return null;
        }

        // Detect source coordinate system
        String fileExtension = getFileExtension(resourcePath);
        CoordinateSystemConverter.CoordinateSystem sourceSystem =
                CoordinateSystemConverter.detectCoordinateSystem(fileExtension);

        // Infer mode from flags used
        ModelImportMode mode = (DEFAULT_FLAGS & aiProcess_PreTransformVertices) != 0
            ? ModelImportMode.STATIC
            : ModelImportMode.DYNAMIC;

        return new AssimpScene(aiScene, null, sourceSystem, mode);
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

            DebugManager.info(DebugCategory.CORE, "Assimp supports {} import formats:", count);

            for (int i = 0; i < count; i++) {
                AIImporterDesc desc = aiGetImportFormatDescription(i);
                if (desc != null) {
                    String name = desc.mNameString();
                    String extensions = desc.mFileExtensionsString();
                    DebugManager.debug(DebugCategory.CORE, "  - {} ({})", name, extensions);
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

    /**
     * Enable or disable automatic coordinate system conversion
     */
    public void setAutoConvertCoordinates(boolean autoConvert) {
        this.autoConvertCoordinates = autoConvert;
    }

    public boolean isAutoConvertCoordinates() {
        return autoConvertCoordinates;
    }

    /**
     * Update model cache for hot-reload
     * @param modelName Model name (without extension)
     * @param scene Loaded scene
     */
    public static void updateModelCache(String modelName, AssimpScene scene) {
        modelCache.put(modelName, scene);
        DebugManager.info(DebugCategory.CORE, "Updated model cache: {}, scene object: {}, has textures: {}, texture count: {}",
            modelName, System.identityHashCode(scene), scene.hasEmbeddedTextures(), scene.getTextureCount());
    }

    /**
     * Get cached model with priority: hot-reload > resource pack
     * @param modelName Model name (without extension)
     * @return Cached scene or null
     */
    public static AssimpScene getCachedModel(String modelName) {
        // PRIORITY 1: Check hot-reload cache (external models)
        AssimpScene hotReloadScene = cn.minerealms.assimploader.hotreload.ModelHotReloadManager.getModel(modelName);
        if (hotReloadScene != null) {
            return hotReloadScene;
        }

        // PRIORITY 2: Check resource pack cache
        return modelCache.get(modelName);
    }

    /**
     * Get all cached models
     */
    public static Map<String, AssimpScene> getAllCachedModels() {
        return new ConcurrentHashMap<>(modelCache);
    }

    /**
     * Clear model cache
     */
    public static void clearCache() {
        modelCache.clear();
        DebugManager.info(DebugCategory.CORE, "Cleared model cache");
    }

    /**
     * Static method for hot-reload - load model from file path string
     * @param filePath Absolute file path
     * @return Loaded scene or null
     */
    public static AssimpScene loadModelStatic(String filePath) {
        try {
            AssimpModelLoader loader = new AssimpModelLoader();
            return loader.loadModel(Path.of(filePath));
        } catch (IOException e) {
            DebugManager.error(DebugCategory.CORE, "Failed to load model from: {}", filePath, e);
            return null;
        }
    }
}
