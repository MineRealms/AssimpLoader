# Node Editor Implementation Plan

## Overview

Implement a ComfyUI-style node editor in LookDev viewer using ModernUI framework.
Visual node-based material/shader graph editor with drag-and-drop connections.

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

## Phase Breakdown

### Phase 1: Core Node Editor Framework (Foundation)
**Goal:** Basic node editor canvas with pan/zoom

**Tasks:**
1. Create `NodeEditorFragment.java` - Main fragment for node editor tab
2. Create `NodeEditorCanvas.java` - Custom View for drawing nodes and connections
3. Implement pan/zoom controls (mouse drag + scroll wheel)
4. Add grid background rendering
5. Integrate as new tab in LookDevScreen

**Deliverables:**
- Empty canvas with grid
- Pan/zoom working
- New "Node Editor" tab in LookDev

**Validation:**
- `./gradlew compileJava` passes
- Can switch to Node Editor tab
- Can pan and zoom canvas

---

### Phase 2: Node Data Model (Core Classes)
**Goal:** Define node structure and graph data model

**Tasks:**
1. Create `Node.java` - Base node class (position, size, title)
2. Create `NodeSocket.java` - Input/output socket (type, value, connection)
3. Create `NodeConnection.java` - Connection between sockets
4. Create `NodeGraph.java` - Graph container (nodes, connections)
5. Create `NodeType.java` - Enum for node types

**Deliverables:**
- Complete data model for nodes and connections
- Type-safe socket system (Float, Vector3, Color, Texture)

**Validation:**
- `./gradlew compileJava` passes
- Can create nodes programmatically
- Can connect nodes in code

---

### Phase 3: Node Rendering (Visual Display)
**Goal:** Draw nodes and connections on canvas

**Tasks:**
1. Implement `NodeRenderer.java` - Renders individual nodes
2. Draw node body (rounded rect, title bar, sockets)
3. Draw connections (bezier curves between sockets)
4. Implement socket rendering (circles with type colors)
5. Add selection highlighting

**Deliverables:**
- Nodes visible on canvas
- Connections drawn as curves
- Visual feedback for selection

**Validation:**
- `./gradlew compileJava` passes
- Demo nodes render correctly
- Connections visible between nodes

---

### Phase 4: Node Interaction (Mouse Events)
**Goal:** Drag nodes, create connections

**Tasks:**
1. Implement node dragging (click + drag to move)
2. Implement connection creation (drag from output to input socket)
3. Add node selection (click to select)
4. Add connection deletion (right-click or select + delete)
5. Implement multi-selection (Ctrl+click)

**Deliverables:**
- Fully interactive node editor
- Can create/delete connections
- Can move nodes around

**Validation:**
- `./gradlew compileJava` passes
- Can drag nodes smoothly
- Can connect/disconnect nodes

---

### Phase 5: Example Nodes (Demo Content)
**Goal:** Create example nodes for material editing

**Tasks:**
1. Create `TextureInputNode` - Loads a texture
2. Create `ColorNode` - Outputs a color value
3. Create `MixNode` - Mixes two inputs (lerp)
4. Create `MultiplyNode` - Multiplies two values
5. Create `OutputNode` - Final material output
6. Create `ValueNode` - Numeric slider input

**Example Graph:**
```
[Texture] ──> [Mix] ──> [Output]
[Color]   ──>  ↑
[Value]   ─────┘ (mix factor)
```

**Deliverables:**
- 6 working example nodes
- Demo graph on startup
- Nodes execute and produce output

**Validation:**
- `./gradlew compileJava` passes
- Demo graph loads automatically
- Can see connections between example nodes

---

### Phase 6: Node Execution Engine (Graph Evaluation)
**Goal:** Execute node graph and compute results

**Tasks:**
1. Create `NodeExecutor.java` - Evaluates node graph
2. Implement topological sort (dependency order)
3. Implement value propagation through connections
4. Add caching (don't re-execute unchanged nodes)
5. Handle cyclic dependency detection

**Deliverables:**
- Working execution engine
- Nodes compute correct outputs
- Efficient caching system

**Validation:**
- `./gradlew compileJava` passes
- Changing input updates output
- No infinite loops on cycles

---

### Phase 7: Node Library Panel (Node Palette)
**Goal:** Add/remove nodes from palette

**Tasks:**
1. Create `NodeLibraryPanel.java` - Side panel with node list
2. Implement node search/filter
3. Add drag-from-palette to create nodes
4. Implement right-click context menu (add node)
5. Add node deletion (Delete key)

**Deliverables:**
- Node palette on left side
- Can add nodes by dragging
- Can search for nodes

**Validation:**
- `./gradlew compileJava` passes
- Can add nodes from palette
- Search filters nodes correctly

---

### Phase 8: Material Integration (Connect to LookDev)
**Goal:** Apply node graph output to 3D preview

**Tasks:**
1. Connect OutputNode to PBRMaterial
2. Update OffscreenRenderer when graph changes
3. Add real-time preview updates
4. Implement graph serialization (save/load)
5. Add preset system for node graphs

**Deliverables:**
- Node graph affects 3D preview
- Can save/load node graphs
- Real-time material updates

**Validation:**
- `./gradlew compileJava` passes
- Changing nodes updates 3D model
- Can save and reload graphs

---

## Technical Stack

**UI Framework:** ModernUI (Fragment + Custom Views)
**Rendering:** Canvas 2D API (drawRect, drawLine, drawText)
**Data Model:** Java classes (Node, Socket, Connection, Graph)
**Execution:** Topological sort + value propagation
**Serialization:** JSON (Gson)

## File Structure

```
src/main/java/cn/minerealms/assimploader/lookdev/
├── nodeeditor/
│   ├── NodeEditorFragment.java       (Phase 1)
│   ├── NodeEditorCanvas.java         (Phase 1)
│   ├── model/
│   │   ├── Node.java                 (Phase 2)
│   │   ├── NodeSocket.java           (Phase 2)
│   │   ├── NodeConnection.java       (Phase 2)
│   │   ├── NodeGraph.java            (Phase 2)
│   │   └── NodeType.java             (Phase 2)
│   ├── render/
│   │   └── NodeRenderer.java         (Phase 3)
│   ├── interaction/
│   │   └── NodeInteractionHandler.java (Phase 4)
│   ├── nodes/
│   │   ├── TextureInputNode.java     (Phase 5)
│   │   ├── ColorNode.java            (Phase 5)
│   │   ├── MixNode.java              (Phase 5)
│   │   ├── MultiplyNode.java         (Phase 5)
│   │   ├── OutputNode.java           (Phase 5)
│   │   └── ValueNode.java            (Phase 5)
│   ├── execution/
│   │   └── NodeExecutor.java         (Phase 6)
│   ├── ui/
│   │   └── NodeLibraryPanel.java     (Phase 7)
│   └── integration/
│       └── MaterialGraphIntegration.java (Phase 8)
```

## Success Criteria

- All phases complete with passing `./gradlew compileJava`
- Demo graph loads on startup
- Can create, connect, and execute nodes
- Node output affects 3D preview
- Can save/load node graphs

## Estimated Complexity

- **Phase 1-2:** Foundation (Medium)
- **Phase 3-4:** Rendering + Interaction (High)
- **Phase 5:** Example Nodes (Low)
- **Phase 6:** Execution Engine (High)
- **Phase 7:** Node Library (Medium)
- **Phase 8:** Integration (Medium)

**Total:** ~8 phases, incremental development with validation at each step.
