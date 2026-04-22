# FBX Model Hot-Reload System

## Overview

This system allows you to hot-reload FBX models without restarting Minecraft, making model iteration much faster during development.

## Setup

1. **External Model Directory**: `H:\MinecraftMods\ImprovedMobs\models`
   - Place your FBX files in this directory
   - Files should be named with `.fbx` extension (e.g., `cnc.fbx`, `machine.fbx`)

2. **Model Naming**: The model name is extracted from the filename without extension
   - `cnc.fbx` → model name: `cnc`
   - `my_model.fbx` → model name: `my_model`

## Usage

### In-Game Command

```
/assimploader reload
```

**Requirements**: OP level 2 (operator permissions)

**What it does**:
1. Scans `H:\MinecraftMods\ImprovedMobs\models` directory
2. Loads all `.fbx` files found
3. Updates the model cache with new data
4. Triggers a resource pack reload to refresh all models in-game

### Workflow

1. **Edit your FBX model** in Blender/Maya/etc.
2. **Export to** `H:\MinecraftMods\ImprovedMobs\models\your_model.fbx`
3. **Run command** `/assimploader reload` in-game
4. **See changes** immediately without restarting Minecraft!

## Technical Details

### Architecture

- **ModelHotReloadManager**: Scans directory and loads FBX files
- **AssimpModelLoader**: Static cache for hot-reloaded models
- **AssimpGeometryLoader**: Checks cache before loading from resources
- **ModelHotReloadCommand**: Brigadier command registration

### Model Priority

When baking a model, the system checks in this order:
1. **Hot-reloaded cache** (from `/assimploader reload` command)
2. **Resource pack** (from `assets/modid/models/...`)
3. **JAR resources** (bundled with mod)

### Supported Features

All standard FBX features are supported:
- ✅ PhongE materials with diffuse colors
- ✅ Vertex colors
- ✅ UV coordinates
- ✅ Normals
- ✅ Multiple meshes
- ✅ Coordinate system conversion (right-hand → left-hand)
- ✅ Minecraft lighting integration

## Example

```bash
# 1. Export your model
# Blender: File → Export → FBX (.fbx)
# Save to: H:\MinecraftMods\ImprovedMobs\models\cnc.fbx

# 2. In Minecraft, run:
/assimploader reload

# Output:
# [AssimpLoader] Starting model hot-reload...
# [AssimpLoader] Hot-reloading model: cnc from H:\MinecraftMods\ImprovedMobs\models\cnc.fbx
# [AssimpLoader] Successfully reloaded model: cnc
# [AssimpLoader] Successfully reloaded 1 model(s)!
```

## Troubleshooting

### Command not found
- Make sure you have OP permissions (level 2+)
- Check that the mod is loaded (`/mods` command)

### Models not updating
- Verify the FBX file is in the correct directory
- Check the filename matches the model reference in your JSON
- Look for errors in `logs/latest.log`

### Directory not found
- The system will create the directory automatically on first use
- Manually create: `mkdir H:\MinecraftMods\ImprovedMobs\models`

## Performance Notes

- Hot-reload triggers a full resource pack reload (`F3+T` equivalent)
- This may cause a brief freeze (1-3 seconds) depending on resource pack size
- Model cache is stored in memory - no disk I/O after initial load
- Concurrent loading is thread-safe (uses `ConcurrentHashMap`)

## Future Enhancements

Potential improvements:
- [ ] Watch directory for automatic reload on file change
- [ ] Selective model reload (only changed files)
- [ ] Hot-reload for animations
- [ ] Hot-reload for textures
- [ ] Web-based model preview
