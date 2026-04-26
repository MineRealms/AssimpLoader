# LookDev Node Editor - Photon Shader System Integration Plan

## Project Overview
Integrate a visual node-based material editor into AssimpLoader's LookDev system, with conditional Photon shader system integration for advanced rendering capabilities.

## Architecture Tiers

### Tier 1: Standalone (Minecraft + AssimpLoader only)
- Basic node editor UI with limited functionality
- Simple material preview using Minecraft's default rendering
- Export to basic material format (JSON)
- No shader compilation or advanced rendering

### Tier 2: Full Integration (Minecraft + AssimpLoader + Photon + Oculus)
- Complete node editor with all node types
- Real-time shader compilation and preview
- Direct integration with Photon's shader system
- Advanced rendering features (PBR, custom shaders, bloom)
- Live material editing with hot-reload

## Core Node Types

### Input Nodes
1. **Texture Sample Node**
   - Inputs: UV (vec2), Texture (sampler2D)
   - Outputs: Color (vec4), RGB (vec3), Alpha (float)
   - Properties: Texture path, filtering mode, wrap mode

2. **Value Node**
   - Outputs: Float value
   - Properties: Slider (0.0 - 1.0), numeric input

3. **Color Node**
   - Outputs: Color (vec4), RGB (vec3)
   - Properties: Color picker

4. **UV Coordinate Node**
   - Outputs: UV (vec2)
   - Properties: Tiling (vec2), Offset (vec2)

### Math Nodes
5. **Multiply Node**
   - Inputs: A (float/vec3/vec4), B (float/vec3/vec4)
   - Outputs: Result (same type as inputs)

6. **Add Node**
   - Inputs: A (float/vec3/vec4), B (float/vec3/vec4)
   - Outputs: Result (same type as inputs)

7. **Lerp Node**
   - Inputs: A (float/vec3/vec4), B (float/vec3/vec4), T (float)
   - Outputs: Result (same type as A/B)

### Utility Nodes
8. **Normal Map Node**
   - Inputs: Normal Map (sampler2D), UV (vec2), Strength (float)
   - Outputs: Normal (vec3)

### Output Node
9. **Material Output Node** (required, only one per graph)
   - Inputs: Base Color (vec3), Metallic (float), Roughness (float), Normal (vec3), Emissive (vec3), Alpha (float)
   - Properties: Blend mode (Opaque/Transparent)

## Implementation Phases

### Phase 1: Node Type System Foundation
**Goal**: Define node type registry and base classes
**Files to create/modify**:
- `NodeType.java` - Enum of all node types
- `NodeRegistry.java` - Registry for node type metadata
- `NodeFactory.java` - Factory for creating nodes
- `NodeSocket.java` - Enhanced socket with type information
- `SocketType.java` - Enum for socket data types (FLOAT, VEC2, VEC3, VEC4, SAMPLER2D)

**Validation**: Compile successfully, unit test node creation

### Phase 2: Concrete Node Implementations
**Goal**: Implement all 9 node types
**Files to create**:
- `nodes/input/TextureSampleNode.java`
- `nodes/input/ValueNode.java`
- `nodes/input/ColorNode.java`
- `nodes/input/UVCoordinateNode.java`
- `nodes/math/MultiplyNode.java`
- `nodes/math/AddNode.java`
- `nodes/math/LerpNode.java`
- `nodes/utility/NormalMapNode.java`
- `nodes/output/MaterialOutputNode.java`

**Validation**: Each node renders correctly in editor, sockets have proper types

### Phase 3: Type System and Connection Validation
**Goal**: Implement socket type checking and auto-conversion
**Files to modify**:
- `NodeEditorCanvas.java` - Add connection validation
- `Connection.java` - Add type compatibility checking
- `TypeConverter.java` (new) - Handle float→vec3 expansion, etc.

**Validation**: Invalid connections rejected, valid connections with type conversion work

### Phase 4: Node Graph Execution Engine
**Goal**: Implement graph traversal and value propagation
**Files to modify**:
- `NodeGraphExecutor.java` - Complete TODO at line 98
- `NodeEvaluationContext.java` (new) - Store intermediate values
- `Node.java` - Add `evaluate()` method

**Validation**: Simple graph (Value → Multiply → Output) evaluates correctly

### Phase 5: Photon Detection and Conditional Loading
**Goal**: Detect Photon/Oculus at runtime and enable features
**Files to create/modify**:
- `PhotonIntegration.java` - Check if Photon is loaded
- `FeatureTier.java` - Enum (STANDALONE, FULL_INTEGRATION)
- `NodeEditorFragment.java` - Modify line 188 TODO, conditional UI

**Validation**: Correct tier detected, UI adapts accordingly

### Phase 6: Shader Code Generation (Tier 2 only)
**Goal**: Convert node graph to GLSL shader code
**Files to create**:
- `ShaderCodeGenerator.java` - Generate .vsh/.fsh from graph
- `GLSLEmitter.java` - Helper for GLSL syntax
- `ShaderTemplate.java` - Base shader templates

**Validation**: Simple graph generates valid GLSL code

### Phase 7: Photon Shader System Integration (Tier 2 only)
**Goal**: Compile and apply generated shaders via Photon
**Files to create/modify**:
- `PhotonShaderCompiler.java` - Use CustomShaderMaterial API
- `PhotonMaterialBridge.java` - Create/update Photon materials
- `NodeEditorCanvas.java` - Complete line 543 TODO

**Validation**: Generated shader compiles in Photon, appears in editor

### Phase 8: Real-time Preview Rendering (Tier 2 only)
**Goal**: Live preview of material on 3D model
**Files to modify**:
- `LookDevScreen.java` - Integrate Photon rendering for preview
- `MaterialPreviewRenderer.java` (new) - Render with custom shader

**Validation**: Changes to nodes update preview in real-time

### Phase 9: Material Save/Load System
**Goal**: Serialize node graphs and reload them
**Files to create/modify**:
- `NodeGraphSerializer.java` - Save/load graph to JSON
- `MaterialAssetManager.java` - Manage material files
- Update existing snapshot system in `ParameterPanelFragment.java` (lines 774, 849, 867)

**Validation**: Save graph, reload, verify identical

### Phase 10: UI Polish and Remaining TODOs
**Goal**: Complete all remaining TODO items
**Files to modify**:
- `Node.java` line 87 - Add text rendering for node titles
- `NodeEditorCanvas.java` line 247 - Add text rendering for labels
- `LookDevScreen.java` lines 203, 543 - Pause rotation, split-screen comparison
- `MathNode.java` line 62 - Add operation label text
- `TextureInputNode.java` line 41 - Draw actual texture preview

**Validation**: All TODOs resolved, UI fully functional

## Testing Strategy

### Per-Phase Testing
- Each phase must compile without errors
- Each phase must pass unit tests (where applicable)
- Each phase must be manually tested in-game
- Git commit after each successful phase

### Integration Testing
- Test Tier 1 (without Photon): Basic node editor works
- Test Tier 2 (with Photon): Full shader integration works
- Test edge cases: Empty graph, disconnected nodes, cyclic connections

### Performance Testing
- Graph evaluation performance (target: <1ms for typical graphs)
- Shader compilation time (target: <100ms)
- Real-time preview frame rate (target: 60 FPS)

## Photon Integration Details

### Shader Generation Flow
1. User edits node graph in editor
2. `ShaderCodeGenerator` traverses graph from Material Output node
3. Generate GLSL code for vertex and fragment shaders
4. Write temporary .json/.vsh/.fsh files to `assets/assimploader/shaders/generated/`
5. `PhotonShaderCompiler` creates `CustomShaderMaterial` instance
6. Apply material to preview model via `PhotonMaterialBridge`

### Uniform Management
- Extract uniform values from node properties (Value nodes, Color nodes)
- Store in NBT format compatible with Photon's system
- Update uniforms when node values change (via Runnable cache)

### Texture Binding
- Texture Sample nodes → sampler2D uniforms
- Map texture paths to Photon's resource system
- Handle texture loading and binding via `RenderSystem.setShaderTexture()`

### Hot Reload
- On graph change: regenerate shader, recompile, update material
- Use Photon's "Recompile" mechanism to clear shader cache
- Preserve uniform values across recompiles

## File Structure
```
src/main/java/cn/minerealms/assimploader/lookdev/
├── nodeeditor/
│   ├── nodes/
│   │   ├── input/
│   │   │   ├── TextureSampleNode.java
│   │   │   ├── ValueNode.java
│   │   │   ├── ColorNode.java
│   │   │   └── UVCoordinateNode.java
│   │   ├── math/
│   │   │   ├── MultiplyNode.java
│   │   │   ├── AddNode.java
│   │   │   └── LerpNode.java
│   │   ├── utility/
│   │   │   └── NormalMapNode.java
│   │   └── output/
│   │       └── MaterialOutputNode.java
│   ├── NodeType.java
│   ├── NodeRegistry.java
│   ├── NodeFactory.java
│   ├── SocketType.java
│   ├── TypeConverter.java
│   └── NodeEvaluationContext.java
├── shader/
│   ├── ShaderCodeGenerator.java
│   ├── GLSLEmitter.java
│   └── ShaderTemplate.java
├── photon/
│   ├── PhotonIntegration.java
│   ├── PhotonShaderCompiler.java
│   ├── PhotonMaterialBridge.java
│   └── MaterialPreviewRenderer.java
└── serialization/
    ├── NodeGraphSerializer.java
    └── MaterialAssetManager.java
```

## Success Criteria
- [ ] All 10 phases completed and committed
- [ ] All TODO items resolved
- [ ] Tier 1 works without Photon (basic editor)
- [ ] Tier 2 works with Photon (full shader integration)
- [ ] Real-time preview updates smoothly
- [ ] Materials can be saved and reloaded
- [ ] No compilation errors or warnings
- [ ] Documentation updated

## Timeline Estimate
- Phase 1-2: Foundation and nodes (2-3 days)
- Phase 3-4: Type system and execution (2 days)
- Phase 5: Conditional loading (1 day)
- Phase 6-8: Shader generation and Photon integration (3-4 days)
- Phase 9: Serialization (1-2 days)
- Phase 10: Polish and TODOs (1-2 days)
- **Total: ~10-14 days of development**


## Important Guidelines

### Development Workflow (CRITICAL)
**每完成一个 Phase 必须执行以下循环，不得跳过：**

1. **编译验证** (`gradlew build`)
   - 必须编译成功，无错误
   - 解决所有编译警告
   - 检查依赖项是否正确

2. **代码审查**
   - 仔细检查所有新增/修改的代码
   - 验证逻辑正确性
   - 确保符合现有代码风格
   - 检查是否有潜在的 NPE 或资源泄漏

3. **功能测试**
   - 启动游戏测试新功能
   - 验证 Phase 目标是否达成
   - 测试边界情况和错误处理
   - 确保不影响现有功能

4. **Git 提交**
   - 提交信息格式：`feat(lookdev): Phase X - <简短描述>`
   - 提交前再次检查 git diff
   - 确保只提交相关文件
   - 不提交临时文件或调试代码

**示例提交信息**：
```
feat(lookdev): Phase 1 - Add node type system foundation
feat(lookdev): Phase 2 - Implement concrete node classes
fix(lookdev): Phase 3 - Fix type conversion edge cases
```

### Technical Guidelines

#### Mixin Usage
- **允许使用 Mixin** 来集成 Photon 系统
- Mixin 目标类必须添加 `@Mixin` 注解
- 使用 `@Inject` 时指定正确的注入点
- 避免过度使用 Mixin，优先使用事件或 API
- Mixin 类必须放在 `mixin` 包下
- 在 `assimploader.mixins.json` 中注册所有 Mixin

**Mixin 示例场景**：
- Hook Photon 的 shader 编译流程
- 拦截材质加载事件
- 注入自定义渲染逻辑

#### Code Quality Checks
**每次提交前必须检查**：
- [ ] 所有 import 语句正确且必要
- [ ] 没有未使用的变量或方法
- [ ] 所有公共 API 有 Javadoc 注释
- [ ] 异常处理完整（try-catch 或 throws）
- [ ] 资源正确关闭（使用 try-with-resources）
- [ ] 线程安全（如果涉及多线程）
- [ ] 没有硬编码的魔法数字或字符串

#### Common Pitfalls to Avoid
1. **反射使用**：
   - 缓存 Field/Method 对象，避免重复查找
   - 处理 NoSuchFieldException/IllegalAccessException
   - 考虑性能影响

2. **Photon 集成**：
   - 检查 Photon 是否加载再调用 API
   - 使用 Optional 或 null 检查
   - 不要假设 Photon 类一定存在

3. **ModernUI 渲染**：
   - 正确使用 Canvas.save()/restore()
   - 避免在渲染循环中创建对象
   - 注意坐标系转换

4. **内存管理**：
   - 及时清理 node graph 引用
   - 避免循环引用导致内存泄漏
   - 大对象使用弱引用

#### Testing Checklist per Phase
- [ ] 编译通过（`gradlew build`）
- [ ] 游戏启动无崩溃
- [ ] 日志无 ERROR 级别输出
- [ ] 新功能按预期工作
- [ ] 现有功能未受影响
- [ ] 代码已仔细审查
- [ ] Git 提交信息清晰

## Notes
- Prioritize Tier 1 functionality first (works without Photon)
- Photon integration is additive, not required
- Keep shader generation modular for future extensibility
- Consider adding more node types in future (e.g., Fresnel, Gradient, Noise)
