package cn.minerealms.assimploader.lookdev.preset;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.lookdev.material.MaterialOverride;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manager for saving and loading material presets.
 *
 * Presets are stored as JSON files in .minecraft/lookdev/presets/
 */
public class PresetManager {

    private static final String PRESETS_DIR = "lookdev/presets";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Path presetsPath;

    public PresetManager() {
        // Get Minecraft directory
        Path minecraftDir = Paths.get(".").toAbsolutePath().normalize();
        this.presetsPath = minecraftDir.resolve(PRESETS_DIR);

        // Create presets directory if it doesn't exist
        try {
            Files.createDirectories(presetsPath);
            AssimpLoader.LOGGER.info("Presets directory: {}", presetsPath);
        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to create presets directory", e);
        }
    }

    /**
     * Save a preset to disk
     *
     * @param preset Preset to save
     * @return true if successful
     */
    public boolean savePreset(MaterialPreset preset) {
        if (preset == null || preset.getName() == null || preset.getName().isEmpty()) {
            AssimpLoader.LOGGER.error("Cannot save preset: invalid name");
            return false;
        }

        try {
            // Sanitize filename
            String filename = sanitizeFilename(preset.getName()) + ".json";
            Path presetFile = presetsPath.resolve(filename);

            // Serialize to JSON
            String json = GSON.toJson(preset);

            // Write to file
            Files.writeString(presetFile, json);

            AssimpLoader.LOGGER.info("Saved preset: {}", presetFile);
            return true;

        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to save preset: {}", preset.getName(), e);
            return false;
        }
    }

    /**
     * Load a preset from disk
     *
     * @param name Preset name
     * @return Loaded preset, or null if not found
     */
    @Nullable
    public MaterialPreset loadPreset(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        try {
            // Sanitize filename
            String filename = sanitizeFilename(name) + ".json";
            Path presetFile = presetsPath.resolve(filename);

            if (!Files.exists(presetFile)) {
                AssimpLoader.LOGGER.warn("Preset not found: {}", name);
                return null;
            }

            // Read from file
            String json = Files.readString(presetFile);

            // Deserialize from JSON
            MaterialPreset preset = GSON.fromJson(json, MaterialPreset.class);

            AssimpLoader.LOGGER.info("Loaded preset: {}", name);
            return preset;

        } catch (IOException | JsonSyntaxException e) {
            AssimpLoader.LOGGER.error("Failed to load preset: {}", name, e);
            return null;
        }
    }

    /**
     * Delete a preset from disk
     *
     * @param name Preset name
     * @return true if successful
     */
    public boolean deletePreset(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        try {
            String filename = sanitizeFilename(name) + ".json";
            Path presetFile = presetsPath.resolve(filename);

            if (!Files.exists(presetFile)) {
                AssimpLoader.LOGGER.warn("Preset not found: {}", name);
                return false;
            }

            Files.delete(presetFile);
            AssimpLoader.LOGGER.info("Deleted preset: {}", name);
            return true;

        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to delete preset: {}", name, e);
            return false;
        }
    }

    /**
     * List all available presets
     *
     * @return List of preset names
     */
    public List<String> listPresets() {
        List<String> presets = new ArrayList<>();

        try {
            if (!Files.exists(presetsPath)) {
                return presets;
            }

            try (Stream<Path> files = Files.list(presetsPath)) {
                presets = files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .map(p -> {
                            String filename = p.getFileName().toString();
                            return filename.substring(0, filename.length() - 5); // Remove .json
                        })
                        .collect(Collectors.toList());
            }

        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to list presets", e);
        }

        return presets;
    }

    /**
     * Check if a preset exists
     *
     * @param name Preset name
     * @return true if preset exists
     */
    public boolean presetExists(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        String filename = sanitizeFilename(name) + ".json";
        Path presetFile = presetsPath.resolve(filename);
        return Files.exists(presetFile);
    }

    /**
     * Sanitize filename to remove invalid characters
     */
    private String sanitizeFilename(String name) {
        // Replace invalid characters with underscore
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Get the presets directory path
     */
    public Path getPresetsPath() {
        return presetsPath;
    }
}
