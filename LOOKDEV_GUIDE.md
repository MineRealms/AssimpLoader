# LookDev 快速上手指南

## 概述

LookDev 是一个实时 3D 模型材质预览工具，用于在游戏内调整和预览 PBR 材质参数。

**核心功能：**
- 实时加载和预览 3D 模型（支持 FBX、OBJ 等格式）
- 调整 PBR 材质参数（Base Color、Metallic、Roughness、Emissive 等）
- 实时预览材质变化效果
- 支持多材质模型

## 架构概览

### 核心组件

```
LookDevScreen (父 Fragment)
├── ModelTreeFragment (左侧 20%)    - 模型列表和材质选择
├── PreviewFragment (中间 60%)      - 3D 预览窗口
└── ParameterPanelFragment (右侧 20%) - 材质参数调整面板
```

### 关键类

1. **MaterialOverrideManager** - 材质覆盖管理器
   - 管理材质参数的临时覆盖值
   - 不修改原始材质，只在预览时应用覆盖
   - 位置：`lookdev/material/MaterialOverrideManager.java`

2. **OffscreenRenderer** - 离屏渲染器
   - 使用 FBO (Framebuffer Object) 在后台渲染 3D 模型
   - 渲染结果通过纹理传递给 UI 线程显示
   - 位置：`lookdev/render/OffscreenRenderer.java`

3. **PreviewView** - 预览视图
   - 显示 OffscreenRenderer 渲染的纹理
   - 处理鼠标交互（旋转、缩放）
   - 位置：`lookdev/ui/PreviewView.java`

4. **OrbitCamera** - 轨道相机
   - 管理相机位置和视角
   - 支持鼠标拖动旋转和滚轮缩放
   - 位置：`lookdev/camera/OrbitCamera.java`

## 数据流详解

### 1. 模型加载流程

```
用户点击模型 
  → ModelTreeFragment.onModelSelected()
  → LookDevScreen.onModelSelected()
  → PreviewFragment.setModel()
  → OffscreenRenderer.setModel()
  → 触发渲染
```

### 2. 材质参数调整流程（关键！）

```
用户拖动滑块
  → SeekBar.onProgressChanged(fromUser=true)
  → ColorSliderCallback.onValueChanged()
  → MaterialOverrideManager.setBaseColorOverride()  // 更新覆盖值
  → ParameterPanelFragment.notifyMaterialChanged()
  → LookDevScreen.onMaterialParametersChanged()
  → PreviewFragment.refresh()
  → PreviewFragment.updateOffscreenRenderer()
  → OffscreenRenderer.setMaterial(PBRMaterial)      // 传递包含覆盖值的材质
  → OffscreenRenderer.requestRender()
  → [Render Thread] OffscreenRenderer.renderModel()
  → renderMesh() 使用 currentMaterial.getBaseColor() // 关键：使用 PBRMaterial 而非 AssimpMaterial
  → 渲染完成，更新纹理
  → [UI Thread] PreviewView.invalidate()
  → 显示更新后的预览
```

**关键点：**
- `fromUser=true` 才会触发回调，程序设置初始值时 `fromUser=false`
- 必须使用 `currentMaterial`（PBRMaterial）而不是 `material`（AssimpMaterial）
- PBRMaterial 包含 MaterialOverrideManager 的覆盖值
- AssimpMaterial 只包含模型文件中的原始值

### 3. Fragment 间通信

```
ParameterPanelFragment (子)
  → getParentFragment() 获取 LookDevScreen
  → 调用 LookDevScreen.onMaterialParametersChanged()
  → LookDevScreen 转发给 PreviewFragment
```

**注意：** 不要使用 EventBus 或其他复杂机制，直接通过父 Fragment 协调即可。

## 常见问题和解决方案

### 问题 1：调整滑块没有反应

**症状：** 拖动 Base Color 或 Emissive Color 滑块，预览不更新

**原因：** OffscreenRenderer.renderMesh() 使用了错误的材质数据源

**错误代码：**
```java
// ❌ 错误：使用 AssimpMaterial 的原始值
float[] diffuseColor = material.getDiffuseColor();
Vector4f color = new Vector4f(diffuseColor[0], diffuseColor[1], diffuseColor[2], diffuseColor[3]);
```

**正确代码：**
```java
// ✅ 正确：使用 PBRMaterial 的覆盖值
Vector4f baseColor;
if (currentMaterial != null) {
    baseColor = currentMaterial.getBaseColor();  // 包含 MaterialOverrideManager 的覆盖
} else {
    float[] diffuseColor = material != null ? material.getDiffuseColor() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    baseColor = new Vector4f(diffuseColor[0], diffuseColor[1], diffuseColor[2], diffuseColor[3]);
}
```

**调试方法：**
1. 检查日志中是否有 `onProgressChanged: progress=X, fromUser=true`
2. 检查是否有 `Base Color X changed to: Y`
3. 检查是否有 `notifyMaterialChanged called`
4. 检查是否有 `LookDevScreen.onMaterialParametersChanged called`
5. 检查是否有 `PreviewFragment.refresh() called`
6. 检查是否有 `OffscreenRenderer.requestRender called`
7. 检查渲染后的 `first pixel` 值是否变化

### 问题 2：UI 控件不显示

**症状：** ParameterPanel 或 ModelTree 的内容不可见

**原因：** ScrollView 或 contentLayout 缺少 LayoutParams

**解决方案：**
```java
scrollView = new ScrollView(context);
scrollView.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.MATCH_PARENT
));

contentLayout = new LinearLayout(context);
contentLayout.setLayoutParams(new ViewGroup.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
));
```

### 问题 3：显存溢出（GL_OUT_OF_MEMORY）

**症状：** 游戏启动后不久崩溃，日志显示 `GL_OUT_OF_MEMORY`

**原因：** 
- FBO 和纹理没有正确释放
- 多次创建 OffscreenRenderer 导致资源泄漏
- 游戏整体显存占用过高

**解决方案：**
1. 确保 OffscreenRenderer 在 Fragment.onDestroyView() 中释放资源
2. 使用单例模式或在 LookDevScreen 级别管理 OffscreenRenderer
3. 降低预览分辨率（默认 512x512）
4. 检查是否有纹理缓存未清理

**资源释放代码：**
```java
@Override
public void onDestroyView() {
    super.onDestroyView();
    if (offscreenRenderer != null) {
        RenderSystem.recordRenderCall(() -> {
            offscreenRenderer.cleanup();  // 释放 FBO、纹理、Renderbuffer
        });
    }
}
```

### 问题 4：Fragment 生命周期混乱

**症状：** UI 控件为 null，或者回调没有触发

**原因：** Fragment 生命周期理解错误

**正确的生命周期顺序：**
```
1. onCreateView()        - 创建 View 树，初始化 UI 控件
2. onViewCreated()       - View 已创建，可以设置监听器和初始化数据
3. [用户交互]
4. onDestroyView()       - 清理资源，释放引用
```

**注意事项：**
- 在 `onCreateView()` 中创建所有 UI 控件
- 在 `onViewCreated()` 中设置监听器和加载数据
- 不要在 `onCreateView()` 中访问父 Fragment（可能还未关联）
- 在 `onDestroyView()` 中清理所有资源引用

### 问题 5：日志不输出

**症状：** 添加了 `DebugManager.debug()` 但日志不显示

**原因：** debug 模式默认禁用

**解决方案：**
```java
// 在需要详细日志的地方启用 debug 模式
DebugManager.setDebugEnabled(true);

// 或者使用 info 级别（总是输出）
DebugManager.info(DebugCategory.LOOKDEV, "Message");
```

**日志级别：**
- `debug()` - 详细调试信息，默认禁用
- `info()` - 一般信息，总是输出
- `warn()` - 警告信息
- `error()` - 错误信息

## 调试技巧

### 1. 启用详细日志

在关键路径上添加日志：

```java
// 滑块事件
DebugManager.info(DebugCategory.LOOKDEV, "onProgressChanged: progress={}, fromUser={}", progress, fromUser);

// 材质更新
DebugManager.info(DebugCategory.LOOKDEV, "Base Color R changed to: {}", value);

// Fragment 通信
DebugManager.info(DebugCategory.LOOKDEV, "notifyMaterialChanged called");
DebugManager.info(DebugCategory.LOOKDEV, "LookDevScreen.onMaterialParametersChanged called");

// 渲染
DebugManager.info(DebugCategory.LOOKDEV, "[PreviewFragment] refresh() called");
DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] requestRender called");
DebugManager.info(DebugCategory.LOOKDEV, "[OffscreenRenderer] Using PBRMaterial baseColor: ({}, {}, {}, {})", 
    baseColor.x, baseColor.y, baseColor.z, baseColor.w);
```

### 2. 检查构建和部署

**常见错误：** 修改了代码但游戏运行的是旧版本

**检查步骤：**
1. 确认 JAR 文件时间戳是最新的
2. 确认部署路径正确（`versions\Create New Horizon\mods\` 而不是 `.minecraft\mods\`）
3. 确认游戏重启后加载了新的 JAR
4. 检查日志时间戳是否在 JAR 构建之后

**自动化脚本：**
```powershell
# build-and-run.ps1 会自动：
# 1. 构建 mod
# 2. 部署到正确的 mods 目录
# 3. 启动游戏（如果 testgame.ps1 存在）
.\build-and-run.ps1
```

### 3. 使用 Git 追踪问题

如果功能突然失效：

```bash
# 查看最近的提交
git log --oneline -10

# 对比工作版本和当前版本
git diff <working-commit> HEAD -- src/main/java/cn/minerealms/assimploader/lookdev/

# 回滚到工作版本测试
git checkout <working-commit>
```

## 开发工作流

### 1. 添加新的材质参数

**步骤：**

1. 在 `PBRMaterial` 中添加字段和 getter/setter
2. 在 `MaterialOverrideManager` 中添加覆盖方法
3. 在 `ParameterPanelFragment` 中添加 UI 控件和回调
4. 在 `OffscreenRenderer.renderMesh()` 中使用新参数
5. 测试并提交

**示例：添加 Roughness 参数**

```java
// 1. PBRMaterial.java
private float roughness = 0.5f;

public float getRoughness() {
    return roughness;
}

public void setRoughness(float roughness) {
    this.roughness = roughness;
}

// 2. MaterialOverrideManager.java
public void setRoughnessOverride(float roughness) {
    if (overrideMaterial != null) {
        overrideMaterial.setRoughness(roughness);
    }
}

// 3. ParameterPanelFragment.java
private SeekBar roughnessSlider;
private TextView roughnessValue;

// 在 onCreateView() 中创建控件
roughnessSlider = new SeekBar(context);
roughnessSlider.setMax(100);
roughnessValue = new TextView(context);

// 在 setupListeners() 中设置回调
setupFloatSlider(roughnessSlider, roughnessValue, (value) -> {
    if (overrideManager != null) {
        overrideManager.setRoughnessOverride(value / 100f);
        notifyMaterialChanged();
    }
});

// 4. OffscreenRenderer.java
// 在 renderMesh() 或 shader 中使用 currentMaterial.getRoughness()
```

### 2. 修改渲染逻辑

**注意事项：**
- 所有 OpenGL 调用必须在 Render Thread 执行
- 使用 `RenderSystem.recordRenderCall()` 从 UI Thread 调度渲染任务
- 不要在 Render Thread 中修改 UI 控件

**示例：**
```java
// ❌ 错误：在 UI Thread 直接调用 OpenGL
GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

// ✅ 正确：通过 RenderSystem 调度
RenderSystem.recordRenderCall(() -> {
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
});
```

### 3. 测试流程

1. 修改代码
2. 运行 `.\build-and-run.ps1`（自动构建、部署、启动游戏）
3. 在游戏中打开 LookDev 界面
4. 测试功能
5. 检查日志：`G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log`
6. 如果有问题，添加更多日志并重复步骤 1-5

## 性能优化建议

### 1. 减少不必要的渲染

```java
// 使用 needsRender 标志避免重复渲染
private boolean needsRender = true;

public void requestRender() {
    if (!needsRender) {
        return;  // 已经有渲染请求在队列中
    }
    needsRender = true;
    RenderSystem.recordRenderCall(this::render);
}

private void render() {
    if (!needsRender) {
        return;
    }
    needsRender = false;
    // 执行渲染...
}
```

### 2. 纹理缓存

```java
// 缓存嵌入纹理，避免重复加载
private final Map<String, Map<Integer, ResourceLocation>> textureCache = new HashMap<>();

// 使用前检查缓存
if (!textureCache.containsKey(modelKey)) {
    loadEmbeddedTextures(model, modelKey);
}
```

### 3. 降低预览分辨率

```java
// 默认 512x512，可以根据需要调整
private static final int PREVIEW_WIDTH = 512;
private static final int PREVIEW_HEIGHT = 512;

// 或者提供用户可调节的分辨率选项
```

## 关键代码位置速查

| 功能 | 文件路径 |
|------|---------|
| 主界面 | `lookdev/ui/LookDevScreen.java` |
| 模型列表 | `lookdev/ui/ModelTreeFragment.java` |
| 预览窗口 | `lookdev/ui/PreviewFragment.java` |
| 参数面板 | `lookdev/ui/ParameterPanelFragment.java` |
| 预览视图 | `lookdev/ui/PreviewView.java` |
| 离屏渲染 | `lookdev/render/OffscreenRenderer.java` |
| 材质覆盖 | `lookdev/material/MaterialOverrideManager.java` |
| 轨道相机 | `lookdev/camera/OrbitCamera.java` |
| PBR 材质 | `material/PBRMaterial.java` |
| 调试管理 | `debug/DebugManager.java` |

## 重要提醒

1. **永远不要直接修改 AssimpMaterial**
   - AssimpMaterial 是从模型文件加载的原始数据
   - 使用 MaterialOverrideManager 管理临时覆盖
   - 渲染时使用 PBRMaterial（包含覆盖值）

2. **注意线程安全**
   - UI 操作在 UI Thread
   - OpenGL 调用在 Render Thread
   - 使用 RenderSystem.recordRenderCall() 跨线程调度

3. **资源管理**
   - Fragment 销毁时释放 OpenGL 资源
   - 使用完纹理后调用 glDeleteTextures
   - FBO 和 Renderbuffer 也要释放

4. **调试优先**
   - 遇到问题先添加日志
   - 确认数据流的每个环节
   - 不要猜测，用日志验证

5. **构建验证**
   - 修改代码后必须重新构建
   - 确认 JAR 文件时间戳
   - 确认游戏加载了新版本

## 参考资料

- ModernUI 文档：https://github.com/BloCamLimb/ModernUI
- Assimp 文档：https://assimp.sourceforge.net/
- OpenGL FBO 教程：https://learnopengl.com/Advanced-OpenGL/Framebuffers
- Minecraft Forge 文档：https://docs.minecraftforge.net/

## 更新日志

### 2024-XX-XX - 修复材质参数调整不生效
- 修复 OffscreenRenderer 使用错误的材质数据源
- 添加完整的调试日志链
- 修复 ScrollView 缺少 LayoutParams
- 修复构建脚本部署路径

---

**如果遇到本文档未涵盖的问题，请：**
1. 检查日志中的错误信息
2. 搜索代码中的相关日志输出
3. 对比最近的 Git 提交
4. 添加更多日志来定位问题
