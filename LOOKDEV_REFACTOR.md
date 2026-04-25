# LookDev Viewer 重构设计文档

## 1. 项目概述

LookDev Viewer 是一个用于实时预览和调整 3D 模型材质的工具，集成在 Minecraft 中。

### 1.1 核心目标

- 从已加载的 FBX 模型缓存中获取所有数据（模型、材质、纹理）
- 提供节点编辑器界面，支持实时调整材质参数
- 使用真实的 ItemRenderer/BlockEntityRenderer 进行实时预览
- 支持 3D 视口交互（旋转、缩放、平移）

### 1.2 用户工作流

```
1. 执行 /lookdev 命令打开界面
2. 左侧面板：选择已加载的 FBX 模型
3. 左侧面板：选择模型中的材质
4. 右侧面板：调整材质参数（Roughness, Metallic, Base Color 等）
5. 中央面板：实时预览渲染效果
6. 中央面板：使用鼠标交互（拖动旋转、滚轮缩放、右键平移）
```

---

## 2. 当前状态分析

### 2.1 已完成功能 ✓

1. **Fragment 生命周期修复**
   - 使用 FragmentContainerView + FragmentManager 正确管理子 Fragment
   - 所有子 Fragment 可以正常访问 Context
   - 日志显示："Child fragments added successfully"

2. **三面板布局**
   - 左侧：ModelTreeFragment (20% 宽度)
   - 中央：PreviewFragment (60% 宽度)
   - 右侧：ParameterPanelFragment (20% 宽度)

3. **基础数据结构**
   - `AssimpScene`: 封装 Assimp 场景数据
   - `AssimpMaterial`: 封装 Assimp 材质数据
   - `PBRMaterial`: PBR 材质参数
   - `OrbitCamera`: 轨道相机控制

4. **模型缓存系统**
   - `AssimpModelLoader.getAllCachedModels()`: 获取所有已加载模型
   - `AssimpModelLoader.getCachedModel(name)`: 获取指定模型

5. **调试日志系统**
   - `DebugManager`: 统一的日志管理
   - `DebugCategory.LOOKDEV`: LookDev 专用日志分类

### 2.2 待完成功能 ✗

1. **模型和材质加载**
   - ✗ 从模型缓存加载所有 FBX 模型
   - ✗ 在 ModelTreeFragment 中显示模型列表
   - ✗ 提取完整的材质信息（纹理路径、PBR 参数）
   - ✗ 支持用户选择不同的模型和材质

2. **实时渲染预览**
   - ✗ 集成 ItemRenderer 或 BlockEntityRenderer
   - ✗ 使用真实的 OpenGL 渲染管线
   - ✗ 应用当前选中的材质参数
   - ✗ 连接 OrbitCamera 到渲染矩阵

3. **参数编辑和实时更新**
   - ✗ ParameterPanelFragment 参数修改回调
   - ✗ 参数修改触发 PreviewFragment 重新渲染
   - ✗ 实时反映材质参数变化

4. **相机交互**
   - ✗ 完善鼠标/触摸输入处理
   - ✗ 自动聚焦到模型边界
   - ✗ 相机状态显示

---

## 3. 系统架构设计

### 3.1 数据流图

```
┌─────────────────────────────────────────────────────────────┐
│                     AssimpModelLoader                        │
│                    (模型缓存管理器)                           │
│  - getAllCachedModels(): Map<String, AssimpScene>           │
│  - getCachedModel(name): AssimpScene                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      LookDevScreen                           │
│                   (主控制器 Fragment)                         │
│                                                              │
│  State:                                                      │
│  - currentModel: AssimpScene                                │
│  - materials: List<PBRMaterial>                             │
│  - selectedMaterialIndex: int                               │
│                                                              │
│  Methods:                                                    │
│  - loadTestData(): 从缓存加载模型                            │
│  - extractMaterials(scene): 提取材质                         │
│  - selectMaterial(index): 选择材质                           │
│  - onMaterialParametersChanged(): 参数修改回调               │
└─────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ModelTreeFragment │  │ PreviewFragment  │  │ParameterPanel    │
│                  │  │                  │  │Fragment          │
│- 显示模型列表    │  │- 3D 实时预览     │  │- 材质参数编辑    │
│- 显示材质列表    │  │- 相机交互        │  │- 纹理通道开关    │
│- 显示网格列表    │  │- 渲染管线集成    │  │- 数值滑块        │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

### 3.2 组件职责

#### 3.2.1 LookDevScreen (主控制器)

**职责：**
- 管理全局状态（当前模型、材质列表、选中材质）
- 协调三个子 Fragment 之间的通信
- 实现 IModelProvider 和 IMaterialProvider 接口
- 处理键盘快捷键

**关键方法：**
```java
// 数据加载
private void loadTestData()
private void extractMaterials(AssimpScene scene)

// 材质管理
public boolean selectMaterial(int index)
public PBRMaterial getSelectedMaterial()
public void onMaterialParametersChanged()

// 模型管理
public AssimpScene getCurrentModel()
public boolean loadModel(String modelPath)
```

#### 3.2.2 ModelTreeFragment (左侧面板)

**职责：**
- 显示所有已加载的模型列表
- 显示当前模型的材质列表
- 显示当前模型的网格列表
- 处理用户选择事件

**UI 结构：**
```
┌─────────────────────┐
│ Model               │
│ ├─ blocks/ccb.fbx   │ ← 可选择
│ ├─ blocks/cnc.fbx   │
│ └─ ...              │
│                     │
│ Materials           │
│ ├─ Material 1       │ ← 可选择
│ ├─ Material 2       │
│ └─ ...              │
│                     │
│ Meshes              │
│ ├─ Mesh_0 (8 tris)  │
│ ├─ Mesh_1 (12 tris) │
│ └─ ...              │
└─────────────────────┘
```

**关键方法：**
```java
public void updateModel()  // 刷新模型信息显示
public void setModelProvider(IModelProvider provider)
public void setMaterialProvider(IMaterialProvider provider)
```

#### 3.2.3 PreviewFragment (中央面板)

**职责：**
- 使用 ItemRenderer/BlockEntityRenderer 渲染模型
- 应用当前选中的材质参数
- 处理相机交互（旋转、缩放、平移）
- 实时更新渲染

**渲染管线：**
```
用户输入 → OrbitCamera → 视图矩阵 → OpenGL 渲染 → 显示
                                      ↑
                          当前模型 + 当前材质参数
```

**关键方法：**
```java
public void setModel(AssimpScene model)
public void setMaterial(PBRMaterial material)
public void refresh()  // 触发重新渲染
public void resetCamera()
```

#### 3.2.4 ParameterPanelFragment (右侧面板)

**职责：**
- 显示当前材质的所有参数
- 提供滑块、颜色选择器等 UI 控件
- 参数修改时回调 LookDevScreen
- 显示纹理通道信息

**UI 结构：**
```
┌─────────────────────┐
│ Material Parameters │
│                     │
│ Texture Channels    │
│ □ Base Color        │ ← 复选框
│ □ Normal Map        │
│ □ Metallic          │
│ □ Roughness         │
│ □ Emissive          │
│                     │
│ Parameters          │
│ Roughness  [====|--]│ ← 滑块
│            0.30     │
│ Metallic   [========]│
│            0.80     │
│                     │
│ Base Color          │
│ R [============|---]│
│   255               │
│ G [============|---]│
│   255               │
│ B [============|---]│
│   255               │
└─────────────────────┘
```

**关键方法：**
```java
public void setMaterial(PBRMaterial material)
public void toggleChannel(int channelIndex)
private void onParameterChanged()  // 回调 LookDevScreen
```

---

## 4. 技术实现细节

### 4.1 模型和材质加载

#### 4.1.1 从缓存加载模型

```java
// LookDevScreen.loadTestData()
private void loadTestData() {
    // 获取所有已加载的模型
    var cachedModels = AssimpModelLoader.getAllCachedModels();
    
    DebugManager.info(DebugCategory.LOOKDEV, "Found {} cached models", cachedModels.size());
    
    if (!cachedModels.isEmpty()) {
        // 默认加载第一个模型
        var firstEntry = cachedModels.entrySet().iterator().next();
        String modelName = firstEntry.getKey();
        AssimpScene scene = firstEntry.getValue();
        
        DebugManager.info(DebugCategory.LOOKDEV, "Loading model: {}", modelName);
        currentModel = scene;
        
        // 提取材质
        extractMaterials(scene);
        
        // 更新预览
        if (previewFragment != null) {
            previewFragment.setModel(currentModel);
        }
    } else {
        DebugManager.warn(DebugCategory.LOOKDEV, "No models in cache");
    }
    
    // 选择第一个材质
    if (!materials.isEmpty()) {
        selectMaterial(0);
    }
    
    // 更新树视图
    if (modelTreeFragment != null) {
        modelTreeFragment.updateModel();
    }
}
```

#### 4.1.2 提取材质信息

```java
// LookDevScreen.extractMaterials()
private void extractMaterials(AssimpScene scene) {
    materials.clear();
    
    if (scene == null) {
        DebugManager.warn(DebugCategory.LOOKDEV, "Scene is null");
        return;
    }
    
    var assimpMaterials = scene.getMaterials();
    DebugManager.info(DebugCategory.LOOKDEV, "Extracting {} materials", assimpMaterials.size());
    
    for (int i = 0; i < assimpMaterials.size(); i++) {
        var assimpMat = assimpMaterials.get(i);
        
        // 创建 PBR 材质
        String name = "Material " + (i + 1);
        PBRMaterial pbrMat = new PBRMaterial(name);
        
        // 提取纹理路径
        String diffuseTex = assimpMat.getDiffuseTexture();
        String normalTex = assimpMat.getNormalTexture();
        String specularTex = assimpMat.getSpecularTexture();
        
        // TODO: 将纹理路径转换为 ResourceLocation 并设置到 PBRMaterial
        
        // 设置默认 PBR 值
        pbrMat.setRoughnessFactor(0.5f);
        pbrMat.setMetallicFactor(0.0f);
        
        materials.add(pbrMat);
        
        DebugManager.debug(DebugCategory.LOOKDEV, 
            "Material {}: diffuse={}, normal={}, specular={}", 
            i, diffuseTex, normalTex, specularTex);
    }
    
    DebugManager.info(DebugCategory.LOOKDEV, "Extracted {} materials", materials.size());
}
```

### 4.2 实时渲染集成

#### 4.2.1 PreviewRenderer 重构

**当前状态（占位符）：**
```java
// PreviewFragment.PreviewRenderer.onDraw()
@Override
public void onDraw(@NonNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                  double guiScale, float alpha) {
    if (currentModel == null || currentMaterial == null) {
        return;
    }
    
    // 绘制网格和坐标轴（占位符）
    drawGrid(gr);
    drawAxes(gr);
    
    // TODO: 渲染真实模型
}
```

**目标实现：**
```java
@Override
public void onDraw(@NonNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                  double guiScale, float alpha) {
    if (currentModel == null || currentMaterial == null) {
        return;
    }
    
    // 1. 设置 OpenGL 状态
    RenderSystem.enableDepthTest();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    
    // 2. 获取相机矩阵
    Matrix4f viewMatrix = camera.getViewMatrix();
    Matrix4f projectionMatrix = camera.getProjectionMatrix(
        surfaceWidth, surfaceHeight, 0.1f, 1000.0f);
    
    // 3. 设置着色器
    RenderSystem.setShader(GameRenderer::getPositionTexColorNormalShader);
    
    // 4. 绑定材质纹理
    bindMaterialTextures(currentMaterial);
    
    // 5. 渲染模型网格
    renderModel(currentModel, viewMatrix, projectionMatrix);
    
    // 6. 恢复 OpenGL 状态
    RenderSystem.disableBlend();
}

private void bindMaterialTextures(PBRMaterial material) {
    // 绑定 Base Color 纹理
    if (material.getBaseColorTexture() != null) {
        RenderSystem.setShaderTexture(0, material.getBaseColorTexture());
    }
    
    // 绑定 Normal 纹理
    if (material.getNormalTexture() != null) {
        RenderSystem.setShaderTexture(1, material.getNormalTexture());
    }
    
    // TODO: 绑定其他纹理通道
}

private void renderModel(AssimpScene scene, Matrix4f view, Matrix4f projection) {
    var meshes = scene.getMeshes();
    
    for (var mesh : meshes) {
        // 获取顶点数据
        var vertices = mesh.getVertices();
        var indices = mesh.getIndices();
        
        // 使用 BufferBuilder 构建渲染数据
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        
        // 添加顶点
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            var vertex = vertices[idx];
            
            builder.vertex(vertex.position.x, vertex.position.y, vertex.position.z)
                   .uv(vertex.texCoord.x, vertex.texCoord.y)
                   .color(255, 255, 255, 255)
                   .normal(vertex.normal.x, vertex.normal.y, vertex.normal.z)
                   .endVertex();
        }
        
        // 渲染
        BufferUploader.drawWithShader(builder.end());
    }
}
```

#### 4.2.2 OrbitCamera 集成

```java
// OrbitCamera.java
public Matrix4f getViewMatrix() {
    Matrix4f matrix = new Matrix4f();
    
    // 1. 平移到原点
    matrix.translate(0, 0, -distance);
    
    // 2. 应用旋转
    matrix.rotateX((float) Math.toRadians(elevation));
    matrix.rotateY((float) Math.toRadians(azimuth));
    
    // 3. 应用平移偏移
    matrix.translate(-panX, -panY, 0);
    
    return matrix;
}

public Matrix4f getProjectionMatrix(int width, int height, float near, float far) {
    float aspect = (float) width / height;
    float fov = (float) Math.toRadians(45.0f);
    
    Matrix4f matrix = new Matrix4f();
    matrix.perspective(fov, aspect, near, far);
    
    return matrix;
}
```

### 4.3 参数修改和实时更新

#### 4.3.1 参数修改回调链

```
用户拖动滑块
    ↓
ParameterPanelFragment.onSliderChanged()
    ↓
更新 PBRMaterial 对象
    ↓
调用 LookDevScreen.onMaterialParametersChanged()
    ↓
调用 PreviewFragment.refresh()
    ↓
设置 needsRedraw = true
    ↓
下一帧调用 PreviewRenderer.onDraw()
    ↓
使用新参数渲染
```

#### 4.3.2 实现代码

```java
// ParameterPanelFragment.java
private void setupRoughnessSlider() {
    roughnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (currentMaterial != null && fromUser) {
                float value = progress / 100.0f;
                currentMaterial.setRoughnessFactor(value);
                
                DebugManager.debug(DebugCategory.LOOKDEV, 
                    "Roughness changed: {}", value);
                
                // 触发回调
                onParameterChanged();
            }
        }
    });
}

private void onParameterChanged() {
    // 通知父 Fragment
    Fragment parent = getParentFragment();
    if (parent instanceof LookDevScreen) {
        ((LookDevScreen) parent).onMaterialParametersChanged();
    }
}
```

```java
// LookDevScreen.java
public void onMaterialParametersChanged() {
    DebugManager.debug(DebugCategory.LOOKDEV, "Material parameters changed");
    
    // 刷新预览
    if (previewFragment != null) {
        previewFragment.refresh();
    }
}
```

```java
// PreviewFragment.java
public void refresh() {
    needsRedraw = true;
    requestRender();
    
    DebugManager.debug(DebugCategory.LOOKDEV, "Preview refresh requested");
}

private void requestRender() {
    if (surfaceView == null) return;
    
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastRenderTime >= RENDER_THROTTLE_MS) {
        surfaceView.invalidate();
        lastRenderTime = currentTime;
    }
}
```

---

## 5. 日志系统规范

### 5.1 日志级别使用

```java
// INFO: 关键操作和状态变化
DebugManager.info(DebugCategory.LOOKDEV, "Loading model: {}", modelName);
DebugManager.info(DebugCategory.LOOKDEV, "Material selected: {} (index: {})", name, index);

// DEBUG: 详细的执行流程
DebugManager.debug(DebugCategory.LOOKDEV, "Extracting {} materials", count);
DebugManager.debug(DebugCategory.LOOKDEV, "Camera state: azimuth={}, elevation={}", az, el);

// WARN: 非致命问题
DebugManager.warn(DebugCategory.LOOKDEV, "No models in cache");
DebugManager.warn(DebugCategory.LOOKDEV, "Material has no diffuse texture");

// ERROR: 错误和异常
DebugManager.error(DebugCategory.LOOKDEV, "Failed to load model", exception);
DebugManager.error(DebugCategory.LOOKDEV, "Render error: {}", errorMessage);
```

### 5.2 关键日志点

1. **模型加载：**
   - 开始加载：`"Loading model from cache: {}"`
   - 加载成功：`"Model loaded: {} meshes, {} materials"`
   - 加载失败：`"Failed to load model: {}"`

2. **材质提取：**
   - 开始提取：`"Extracting {} materials from model"`
   - 每个材质：`"Material {}: diffuse={}, normal={}, specular={}"`
   - 提取完成：`"Extracted {} materials"`

3. **材质选择：**
   - 选择材质：`"Material selected: {} (index: {})"`
   - 更新预览：`"Preview updated with material: {}"`
   - 更新参数面板：`"Parameter panel updated with material: {}"`

4. **参数修改：**
   - 参数变化：`"Parameter changed: {}={}"` (例如 "Roughness=0.3")
   - 触发刷新：`"Preview refresh requested"`

5. **渲染：**
   - 开始渲染：`"Rendering frame: model={}, material={}"`
   - 渲染错误：`"Render error: {}"`

6. **相机交互：**
   - 相机移动：`"Camera orbit: azimuth={}, elevation={}"`
   - 相机缩放：`"Camera zoom: distance={}"`
   - 相机重置：`"Camera reset to default position"`

---

## 6. 测试计划

### 6.1 单元测试

1. **OrbitCamera 测试**
   - 测试旋转、缩放、平移功能
   - 测试矩阵计算正确性
   - 测试边界条件（最小/最大距离）

2. **材质提取测试**
   - 测试从 AssimpScene 提取材质
   - 测试纹理路径解析
   - 测试 PBR 参数转换

### 6.2 集成测试

1. **完整工作流测试**
   - 加载游戏 → 执行 /lookdev → 验证界面显示
   - 选择模型 → 验证材质列表更新
   - 选择材质 → 验证预览更新
   - 调整参数 → 验证实时预览

2. **性能测试**
   - 测量渲染帧率（目标 >30 FPS）
   - 测量参数修改延迟（目标 <100ms）
   - 测量内存占用

### 6.3 用户验收测试

1. **功能完整性**
   - 所有已加载的 FBX 模型都能显示
   - 所有材质参数都能调整
   - 预览实时反映参数变化

2. **交互体验**
   - 相机控制流畅自然
   - 参数调整响应及时
   - 界面布局合理清晰

---

## 7. 实现优先级

### Phase 1: 数据加载（高优先级）
- ✓ 从模型缓存加载 FBX
- ✓ 提取材质信息
- ⚠ 在 ModelTreeFragment 中显示模型列表
- ⚠ 支持选择不同的模型

### Phase 2: 渲染集成（高优先级）
- ⚠ 集成 ItemRenderer/BlockEntityRenderer
- ⚠ 连接 OrbitCamera 到渲染
- ⚠ 应用材质参数到渲染

### Phase 3: 实时更新（中优先级）
- ⚠ 参数修改回调链
- ⚠ 预览自动刷新
- ⚠ 性能优化（节流）

### Phase 4: 交互完善（低优先级）
- ⚠ 完善相机控制
- ⚠ 添加快捷键
- ⚠ 优化 UI 布局

---

## 8. 技术难点和解决方案

### 8.1 难点：Minecraft 渲染管线集成

**问题：**
- Minecraft 的渲染系统复杂，涉及 RenderSystem、BufferBuilder、Shader 等
- 需要正确设置 OpenGL 状态
- 需要处理纹理绑定和着色器参数

**解决方案：**
- 参考 ItemRenderer 和 BlockEntityRenderer 的实现
- 使用 ModernUI 的 MinecraftSurfaceView 作为渲染容器
- 逐步实现：先渲染简单网格，再添加纹理，最后添加 PBR 材质

### 8.2 难点：实时性能优化

**问题：**
- 参数修改频繁（拖动滑块）
- 每次修改都触发重新渲染可能导致卡顿

**解决方案：**
- 使用渲染节流（RENDER_THROTTLE_MS = 16ms，约 60 FPS）
- 使用脏标记（needsRedraw）避免不必要的渲染
- 考虑使用双缓冲或延迟更新

### 8.3 难点：纹理路径解析

**问题：**
- AssimpMaterial 返回的纹理路径可能是：
  - 绝对路径：`H:/models/texture.png`
  - 相对路径：`textures/texture.png`
  - 嵌入纹理：`*0`, `*1`

**解决方案：**
- 实现纹理路径解析器
- 将路径转换为 Minecraft 的 ResourceLocation
- 处理嵌入纹理的特殊情况

---

## 9. 下一步行动

1. ✓ 创建任务列表
2. ✓ 编写设计文档
3. ⚠ 实现 ModelTreeFragment 的模型列表显示
4. ⚠ 实现 PreviewFragment 的渲染管线集成
5. ⚠ 实现参数修改的实时更新
6. ⚠ 测试完整工作流
7. ⚠ 性能优化和 bug 修复

---

## 10. 参考资料

- ModernUI Fragment 文档
- Minecraft RenderSystem API
- Assimp 材质属性文档
- PBR 渲染理论：https://learnopengl.com/PBR/Theory
- glTF 材质规范：https://github.com/KhronosGroup/glTF/blob/master/specification/2.0/README.md#materials
