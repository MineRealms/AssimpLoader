# Node Editor Implementation Plan

## Overview

Implement a ComfyUI-style node editor in LookDev viewer using ModernUI framework.
Visual node-based material/shader graph editor with drag-and-drop connections.

**Current Focus:** Build and validate the frontend UI and basic node workflow logic first, before integrating with the rendering pipeline.

## Architecture Reference

**ModernUI Framework:**
- Fragment-based UI architecture
- Custom View components
- Event handling system
- Canvas drawing API

**ComfyUI-style Features:**
- Visual node graph
- Drag-and-drop connections
- Real-time preview
- Node library/palette

## Implementation Status

### ✅ Phase 1: Core Node Editor Framework (COMPLETED)
**Goal:** Basic node editor canvas with pan/zoom

**Completed:**
- ✅ `NodeEditorFragment.java` - Main fragment for node editor tab
- ✅ `NodeEditorCanvas.java` - Custom View for drawing nodes and connections
- ✅ Pan/zoom controls (mouse drag + scroll wheel)
- ✅ Grid background rendering
- ✅ Integrated as new tab in LookDevScreen

**Status:** Canvas with grid, pan/zoom working, Node Editor tab available

---

### ✅ Phase 2: Node Data Model (COMPLETED)
**Goal:** Define node structure and graph data model

**Completed:**
- ✅ `Node.java` - Base node class (position, size, title, sockets)
- ✅ `NodeSocket.java` - Input/output socket (type, value, connection)
- ✅ `NodeConnection.java` - Connection between sockets
- ✅ Type-safe socket system (FLOAT, COLOR, TEXTURE, VECTOR)

**Status:** Complete data model with socket connections

---

### ✅ Phase 3: Node Rendering (COMPLETED)
**Goal:** Draw nodes and connections on canvas

**Completed:**
- ✅ Node rendering in `Node.draw()` - rounded rect, title bar, sockets
- ✅ Connection rendering in `NodeConnection.draw()` - bezier curves
- ✅ Socket rendering - circles with type colors
- ✅ Selection highlighting

**Status:** Nodes and connections render correctly

---

### ✅ Phase 4: Node Interaction (COMPLETED)
**Goal:** Drag nodes, create connections

**Completed:**
- ✅ Node dragging (click title bar + drag to move)
- ✅ Connection creation (drag from output to input socket)
- ✅ Node selection (click to select)
- ✅ Connection validation (type checking)
- ✅ Pan canvas (click empty space + drag)

**Status:** Fully interactive node editor

---

### ✅ Phase 5: Example Nodes (COMPLETED)
**Goal:** Create example nodes for material editing

**Completed:**
- ✅ `TextureInputNode` - Texture input
- ✅ `ColorInputNode` - Color value input
- ✅ `FloatInputNode` - Float value input
- ✅ `MathNode` - Math operations (Add, Multiply, etc.)
- ✅ `OutputNode` - Final material output
- ✅ Demo graph created in `createExampleNodes()`

**Status:** Example nodes render and can be connected

---

### ✅ Phase 6: Node Execution Engine (COMPLETED)
**Goal:** Execute node graph and compute results

**Completed:**
- ✅ `NodeGraphExecutor.java` - Graph evaluation
- ✅ Topological sort for execution order
- ✅ Cycle detection
- ✅ Value propagation through connections
- ✅ Material generation from OutputNode

**Status:** Graph execution works, can generate PBRMaterial from nodes

---

### 🔄 Phase 7: Frontend Validation & Testing (IN PROGRESS)
**Goal:** Verify all frontend features work correctly before integration

**Tasks:**
1. ✅ Compile verification - ensure all code compiles
2. 🔄 Runtime testing - launch game and test node editor
3. 🔄 Interaction testing:
   - Can drag nodes smoothly
   - Can create connections by dragging from sockets
   - Can delete connections (right-click or disconnect)
   - Pan and zoom work correctly
   - Selection highlighting works
4. 🔄 Node functionality testing:
   - ColorInputNode shows color picker
   - FloatInputNode shows slider
   - MathNode operations work
   - OutputNode collects all inputs
5. 🔄 Graph execution testing:
   - Click "Generate Material" button
   - Verify material values in logs
   - Test cycle detection
   - Test disconnected nodes

**Validation Checklist:**
- [ ] `./gradlew compileJava` passes
- [ ] Can launch game without crashes
- [ ] Can open LookDev screen
- [ ] Can switch to Node Editor tab
- [ ] Can see example nodes on canvas
- [ ] Can drag nodes around
- [ ] Can create new connections
- [ ] Can delete connections
- [ ] Pan/zoom works smoothly
- [ ] "Generate Material" button works
- [ ] Material values logged correctly

**Current Status:** Code compiles, need runtime testing

---

### 📋 Phase 8: Advanced Node Features (PLANNED)
**Goal:** Add more node types and features

**Planned Tasks:**
1. Add more node types:
   - `MixNode` - Lerp between two values
   - `SplitNode` - Split vector/color into components
   - `CombineNode` - Combine components into vector/color
   - `TextureSampleNode` - Sample texture at UV
   - `UVNode` - UV coordinate input
2. Node library panel (side panel with node list)
3. Add node via right-click context menu
4. Node search/filter
5. Delete node (Delete key)
6. Copy/paste nodes (Ctrl+C/V)
7. Undo/redo system

**Status:** Not started - waiting for Phase 7 validation

---

### 📋 Phase 9: Graph Serialization (PLANNED)
**Goal:** Save and load node graphs

**Planned Tasks:**
1. JSON serialization for nodes
2. JSON serialization for connections
3. Save graph to file
4. Load graph from file
5. Graph preset system
6. Auto-save functionality

**Status:** Not started

---

### 📋 Phase 10: Material System Integration (PLANNED)
**Goal:** Connect node editor to preview rendering

**Planned Tasks:**
1. Connect OutputNode to PreviewFragment
2. Apply generated material to 3D preview
3. Real-time preview updates when nodes change
4. Material override system integration
5. Sync with parameter panel

**Status:** Not started - will do after frontend validation

---

## Current Priority

**Phase 7: Frontend Validation & Testing**

Focus on verifying the node editor UI works correctly:
1. Compile the code
2. Launch the game
3. Test all interactions
4. Fix any bugs found
5. Document any issues

**DO NOT** proceed to Phase 10 (integration) until Phase 7 is complete and validated.

---

## Technical Stack

**UI Framework:** ModernUI (Fragment + Custom Views)
**Rendering:** Canvas 2D API (drawRect, drawLine, drawCircle)
**Data Model:** Java classes (Node, Socket, Connection)
**Execution:** Topological sort + value propagation
**Serialization:** JSON (Gson) - Phase 9

## File Structure

```
src/main/java/cn/minerealms/assimploader/lookdev/
├── nodeeditor/
│   ├── NodeEditorFragment.java       ✅ Phase 1
│   ├── NodeEditorCanvas.java         ✅ Phase 1
│   ├── Node.java                     ✅ Phase 2
│   ├── NodeSocket.java               ✅ Phase 2
│   ├── NodeConnection.java           ✅ Phase 2
│   ├── NodeGraphExecutor.java        ✅ Phase 6
│   └── nodes/
│       ├── TextureInputNode.java     ✅ Phase 5
│       ├── ColorInputNode.java       ✅ Phase 5
│       ├── FloatInputNode.java       ✅ Phase 5
│       ├── MathNode.java             ✅ Phase 5
│       └── OutputNode.java           ✅ Phase 5
```

## Success Criteria

**Phase 7 (Current):**
- All code compiles without errors
- Node editor tab opens in game
- Can interact with nodes (drag, connect, select)
- "Generate Material" button produces correct output
- No crashes or errors in logs

**Phase 8-9 (Future):**
- More node types available
- Can save/load graphs
- Node library panel works

**Phase 10 (Future):**
- Node graph affects 3D preview
- Real-time material updates
- Full integration with LookDev system

## Next Steps

1. **Compile verification:**
   ```bash
   ./gradlew compileJava
   ```

2. **Runtime testing:**
   ```bash
   ./gradlew runClient
   ```
   - Open LookDev screen (`/lookdev`)
   - Switch to Node Editor tab
   - Test all interactions

3. **Bug fixing:**
   - Fix any issues found during testing
   - Document problems and solutions

4. **Documentation:**
   - Update this plan with test results
   - Note any limitations or issues
   - Plan next phase based on findings
