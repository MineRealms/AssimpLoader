# Oculus Integration Testing Checklist

## Overview

This document is a manual test plan, not a record that every listed configuration has passed. Current completed checks and screenshots are recorded in [PBR Validation](PBR_VALIDATION_2026-11-13.md); setup and limitations are in [Oculus PBR Usage](../guides/OCULUS_PBR_USAGE.md). CNC/CCB was removed at the user's request, so tests below use the current showcase fixtures.

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
   - A shader pack configured to consume LabPBR entity textures
   - The validated setup is Oculus 1.8.0, Embeddium 0.3.31 and Complementary Unbound r5.3 with `RP_MODE=3`; other packs/settings require separate verification
   - Shaders and the pack's resource-pack/PBR material setting enabled

### Test Models

Use the assets listed in [Model Showcase](../showcase/MODEL_SHOWCASE.md):

| Fixture | World/item ID | Purpose |
|---|---|---|
| Textured CIWS | `assimploader:showcase_ciws_textured` | Opaque geometry, embedded base-color texture and articulated node hierarchy |
| PBR probe | `assimploader:showcase_pbr_probe` | Normal-map detail and matte/polished dielectric/metal comparison |
| PBR portal | `assimploader:showcase_portal_pbr` | Multiple materials, metal, rough surfaces, glass and emission |

The CIWS base-color atlas alone is not evidence that authored normal/roughness/metallic images reached the shader. Use the probe or portal for that check. `/assimpdemo` places the showcase in a sufficiently clear area and requires permission level 2; the local `/assimploader reload` command has no OP requirement.

## Phase 1: Soft Dependency Detection

### Test 1.1: Mod Loading Without Oculus
- [ ] Launch Minecraft without Oculus installed
- [ ] Confirm Oculus is absent and the model uses the vanilla rendering path
- [ ] Verify no ClassNotFoundException or NoClassDefFoundError
- [ ] Verify game loads successfully

**Expected Result**: Clean startup, vanilla rendering active

### Test 1.2: Mod Loading With Oculus
- [ ] Launch Minecraft with Oculus installed
- [ ] Confirm Oculus is detected and the shader state matches the selected pack
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
- [ ] Place `assimploader:showcase_ciws_textured` in world
- [ ] Verify model renders correctly
- [ ] Check texture is applied
- [ ] Verify lighting works (day/night cycle)
- [ ] Check no visual artifacts or missing faces

**Expected Result**: Perfect vanilla rendering

### Test 2.2: Item Rendering
- [ ] Hold `assimploader:showcase_ciws_textured` in hand (first person)
- [ ] View in third person
- [ ] Place in inventory GUI
- [ ] Drop on ground
- [ ] Place in item frame

**Expected Result**: Correct rendering in all contexts

### Test 2.3: Transparent Model
- [ ] Place `assimploader:showcase_portal_pbr` in world
- [ ] Verify transparency works correctly
- [ ] Check no z-fighting or sorting issues
- [ ] Verify glass uses authored alpha and remains visible against the background

**Expected Result**: Proper transparency rendering

### Test 2.4: Optional Performance Investigation

Run this only when investigating a concrete performance issue; it is not required for the current bug-fix acceptance.
- [ ] Place 50+ test blocks in world
- [ ] Measure FPS (F3 debug screen)
- [ ] Verify no performance degradation
- [ ] Check memory usage is stable

**Expected Result**: Record frame-time/memory observations with fixture count and hardware; do not infer zero overhead from an unmeasured scene

## Phase 3: Oculus Installed, Shaders Disabled

### Test 3.1: Graceful Degradation
- [ ] Launch with Oculus but shaders disabled
- [ ] Verify vanilla rendering is used
- [ ] Check logs for: shader detection returns false
- [ ] Verify the renderer does not replace the active vanilla shader program with a PBR-specific program

**Expected Result**: Identical to vanilla rendering

### Test 3.2: Optional Performance Comparison
- [ ] Measure FPS with shaders disabled
- [ ] Compare to vanilla (no Oculus) FPS
- [ ] Record differences and measurement conditions if this optional test is performed

**Expected Result**: A measured result, with no unverified percentage claim

## Phase 4: Shader-Aware Rendering

### Test 4.1: Basic Shader Compatibility
- [ ] Enable basic shader pack (Sildur's Lite)
- [ ] Place `assimploader:showcase_ciws_textured`
- [ ] Verify model renders with shader effects
- [ ] Check lighting is shader-enhanced
- [ ] Verify shadows work correctly

**Expected Result**: Model integrates with shader pipeline

### Test 4.2: RenderType Selection
- [ ] Check logs for RenderType selection
- [ ] Verify opaque materials use the entity-cutout shader state with triangle geometry
- [ ] Verify glass uses the entity-translucent shader state with triangle geometry
- [ ] Confirm shader compatibility

**Expected Result**: Correct RenderType based on material

### Test 4.3: Shadow Pass
- [ ] Enable shader pack with shadows
- [ ] Verify models cast shadows correctly
- [ ] Check shadow quality and resolution
- [ ] Verify no shadow artifacts

**Expected Result**: Proper shadow casting

### Test 4.4: Translucency with Shaders
- [ ] Place `assimploader:showcase_portal_pbr` with shaders enabled
- [ ] Verify transparency works with shader effects
- [ ] Verify authored alpha blending and PBR highlights; Blender Transmission/IOR is not converted into physical refraction
- [ ] Verify no rendering glitches

**Expected Result**: Enhanced transparency rendering

## Phase 5: PBR Texture Support

### Test 5.1: PBR System Detection
- [ ] Enable the configured LabPBR shader pack described above
- [ ] Check for `Registered Oculus PBR loader for AssimpLoader material textures`
- [ ] Inspect ready material diagnostics for actual normal/specular images and their sources
- [ ] Confirm the probe's three materials retain distinct PBR textures despite sharing an albedo

**Expected Result**: Actual material channels are prepared and handed to Oculus. A positive API flag or nonzero default texture ID alone does not establish this

### Test 5.2: Conventional Texture Paths
- [ ] Use a copy of the PBR probe and explicitly pack `body_n.png`/`body_s.png` as LabPBR siblings of the referenced `body.png`
- [ ] Keep raw Blender normal/roughness/metallic filenames separate; follow [PBR Asset Specification](../guides/PBR_ASSET_SPEC.md)
- [ ] Place block in world
- [ ] Verify all textures are loaded
- [ ] Check logs for PBR texture registration

**Expected Result**: All PBR textures detected and loaded

### Test 5.3: Normal Map Rendering
- [ ] Use the PBR probe's normal-detail surface or the portal's authored normal maps
- [ ] Enable PBR shader pack
- [ ] Verify surface detail from normal map
- [ ] Check lighting responds to normals
- [ ] Verify tangent space is correct

**Expected Result**: Realistic surface detail

### Test 5.4: Specular Map Rendering
- [ ] Use the probe's matte dielectric, polished dielectric and polished metal materials
- [ ] Enable PBR shader pack
- [ ] Verify reflectivity varies across surface
- [ ] Capture matched views with AssimpLoader PBR enabled/disabled under the same active shader pack
- [ ] Verify roughness/metallic properties

**Expected Result**: Roughness/metal response changes in matched world captures; retain material source diagnostics alongside the screenshots

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

Use the namespace/path layout and separate F3+T behavior in [Model Hot-Reload](Hot_Reload_System.md).

- [ ] Place test block in world
- [ ] Modify an external copy of `ciws_textured.fbx` at the configured override path
- [ ] Run `/assimploader reload` and wait for its success/failure message
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

## Phase 8: Optional Performance Testing

This historical benchmark plan is deferred for the current bug-fix work. No entries below imply measurements have been run.

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

For tests actually executed, record results with version/settings and evidence. The unchecked list is not an assertion of broad compatibility or measured overhead:

✅ **No Crashes**: Zero crashes in any configuration  
✅ **Graceful Degradation**: Works perfectly without Oculus  
✅ **Shader Compatibility**: Works with major shader packs  
✅ **PBR Support**: Normal/specular maps work when supported  
**Performance**: Investigate observed regressions; attach measurements only when the optional comparison is performed.
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
