# LookDev Phase 10 后端集成实现指南

## 概述

本文档描述LookDev系统中剩余的24个TODO项的实现路径，主要集中在Phase 2（节点预览）和Phase 10（后端集成）。

## 架构现状

### 已完成的基础设施 ✅

1. **节点图系统**
   - `NodeGraphExecutor`: 拓扑排序、循环检测、执行顺序计算
   - `Node`/`NodeSocket`/`NodeConnection`: 完整的节点图数据结构
   - 20+ 节点类型（数学、输入、纹理、PBR等）

2. **Shader生成系统**
   - `ShaderCodeGenerator`: 完整shader生成（Photon模式）
   - `GLSLFragmentGenerator`: 可注入代码片段生成（Iris/Oculus模式）
   - `GLSLEmitter`/`GLSLFragmentEmitter`: GLSL代码发射器
   - `DualModeShaderManager`: 双模式编译管理器

3. **Shader注入系统**
   - `OculusShaderInjector`: 使用GLSL-Transformer的AST注入器
   - 支持多个注入点（BEFORE_MAIN, AFTER_MAIN, REPLACE_FUNCTION等）
   - 已初始化transformer和Iris检测

4. **运行时检测**
   - `PhotonIntegration`: Photon/Oculus mod检测
   - `FeatureTier`: STANDALONE vs FULL_INTEGRATION模式切换
   - `OculusCompat`: Oculus软依赖兼容层

5. **材质系统**
   - `NodeBasedMaterial`: 节点材质封装
   - `PBRMaterial`: PBR材质数据结构
   - `MaterialComparisonManager`: 材质对比系统

## 核心TODO项分类

### 类别1: 后端集成触发器（4项）

**问题**: 节点图编辑后不会自动触发shader重新生成和材质更新

**涉及文件**:
- `NodeEditorCanvas.java:646` - 连接创建时触发
- `NodeEditorFragment.java:508` - Apply按钮触发
- `NodePropertiesPanel.java` - 属性修改触发

**实现步骤**:

1. **在NodeEditorCanvas中添加材质更新回调**
```java
// NodeEditorCanvas.java:646
private MaterialUpdateListener materialUpdateListener;

public void setMaterialUpdateListener(MaterialUpdateListener listener) {
    this.materialUpdateListener = listener;
}

private void createConnection(NodeSocket outputSocket, NodeSocket inputSocket) {
    // ... 现有连接创建代码 ...
    
    // 触发材质更新
    if (materialUpdateListener != null) {
        materialUpdateListener.onGraphChanged();
    }
}

public interface MaterialUpdateListener {
    void onGraphChanged();
}
```

2. **在NodeEditorFragment中实现完整的shader生成流程**
```java
// NodeEditorFragment.java:508
private void applyMaterial() {
    try {
        // 1. 获取当前节点图
        List<Node> nodes = canvas.getNodes();
        List<NodeConnection> connections = canvas.getConnections();
        
        // 2. 根据FeatureTier选择编译模式
        if (PhotonIntegration.hasShaderSupport()) {
            // FULL_INTEGRATION模式：生成完整shader
            compileAndApplyFullShader(nodes, connections);
        } else {
            // STANDALONE模式：仅执行节点图生成PBRMaterial
            executeGraphAndApplyMaterial(nodes, connections);
        }
    } catch (Exception e) {
        DebugManager.error(DebugCategory.LOOKDEV, "Failed to apply material", e);
    }
}

private void compileAndApplyFullShader(List<Node> nodes, List<NodeConnection> connections) {
    // 使用DualModeShaderManager编译
    DualModeShaderManager.CompiledShader compiled = 
        DualModeShaderManager.compile(nodes, connections);
    
    if (compiled != null && compiled.isSuccess()) {
        // 创建NodeBasedMaterial并应用到预览
        NodeBasedMaterial material = new NodeBasedMaterial(compiled);
        notifyMaterialChanged(material);
    }
}
```

3. **添加防抖动机制**
```java
private ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor();
private ScheduledFuture<?> pendingUpdate = null;

private void scheduleGraphUpdate() {
    if (pendingUpdate != null) {
        pendingUpdate.cancel(false);
    }
    
    pendingUpdate = debounceExecutor.schedule(() -> {
        applyMaterial();
    }, 500, TimeUnit.MILLISECONDS); // 500ms防抖
}
```

### 类别2: Shader注入完成（1项）

**问题**: `DualModeShaderManager.java:137` - OculusShaderInjector未被调用

**实现步骤**:

```java
// DualModeShaderManager.java:137
private static CompiledShader compileInjectMode(List<Node> nodes,
                                                 List<NodeConnection> connections,
                                                 GLSLFragment.MaterialOutputConfig config) {
    // 1. 生成GLSL fragment
    GLSLFragmentGenerator fragmentGen = new GLSLFragmentGenerator(nodes, connections);
    GLSLFragment fragment = fragmentGen.generate(config, null);
    
    if (fragment == null) {
        return null;
    }
    
    // 2. 使用OculusShaderInjector注入
    if (OculusShaderInjector.isAvailable()) {
        String targetShader = "gbuffers_textured"; // 或根据材质类型选择
        String injectedCode = OculusShaderInjector.inject(
            targetShader,
            fragment.getCode(),
            fragment.getUsedUniforms(),
            OculusShaderInjector.InjectionPoint.BEFORE_MAIN
        );
        
        if (injectedCode != null) {
            // 3. 通过Iris API注册修改后的shader
            // 注意：这需要Iris的shader reload机制
            return new CompiledShader(
                CompilationMode.INJECTION,
                injectedCode,
                null, // shaderInstance在注入模式下为null
                fragment.getUsedUniforms()
            );
        }
    }
    
    return null;
}
```

**关键点**: 
- GLSL-Transformer已在`OculusShaderInjector`中初始化
- 需要确定正确的注入点（BEFORE_MAIN vs AFTER_MAIN）
- 需要Iris的shader reload API来应用修改后的shader

### 类别3: 纹理管理和绑定（3项）

**问题**: 纹理未绑定到OpenGL纹理单元

**涉及文件**:
- `NodeBasedMaterial.java:107` - 纹理绑定
- `TextureSampleNode.java:64` - 纹理预览
- `TextureInputNode.java:42` - 纹理输入

**实现步骤**:

1. **在NodeBasedMaterial中实现纹理绑定**
```java
// NodeBasedMaterial.java:107
public void begin() {
    if (compiledShader == null || !compiledShader.isSuccess()) {
        return;
    }
    
    RenderSystem.setShader(() -> compiledShader.shaderInstance);
    
    // 绑定纹理到单元
    int textureUnit = 0;
    for (Map.Entry<Integer, ResourceLocation> entry : textures.entrySet()) {
        ResourceLocation texture = entry.getValue();
        
        // 激活纹理单元
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        
        // 绑定纹理
        Minecraft.getInstance().getTextureManager().bindForSetup(texture);
        
        // 设置shader uniform
        if (compiledShader.shaderInstance != null) {
            compiledShader.shaderInstance.setSampler(
                "u_Texture" + textureUnit, 
                textureUnit
            );
        }
        
        textureUnit++;
    }
    
    setupUniforms();
}
```

2. **在TextureSampleNode中添加纹理预览**
```java
// TextureSampleNode.java:64
@Override
public void drawPreview(Canvas canvas, float x, float y, float size) {
    // 获取纹理ID
    String textureId = getProperty("texture", "");
    if (textureId.isEmpty()) {
        // 绘制占位符
        drawPlaceholder(canvas, x, y, size);
        return;
    }
    
    // 加载纹理
    ResourceLocation textureLoc = new ResourceLocation(textureId);
    
    // 使用Minecraft的纹理管理器绘制
    RenderSystem.setShaderTexture(0, textureLoc);
    RenderSystem.setShader(GameRenderer::getPositionTexShader);
    
    BufferBuilder buffer = Tesselator.getInstance().getBuilder();
    buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
    buffer.vertex(x, y + size, 0).uv(0, 1).endVertex();
    buffer.vertex(x + size, y + size, 0).uv(1, 1).endVertex();
    buffer.vertex(x + size, y, 0).uv(1, 0).endVertex();
    buffer.vertex(x, y, 0).uv(0, 0).endVertex();
    Tesselator.getInstance().end();
}
```

### 类别4: 节点特定预览（2项）

**问题**: MaterialPreviewView只显示最终材质，不支持单个节点预览

**涉及文件**:
- `MaterialPreviewView.java:93` - 节点预览渲染

**实现步骤**:

```java
// MaterialPreviewView.java:93
public void setPreviewNode(Node node) {
    if (node == null) {
        // 清除节点预览，显示完整材质
        this.previewNode = null;
        return;
    }
    
    this.previewNode = node;
    
    // 生成仅包含此节点的临时材质
    List<Node> singleNodeGraph = new ArrayList<>();
    singleNodeGraph.add(node);
    
    // 创建临时输出节点连接到此节点
    OutputNode tempOutput = new OutputNode();
    NodeConnection tempConnection = new NodeConnection(
        node.getOutputSocket(0),
        tempOutput.getInputSocket("albedo")
    );
    
    // 执行节点图
    NodeGraphExecutor executor = new NodeGraphExecutor(
        singleNodeGraph, 
        Collections.singletonList(tempConnection)
    );
    
    Map<String, Object> result = executor.execute(new HashMap<>());
    
    // 将结果可视化（颜色、数值等）
    visualizeNodeOutput(result);
}

private void visualizeNodeOutput(Map<String, Object> result) {
    // 根据输出类型选择可视化方式
    Object output = result.get("output");
    
    if (output instanceof Vector3f) {
        // 颜色预览
        Vector3f color = (Vector3f) output;
        renderColorPreview(color);
    } else if (output instanceof Float) {
        // 灰度预览
        float value = (Float) output;
        renderGrayscalePreview(value);
    }
    // ... 其他类型
}
```

### 类别5: UI增强（2项）

**问题**: 缺少文本输入和用户反馈

**涉及文件**:
- `NodePropertiesPanel.java:383` - 文本编辑
- `NodePreviewPanel.java:217` - 纹理ID显示

**实现方案**:

这两项依赖ModernUI的TextField支持，当前ModernUI版本可能不支持。

**临时方案**: 使用Minecraft原生GUI
```java
// 打开原生文本输入屏幕
Minecraft.getInstance().setScreen(new TextInputScreen(
    Component.literal("Enter Texture ID"),
    currentValue,
    newValue -> {
        // 回调更新属性
        updateProperty("texture", newValue);
    }
));
```

**长期方案**: 等待ModernUI TextField支持或自定义实现

## 实施优先级

### P0 - 核心功能（必须完成）
1. ✅ 后端集成触发器（类别1）- 无此功能则节点图无法生成shader
2. ✅ Shader注入完成（类别2）- Iris/Oculus模式的核心
3. ✅ 纹理绑定（类别3的NodeBasedMaterial部分）- 材质渲染必需

### P1 - 重要功能（应该完成）
4. 🔶 节点特定预览（类别4）- 提升调试体验
5. 🔶 纹理预览（类别3的TextureSampleNode部分）- 视觉反馈

### P2 - 增强功能（可选）
6. ⬜ UI文本输入（类别5）- 可用原生GUI替代

## 技术依赖

### 已满足的依赖 ✅
- GLSL-Transformer: 已在OculusShaderInjector中集成
- Photon/Oculus检测: PhotonIntegration完成
- 节点图执行: NodeGraphExecutor完成
- Shader生成: ShaderCodeGenerator + GLSLFragmentGenerator完成

### 需要确认的依赖 ⚠️
- **Iris Shader Reload API**: 注入后的shader如何应用到渲染管线？
  - 可能需要调用Iris的`ShaderPackReloader`
  - 或者在shader加载时通过mixin拦截
  
- **Photon Shader API**: 完整shader模式如何注册自定义shader？
  - 需要查阅Photon文档
  - 可能需要使用`ShaderInstance`注册

### 缺失的依赖 ❌
- ModernUI TextField: 文本输入功能
  - 临时方案：使用Minecraft原生GUI

## 实施步骤

### 第1步: 完成后端集成触发器（2-3小时）
1. 在NodeEditorCanvas添加MaterialUpdateListener接口
2. 在NodeEditorFragment实现applyMaterial完整流程
3. 添加防抖动机制
4. 测试节点连接后是否触发更新

### 第2步: 完成Shader注入（3-4小时）
1. 在DualModeShaderManager.compileInjectMode中调用OculusShaderInjector
2. 研究Iris的shader reload机制
3. 实现shader注册和应用
4. 测试Iris/Oculus环境下的shader注入

### 第3步: 实现纹理绑定（2小时）
1. 在NodeBasedMaterial.begin()中实现纹理绑定
2. 设置shader uniform
3. 测试纹理是否正确显示

### 第4步: 添加节点预览（3小时）
1. 实现MaterialPreviewView.setPreviewNode()
2. 添加输出可视化逻辑
3. 测试单节点预览

### 第5步: 添加纹理预览（2小时）
1. 在TextureSampleNode中实现drawPreview()
2. 使用Minecraft纹理管理器加载和绘制
3. 测试纹理预览显示

**总计**: 12-14小时开发时间

## 测试计划

### 单元测试
- NodeGraphExecutor循环检测
- GLSLFragmentGenerator代码生成
- OculusShaderInjector注入正确性

### 集成测试
1. **STANDALONE模式**（无Photon）
   - 节点图编辑和执行
   - PBRMaterial生成
   - 材质参数应用

2. **FULL_INTEGRATION模式**（有Photon）
   - 完整shader生成
   - Shader编译成功
   - 材质实时预览

3. **INJECTION模式**（有Iris/Oculus）
   - GLSL fragment生成
   - Shader注入成功
   - 注入后shader正常渲染

### 性能测试
- 节点图执行性能（1000节点图）
- Shader编译时间
- 防抖动机制有效性

## 风险和缓解

### 风险1: Iris Shader Reload API不可用
**影响**: 无法在运行时应用注入的shader
**缓解**: 
- 方案A: 使用mixin在shader加载时拦截
- 方案B: 要求用户手动reload shader pack（F3+R）

### 风险2: Photon API变更
**影响**: ShaderInstance注册失败
**缓解**: 
- 使用反射动态调用API
- 提供多版本兼容层

### 风险3: 纹理绑定冲突
**影响**: 纹理显示错误或崩溃
**缓解**: 
- 保存和恢复OpenGL状态
- 使用独立的纹理单元范围

## 参考资料

### 代码位置
- Shader生成: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/shader/`
- 节点系统: `src/main/java/cn/minerealms/assimploader/lookdev/nodeeditor/`
- Iris集成: `src/main/java/cn/minerealms/assimploader/lookdev/iris/`

### 外部文档
- GLSL-Transformer: https://github.com/IrisShaders/glsl-transformer
- Iris Shader API: https://github.com/IrisShaders/Iris
- Photon文档: (需要查找)

## 总结

LookDev Phase 10的核心是**连接节点图编辑器和shader渲染管线**。关键路径：

```
节点图编辑 → 触发更新 → 生成shader → 编译shader → 绑定纹理 → 应用到渲染
```

GLSL-Transformer已集成，主要工作是：
1. 添加触发器和回调
2. 完成shader注入调用
3. 实现纹理绑定
4. 添加预览功能

预计12-14小时可完成P0+P1功能，实现完整的节点到shader工作流。
