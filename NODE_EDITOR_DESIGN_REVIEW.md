# Node Editor Design Review & Improvement Plan

**日期**: 2026-04-27  
**目的**: 全面审视节点编辑器的设计，确保功能完整、UI 合理、用户体验良好

---

## 一、当前系统架构分析

### 1.1 核心组件

| 组件 | 状态 | 功能 | 评价 |
|------|------|------|------|
| **NodeEditorFragment** | ✅ 完成 | 主界面容器 | 良好 |
| **NodeEditorCanvas** | ✅ 完成 | 节点画布、拖拽、连线 | 良好 |
| **NodePalettePanel** | ✅ 新增 | 节点选择面板 | 良好 |
| **NodeRegistry** | ✅ 完成 | 节点类型注册 | 良好 |
| **NodeFactory** | ✅ 完成 | 节点实例创建 | 良好 |
| **NodeGraphExecutor** | ✅ 完成 | 图执行、拓扑排序 | 良好 |
| **ShaderCodeGenerator** | ✅ 完成 | GLSL 代码生成 | 良好 |
| **MaterialPreviewRenderer** | ✅ 完成 | 3D 预览渲染 | 良好 |

### 1.2 现有节点类型

#### Input 节点（4种）
- ✅ **TextureSampleNode** - 纹理采样
- ✅ **ValueNode** - 浮点数值
- ✅ **ColorNode** - 颜色输入
- ✅ **UVCoordinateNode** - UV 坐标

#### Math 节点（3种）
- ✅ **MultiplyNode** - 乘法
- ✅ **AddNode** - 加法
- ✅ **LerpNode** - 线性插值

#### Utility 节点（1种）
- ✅ **NormalMapNode** - 法线贴图

#### Output 节点（1种）
- ✅ **MaterialOutputNode** - 材质输出

**总计**: 9 种节点类型

---

## 二、功能完整性评估

### 2.1 基础功能 ✅

| 功能 | 状态 | 说明 |
|------|------|------|
| 节点创建 | ✅ | 通过 NodePalettePanel 选择 |
| 节点拖拽 | ✅ | NodeEditorCanvas 支持 |
| 节点连接 | ✅ | 拖拽连线创建连接 |
| 节点删除 | ✅ | 选中后删除 |
| 画布平移 | ✅ | 鼠标拖拽 |
| 画布缩放 | ✅ | 鼠标滚轮 |
| 视图重置 | ✅ | Reset View 按钮 |

### 2.2 高级功能

| 功能 | 状态 | 优先级 | 说明 |
|------|------|--------|------|
| 节点参数编辑 | ⚠️ 部分 | 高 | 需要属性面板 |
| 材质生成 | ✅ | 高 | Generate Material 按钮 |
| 材质预览 | ✅ | 高 | MaterialPreviewRenderer |
| 保存/加载 | ✅ | 高 | MaterialSerializer |
| 撤销/重做 | ❌ | 中 | 需要实现 |
| 节点搜索 | ⚠️ 占位 | 中 | NodeSearchDialog 已创建但未集成 |
| 快捷键 | ⚠️ 占位 | 中 | KeyboardShortcutManager 已创建但未集成 |
| 错误提示 | ⚠️ 占位 | 中 | NotificationManager 已创建但未集成 |
| 节点分组 | ❌ | 低 | 未实现 |
| 节点注释 | ❌ | 低 | 未实现 |

---

## 三、UI/UX 设计评估

### 3.1 当前布局

```
┌─────────────────────────────────────────────────────────┐
│  [Node Editor]  [Hide Palette] [Add Node] [Generate] [Reset] │ ← Toolbar
├──────────┬──────────────────────────────────────────────┤
│          │                                              │
│  Node    │                                              │
│  Palette │          Canvas (节点画布)                    │
│          │                                              │
│  ▼ Input │                                              │
│  - Color │                                              │
│  - Value │                                              │
│  ...     │                                              │
│          │                                              │
│  ▼ Math  │                                              │
│  - Add   │                                              │
│  ...     │                                              │
├──────────┴──────────────────────────────────────────────┤
│  Status: Ready - Pan: Click+Drag | Zoom: Mouse Wheel    │ ← Status Bar
└─────────────────────────────────────────────────────────┘
```

**评价**: 
- ✅ 布局清晰，左侧面板 + 中央画布的经典设计
- ✅ 可折叠分类，节省空间
- ✅ 状态栏提供操作提示
- ⚠️ 缺少右侧属性面板（节点参数编辑）
- ⚠️ 缺少顶部标签页（切换不同视图）

### 3.2 缺失的 UI 组件

#### 高优先级
1. **属性面板（Properties Panel）**
   - 位置：右侧
   - 功能：编辑选中节点的参数
   - 示例：
     - ColorNode: 颜色选择器
     - ValueNode: 数值滑块
     - TextureSampleNode: 纹理选择器

2. **节点上下文菜单**
   - 触发：右键点击节点
   - 功能：删除、复制、重命名

3. **画布上下文菜单**
   - 触发：右键点击空白区域
   - 功能：添加节点（快速访问）

#### 中优先级
4. **标签页系统（Tab System）**
   - 位置：顶部或左侧
   - 功能：切换不同视图
   - 标签：
     - "Node Graph" - 节点编辑
     - "Preview" - 材质预览
     - "Code" - 生成的 GLSL 代码
     - "Settings" - 编辑器设置

5. **迷你地图（Minimap）**
   - 位置：右下角
   - 功能：显示整个节点图的缩略图，快速导航

---

## 四、节点类型扩展建议

### 4.1 当前节点类型的问题

**问题 1**: 节点类型太少（仅 9 种）
- 对比：Blender Shader Editor 有 100+ 节点
- 对比：Unreal Material Editor 有 200+ 节点

**问题 2**: 缺少常用节点
- 没有向量操作节点（Split/Combine）
- 没有数学函数节点（Sin/Cos/Pow）
- 没有纹理坐标变换节点（Scale/Rotate）
- 没有条件节点（If/Switch）

### 4.2 推荐新增节点（按优先级）

#### 高优先级（基础功能）

**Vector 节点**
- **SplitNode** - 分离向量 (Vec3 → Float, Float, Float)
- **CombineNode** - 合并向量 (Float, Float, Float → Vec3)
- **VectorMathNode** - 向量运算 (Dot, Cross, Normalize, Length)

**Math 节点扩展**
- **SubtractNode** - 减法
- **DivideNode** - 除法
- **PowerNode** - 幂运算
- **ClampNode** - 限制范围
- **MixNode** - 混合（支持多种模式）

**Texture 节点扩展**
- **TextureCoordinateNode** - 纹理坐标变换 (Scale, Rotate, Offset)
- **MappingNode** - UV 映射

**Converter 节点**
- **ColorRampNode** - 颜色渐变映射
- **MathFunctionNode** - 数学函数 (Sin, Cos, Tan, Sqrt, Abs)

#### 中优先级（高级功能）

**Shader 节点**
- **FresnelNode** - 菲涅尔效果
- **LayerWeightNode** - 层权重
- **AmbientOcclusionNode** - 环境光遮蔽

**Input 节点扩展**
- **TimeNode** - 时间输入（动画）
- **CameraDataNode** - 相机数据（视角方向）
- **ObjectInfoNode** - 对象信息（位置、旋转）

**Utility 节点扩展**
- **BumpNode** - 凹凸贴图
- **DisplacementNode** - 位移贴图
- **NoiseTextureNode** - 程序化噪声

#### 低优先级（特殊效果）

**Advanced 节点**
- **CustomCodeNode** - 自定义 GLSL 代码
- **SubgraphNode** - 子图（节点组）
- **ScriptNode** - 脚本节点

---

## 五、改进计划

### Phase A: UI 完善（1-2天）

**目标**: 完善基础 UI，提升用户体验

#### A.1 创建属性面板
```java
// NodePropertiesPanel.java
public class NodePropertiesPanel extends LinearLayout {
    private Node selectedNode;
    
    public void setSelectedNode(Node node) {
        // 根据节点类型显示不同的属性编辑器
        // - ColorNode: 颜色选择器
        // - ValueNode: 数值滑块
        // - TextureSampleNode: 纹理选择器
    }
}
```

#### A.2 集成已创建的 UI 组件
- 集成 NodeSearchDialog（Ctrl+A 快捷键）
- 集成 KeyboardShortcutManager
- 集成 NotificationManager

#### A.3 添加上下文菜单
- 节点右键菜单：删除、复制、重命名
- 画布右键菜单：添加节点

### Phase B: 节点扩展（2-3天）

**目标**: 增加常用节点类型

#### B.1 Vector 节点（3个）
- SplitNode
- CombineNode
- VectorMathNode

#### B.2 Math 节点扩展（4个）
- SubtractNode
- DivideNode
- ClampNode
- MathFunctionNode

#### B.3 Texture 节点扩展（2个）
- TextureCoordinateNode
- MappingNode

**预期结果**: 节点类型从 9 种增加到 18 种

### Phase C: 高级功能（2-3天）

**目标**: 实现高级编辑功能

#### C.1 撤销/重做系统
```java
// CommandHistory.java
public class CommandHistory {
    private Stack<Command> undoStack;
    private Stack<Command> redoStack;
    
    public void execute(Command cmd);
    public void undo();
    public void redo();
}
```

#### C.2 节点分组
- 选中多个节点
- 创建 Frame（边框）
- 添加注释文本

#### C.3 标签页系统
- Node Graph 标签
- Preview 标签（集成 MaterialPreviewRenderer）
- Code 标签（显示生成的 GLSL）

---

## 六、当前设计的优点

### 6.1 架构优点
- ✅ **模块化设计**: 各组件职责清晰
- ✅ **可扩展性**: 易于添加新节点类型
- ✅ **类型安全**: 使用 EnhancedSocketType 进行类型检查
- ✅ **调试支持**: 全面的 DebugManager 日志
- ✅ **状态保存**: 支持序列化和反序列化

### 6.2 功能优点
- ✅ **完整的工作流**: 从节点编辑到材质生成
- ✅ **实时预览**: MaterialPreviewRenderer 提供 3D 预览
- ✅ **Photon 集成**: 支持 Shader 编译和应用
- ✅ **错误处理**: 循环检测、类型验证

---

## 七、当前设计的不足

### 7.1 UI 不足
- ❌ **缺少属性面板**: 无法直观编辑节点参数
- ❌ **缺少上下文菜单**: 操作不够便捷
- ❌ **缺少标签页**: 无法快速切换视图
- ❌ **缺少迷你地图**: 大型节点图难以导航

### 7.2 功能不足
- ❌ **节点类型太少**: 仅 9 种，功能有限
- ❌ **缺少撤销/重做**: 编辑错误无法恢复
- ❌ **缺少节点搜索**: 虽然创建了 NodeSearchDialog 但未集成
- ❌ **缺少节点分组**: 复杂节点图难以组织

### 7.3 用户体验不足
- ❌ **学习曲线陡峭**: 缺少教程和示例
- ❌ **错误提示不足**: 虽然创建了 NotificationManager 但未集成
- ❌ **快捷键未启用**: KeyboardShortcutManager 未集成

---

## 八、推荐的实施顺序

### 立即实施（今天）
1. **创建属性面板** - 最重要的缺失功能
2. **集成 NotificationManager** - 改善用户反馈
3. **集成 KeyboardShortcutManager** - 提升操作效率

### 短期实施（本周）
4. **添加 Vector 节点** - 扩展基础功能
5. **添加 Math 节点扩展** - 增强计算能力
6. **添加上下文菜单** - 改善交互体验

### 中期实施（下周）
7. **实现撤销/重做** - 提升编辑体验
8. **添加标签页系统** - 改善界面组织
9. **集成 NodeSearchDialog** - 快速查找节点

### 长期实施（未来）
10. **节点分组和注释** - 组织复杂节点图
11. **迷你地图** - 改善导航
12. **高级节点类型** - Fresnel, AO, Noise 等

---

## 九、总结

### 当前状态评分

| 方面 | 评分 | 说明 |
|------|------|------|
| 核心架构 | 9/10 | 设计优秀，模块化清晰 |
| 基础功能 | 7/10 | 节点编辑基础功能完整 |
| 节点类型 | 4/10 | 仅 9 种，远远不够 |
| UI/UX | 5/10 | 缺少属性面板和上下文菜单 |
| 高级功能 | 3/10 | 缺少撤销/重做、搜索等 |
| **总体** | **6/10** | **可用但需要改进** |

### 关键建议

1. **优先实现属性面板** - 这是最重要的缺失功能
2. **扩展节点类型** - 至少增加到 20+ 种
3. **集成已创建的 UI 组件** - NotificationManager, KeyboardShortcutManager, NodeSearchDialog
4. **添加撤销/重做** - 提升编辑体验
5. **改善用户反馈** - 更好的错误提示和操作反馈

### 下一步行动

**立即开始**: 创建 NodePropertiesPanel，让用户能够编辑节点参数

---

*文档版本: 1.0*  
*作者: Claude Code*  
*日期: 2026-04-27*
