# AssimpLoader 与 Oculus 渲染管线兼容性分析

## 1. 核心问题

**AssimpLoader 负责**：把 FBX/GLTF 等格式 → 转换成 Minecraft 的 BakedModel/BakedQuad

**Oculus 负责**：增强 Minecraft 原生渲染管线，提供 PBR、延迟渲染、阴影

**关键问题**：AssimpLoader 输出的模型，能否被 Oculus 的渲染增强捕获并处理？

**答案**：✅ 可以，只需让模型走 Minecraft 标准渲染流程，Oculus 的 Mixin 会自动拦截。

---

## 2. 技术原理

### 2.1 Oculus 注入点

Oculus 通过 Mixin 注入到 Minecraft 渲染调度的关键位置：

```
Minecraft 渲染调用链
     ↓
BlockRenderDispatcher.render() / EntityRenderDispatcher.render()
     ↓
[Mixin] → LevelRenderer.render() → IrisRenderingPipeline
     ↓
G-Buffer 填充 → 延迟渲染 → PBR 处理
```

### 2.2 渲染流程

```
AssimpLoader 输出
    │
    ▼
AssimpBakedModel.getQuads()  ← 被 Minecraft 标准渲染调用
    │
    ▼
[如果 Oculus 启用] → Mixin 拦截 → IrisRenderingPipeline 处理
    │
    ▼
应用 PBR/延迟渲染/阴影效果 → 输出到屏幕
```

---

## 3. 关键类映射

### 3.1 AssimpLoader 输出端

| 类 | 作用 |
|----|------|
| `AssimpBakedModel` | Minecraft BakedModel 接口实现 |
| `MinecraftMeshConverter` | Assimp Mesh → BakedQuad 转换 |
| `AssimpGeometryLoader` | Forge 模型加载器注册 |

### 3.2 Oculus 渲染增强端

| 类 | 作用 |
|----|------|
| `IrisRenderingPipeline` | 主渲染管线 |
| `ShaderMap` | 着色器管理 |
| `ProgramSamplers` | 纹理采样器 |

---

## 4. 集成方案（简化版）

### 4.1 现状分析

AssimpLoader 已完成的工作：
```java
// AssimpLoader 输出
AssimpScene scene = loader.loadModel(path);        // 加载 FBX/GLTF
AssimpBakedModel model = new AssimpBakedModel(scene, sprite);  // 转为 BakedModel
// model 注册到 Forge，被 Minecraft 渲染系统调用
```

Oculus 已完成的工作：
```java
// Oculus 注入
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Inject(method = "renderLevel", ...)
    void onRenderLevel(...) {
        pipeline.beginLevelRendering();  // 替换为 Iris 管线
        // ... 延迟渲染、PBR 逻辑
    }
}
```

### 4.2 无缝对接方式

**核心点**：只要 `AssimpBakedModel` 走标准 Minecraft 渲染流程，Oculus 就会自动处理。

```
AssimpBakedModel.getQuads() 
    ↓
Minecraft RenderSystem 调用
    ↓
[Mixin] LevelRenderer.render() → 捕获
    ↓
IrisRenderingPipeline.deferredRenderer.renderAll()
    ↓
G-Buffer 填充（PBR 贴图、法线、深度等）
    ↓
Composite Shader 处理
    ↓
Final 输出
```

---

## 5. 需要确认的事项

### 5.1 材质系统兼容性

| 功能 | AssimpLoader | Oculus | 兼容性 |
|------|-------------|--------|--------|
| 基础色 | ✅ diffuseColor | ✅ | 直接兼容 |
| 法线贴图 | ❌ 无 | ✅ PBR | 需扩展 |
| 金属度 | ❌ 无 | ✅ | 需扩展 |
| 粗糙度 | ❌ 无 | ✅ | 需扩展 |
| 环境光遮蔽 | ❌ 无 | ✅ | Oculus 自动 |

### 5.2 纹理绑定

AssimpLoader 当前的纹理绑定：
```java
// MinecraftMeshConverter.java
float[] texCoords = mesh.getTexCoords();
// 使用 sprite (TextureAtlasSprite)
```

Oculus 的 PBR 采样：
```java
// IrisSamplers.java
addPBRSamplers(samplerHolder, ...);  // normal, metallic, roughness
```

**问题**：AssimpLoader 目前没有生成 PBR 纹理（法线、金属度、粗糙度），Oculus 的 PBR Shader 收不到数据。

---

## 6. 扩展方案

### 6.1 方案 A：保持现状（推荐）

AssimpLoader 输出基础模型 + Minecraft 默认渲染 → 不使用 Oculus 增强

```
优点：快速集成，简单可靠
缺点：无 PBR/延迟渲染效果
```

```java
// 当前代码无需改动
AssimpBakedModel model = new AssimpBakedModel(scene, sprite);
// 注册到 Forge，模型会被正常渲染
// 如果未启用 Oculus，使用 Minecraft 默认着色器
// 如果启用 Oculus，使用 OptiFine 兼容着色器
```

### 6.2 方案 B：扩展 PBR 材质（推荐进阶）

扩展 AssimpLoader 生成 PBR 数据，让 Oculus 能够处理：

```java
// 扩展 AssimpMaterial.java
public class AssimpMaterial {
    // 现有
    private float[] diffuseColor;
    
    // 新增 PBR 属性
    private int normalTextureId;      // 法线贴图
    private float metallic = 0.0f;   // 金属度
    private float roughness = 0.5f;  // 粗糙度
    
    public boolean hasPBR() {
        return normalTextureId > 0 || metallic > 0 || roughness != 0.5f;
    }
}
```

修改 `MinecraftMeshConverter` 传递 PBR 数据到顶点属性：
```java
// 在顶点着色器输入中添加 PBR 通道
// 对应 VertexFormat.CUSTOM1, CUSTOM2 等
```

### 6.3 方案 C：完全接入 Oculus 管线

让 AssimpLoader 生成 `ExtendedShader`（Oculus 格式）：

```java
// 创建 AssimpExtendedShader.java 继承 ExtendedShader
public class AssimpExtendedShader extends ExtendedShader {
    // 实现 PBR 采样
    // 实现自定义 Uniform 传递
    
    @Override
    public void loadSamplers(ProgramSamplers samplers) {
        // 加载 PBR 纹理
        samplers.addPBRSamplers(...);
    }
}
```

---

## 7. 最小改造清单

### 7.1 短期（方案 A）

无需改动，直接使用。AssimpLoader 模型会被正常渲染，但不使用 Oculus 的增强效果。

### 7.2 中期（方案 B）

| 步骤 | 改动 |
|------|------|
| 1 | 扩展 `AssimpMaterial` 添加 PBR 属性 |
| 2 | 扩展 `MinecraftMeshConverter` 生成 PBR 顶点属性 |
| 3 | 测试与 PBR Shader 对接 |

### 7.3 长期（方案 C）

| 步骤 | 改动 |
|------|------|
| 1 | 创建 `AssimpExtendedShader` |
| 2 | 注册到 `ShaderMap` |
| 3 | 实现 `IrisRenderingPipeline` 中的资源调度 |

---

## 8. 快速测试验证

### 8.1 测试步骤

1. 确保 AssimpLoader 独立工作
```bash
# 加载一个 FBX 模型到游戏
# 验证模型正常显示
```

2. 启用 Oculus
```bash
# 安装 Oculus mod
# 使用一个支持延迟渲染的 Shader Pack
# 验证内置方块有 PBR 效果
```

3. 加载 AssimpLoader 模型
```java
// 检查是否被 Oculus 捕获
// 打印日志确认是否经过 deferredRenderer
```

### 8.2 调试代码

```java
// 在 AssimpBakedModel.getQuads() 添加日志
@Override
public List<BakedQuad> getQuads(...) {
    AssimpLoader.LOGGER.info("AssimpBakedModel.getQuads() called - side: {}", side);
    return quads;
}
```

---

## 9. 结论

| 场景 | 结论 |
|------|------|
| AssimpLoader → Minecraft 默认渲染 | ✅ 完全兼容 |
| AssimpLoader → Oculus 基础增强 | ✅ Mixin 自动捕获 |
| AssimpLoader → Oculus PBR 增强 | ⚠️ 需要扩展材质系统 |
| AssimpLoader → Oculus 延迟渲染 | ⚠️ 需要 ExtendedShader |

**核心结论**：AssimpLoader 输出的 BakedModel 只要走 Minecraft 标准渲染流程，Oculus 的 Mixin 就会自动捕获并处理。唯一需要扩展的是 PBR 材质数据的生成。

---

*文档更新：2026-04-23*
*版本：Oculus 1.8.0 / ImprovedMobs AssimpLoader*