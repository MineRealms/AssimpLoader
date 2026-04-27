# LookDev Node Editor Refactoring Plan

## 📋 Overview

**Goal**: Implement material-node graph binding system with auto-save/load functionality

**Status**: Ready for execution

**Estimated Phases**: 3 phases (P0 → P1 → P2)

---

## 🎯 Phase 0: Core Material-Node Graph Binding (CRITICAL)

### Task 0.1: Create MaterialNodeGraphManager
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/MaterialNodeGraphManager.java`
- **Purpose**: Central manager for material ↔ node graph binding
- **Features**:
  - In-memory cache: `Map<String, NodeGraphState>`
  - Auto-save current graph when switching materials
  - Auto-load from file or generate default graph
  - File I/O via MaterialFileManager

### Task 0.2: Modify NodeEditorFragment
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeEditorFragment.java`
- **Changes**:
  - Add field: `MaterialNodeGraphManager nodeGraphManager`
  - Add field: `PBRMaterial currentMaterial`
  - Implement `loadMaterial(PBRMaterial material)` - called by LookDevScreen
  - Implement `saveMaterial()` - Ctrl+S handler
  - Add `updateTitle()` - show current material name
  - Modify `onPause()` - auto-save on exit

### Task 0.3: Modify LookDevScreen
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/ui/LookDevScreen.java`
- **Changes**:
  - Add `onMaterialSelected(PBRMaterial material)` - entry point from ModelTree
  - Add `getNodeEditorFragment()` - helper to access NodeEditorFragment
  - Wire up material selection → node editor loading

### Task 0.4: Modify TabbedCenterFragment
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/ui/TabbedCenterFragment.java`
- **Changes**:
  - Add `getNodeEditorFragment()` - expose NodeEditorFragment instance

### Task 0.5: Modify ModelTreeFragment
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/ui/ModelTreeFragment.java`
- **Changes**:
  - Find material click handler
  - Add callback: `((LookDevScreen) parent).onMaterialSelected(material)`

### Task 0.6: Modify NodeEditorCanvas
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/NodeEditorCanvas.java`
- **Changes**:
  - Add `clear()` - remove all nodes/connections
  - Add `loadNodeGraph(List<Node>, List<NodeConnection>)` - load graph
  - Add `getNodes()` - return copy of nodes
  - Add `getConnections()` - return copy of connections

---

## 🎨 Phase 1: Material Preview System

### Task 1.1: Create MaterialPreviewView
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/ui/MaterialPreviewView.java`
- **Purpose**: Real-time material sphere preview in right panel
- **Features**:
  - Use OffscreenRenderer for 200x200 preview
  - Render sphere mesh with PBRMaterial
  - Auto-refresh on material change

### Task 1.2: Modify NodePropertiesPanel
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/ui/NodePropertiesPanel.java`
- **Changes**:
  - Add field: `MaterialPreviewView materialPreviewView`
  - Add preview view to layout (below NodePreviewPanel)
  - Add `updateMaterialPreview(PBRMaterial material)` - public API

### Task 1.3: Modify NodeEditorFragment.onGenerateMaterial()
- **File**: `NodeEditorFragment.java`
- **Changes**:
  - Add `generateMaterialFromNodes()` - extract PBRMaterial from OutputNode
  - Call `propertiesPanel.updateMaterialPreview(material)` after generation
  - Apply material to LookDevScreen preview (if FULL_INTEGRATION mode)

---

## ⚡ Phase 2: Auto-Save & Polish

### Task 2.1: Add Auto-Save to MaterialNodeGraphManager
- **Changes**:
  - Add `ScheduledExecutorService autoSaveExecutor`
  - Add `startAutoSave(NodeEditorCanvas canvas)` - 60s interval
  - Add `stopAutoSave()` - cleanup

### Task 2.2: Wire Auto-Save in NodeEditorFragment
- **Changes**:
  - Call `nodeGraphManager.startAutoSave(canvas)` in `onResume()`
  - Call `nodeGraphManager.stopAutoSave()` in `onPause()`

### Task 2.3: Add Material List Dialog (Optional)
- **File**: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/ui/MaterialListDialog.java`
- **Purpose**: Ctrl+O to open saved materials
- **Features**:
  - List all .material.json files
  - Search/filter
  - Load selected material

---

## ✅ Verification Checklist

### Functional Tests
- [ ] Select material A in ModelTree → Node Editor loads graph
- [ ] Edit nodes → Ctrl+S → File saved to `config/assimploader/materials/`
- [ ] Switch to material B → Graph auto-switches
- [ ] Return to material A → Edits preserved
- [ ] Restart game → Load material A → Graph loaded from file
- [ ] Generate Material → Right panel shows material sphere preview

### Technical Tests
- [ ] `.\gradlew.bat compileJava` - no errors
- [ ] `.\build-and-run.ps1` - game launches
- [ ] Check logs: `logs/assimploader-debug.log` for MaterialNodeGraphManager messages
- [ ] Verify files: `ls config/assimploader/materials/`

---

## 🔧 Implementation Order

```
Phase 0 (P0 - Critical)
├─ 0.1 MaterialNodeGraphManager.java          [NEW FILE]
├─ 0.2 NodeEditorFragment.java                [MODIFY]
├─ 0.3 LookDevScreen.java                     [MODIFY]
├─ 0.4 TabbedCenterFragment.java              [MODIFY]
├─ 0.5 ModelTreeFragment.java                 [MODIFY]
└─ 0.6 NodeEditorCanvas.java                  [MODIFY]
    ↓
    COMPILE & TEST
    ↓
Phase 1 (P1 - Important)
├─ 1.1 MaterialPreviewView.java               [NEW FILE]
├─ 1.2 NodePropertiesPanel.java               [MODIFY]
└─ 1.3 NodeEditorFragment.onGenerateMaterial  [MODIFY]
    ↓
    COMPILE & TEST
    ↓
Phase 2 (P2 - Polish)
├─ 2.1 MaterialNodeGraphManager auto-save     [MODIFY]
├─ 2.2 NodeEditorFragment auto-save wiring    [MODIFY]
└─ 2.3 MaterialListDialog.java (optional)     [NEW FILE]
    ↓
    FINAL TEST & COMMIT
```

---

## 📝 Key Design Decisions

1. **One Material = One Node Graph File**
   - File: `config/assimploader/materials/{materialName}.material.json`
   - Format: JSON (via MaterialSerializer)

2. **In-Memory Cache**
   - Avoid frequent file I/O
   - Cache invalidation on material switch

3. **Auto-Save Strategy**
   - On material switch: save current → load new
   - On Ctrl+S: explicit save to file
   - On pause/exit: auto-save current
   - Background: 60s interval (optional)

4. **Fallback Mechanism**
   - File not found → Generate default from PBRMaterial
   - JSON parse error → Show error + generate default

5. **Preview System**
   - Right panel: Material sphere (200x200)
   - Uses OffscreenRenderer + PreviewMesh
   - Updates on "Generate Material" click

---

## 🚨 Critical Notes

- **Deep Copy**: NodeGraphState must deep-copy nodes/connections
- **File Naming**: Sanitize material names (remove `/`, `:`, etc.)
- **Error Handling**: All file I/O must have try-catch with logging
- **Thread Safety**: Auto-save runs on background thread
- **Performance**: Material preview uses debounce (500ms)

---

## 📚 Dependencies

- ModernUI-MC: UI framework
- MaterialFileManager: File I/O (already exists)
- MaterialSerializer/Deserializer: JSON (already exists)
- MaterialNodeGraphGenerator: Default graph generation (already exists)
- OffscreenRenderer: Preview rendering (already exists)
- PreviewMesh: Sphere mesh (already exists)

---

## 🎓 Success Criteria

✅ **Phase 0 Complete When**:
- User can select material in ModelTree
- Node Editor auto-loads corresponding graph
- Ctrl+S saves graph to file
- Material switching preserves edits

✅ **Phase 1 Complete When**:
- "Generate Material" shows material sphere preview
- Preview updates in real-time

✅ **Phase 2 Complete When**:
- Auto-save works (60s interval)
- No data loss on game exit

---

**Document Version**: 1.0  
**Last Updated**: 2026-04-27  
**Status**: Ready for execution
