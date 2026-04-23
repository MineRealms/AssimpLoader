# Oculus Integration Testing Checklist

## Overview

This document provides a comprehensive testing checklist for the Oculus shader integration in AssimpLoader. All tests should pass before merging the OculusSupport branch.

## Test Environment Setup

### Required Configurations

1. **Vanilla (No Oculus)**
   - Minecraft 1.20.1 with Forge
   - AssimpLoader mod only
   - No Oculus installed

2. **Oculus Installed, Shaders Disabled**
   - Minecraft 1.20.1 with Forge
   - AssimpLoader + Oculus mods
   - Shaders disabled in video settings

3. **Oculus with Basic Shader Pack**
   - Minecraft 1.20.1 with Forge
   - AssimpLoader + Oculus mods
   - Basic shader pack (e.g., Sildur's Vibrant Shaders Lite)
   - Shaders enabled

4. **Oculus with PBR Shader Pack**
   - Minecraft 1.20.1 with Forge
   - AssimpLoader + Oculus mods
   - PBR-capable shader pack (BSL, Complementary, Seus PTGI)
   - Shaders enabled

### Test Models

Use these existing models:
- **CNC Machine** (`models/block/cnc.fbx`) - Opaque model
- **CCB Block** (`models/block/ccb.fbx`) - Transparent model

## Phase 1: Soft Dependency Detection

### Test 1.1: Mod Loading Without Oculus
- [ ] Launch Minecraft without Oculus installed
- [ ] Check logs for: "Oculus not found - using vanilla rendering"
- [ ] Verify no ClassNotFoundException or NoClassDefFoundError
- [ ] Verify game loads successfully

**Expected Result**: Clean startup, vanilla rendering active

### Test 1.2: Mod Loading With Oculus
- [ ] Launch Minecraft with Oculus installed
- [ ] Check logs for: "Oculus detected - shader support enabled"
- [ ] Verify OculusCompat initializes successfully
- [ ] Verify game loads successfully

**Expected Result**: Clean startup, shader support detected

### Test 1.3: Runtime Detection
- [ ] Place test block in world
- [ ] Check logs for shader status detection
- [ ] Toggle shaders on/off in video settings
- [ ] Verify detection updates correctly

**Expected Result**: Dynamic detection of shader state changes

## Phase 2: Vanilla Rendering (No Oculus)

### Test 2.1: Block Entity Rendering
- [ ] Place CNC machine block in world
- [ ] Verify model renders correctly
- [ ] Check texture is applied
- [ ] Verify lighting works (day/night cycle)
- [ ] Check no visual artifacts or missing faces

**Expected Result**: Perfect vanilla rendering

### Test 2.2: Item Rendering
- [ ] Hold CNC machine item in hand (first person)
- [ ] View in third person
- [ ] Place in inventory GUI
- [ ] Drop on ground
- [ ] Place in item frame

**Expected Result**: Correct rendering in all contexts

### Test 2.3: Transparent Model
- [ ] Place CCB block in world
- [ ] Verify transparency works correctly
- [ ] Check no z-fighting or sorting issues
- [ ] Verify lighting through transparent parts

**Expected Result**: Proper transparency rendering

### Test 2.4: Performance
- [ ] Place 50+ test blocks in world
- [ ] Measure FPS (F3 debug screen)
- [ ] Verify no performance degradation
- [ ] Check memory usage is stable

**Expected Result**: No performance impact vs. baseline

## Phase 3: Oculus Installed, Shaders Disabled

### Test 3.1: Graceful Degradation
- [ ] Launch with Oculus but shaders disabled
- [ ] Verify vanilla rendering is used
- [ ] Check logs for: shader detection returns false
- [ ] Verify no shader-specific code executes

**Expected Result**: Identical to vanilla rendering

### Test 3.2: No Performance Overhead
- [ ] Measure FPS with shaders disabled
- [ ] Compare to vanilla (no Oculus) FPS
- [ ] Verify difference is < 1%

**Expected Result**: Negligible performance difference

## Phase 4: Shader-Aware Rendering

### Test 4.1: Basic Shader Compatibility
- [ ] Enable basic shader pack (Sildur's Lite)
- [ ] Place CNC machine block
- [ ] Verify model renders with shader effects
- [ ] Check lighting is shader-enhanced
- [ ] Verify shadows work correctly

**Expected Result**: Model integrates with shader pipeline

### Test 4.2: RenderType Selection
- [ ] Check logs for RenderType selection
- [ ] Verify entityCutout is used for opaque materials
- [ ] Verify entityTranslucent is used for transparent materials
- [ ] Confirm shader compatibility

**Expected Result**: Correct RenderType based on material

### Test 4.3: Shadow Pass
- [ ] Enable shader pack with shadows
- [ ] Verify models cast shadows correctly
- [ ] Check shadow quality and resolution
- [ ] Verify no shadow artifacts

**Expected Result**: Proper shadow casting

### Test 4.4: Translucency with Shaders
- [ ] Place CCB block with shaders enabled
- [ ] Verify transparency works with shader effects
- [ ] Check refraction/distortion if supported
- [ ] Verify no rendering glitches

**Expected Result**: Enhanced transparency rendering

## Phase 5: PBR Texture Support

### Test 5.1: PBR System Detection
- [ ] Enable PBR shader pack (BSL/Complementary)
- [ ] Check logs for: "PBR texture system initialized successfully"
- [ ] Verify isPBRAvailable() returns true
- [ ] Confirm PBRTextureManager is accessible

**Expected Result**: PBR system initializes correctly

### Test 5.2: Conventional Texture Paths
- [ ] Create test model with textures:
  - `test.png` (albedo)
  - `test_n.png` (normal map)
  - `test_s.png` (specular map)
- [ ] Place block in world
- [ ] Verify all textures are loaded
- [ ] Check logs for PBR texture registration

**Expected Result**: All PBR textures detected and loaded

### Test 5.3: Normal Map Rendering
- [ ] Create model with detailed normal map
- [ ] Enable PBR shader pack
- [ ] Verify surface detail from normal map
- [ ] Check lighting responds to normals
- [ ] Verify tangent space is correct

**Expected Result**: Realistic surface detail

### Test 5.4: Specular Map Rendering
- [ ] Create model with varied specular map
- [ ] Enable PBR shader pack
- [ ] Verify reflectivity varies across surface
- [ ] Check highlights respond to lighting
- [ ] Verify roughness/metallic properties

**Expected Result**: Realistic material properties

### Test 5.5: PBR Fallback
- [ ] Enable non-PBR shader pack
- [ ] Verify PBR textures are not used
- [ ] Check graceful degradation to basic rendering
- [ ] Verify no errors or warnings

**Expected Result**: Clean fallback to basic shaders

## Phase 6: Error Handling

### Test 6.1: Missing Oculus API
- [ ] Simulate API method not found
- [ ] Verify graceful error handling
- [ ] Check logs for appropriate warnings
- [ ] Verify rendering continues with fallback

**Expected Result**: No crashes, clean fallback

### Test 6.2: Invalid Texture Paths
- [ ] Specify non-existent PBR texture paths
- [ ] Verify no crashes or exceptions
- [ ] Check logs for debug messages
- [ ] Verify rendering continues with available textures

**Expected Result**: Graceful handling of missing textures

### Test 6.3: Shader Pack Compatibility
- [ ] Test with 5+ different shader packs
- [ ] Verify compatibility with each
- [ ] Document any issues or limitations
- [ ] Verify no crashes with any pack

**Expected Result**: Broad shader pack compatibility

## Phase 7: Hot-Reload Testing

### Test 7.1: Model Hot-Reload
- [ ] Place test block in world
- [ ] Modify FBX model file
- [ ] Trigger hot-reload
- [ ] Verify model updates correctly
- [ ] Check texture cache is cleared

**Expected Result**: Hot-reload works with shaders

### Test 7.2: Texture Hot-Reload
- [ ] Place test block with PBR textures
- [ ] Modify normal/specular maps
- [ ] Trigger hot-reload
- [ ] Verify textures update correctly
- [ ] Check PBR registration updates

**Expected Result**: PBR textures hot-reload correctly

### Test 7.3: Shader Pack Switch
- [ ] Place test blocks in world
- [ ] Switch between shader packs
- [ ] Verify rendering adapts correctly
- [ ] Check no visual glitches during switch

**Expected Result**: Smooth shader pack transitions

## Phase 8: Performance Testing

### Test 8.1: Baseline Performance
- [ ] Measure FPS with vanilla rendering
- [ ] Record as baseline

### Test 8.2: Shader Performance
- [ ] Measure FPS with basic shaders
- [ ] Compare to baseline
- [ ] Verify overhead is from shaders, not integration

### Test 8.3: PBR Performance
- [ ] Measure FPS with PBR shaders
- [ ] Compare to basic shaders
- [ ] Verify PBR overhead is acceptable

### Test 8.4: Stress Test
- [ ] Place 100+ blocks with PBR textures
- [ ] Measure FPS and memory usage
- [ ] Verify no memory leaks
- [ ] Check frame time consistency

**Expected Result**: Performance within acceptable limits

## Phase 9: Integration Testing

### Test 9.1: Multiple Mods
- [ ] Test with other rendering mods installed
- [ ] Verify no conflicts or crashes
- [ ] Check rendering priority is correct

### Test 9.2: Multiplayer
- [ ] Test on dedicated server
- [ ] Verify client-side rendering works
- [ ] Check no server-side issues

### Test 9.3: World Save/Load
- [ ] Place test blocks in world
- [ ] Save and exit
- [ ] Reload world
- [ ] Verify blocks render correctly

**Expected Result**: Persistent correct rendering

## Success Criteria

All tests must pass with these results:

✅ **No Crashes**: Zero crashes in any configuration  
✅ **Graceful Degradation**: Works perfectly without Oculus  
✅ **Shader Compatibility**: Works with major shader packs  
✅ **PBR Support**: Normal/specular maps work when supported  
✅ **Performance**: < 5% overhead when shaders disabled  
✅ **Visual Quality**: No artifacts, glitches, or missing faces  
✅ **Error Handling**: Clean fallback on any error  

## Known Limitations

Document any discovered limitations:
- Shader pack specific issues
- Performance bottlenecks
- Unsupported features
- Compatibility notes

## Test Results Log

| Test ID | Status | Notes | Tester | Date |
|---------|--------|-------|--------|------|
| 1.1 | ⬜ | | | |
| 1.2 | ⬜ | | | |
| ... | ⬜ | | | |

## Conclusion

After completing all tests, summarize:
- Overall integration quality
- Any issues found and resolved
- Recommendations for merge
- Future enhancement opportunities
