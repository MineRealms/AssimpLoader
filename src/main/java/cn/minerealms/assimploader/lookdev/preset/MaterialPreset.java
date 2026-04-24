package cn.minerealms.assimploader.lookdev.preset;

import cn.minerealms.assimploader.lookdev.material.MaterialOverride;

import javax.annotation.Nullable;

/**
 * Material preset containing saved material override parameters.
 *
 * Presets can be saved to disk and loaded later to quickly apply
 * material parameter configurations.
 */
public class MaterialPreset {

    private String name;
    private String description;
    private MaterialOverride override;

    // Metadata
    private long createdTime;
    private long modifiedTime;
    private String author;

    public MaterialPreset(String name, MaterialOverride override) {
        this.name = name;
        this.override = override;
        this.createdTime = System.currentTimeMillis();
        this.modifiedTime = this.createdTime;
    }

    public MaterialPreset(String name, String description, MaterialOverride override) {
        this(name, override);
        this.description = description;
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.modifiedTime = System.currentTimeMillis();
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.modifiedTime = System.currentTimeMillis();
    }

    public MaterialOverride getOverride() {
        return override;
    }

    public void setOverride(MaterialOverride override) {
        this.override = override;
        this.modifiedTime = System.currentTimeMillis();
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(long modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    @Nullable
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return "MaterialPreset{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
