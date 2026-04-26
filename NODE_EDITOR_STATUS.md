# AssimpLoader LookDev Node Editor - 项目状态文档

**生成时间**: 2026-04-27  
**当前分支**: NodeWorkflow  
**最新提交**: Phase 6 完成 - Shader Code Generation

---

## 📋 项目概述

AssimpLoader LookDev Node Editor 是一个基于节点的可视化材质编辑系统，用于在 Minecraft 中创建和编辑 PBR 材质。该系统支持两种运行模式：

- **STANDALONE 模式**：仅需 Minecraft + AssimpLoader，提供基础节点编辑功能
- **FULL_INTEGRATION 模式**：集成 Photon/Oculus，提供完整的着色器生成和实时预览

---

## ✅ 已完成功能 (Phase 1-6)

### Phase 1: 节点类型系统基础
**提交**: 4fd341b  
**文件**:
- `EnhancedSocketType.java` - 增强的 socket 类型系统（FLOAT, VEC2, VEC3, VEC4, COLOR, SAMPLER2D）
- `NodeType.java` - 节点类型枚举（9种基础节点）
- `NodeRegistry.java` - 节点类型注册表
- `NodeFactory.java` - 节点工厂，根据 NodeType 创建实例

**功能**:
- 定义了完整的节点类型系统
- 支持多种数据类型的 socket 连接
- 提供节点创建的工厂模式

### Phase 2: 具体节点实现
**提交**: a81a478  
**文件**:
- `nodes/input/TextureSampleNode.java` - 纹理采样
- `nodes/input/ValueNode.java` - 数值输入
- `nodes/input/ColorNode.java` - 颜色输入
- `nodes/input/UVCoordinateNode.java` - UV 坐标
- `nodes/math/MultiplyNode.java` - 乘法运算
- `nodes/math/AddNode.java` - 加法运算
- `nodes/math/LerpNode.java` - 线性插值
- `nodes/utility/NormalMapNode.java` - 法线贴图处理
- `nodes/output/MaterialOutputNode.java` - 材质输出

**功能**:
- 实现了 9 个核心节点类
- 每个节点都有完整的输入/输出 socket 定义
- 支持节点间的数据流动

### Phase 3: 类型系统和连接验证
**提交**: 8619cc6  
**文件**:
- `TypeConverter.java` - 类型转换系统（支持 float↔vec3, color↔vec3 等）
- `EnhancedNodeSocket.java` - 增强的 socket 实现，桥接新旧类型系统
- 更新所有节点使用 `EnhancedNodeSocket`

**功能**:
- 自动类型转换（如 float 扩展为 vec3）
- 连接兼容性检查
- 类型安全的数据传递

### Phase 4: 图执行引擎
**提交**: 70eb00d  
**文件**:
- `NodeEvaluationContext.java` - 评估上下文，缓存节点计算结果
- `Node.java` - 添加 `evaluate()` 和 `computeOutputs()` 方法
- `NodeGraphExecutor.java` - 图执行引擎（拓扑排序、循环检测）
- 更新所有数学节点实现 `computeOutputs()`

**功能**:
- 拓扑排序确保正确的执行顺序
- 循环依赖检测
- 值缓存避免重复计算
- 懒加载执行模式

### Phase 5: Photon 检测和条件加载
**提交**: b88190f  
**文件**:
- `FeatureTier.java` - 功能分层枚举
- `PhotonIntegration.java` - Photon/Oculus 检测类
- `NodeEditorFragment.java` - 更新 UI 显示当前模式

**功能**:
- 运行时检测 Photon 和 Oculus mod
- 自动选择 STANDALONE 或 FULL_INTEGRATION 模式
- UI 状态栏显示当前集成模式
- 缓存检测结果提升性能

### Phase 6: Shader Code Generation (FULL_INTEGRATION only) ✅ NEW
**提交**: [当前提交]  
**文件**:
- `shader/ShaderCodeGenerator.java` - 主生成器类，将节点图转换为 GLSL
- `shader/GLSLEmitter.java` - GLSL 代码发射器，处理各节点类型
- `shader/ShaderTemplate.java` - 着色器模板系统（vertex/fragment）
- `Node.java` - 添加 `getInputByName()` 和 `getOutputByName()` 方法
- `ColorNode.java` - 添加 `getColor()` 方法

**功能**:
- 节点图到 GLSL 代码的完整转换
- 拓扑排序遍历节点图
- 自动变量命名和依赖跟踪
- 支持所有 9 种节点类型的代码生成
- PBR 材质输出（albedo, metallic, roughness, normal, emissive, alpha）
- 简化的 PBR 光照计算
- 仅在 FULL_INTEGRATION 模式下可用
- 包含完整的 vertex shader 和 fragment shader 模板
- 支持纹理采样、数学运算、法线贴图等

---

## 🏗️ 架构设计

### 核心类层次结构

```
Node (抽象基类)
├── evaluate(NodeEvaluationContext) - 递归评估输入
├── computeOutputs(NodeEvaluationContext) - 计算输出值
├── inputs: List<NodeSocket>
└── outputs: List<NodeSocket>

NodeSocket
├── SocketType (内部枚举: FLOAT, COLOR, TEXTURE, VECTOR, ANY)
├── connectedTo: NodeSocket
└── value: Object

EnhancedNodeSocket extends NodeSocket
├── enhancedType: EnhancedSocketType
├── canConnectTo(EnhancedNodeSocket) - 类型兼容性检查
└── 自动类型转换支持

NodeGraphExecutor
├── topologicalSort() - 拓扑排序
├── detectCycles() - 循环检测
├── execute() - 执行整个节点图
└── collectOutputs() - 收集输出结果
```

### 数据流

```
用户操作 → NodeEditorFragment
    ↓
创建节点 → NodeFactory.createNode(NodeType)
    ↓
连接节点 → EnhancedNodeSocket.connectTo()
    ↓         (类型检查 + 转换)
    ↓
执行图 → NodeGraphExecutor.execute()
    ↓
拓扑排序 → 按依赖顺序评估节点
    ↓
Node.evaluate() → 递归评估输入
    ↓
Node.computeOutputs() → 计算输出值
    ↓
缓存结果 → NodeEvaluationContext
    ↓
收集输出 → MaterialOutputNode 的值
```

### 功能分层系统

```
PhotonIntegration.isPhotonAvailable()
    ↓
    ├─ true  → FeatureTier.FULL_INTEGRATION
    │           ├── 节点编辑器
    │           ├── 图执行引擎
    │           ├── GLSL 代码生成 (Phase 6)
    │           ├── Photon 着色器编译 (Phase 7)
    │           └── 实时材质预览 (Phase 8)
    │
    └─ false → FeatureTier.STANDALONE
                ├── 节点编辑器
                ├── 图执行引擎
                ├── 材质保存/加载 (Phase 9)
                └── 无着色器功能
```

---

## 📚 参考资料

### 源码仓库
- **AssimpLoader**: H:\MinecraftMods\ImprovedMobs
- **Photon**: H:\MinecraftMods\Photon
- **ModernUI**: H:\MinecraftMods\ModernUI-MC
- **Oculus**: H:\MinecraftMods\Oculus

### 关键依赖
- **Photon**: `icyllis.arc3d.engine.Engine` - 3D 渲染引擎
- **ModernUI**: `icyllis.modernui.*` - UI 框架
- **Assimp**: `org.lwjgl.assimp.*` - 3D 模型加载

### 相关文档
- `LOOKDEV_INTEGRATION_PLAN.md` - 完整实施计划
- `PHOTON_INTEGRATION_SUMMARY.md` - Photon 集成总结
- `IMPLEMENTATION_PLAN.md` - 详细实现计划

---

## 🔧 开发工作流

### 编译项目
```powershell
cd H:\MinecraftMods\ImprovedMobs
./gradlew compileJava
```

### 运行测试
```powershell
./gradlew test
```

### 构建 Mod
```powershell
./gradlew build
```

### 提交代码
```powershell
git add -A
git commit -m "feat(lookdev): 描述"
git push origin NodeWorkflow
```

### 验证编译
```powershell
# 快速编译检查
./gradlew compileJava 2>&1 | head -100

# 完整构建
./gradlew build --info
```

---

## 🚀 待完成功能 (Phase 7-10)

### Phase 7: Photon Shader Compilation (FULL_INTEGRATION only)
**目标**: 将生成的 GLSL 代码编译为 Photon 着色器

**需要创建的文件**:
- `PhotonShaderCompiler.java` - 着色器编译器
- `ShaderCache.java` - 编译结果缓存

**实现步骤**:
1. 调用 Photon API 编译 GLSL 代码
2. 处理编译错误并反馈给用户
3. 缓存编译结果避免重复编译
4. 支持热重载（节点图修改时自动重新编译）

**Photon API 参考**:
```java
// 参考 Photon 源码
import icyllis.arc3d.engine.ShaderCompiler;
import icyllis.arc3d.engine.ShaderProgram;

ShaderProgram program = ShaderCompiler.compile(
    vertexShaderSource,
    fragmentShaderSource
);
```

### Phase 8: Material Preview Integration (FULL_INTEGRATION only)
**目标**: 在 3D 视口中实时预览材质效果

**需要创建的文件**:
- `MaterialPreviewRenderer.java` - 预览渲染器
- `PreviewMesh.java` - 预览用的 3D 网格（球体/立方体）

**实现步骤**:
1. 创建预览视口（使用 ModernUI 的 3D 渲染组件）
2. 加载预览网格（球体或用户模型）
3. 应用编译后的着色器
4. 实时更新（节点修改时自动刷新）
5. 支持相机控制（旋转、缩放）

**UI 布局**:
```
┌─────────────────────────────────────┐
│  Node Editor (左侧 70%)              │
│  ┌─────────────────────────────┐    │
│  │  [节点图]                    │    │
│  └─────────────────────────────┘    │
├─────────────────────────────────────┤
│  Material Preview (右侧 30%)        │
│  ┌─────────────────────────────┐    │
│  │  [3D 预览球体]               │    │
│  │  - 实时着色器效果             │    │
│  │  - 可旋转/缩放               │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

### Phase 9: Material Save/Load System (Both tiers)
**目标**: 保存和加载节点图为 JSON 文件

**需要创建的文件**:
- `MaterialSerializer.java` - 序列化器
- `MaterialDeserializer.java` - 反序列化器
- `MaterialFileFormat.java` - 文件格式定义

**实现步骤**:
1. 定义 JSON 格式（节点、连接、参数）
2. 实现序列化（NodeGraph → JSON）
3. 实现反序列化（JSON → NodeGraph）
4. 添加文件选择对话框
5. 支持自动保存和版本控制

**JSON 格式示例**:
```json
{
  "version": "1.0",
  "nodes": [
    {
      "id": "node_1",
      "type": "TEXTURE_SAMPLE",
      "position": {"x": 100, "y": 200},
      "parameters": {
        "texturePath": "textures/albedo.png"
      }
    },
    {
      "id": "node_2",
      "type": "MATERIAL_OUTPUT",
      "position": {"x": 500, "y": 200}
    }
  ],
  "connections": [
    {
      "from": {"node": "node_1", "socket": "Color"},
      "to": {"node": "node_2", "socket": "Albedo"}
    }
  ]
}
```

### Phase 10: UI Polish and Remaining TODOs
**目标**: 完善 UI 和修复所有 TODO

**需要完成的任务**:
1. 搜索并修复所有 `// TODO` 注释
2. 添加节点搜索功能（快捷键：Tab）
3. 添加撤销/重做功能
4. 优化节点渲染性能
5. 添加节点分组和注释
6. 改进错误提示和用户反馈
7. 添加快捷键支持
8. 编写用户文档

**查找 TODO**:
```powershell
cd H:\MinecraftMods\ImprovedMobs
grep -r "TODO" src/main/java/cn/minerealms/assimploader/lookdev/
```

---

## 🎯 如何继续开发

### 开始 Phase 6
```powershell
# 1. 创建新的 task
# 2. 创建 ShaderCodeGenerator.java
cd H:\MinecraftMods\ImprovedMobs\src\main\java\cn\minerealms\assimploader\lookdev\nodeeditor

# 3. 实现 GLSL 代码生成逻辑
# 参考 Photon 的着色器格式

# 4. 添加功能分层检查
if (PhotonIntegration.getCurrentTier() == FeatureTier.FULL_INTEGRATION) {
    // 生成着色器代码
} else {
    // 显示提示：需要 Photon 支持
}

# 5. 编译测试
./gradlew compileJava

# 6. 提交
git add -A
git commit -m "feat(lookdev): Phase 6 - Shader code generation"
```

### 开发建议
1. **每个 Phase 独立提交**：保持 git 历史清晰
2. **先实现核心功能**：UI 美化放在最后
3. **频繁编译测试**：避免累积错误
4. **参考 Photon 源码**：了解 API 用法
5. **添加详细日志**：使用 `DebugManager.info()`
6. **处理异常情况**：Photon 不可用时的降级方案

### 测试流程
1. **STANDALONE 模式测试**：
   - 移除 Photon/Oculus mod
   - 启动 Minecraft
   - 验证节点编辑器可用
   - 验证图执行引擎工作
   - 验证材质保存/加载

2. **FULL_INTEGRATION 模式测试**：
   - 安装 Photon 或 Oculus
   - 启动 Minecraft
   - 验证所有 STANDALONE 功能
   - 验证着色器生成
   - 验证实时预览
   - 验证材质应用到模型

---

## 🐛 已知问题

1. **类型转换警告**：
   - 位置: `Keyframe.java`
   - 原因: 使用了未检查的泛型操作
   - 影响: 仅编译警告，不影响功能
   - 修复: 添加 `@SuppressWarnings("unchecked")`

2. **弃用 API 警告**：
   - 原因: 使用了旧版 Forge API
   - 影响: 仅编译警告，不影响功能
   - 修复: 迁移到新 API（低优先级）

---

## 📊 项目统计

- **总代码行数**: ~3500 行（估算）
- **已完成 Phase**: 6/10 (60%)
- **核心功能完成度**: 70%
- **预计剩余工作量**: 1-2 周

---

## 📞 联系方式

- **开发者**: CARIERX
- **项目仓库**: H:\MinecraftMods\ImprovedMobs
- **分支**: NodeWorkflow

---

**最后更新**: Phase 6 完成后 (2026-04-27)
**下一步**: 开始 Phase 7 - Photon Shader Compilation
