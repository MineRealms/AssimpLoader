# Node Editor Backend Integration TODO

## Overview

This document outlines the backend integration tasks needed to connect the node editor frontend to the LookDev rendering pipeline.

**Current Status:** Frontend UI complete and functional (Phase 1-7)
**Next Phase:** Backend integration (Phase 10)

---

## Phase 10: Material System Integration

### 1. LookDevScreen Integration

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/ui/LookDevScreen.java`

**Tasks:**
- [ ] Add `getNodeEditorFragment()` method
- [ ] Implement `applyNodeGeneratedMaterial(PBRMaterial material)` method
  - Add material to materials list
  - Select the material
  - Update PreviewFragment with new material
  - Update ParameterPanelFragment
  - Trigger 3D preview refresh

**Code Location:**
```java
// Around line 590 in LookDevScreen.java
public void applyNodeGeneratedMaterial(PBRMaterial material) {
    // Implementation here
}
```

---

### 2. NodeEditorFragment Integration

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeEditorFragment.java`

**Tasks:**
- [ ] Update `onGenerateMaterial()` to call LookDevScreen
- [ ] Get parent LookDevScreen via `getParentFragment().getParentFragment()`
- [ ] Call `lookDevScreen.applyNodeGeneratedMaterial(material)`
- [ ] Handle errors gracefully

**Code Location:**
```java
// Line 175 in NodeEditorFragment.java
private void onGenerateMaterial() {
    // TODO: BACKEND INTEGRATION (Phase 10)
    // Implementation here
}
```

---

### 3. Real-Time Preview Updates

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeEditorCanvas.java`

**Tasks:**
- [ ] Add auto-update toggle (checkbox in toolbar)
- [ ] When connection created/deleted, trigger material update
- [ ] When node parameter changed, trigger material update
- [ ] Implement debouncing to avoid too many updates (e.g., 500ms delay)
- [ ] Show "Updating..." indicator during material generation

**Code Location:**
```java
// Line 513 in NodeEditorCanvas.java
private void tryCreateConnection(NodeSocket socket1, NodeSocket socket2) {
    // TODO: BACKEND INTEGRATION (Phase 10)
    // Trigger material update after connection created
}
```

---

### 4. Node Execution Enhancements

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeGraphExecutor.java`

**Tasks:**
- [ ] Add execution logic for TextureInputNode
  - Load texture from ResourceLocation
  - Handle missing textures gracefully
- [ ] Add execution logic for MixNode (lerp)
- [ ] Add execution logic for TextureSampleNode
  - Sample texture at UV coordinates
  - Return color value
- [ ] Implement value caching to avoid re-execution
- [ ] Add execution time profiling for performance monitoring

**Code Location:**
```java
// Line 91 in NodeGraphExecutor.java
private void executeNode(Node node) {
    // TODO: BACKEND INTEGRATION (Phase 10)
    // Add more node type execution logic
}
```

---

### 5. Material Parameter Mapping

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeEditorFragment.java`

**Tasks:**
- [ ] Enhance `generateMaterial()` to handle more PBR parameters:
  - Ambient Occlusion texture
  - Height/Displacement texture
  - Emissive intensity
  - Alpha/Transparency
  - Subsurface scattering
- [ ] Add validation for material values (clamp to valid ranges)
- [ ] Handle missing/null values with sensible defaults

**Code Location:**
```java
// Line 252 in NodeEditorFragment.java
public PBRMaterial generateMaterial() {
    // TODO: Add more PBR parameter mappings
}
```

---

### 6. OffscreenRenderer Integration

**File:** `src/main/java/cn/minerealms/assimploader/lookdev/render/OffscreenRenderer.java`

**Tasks:**
- [ ] Ensure OffscreenRenderer can handle node-generated materials
- [ ] Verify material override system works with node materials
- [ ] Test texture binding for node-generated textures
- [ ] Handle shader compilation for custom material properties

**Related Files:**
- `PreviewFragment.java` - calls OffscreenRenderer
- `MaterialOverrideManager.java` - manages material overrides

---

### 7. Error Handling & Validation

**Tasks:**
- [ ] Add error messages for invalid node graphs
- [ ] Show warnings for disconnected nodes
- [ ] Validate material values before applying
- [ ] Handle texture loading failures
- [ ] Add try-catch blocks around critical sections
- [ ] Log errors to debug console

**Locations:**
- NodeEditorFragment.onGenerateMaterial()
- NodeGraphExecutor.execute()
- LookDevScreen.applyNodeGeneratedMaterial()

---

### 8. Performance Optimization

**Tasks:**
- [ ] Implement dirty flag system (only regenerate when graph changes)
- [ ] Add debouncing for real-time updates (500ms delay)
- [ ] Cache node execution results
- [ ] Optimize bezier curve rendering (reduce segments when zoomed out)
- [ ] Profile material generation time
- [ ] Add loading indicator for slow operations

---

### 9. User Feedback

**Tasks:**
- [ ] Show "Material Applied" notification
- [ ] Display material generation time in status bar
- [ ] Add progress indicator for long operations
- [ ] Show error messages in UI (not just logs)
- [ ] Add tooltip showing current material values

---

### 10. Testing & Validation

**Test Cases:**
- [ ] Generate material with all parameters connected
- [ ] Generate material with some parameters disconnected (use defaults)
- [ ] Generate material with invalid connections (should fail gracefully)
- [ ] Generate material with cyclic graph (should detect and warn)
- [ ] Apply material and verify preview updates
- [ ] Change node parameters and verify real-time updates
- [ ] Test with multiple materials in scene
- [ ] Test with missing textures
- [ ] Test performance with large node graphs (100+ nodes)

---

## Implementation Order

**Recommended sequence:**

1. **Basic Integration (1-2 hours)**
   - Implement LookDevScreen.applyNodeGeneratedMaterial()
   - Update NodeEditorFragment.onGenerateMaterial()
   - Test manual material application (button click)

2. **Real-Time Updates (2-3 hours)**
   - Add auto-update toggle
   - Implement debouncing
   - Trigger updates on connection changes

3. **Enhanced Execution (3-4 hours)**
   - Add TextureInputNode execution
   - Add MixNode execution
   - Implement value caching

4. **Error Handling (1-2 hours)**
   - Add validation
   - Improve error messages
   - Handle edge cases

5. **Performance & Polish (2-3 hours)**
   - Optimize rendering
   - Add loading indicators
   - Profile and optimize

**Total Estimated Time:** 9-14 hours

---

## Dependencies

**Required Components:**
- ✅ NodeEditorFragment (Phase 1-7)
- ✅ NodeGraphExecutor (Phase 6)
- ✅ PBRMaterial class
- ✅ PreviewFragment
- ✅ OffscreenRenderer
- ✅ MaterialOverrideManager

**Optional Enhancements:**
- Node library panel (Phase 8)
- Graph serialization (Phase 9)
- Undo/redo system (Phase 8)

---

## Success Criteria

**Phase 10 Complete When:**
- [ ] Can generate material from node graph
- [ ] Material applies to 3D preview
- [ ] Preview updates in real-time (with auto-update enabled)
- [ ] All PBR parameters work correctly
- [ ] Textures load and display properly
- [ ] Error handling works gracefully
- [ ] Performance is acceptable (< 100ms material generation)
- [ ] No crashes or errors in logs

---

## Notes

- **Priority:** Backend integration is Phase 10, after frontend validation (Phase 7)
- **Testing:** Must validate frontend first before starting backend work
- **Documentation:** Update this file as tasks are completed
- **Code Quality:** Add comprehensive error handling and logging
- **Performance:** Profile and optimize critical paths

---

## Related Files

**Frontend (Complete):**
- `NodeEditorFragment.java`
- `NodeEditorCanvas.java`
- `Node.java`
- `NodeSocket.java`
- `NodeConnection.java`
- `NodeGraphExecutor.java`
- `nodes/*.java`

**Backend (To Integrate):**
- `LookDevScreen.java`
- `PreviewFragment.java`
- `OffscreenRenderer.java`
- `MaterialOverrideManager.java`
- `PBRMaterial.java`

**Documentation:**
- `NODE_EDITOR_IMPLEMENTATION_PLAN.md` - Overall plan
- `NODE_EDITOR_BACKEND_TODO.md` - This file
