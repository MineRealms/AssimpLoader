# LookDev Viewer - 设计文档

## 项目概述

为 AssimpLoader 创建一个游戏内的 LookDev（Look Development）查看器，使用 ModernUI-MC 构建 GUI，用于实时预览和调试 FBX/3D 模型的材质和渲染效果。

### 核心价值

1. **真实环境预览**：在 Minecraft 真实光照、Shader 包、天气系统下查看模型
2. **实时参数调整**：无需重启游戏即可调整材质参数
3. **非破坏性编辑**：运行时覆盖，不修改原始 FBX 材质数据
4. **生产力工具**：比外部工具（Blender）更能反映游戏内实际效果

### 定位

**不是**：Maya/Blender 级别的材质编辑器  
**是**：Minecraft 版 Substance Previewer - 专注于预览和微调

---

## 技术栈评估

### 当前项目能力

#### AssimpLoader 系统（已完成）
- ✅ FBX/GLTF/OBJ 等 40+ 格式加载
- ✅ PBR 材质解析（`PBRMaterial`, `FBXMaterialParser`）
- ✅ 材质转换系统（`MaterialConverter`）
- ✅ 纹理通道支持：BaseColor, Normal, Metallic, Roughness, Emissive, AO, Opacity
- ✅ Oculus/Iris shader 集成（透明度支持）
- ✅ 模型缓存系统（`ModelCacheManager`）

#### ModernUI-MC 能力（v3.12.0）
- ✅ Fragment-based UI 架构
- ✅ 现代化 UI 组件（按钮、滑块、文本框）
- ✅ 自定义渲染支持
- ✅ 事件系统（点击、拖拽、滚动）
- ✅ 布局系统（LinearLayout, FrameLayout 等）
- ✅ 动画系统
- ✅ 文本渲染（TrueType 字体）

### 技术可行性

| 功能 | 可行性 | 实现方式 |
|------|--------|----------|
| 模型树视图 | ✅ 高 | ModernUI ListView + 递归节点 |
| 3D 实时预览 | ✅ 高 | 自定义 View + Minecraft RenderSystem |
| 参数滑块 | ✅ 高 | ModernUI Slider 组件 |
| 通道开关 | ✅ 高 | ModernUI CheckBox/Switch |
| 材质覆盖 | ✅ 高 | 运行时 PBRMaterial 副本 + 临时参数 |
| 节点流程图 | ⚠️ 中 | 自定义绘制（Phase 2+） |
| 预设保存 | ✅ 高 | JSON 序列化到 config/ |

---

## 系统架构

### 三层架构

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (ModernUI)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Model Tree  │  │  3D Preview  │  │  Parameters  │  │
│  │   Fragment   │  │   Fragment   │  │   Fragment   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│              Material Override System                    │
│  ┌──────────────────────────────────────────────────┐  │
│  │  MaterialOverrideManager                          │  │
│  │  - createOverride(PBRMaterial)                    │  │
│  │  - applyOverride(overrideParams)                  │  │
│  │  - resetOverride()                                │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────┐
│              AssimpLoader Core (已有)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ PBRMaterial  │  │ FBXParser    │  │ Renderer     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 核心类设计

#### 1. LookDevScreen (ModernUI Screen)
```java
public class LookDevScreen extends Fragment {
    private ModelTreeFragment modelTree;
    private PreviewFragment preview;
    private ParameterPanelFragment parameters;
    
    // 当前查看的模型
    private AssimpModel currentModel;
    
    // 材质覆盖管理器
    private MaterialOverrideManager overrideManager;
}
```

#### 2. MaterialOverrideManager (核心逻辑)
```java
public class MaterialOverrideManager {
    // 原始材质（只读）
    private final PBRMaterial originalMaterial;
    
    // 运行时覆盖参数
    private MaterialOverride currentOverride;
    
    // 创建覆盖副本
    public MaterialOverride createOverride(PBRMaterial original);
    
    // 应用参数修改
    public void applyOverride(MaterialOverride override);
    
    // 重置到原始状态
    public void resetOverride();
    
    // 获取当前有效材质（原始 + 覆盖）
    public PBRMaterial getEffectiveMaterial();
}
```

#### 3. MaterialOverride (参数容器)
```java
public class MaterialOverride {
    // 通道开关
    public boolean enableBaseColor = true;
    public boolean enableNormal = true;
    public boolean enableMetallic = true;
    public boolean enableRoughness = true;
    public boolean enableAO = true;
    public boolean enableEmissive = true;
    public boolean enableAlpha = true;
    
    // 参数覆盖
    public Float roughnessOverride = null;  // null = 使用原始值
    public Float metallicOverride = null;
    public Float emissionStrengthOverride = null;
    public Float alphaCutoffOverride = null;
    public Float normalStrengthOverride = null;
    
    // RenderType 覆盖
    public RenderTypeOverride renderTypeOverride = null;
    
    // 应用到材质
    public PBRMaterial applyTo(PBRMaterial original);
}
```

#### 4. PreviewRenderer (3D 渲染)
```java
public class PreviewRenderer {
    // 渲染模型到 ModernUI View
    public void renderModel(
        PoseStack poseStack,
        AssimpModel model,
        PBRMaterial effectiveMaterial,
        int width, int height
    );
    
    // 相机控制
    private float rotationX, rotationY;
    private float zoom;
    
    // 光照设置
    private LightingSetup lighting;
}
```

---

## Phase 开发计划

### Phase 1: 基础框架 + ModernUI 集成 ✅

**目标**：建立 ModernUI 依赖，创建基础 Screen 框架

**任务**：
1. ✅ 配置 Gradle 依赖（ModernUI-Core, ModernUI-Forge）
2. 创建 `LookDevScreen` 基础类
3. 创建三个空 Fragment（ModelTree, Preview, Parameters）
4. 实现 Screen 打开命令/按键绑定
5. 测试 ModernUI Screen 能否正常显示

**验收标准**：
- 游戏内按键可打开 LookDev Screen
- 显示三个空白区域（左中右布局）
- 无崩溃，无渲染错误

**预计时间**：2-3 小时

---

### Phase 2: 材质覆盖系统

**目标**：实现非破坏性材质参数覆盖

**任务**：
1. 创建 `MaterialOverride` 数据类
2. 创建 `MaterialOverrideManager` 管理器
3. 实现 `applyTo()` 方法（覆盖参数到 PBRMaterial 副本）
4. 实现通道开关逻辑（禁用纹理 = 使用白色/默认值）
5. 单元测试覆盖系统

**验收标准**：
- 可创建材质副本并修改参数
- 原始材质不受影响
- 通道开关正确工作
- 参数覆盖正确应用

**预计时间**：3-4 小时

---

### Phase 3: 参数面板 UI

**目标**：实现右侧参数控制面板

**任务**：
1. 创建 `ParameterPanelFragment`
2. 添加通道开关（7 个 CheckBox）
   - BaseColor, Normal, AO, Roughness, Metallic, Emission, Alpha
3. 添加参数滑块（5 个 Slider）
   - Roughness (0-1)
   - Metallic (0-1)
   - Emission Strength (0-10)
   - Alpha Cutoff (0-1)
   - Normal Strength (0-2)
4. 添加 RenderType 下拉菜单（Opaque/Cutout/Translucent）
5. 实现参数变化 → MaterialOverride 更新
6. 添加 "Reset" 按钮

**验收标准**：
- 所有控件正确显示
- 滑块拖动流畅
- 参数变化实时更新 MaterialOverride
- Reset 按钮恢复默认值

**预计时间**：4-5 小时

---

### Phase 4: 3D 预览渲染

**目标**：实现中间区域的 3D 模型实时预览

**任务**：
1. 创建 `PreviewFragment` 和 `PreviewRenderer`
2. 实现模型渲染到 ModernUI View
   - 使用 Minecraft RenderSystem
   - 应用 effectiveMaterial（原始 + 覆盖）
3. 实现相机控制
   - 鼠标拖拽旋转模型
   - 滚轮缩放
4. 实现光照设置
   - 使用 Minecraft 光照系统
   - 可选：自定义光照方向
5. 实时刷新（参数变化时重新渲染）

**验收标准**：
- 模型正确渲染到预览区域
- 鼠标交互流畅
- 参数变化实时反映到渲染
- 支持透明材质预览

**预计时间**：6-8 小时

---

### Phase 5: 模型树视图

**目标**：实现左侧模型/材质树状结构

**任务**：
1. 创建 `ModelTreeFragment`
2. 解析 AssimpModel 结构
   - Mesh 列表
   - Material 列表
   - 节点层级（可选）
3. 实现树状 ListView
   - 可展开/折叠
   - 点击选择材质
4. 材质选择 → 更新预览和参数面板
5. 显示材质基本信息（名称、纹理数量）

**验收标准**：
- 树状结构正确显示
- 点击材质切换预览
- 参数面板同步更新
- 支持多材质模型

**预计时间**：4-5 小时

---

### Phase 6: 预设保存/加载

**目标**：保存和加载材质参数预设

**任务**：
1. 创建 `MaterialPreset` 数据类
2. 实现 JSON 序列化/反序列化
3. 添加 "Save Preset" 按钮
4. 添加 "Load Preset" 下拉菜单
5. 预设文件保存到 `config/assimploader/presets/`
6. 预设命名和管理

**验收标准**：
- 可保存当前参数为预设
- 可加载已保存的预设
- 预设文件格式正确
- 支持多个预设

**预计时间**：3-4 小时

---

### Phase 7: 高级功能（可选）

**目标**：增强功能和用户体验

**任务**：
1. 节点流程图（只读）
   - 显示材质纹理流程
   - 不可编辑，仅展示
2. Before/After 对比
   - 截图保存
   - 左右对比视图
3. 快捷键支持
   - R: Reset
   - Space: 暂停旋转
   - 1-7: 快速切换通道
4. 性能优化
   - 渲染节流
   - 材质缓存

**验收标准**：
- 节点图正确显示材质流程
- 对比功能可用
- 快捷键响应正确
- 无性能问题

**预计时间**：6-8 小时

---

## 开发流程

### 每个 Phase 的循环

```
1. 开写 (Write)
   - 创建类文件
   - 实现核心逻辑
   - 添加必要注释

2. 编译 (Build)
   - ./gradlew build
   - 检查编译错误
   - 修复语法问题

3. 测试 (Test)
   - ./gradlew runClient
   - 游戏内测试功能
   - 记录 Bug

4. 修复 (Fix)
   - 修复发现的问题
   - 优化代码
   - 重新测试

5. 提交 (Commit)
   - git add
   - git commit -m "feat: Phase X - 功能描述"
   - 更新文档
```

---

## 技术细节

### ModernUI Fragment 生命周期

```java
public class LookDevFragment extends Fragment {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // 初始化数据
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 创建 UI 布局
        return inflater.inflate(R.layout.lookdev_screen, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // 绑定 UI 组件
        // 设置事件监听
    }
}
```

### 材质覆盖应用逻辑

```java
public PBRMaterial applyTo(PBRMaterial original) {
    PBRMaterial result = original.copy();
    
    // 应用通道开关
    if (!enableBaseColor) {
        result.setBaseColorTexture(null);
        result.setBaseColor(1, 1, 1, 1); // 白色
    }
    
    if (!enableNormal) {
        result.setNormalTexture(null);
    }
    
    // 应用参数覆盖
    if (roughnessOverride != null) {
        result.setRoughnessFactor(roughnessOverride);
    }
    
    if (metallicOverride != null) {
        result.setMetallicFactor(metallicOverride);
    }
    
    // ... 其他参数
    
    return result;
}
```

### 3D 渲染到 ModernUI View

```java
public class PreviewView extends View {
    private PreviewRenderer renderer;
    
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        // 获取 Minecraft RenderSystem
        PoseStack poseStack = new PoseStack();
        
        // 设置相机
        poseStack.translate(width / 2, height / 2, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(rotationX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));
        poseStack.scale(zoom, zoom, zoom);
        
        // 渲染模型
        renderer.renderModel(poseStack, model, effectiveMaterial);
    }
}
```

---

## 风险和挑战

### 技术风险

1. **ModernUI 学习曲线**
   - 缓解：参考 ModernUI-MC 示例代码
   - 从简单 Fragment 开始

2. **3D 渲染集成**
   - 缓解：复用 AssimpLoader 现有渲染代码
   - 使用 Minecraft 原生 RenderSystem

3. **性能问题**
   - 缓解：渲染节流（60fps → 30fps）
   - 材质缓存，避免重复计算

### 开发风险

1. **时间估算不准**
   - 缓解：Phase 拆分足够细
   - 每个 Phase 独立可测试

2. **功能蔓延**
   - 缓解：严格按 Phase 开发
   - Phase 7 之前不添加额外功能

---

## 成功标准

### MVP (Minimum Viable Product) - Phase 1-4

- ✅ 可打开 LookDev Screen
- ✅ 可选择模型和材质
- ✅ 可调整 Roughness/Metallic 参数
- ✅ 可开关纹理通道
- ✅ 3D 预览实时更新

### 完整版 - Phase 1-6

- ✅ MVP 所有功能
- ✅ 模型树视图
- ✅ 预设保存/加载
- ✅ 完整参数控制

### 增强版 - Phase 1-7

- ✅ 完整版所有功能
- ✅ 节点流程图
- ✅ Before/After 对比
- ✅ 快捷键支持

---

## 下一步行动

1. ✅ **完成 Phase 1 Gradle 配置**（已完成）
2. 创建 `lookdev/` 包结构
3. 实现 `LookDevScreen` 基础类
4. 测试 ModernUI Screen 显示
5. 进入 Phase 2 开发

---

## 参考资料

- [ModernUI-MC Wiki](https://github.com/BloCamLimb/ModernUI/wiki/Forge-1.18)
- [ModernUI Core Docs](https://github.com/BloCamLimb/ModernUI)
- [AssimpLoader 现有代码](src/main/java/cn/minerealms/assimploader/)
- [PBR 材质规范](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#materials)
