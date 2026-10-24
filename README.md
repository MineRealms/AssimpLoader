# AssimpLoader - Minecraft 高级 3D 模型加载器

<div align="center">

**为 Minecraft 带来专业级 3D 模型加载和实时材质编辑能力的综合系统**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.1.3-orange.svg)](https://files.minecraftforge.net/)
[![ModernUI](https://img.shields.io/badge/ModernUI-3.12.0-blue.svg)](https://github.com/BloCamLimb/ModernUI)

[功能特性](#功能特性) | [安装说明](#安装说明) | [使用文档](#使用文档) | [开发指南](#开发指南)

</div>

---

## 项目概述

**AssimpLoader** 是一个强大的 Minecraft 模组，为 Minecraft 带来专业级 3D 模型加载和实时材质编辑能力。基于 Assimp 库和 ModernUI 框架构建，提供从模型导入到着色器渲染的完整流程。

### 核心组件

🎨 **LookDev 系统** - 实时材质预览和编辑界面
- 交互式 3D 视口，支持相机控制
- PBR 材质编辑（基础色、金属度、粗糙度、法线、自发光）
- 实时着色器预览，集成 Photon
- 材质库管理系统

🔗 **节点编辑器** - ComfyUI 风格的可视化着色器图编辑器
- 23 种节点类型，覆盖所有着色器操作
- 实时节点输出预览
- 拖放式连接系统
- 材质到节点图的工作流
- 撤销/重做支持，100 步历史记录

📦 **模型加载器** - 通过 Assimp 支持多种 3D 格式
- 支持 40+ 种 3D 文件格式（FBX、OBJ、GLTF、COLLADA 等）
- 自动网格优化
- 纹理加载和管理
- 骨骼动画（GPU 蒙皮，见下文"骨骼动画"）

🎮 **Oculus 集成** - 高级着色器渲染
- PBR 材质系统，兼容 Oculus
- 自定义着色器注入，增强视觉效果
- Iris Shader 兼容层
- 性能优化的渲染管线

---

## 功能特性

### LookDev 系统

LookDev（Look Development）系统提供专业的材质编辑界面：

**材质编辑：**
- 实时 PBR 材质预览
- 支持所有标准 PBR 贴图（基础色、金属度、粗糙度、法线、自发光）
- 交互式 3D 视口，轨道相机
- 多种预览网格（球体、立方体、平面、圆柱体）
- 材质库，支持保存/加载

**UI 组件：**
- 三面板布局（模型列表 | 预览 | 属性）
- 可调整大小的面板，带拖动手柄
- 可折叠侧边栏
- 选项卡界面（预览 | 节点编辑器 | 设置）
- 常用操作的键盘快捷键

**Photon 集成：**
- 实时着色器编译
- 路径追踪的实时材质预览
- 自动着色器热重载
- Photon 不可用时回退到原版渲染

### 节点编辑器

完整的可视化着色器图编辑器，灵感来自 ComfyUI 和 Blender 的着色器编辑器：

**节点类型（共 23 种）：**
- **输入节点（4 种）**：颜色、数值、纹理采样、UV 坐标
- **数学节点（7 种）**：加、减、乘、除、插值、钳制、数学函数
- **向量节点（3 种）**：分离、组合、向量数学
- **纹理节点（2 种）**：纹理坐标、映射
- **实用节点（1 种）**：法线贴图
- **输出节点（1 种）**：材质输出

**功能特性：**
- 拖放式节点连接，带类型验证
- 实时节点输出预览（颜色、向量、数值）
- 节点搜索，支持模糊匹配
- 撤销/重做系统（100 步）
- 复制/粘贴节点
- 节点属性面板，支持实时编辑
- 视口剔除，提升性能
- 评估缓存，避免冗余计算

**工作流：**
- 点击模型列表中的材质 → 自动切换到节点编辑器
- 从材质生成默认节点图（纹理采样 → 输出）
- 可视化编辑节点和连接
- 实时预览更新
- 保存/加载节点图为 JSON

### 模型加载

**支持的格式：**
- FBX、OBJ、GLTF/GLB、COLLADA (DAE)
- 3DS、Blend、STL、PLY
- 通过 Assimp 支持 30+ 种其他格式

**功能特性：**
- 自动网格优化
- 纹理路径解析
- 材质提取
- 顶点颜色支持
- 每个模型支持多个网格

### 着色器系统

**GLSL 代码生成：**
- 节点图 → GLSL 着色器编译
- 基于模板的着色器生成
- Photon 着色器集成
- Oculus 自定义着色器注入

**渲染：**
- PBR 材质系统
- 法线贴图
- 自发光材质
- 纹理过滤和包裹模式
- Alpha 混合支持

---

## 安装说明

### 系统要求

- **Minecraft**: 1.20.1
- **Forge**: 47.1.3 或更高版本
- **ModernUI**: 3.12.0 或更高版本
- **Assimp**: 4.0+（已内置）

### 可选依赖

- **Photon**: 用于实时路径追踪预览
- **Oculus/Iris**: 用于着色器包集成

### 安装步骤

1. 从 [Releases](https://github.com/minerealms/assimploader/releases) 下载最新版本
2. 将 `assimploader-1.0.0-all.jar` 放入 `mods/` 文件夹
3. 如果尚未安装，请安装 ModernUI
4. 启动 Minecraft

### 首次启动

1. 按 `L` 键（默认快捷键）或输入 `/lookdev` 打开 LookDev 界面
2. 从文件浏览器加载 3D 模型
3. 在预览选项卡中编辑材质
4. 切换到节点编辑器选项卡进行高级着色器编辑

---

## 使用文档

### 用户指南

- [快速入门指南](QUICK_START.md) - 5 分钟快速上手
- [LookDev 指南](LOOKDEV_GUIDE.md) - 完整的材质编辑教程
- [节点编辑器指南](NODE_EDITOR_COMPLETE_SYSTEM.md) - 可视化着色器编辑
- [Photon 集成](PHOTON_USER_GUIDE.md) - 路径追踪设置

### 技术文档

- [节点编辑器技术概述](docs/NODE_EDITOR_TECHNICAL_OVERVIEW.md)
- [GLSL 转换器集成](docs/LOOKDEV_GLSL_TRANSFORMER_INTEGRATION.md)
- [Oculus 集成](OCULUS_PBR_INTEGRATION_GUIDE.md)
- [性能优化](docs/LookDev_Performance_Optimization.md)

### API 文档

参见 [开发者指南](DEVELOPER_GUIDE.md) 了解 API 使用和扩展开发。

---

## 开发指南

### 项目结构

```
src/main/java/cn/minerealms/assimploader/
├── lookdev/                    # LookDev 系统
│   ├── ui/                     # UI 组件
│   │   ├── LookDevScreen.java
│   │   ├── PreviewFragment.java
│   │   └── ModelTreeFragment.java
│   ├── nodeeditor/             # 节点编辑器
│   │   ├── Node.java
│   │   ├── NodeEditorCanvas.java
│   │   ├── NodeEditorFragment.java
│   │   ├── nodes/              # 节点实现
│   │   ├── ui/                 # 节点编辑器 UI
│   │   ├── shader/             # 着色器生成
│   │   ├── serialization/      # 保存/加载系统
│   │   └── undo/               # 撤销/重做命令
│   ├── material/               # 材质系统
│   └── PhotonIntegration.java  # Photon 桥接
├── model/                      # 模型加载
│   ├── AssimpModelLoader.java
│   └── ModelCache.java
└── render/                     # 渲染系统
    ├── PBRRenderer.java
    └── ShaderManager.java
```

### 从源码构建

```bash
# 克隆仓库
git clone https://github.com/minerealms/assimploader.git
cd assimploader

# 构建
./gradlew build

# 输出：build/libs/assimploader-1.0.0-all.jar
```

### 开发环境设置

开发环境需要 `mods_dev/` 目录，里面放（不随仓库分发、也不允许再分发的）本地依赖：

| 文件 | 用途 |
|------|------|
| `ModernUI-*.jar` / `ModernUI-Forge-*.jar` | LookDev UI（必需） |
| `photon-*.jar` / `ldlib-*.jar` | Photon 兼容层（可选） |

缺少这些 jar 时构建会给出明确警告并跳过对应的可选代码。

1. 将项目导入 IntelliJ IDEA
2. 运行 `./gradlew genIntellijRuns`
3. 使用 "runClient" 配置启动测试环境

代理等机器相关的 Gradle 配置请放在 `~/.gradle/gradle.properties`，
不要写进仓库（项目里的 `gradle.properties` 只保留与机器无关的选项）。

### 贡献指南

欢迎贡献！请：

1. Fork 仓库
2. 创建功能分支（`git checkout -b feature/amazing-feature`）
3. 提交更改（`git commit -m 'Add amazing feature'`）
4. 推送到分支（`git push origin feature/amazing-feature`）
5. 打开 Pull Request

---

## 架构设计

### 节点编辑器系统

节点编辑器基于模块化架构构建：

**核心组件：**
- `Node`: 所有节点类型的基类
- `NodeSocket`: 输入/输出连接点
- `NodeConnection`: 插槽之间的链接
- `NodeEditorCanvas`: 主渲染和交互表面
- `NodeGraphExecutor`: 评估节点图

**UI 组件：**
- `NodePalettePanel`: 节点选择侧边栏
- `NodePropertiesPanel`: 属性编辑 + 预览
- `NodePreviewPanel`: 实时输出可视化
- `NodeSearchDialog`: 模糊搜索界面

**系统：**
- `CommandHistory`: 撤销/重做管理
- `NodeGraphSerializer`: JSON 保存/加载
- `ShaderCodeGenerator`: GLSL 代码生成
- `NodeEvaluationCache`: 性能优化

### 材质系统

**PBRMaterial：**
- 基础色（RGB + Alpha）
- 金属度（0-1）
- 粗糙度（0-1）
- 法线贴图
- 自发光（RGB + 强度）

**工作流：**
- 加载模型 → 提取材质
- 在 LookDev 中编辑 → 实时预览
- 转换为节点图 → 高级编辑
- 生成 GLSL → 编译着色器
- 使用 Photon/原版渲染

---

## 性能优化

### 优化策略

- **视口剔除**：仅渲染可见节点（节省 60-80% 绘制调用）
- **评估缓存**：缓存节点输出（5 秒 TTL）
- **异步加载**：模型在后台线程加载
- **纹理流式传输**：渐进式纹理加载
- **着色器编译**：缓存并重用

### 性能说明

本项目没有附带可复现的基准测试。任何"XX ms"的数字都取决于模型、驱动和场景，
请不要把别处的数字当成本项目的承诺。已知的性能敏感点：

- 模型解码（Assimp）在首次加载时发生一次，之后走缓存；
- 静态网格走 Minecraft 的批次渲染管线，逐顶点无额外对象分配；
- 蒙皮网格使用独立 GL program 立即绘制，每个蒙皮网格会打断一次批次。

---

## 已知限制

### 文本渲染

ModernUI Canvas API 不支持在自定义视图中直接渲染文本。这影响：
- 节点标题和插槽标签（不可见）
- 预览面板文本叠加

**解决方案**：使用 TextView 叠加（计划在 ModernUI Phase 10 中实现）

**影响**：不影响功能，仅影响视觉效果

### Photon 集成

- 需要安装 Photon 模组
- 如果不可用，回退到原版渲染
- 某些高级功能需要 Photon 1.0+

---

## 致谢

### 使用的库

- **Assimp** - 3D 模型加载库
- **ModernUI** - Minecraft 现代 UI 框架
- **JOML** - Java OpenGL 数学库
- **Gson** - JSON 序列化

### 灵感来源

- **Blender** - 节点编辑器设计和工作流
- **ComfyUI** - 可视化节点图界面
- **Substance Designer** - 材质创作工作流

### 贡献者

- **CARIERX** - 主要开发者
- **Claude (Anthropic)** - AI 开发助手

---

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件。

---

## 支持

- **问题反馈**：[GitHub Issues](https://github.com/minerealms/assimploader/issues)
- **Discord**：[MineRealms Discord](https://discord.gg/minerealms)
- **文档**：[Wiki](https://wiki.minerealms.cn/assimploader)

---

## 开发路线图

### 版本 1.1（计划中）

- [ ] 动画支持
- [ ] 骨骼网格渲染
- [ ] 节点编辑器文本渲染（ModernUI Phase 10）
- [ ] 更多节点类型（噪声、渐变等）
- [ ] 节点组和宏

### 版本 1.2（未来）

- [ ] 多材质支持
- [ ] 纹理烘焙
- [ ] 导出到 Minecraft 资源包
- [ ] VR 支持

---

<div align="center">

**用 ❤️ 为 Minecraft 模组社区制作**

[⬆ 返回顶部](#assimploader---minecraft-高级-3d-模型加载器)

</div>
