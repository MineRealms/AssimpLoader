# LookDev Node Editor 重构方案

## 当前问题分析

### 1. 材质与节点图的关系不清晰
**现状：**
- `NodeEditorFragment` 有 `currentMaterial` 字段，但没有真正使用
- `MaterialNodeGraphGenerator` 可以从 PBRMaterial 生成节点图，但这个功能没有被调用
- 用户在 Model 选择材质时，节点编辑器不会加载对应的节点图
- 节点图的修改不会自动保存，切换材质会丢失编辑内容

**问题根源：**
- 缺少"材质 ↔ 节点图"的双向绑定机制
- 没有实现节点图的持久化存储（虽然有 MaterialFileManager，但没有被使用）
- 预览渲染器在单独的 Preview 标签页，用户体验割裂

### 2. 预览系统的设计问题
**现状：**
- `NodePreviewPanel`：在右侧属性面板顶部，预览单个节点的输出（颜色/值）
- `PreviewFragment`：在 Preview 标签页，使用 OffscreenRenderer 渲染完整的 3D 模型
- 用户在 Node Editor 标签页编辑，需要切换到 Preview 标签页才能看到效果

**问题根源：**
- 两个预览系统的职责混淆
- 缺少实时预览反馈机制
- 用户工作流被打断（编辑 → 切换标签 → 查看 → 切换回来）

### 3. 快捷键功能未完全实现
**现状：**
- `KeyboardShortcutManager` 定义了 Ctrl+S（保存）、Ctrl+Z/Y（撤销/重做）
- Ctrl+Z/Y 已实现（使用 CommandHistory）
- Ctrl+S 只是显示 "Save material not yet implemented"

**问题根源：**
- `saveMaterial()` 和 `openMaterial()` 是空实现
- 没有连接到 `MaterialFileManager`

---

## 系统设计目标

### 核心理念
**LookDev Node Editor 是一个材质编辑器，而不是通用的节点编辑器。**

每个 PBRMaterial 都应该有对应的节点图：
1. **输入**：从模型加载的 PBRMaterial（纹理路径、颜色值、参数）
2. **编辑**：通过节点图可视化编辑材质属性
3. **输出**：生成新的 PBRMaterial 或 GLSL shader 代码
4. **预览**：实时在 3D 模型上看到效果
5. **持久化**：节点图保存到文件，下次打开自动加载

### 用户工作流
```
1. 用户在 Model Tree 中选择一个材质
   ↓
2. Node Editor 自动加载该材质的节点图（如果存在）
   或生成默认节点图（从 PBRMaterial 转换）
   ↓
3. 用户编辑节点图
   ↓
4. 实时预览在右侧 NodePreviewPanel 显示（材质球预览）
   ↓
5. 用户按 Ctrl+S 保存节点图到文件
   ↓
6. 用户切换到其他材质，当前节点图自动保存到内存
   ↓
7. 用户返回之前的材质，节点图从内存恢复
   ↓
8. 用户点击 "Generate Material" 或 Ctrl+G，应用到 3D 模型预览
```

---

## 重构方案

### Phase 1: 材质-节点图绑定系统

#### 1.1 创建 MaterialNodeGraphManager
**职责：**
- 管理每个材质的节点图状态
- 自动加载/保存节点图
- 处理材质切换时的节点图切换

**实现：**
```java
public class MaterialNodeGraphManager {
    // 内存缓存：材质名 -> 节点图数据
    private Map<String, NodeGraphState> nodeGraphCache = new HashMap<>();
    
    // 当前正在编辑的材质
    private String currentMaterialName = null;
    
    /**
     * 切换到指定材质的节点图
     * @param material 要编辑的材质
     * @param canvas 节点编辑器画布
     * @return 是否成功加载
     */
    public boolean switchToMaterial(PBRMaterial material, NodeEditorCanvas canvas) {
        // 1. 保存当前节点图到缓存
        if (currentMaterialName != null) {
            saveCurrentToCache(canvas);
        }
        
        // 2. 加载新材质的节点图
        String materialName = material.getName();
        NodeGraphState state = nodeGraphCache.get(materialName);
        
        if (state == null) {
            // 2a. 尝试从文件加载
            state = loadFromFile(materialName);
            
            if (state == null) {
                // 2b. 生成默认节点图
                state = generateDefaultNodeGraph(material);
            }
            
            nodeGraphCache.put(materialName, state);
        }
        
        // 3. 应用到画布
        canvas.loadNodeGraph(state.nodes, state.connections);
        currentMaterialName = materialName;
        
        return true;
    }
    
    /**
     * 保存当前节点图（Ctrl+S）
     */
    public boolean saveCurrentToFile(NodeEditorCanvas canvas) {
        if (currentMaterialName == null) return false;
        
        // 更新缓存
        saveCurrentToCache(canvas);
        
        // 保存到文件
        NodeGraphState state = nodeGraphCache.get(currentMaterialName);
        return MaterialFileManager.save(
            currentMaterialName,
            state.nodes,
            state.connections
        );
    }
    
    /**
     * 生成默认节点图（从 PBRMaterial）
     */
    private NodeGraphState generateDefaultNodeGraph(PBRMaterial material) {
        MaterialNodeGraphGenerator.NodeGraphData data = 
            MaterialNodeGraphGenerator.generateFromMaterial(material);
        
        return new NodeGraphState(data.nodes, data.connections);
    }
    
    /**
     * 从文件加载节点图
     */
    private NodeGraphState loadFromFile(String materialName) {
        if (!MaterialFileManager.exists(materialName)) {
            return null;
        }
        
        MaterialDeserializer.DeserializeResult result = 
            MaterialFileManager.load(materialName);
        
        if (!result.isSuccess()) {
            return null;
        }
        
        return new NodeGraphState(result.nodes, result.connections);
    }
    
    /**
     * 保存当前节点图到缓存
     */
    private void saveCurrentToCache(NodeEditorCanvas canvas) {
        if (currentMaterialName == null) return;
        
        List<Node> nodes = canvas.getNodes();
        List<NodeConnection> connections = canvas.getConnections();
        
        nodeGraphCache.put(
            currentMaterialName,
            new NodeGraphState(nodes, connections)
        );
    }
    
    /**
     * 节点图状态
     */
    public static class NodeGraphState {
        public final List<Node> nodes;
        public final List<NodeConnection> connections;
        
        public NodeGraphState(List<Node> nodes, List<NodeConnection> connections) {
            this.nodes = new ArrayList<>(nodes);
            this.connections = new ArrayList<>(connections);
        }
    }
}
```

#### 1.2 修改 NodeEditorFragment
**添加：**
```java
public class NodeEditorFragment extends Fragment {
    // 新增
    private MaterialNodeGraphManager nodeGraphManager;
    private PBRMaterial currentMaterial;
    
    @Override
    public void onCreate(@Nullable DataSet savedInstanceState) {
        super.onCreate(savedInstanceState);
        nodeGraphManager = new MaterialNodeGraphManager();
    }
    
    /**
     * 加载材质的节点图（从 LookDevScreen 调用）
     */
    public void loadMaterial(PBRMaterial material) {
        if (material == null) return;
        
        currentMaterial = material;
        
        boolean success = nodeGraphManager.switchToMaterial(material, canvas);
        
        if (success) {
            notificationManager.success("Loaded material: " + material.getName());
        } else {
            notificationManager.error("Failed to load material: " + material.getName());
        }
        
        // 更新标题
        updateTitle();
    }
    
    /**
     * 保存当前材质的节点图（Ctrl+S）
     */
    private void saveMaterial() {
        if (currentMaterial == null) {
            notificationManager.warning("No material loaded");
            return;
        }
        
        boolean success = nodeGraphManager.saveCurrentToFile(canvas);
        
        if (success) {
            notificationManager.success("Saved: " + currentMaterial.getName());
        } else {
            notificationManager.error("Failed to save material");
        }
    }
    
    /**
     * 更新标题显示当前材质名
     */
    private void updateTitle() {
        if (currentMaterial != null) {
            statusText.setText("Editing: " + currentMaterial.getName());
        } else {
            statusText.setText("Node Editor");
        }
    }
}
```

#### 1.3 修改 LookDevScreen
**添加材质选择回调：**
```java
public class LookDevScreen extends Fragment {
    /**
     * 当用户在 Model Tree 中选择材质时调用
     */
    public void onMaterialSelected(PBRMaterial material) {
        // 更新当前材质
        selectedMaterialIndex = materials.indexOf(material);
        
        // 通知 Node Editor 加载该材质的节点图
        NodeEditorFragment nodeEditor = getNodeEditorFragment();
        if (nodeEditor != null) {
            nodeEditor.loadMaterial(material);
        }
        
        // 更新参数面板
        if (parameterPanelFragment != null) {
            parameterPanelFragment.setMaterial(material);
        }
        
        // 更新预览
        PreviewFragment preview = getPreviewFragment();
        if (preview != null) {
            preview.setMaterial(material);
        }
    }
    
    /**
     * 获取 NodeEditorFragment
     */
    private NodeEditorFragment getNodeEditorFragment() {
        if (tabbedCenterFragment != null) {
            return tabbedCenterFragment.getNodeEditorFragment();
        }
        return null;
    }
}
```

---

### Phase 2: 预览系统重构

#### 2.1 明确两个预览系统的职责

**NodePreviewPanel（右侧属性面板顶部）：**
- 预览单个节点的输出值
- 显示颜色、浮点数、向量
- 保持现有实现

**MaterialPreviewRenderer（新增）：**
- 在 NodePreviewPanel 下方添加材质球预览
- 使用 OffscreenRenderer 渲染一个简单的球体
- 实时显示当前节点图生成的材质效果
- 尺寸：200x200 像素

#### 2.2 实现 MaterialPreviewRenderer
```java
public class MaterialPreviewRenderer {
    private OffscreenRenderer renderer;
    private PreviewMesh sphereMesh;
    private PBRMaterial currentMaterial;
    
    public MaterialPreviewRenderer() {
        // 创建离屏渲染器（200x200）
        renderer = new OffscreenRenderer(200, 200);
        
        // 创建球体网格
        sphereMesh = PreviewMesh.createSphere(32, 32);
    }
    
    /**
     * 更新材质预览
     */
    public void updateMaterial(PBRMaterial material) {
        this.currentMaterial = material;
        refresh();
    }
    
    /**
     * 渲染材质球
     */
    public void refresh() {
        if (currentMaterial == null) return;
        
        renderer.beginRender();
        renderer.setMaterial(currentMaterial);
        renderer.renderMesh(sphereMesh);
        renderer.endRender();
    }
    
    /**
     * 获取渲染结果（用于绘制到 Canvas）
     */
    public int[] getPixels() {
        return renderer.readPixels();
    }
}
```

#### 2.3 修改 NodePropertiesPanel
```java
public class NodePropertiesPanel extends LinearLayout {
    private NodePreviewPanel nodePreviewPanel;      // 节点输出预览
    private MaterialPreviewView materialPreviewView; // 材质球预览（新增）
    
    private void initializeUI() {
        // 1. 节点输出预览（现有）
        nodePreviewPanel = new NodePreviewPanel(getContext());
        addView(nodePreviewPanel, ...);
        
        // 2. 材质球预览（新增）
        materialPreviewView = new MaterialPreviewView(getContext());
        LayoutParams previewParams = new LayoutParams(
            LayoutParams.MATCH_PARENT, 
            dp(200)
        );
        previewParams.setMargins(0, 10, 0, 10);
        addView(materialPreviewView, previewParams);
        
        // 3. 属性编辑区域（现有）
        // ...
    }
    
    /**
     * 更新材质预览（从 NodeEditorFragment 调用）
     */
    public void updateMaterialPreview(PBRMaterial material) {
        materialPreviewView.setMaterial(material);
    }
}
```

#### 2.4 修改 NodeEditorFragment
```java
/**
 * 生成材质并更新预览
 */
private void onGenerateMaterial() {
    // 1. 从节点图生成 PBRMaterial
    PBRMaterial material = generateMaterialFromNodes();
    
    // 2. 更新右侧材质球预览
    if (propertiesPanel != null) {
        propertiesPanel.updateMaterialPreview(material);
    }
    
    // 3. 应用到 LookDevScreen（可选，用户可以选择是否应用）
    // ...
}
```

---

### Phase 3: 自动保存和恢复

#### 3.1 自动保存机制
```java
public class MaterialNodeGraphManager {
    private ScheduledExecutorService autoSaveExecutor;
    private boolean autoSaveEnabled = true;
    private int autoSaveIntervalSeconds = 60; // 每60秒自动保存
    
    public void startAutoSave(NodeEditorCanvas canvas) {
        if (!autoSaveEnabled) return;
        
        autoSaveExecutor = Executors.newSingleThreadScheduledExecutor();
        autoSaveExecutor.scheduleAtFixedRate(
            () -> {
                if (currentMaterialName != null) {
                    saveCurrentToFile(canvas);
                    DebugManager.debug(DebugCategory.LOOKDEV, 
                        "Auto-saved: " + currentMaterialName);
                }
            },
            autoSaveIntervalSeconds,
            autoSaveIntervalSeconds,
            TimeUnit.SECONDS
        );
    }
    
    public void stopAutoSave() {
        if (autoSaveExecutor != null) {
            autoSaveExecutor.shutdown();
        }
    }
}
```

#### 3.2 修改 NodeEditorFragment
```java
@Override
public void onResume() {
    super.onResume();
    // 启动自动保存
    nodeGraphManager.startAutoSave(canvas);
}

@Override
public void onPause() {
    super.onPause();
    // 停止自动保存
    nodeGraphManager.stopAutoSave();
    
    // 保存当前状态
    if (currentMaterial != null) {
        nodeGraphManager.saveCurrentToFile(canvas);
    }
}
```

---

### Phase 4: 文件存储结构

#### 4.1 目录结构
```
config/assimploader/materials/
├── <material_name>.material.json    # 节点图文件
├── <material_name>.material.json
└── ...
```

#### 4.2 文件格式（已有 MaterialSerializer）
```json
{
  "version": "1.0",
  "materialName": "wood_planks",
  "nodes": [
    {
      "id": "node_1",
      "type": "TEXTURE_SAMPLE",
      "position": {"x": 100, "y": 100},
      "properties": {
        "texturePath": "minecraft:block/oak_planks"
      }
    },
    {
      "id": "node_2",
      "type": "OUTPUT",
      "position": {"x": 400, "y": 100}
    }
  ],
  "connections": [
    {
      "from": {"nodeId": "node_1", "socketName": "Color"},
      "to": {"nodeId": "node_2", "socketName": "Base Color"}
    }
  ]
}
```

---

## 实现优先级

### P0 - 核心功能（必须实现）
1. ✅ MaterialNodeGraphManager 创建
2. ✅ NodeEditorFragment.loadMaterial() 实现
3. ✅ LookDevScreen.onMaterialSelected() 回调
4. ✅ Ctrl+S 保存功能实现
5. ✅ 材质切换时节点图自动保存/恢复

### P1 - 预览增强（重要）
6. ✅ MaterialPreviewRenderer 实现
7. ✅ NodePropertiesPanel 添加材质球预览
8. ✅ 实时预览更新机制

### P2 - 用户体验（可选）
9. ⬜ 自动保存功能
10. ⬜ Ctrl+O 打开材质对话框
11. ⬜ 材质列表管理 UI
12. ⬜ 导入/导出材质功能

---

## 测试计划

### 测试场景 1：基本工作流
1. 启动游戏，打开 LookDev 界面
2. 在 Model Tree 选择材质 A
3. Node Editor 自动加载材质 A 的节点图（或生成默认）
4. 编辑节点图
5. 按 Ctrl+S 保存
6. 切换到材质 B
7. Node Editor 自动加载材质 B 的节点图
8. 切换回材质 A
9. 验证：材质 A 的节点图保持之前的编辑状态

### 测试场景 2：预览系统
1. 编辑节点图
2. 点击 "Generate Material"
3. 验证：右侧材质球预览实时更新
4. 验证：NodePreviewPanel 显示选中节点的输出

### 测试场景 3：持久化
1. 编辑材质 A 的节点图
2. 按 Ctrl+S 保存
3. 退出游戏
4. 重新启动游戏
5. 打开 LookDev，选择材质 A
6. 验证：节点图从文件正确加载

---

## 风险和注意事项

### 1. 性能问题
- **风险**：实时预览可能导致频繁的 shader 编译和渲染
- **缓解**：添加防抖机制，用户停止编辑 500ms 后才更新预览

### 2. 文件冲突
- **风险**：多个材质可能有相同的名称
- **缓解**：使用材质的完整路径作为文件名（sanitize 特殊字符）

### 3. 向后兼容
- **风险**：旧版本保存的节点图可能无法加载
- **缓解**：在 MaterialDeserializer 中添加版本检查和迁移逻辑

---

## 总结

这个重构方案的核心思想是：

1. **明确职责**：Node Editor 是材质编辑器，每个材质对应一个节点图
2. **自动化**：材质切换时自动保存/加载节点图，用户无需手动管理
3. **实时反馈**：右侧材质球预览提供即时的视觉反馈
4. **持久化**：节点图保存到文件，支持跨会话使用

实现后，用户体验将大幅提升：
- ✅ 不会丢失编辑内容
- ✅ 快速切换材质
- ✅ 实时预览效果
- ✅ 符合直觉的工作流
