package cn.minerealms.assimploader.lookdev.nodeeditor.serialization;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages material file I/O operations.
 *
 * Features:
 * - Save/load materials to/from JSON files
 * - List available materials
 * - Auto-save functionality
 * - File validation
 * - Error handling
 *
 * File format: .material.json
 * Location: config/assimploader/materials/
 */
public class MaterialFileManager {

    private static final Path MATERIALS_DIR = Paths.get("config/assimploader/materials");
    private static final String FILE_EXTENSION = ".material.json";

    /**
     * Initialize materials directory
     */
    public static void initialize() {
        try {
            Files.createDirectories(MATERIALS_DIR);
            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Initialized materials directory: " + MATERIALS_DIR);
        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to create materials directory: " + e.getMessage());
        }
    }

    /**
     * Save material to file
     */
    public static boolean save(String materialName, List<Node> nodes, List<NodeConnection> connections) {
        try {
            // Ensure directory exists
            Files.createDirectories(MATERIALS_DIR);

            // Serialize to JSON
            String json = MaterialSerializer.serialize(materialName, nodes, connections);
            if (json == null) {
                return false;
            }

            // Write to file
            Path filePath = getMaterialPath(materialName);
            Files.writeString(filePath, json);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Saved material: " + materialName + " to " + filePath);
            return true;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to save material: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load material from file
     */
    public static MaterialDeserializer.DeserializeResult load(String materialName) {
        try {
            Path filePath = getMaterialPath(materialName);

            if (!Files.exists(filePath)) {
                MaterialDeserializer.DeserializeResult result = new MaterialDeserializer.DeserializeResult();
                result.error = "Material file not found: " + materialName;
                return result;
            }

            // Read file
            String json = Files.readString(filePath);

            // Deserialize
            MaterialDeserializer.DeserializeResult result = MaterialDeserializer.deserialize(json);

            if (result.isSuccess()) {
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[MaterialFileManager] Loaded material: " + materialName + " from " + filePath);
            }

            return result;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to load material: " + e.getMessage());
            e.printStackTrace();

            MaterialDeserializer.DeserializeResult result = new MaterialDeserializer.DeserializeResult();
            result.error = "IO error: " + e.getMessage();
            return result;
        }
    }

    /**
     * Delete material file
     */
    public static boolean delete(String materialName) {
        try {
            Path filePath = getMaterialPath(materialName);

            if (!Files.exists(filePath)) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[MaterialFileManager] Material file not found: " + materialName);
                return false;
            }

            Files.delete(filePath);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Deleted material: " + materialName);
            return true;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to delete material: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if material exists
     */
    public static boolean exists(String materialName) {
        Path filePath = getMaterialPath(materialName);
        return Files.exists(filePath);
    }

    /**
     * List all available materials
     */
    public static List<String> listMaterials() {
        try {
            if (!Files.exists(MATERIALS_DIR)) {
                return new ArrayList<>();
            }

            try (Stream<Path> paths = Files.list(MATERIALS_DIR)) {
                return paths
                    .filter(path -> path.toString().endsWith(FILE_EXTENSION))
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
                    })
                    .collect(Collectors.toList());
            }

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to list materials: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get material file path
     */
    private static Path getMaterialPath(String materialName) {
        String fileName = sanitizeFileName(materialName) + FILE_EXTENSION;
        return MATERIALS_DIR.resolve(fileName);
    }

    /**
     * Sanitize file name (remove invalid characters)
     */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /**
     * Validate material file
     */
    public static boolean validate(String materialName) {
        try {
            Path filePath = getMaterialPath(materialName);

            if (!Files.exists(filePath)) {
                return false;
            }

            String json = Files.readString(filePath);
            return MaterialDeserializer.validate(json);

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get material info without full deserialization
     */
    public static MaterialInfo getInfo(String materialName) {
        try {
            Path filePath = getMaterialPath(materialName);

            if (!Files.exists(filePath)) {
                return null;
            }

            String json = Files.readString(filePath);

            MaterialInfo info = new MaterialInfo();
            info.name = MaterialDeserializer.getMaterialName(json);
            info.version = MaterialDeserializer.getVersion(json);
            info.filePath = filePath.toString();
            info.fileSize = Files.size(filePath);
            info.lastModified = Files.getLastModifiedTime(filePath).toMillis();

            return info;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Material file information
     */
    public static class MaterialInfo {
        public String name;
        public String version;
        public String filePath;
        public long fileSize;
        public long lastModified;
    }

    /**
     * Export material to custom location
     */
    public static boolean export(String materialName, Path targetPath) {
        try {
            Path sourcePath = getMaterialPath(materialName);

            if (!Files.exists(sourcePath)) {
                return false;
            }

            Files.copy(sourcePath, targetPath);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Exported material: " + materialName + " to " + targetPath);
            return true;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to export material: " + e.getMessage());
            return false;
        }
    }

    /**
     * Import material from custom location
     */
    public static boolean importMaterial(Path sourcePath, String materialName) {
        try {
            if (!Files.exists(sourcePath)) {
                return false;
            }

            // Validate before importing
            String json = Files.readString(sourcePath);
            if (!MaterialDeserializer.validate(json)) {
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[MaterialFileManager] Invalid material file: " + sourcePath);
                return false;
            }

            // Copy to materials directory
            Path targetPath = getMaterialPath(materialName);
            Files.copy(sourcePath, targetPath);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Imported material: " + materialName + " from " + sourcePath);
            return true;

        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[MaterialFileManager] Failed to import material: " + e.getMessage());
            return false;
        }
    }
}
