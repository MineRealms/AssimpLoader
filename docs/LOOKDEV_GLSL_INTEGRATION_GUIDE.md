# LookDev GLSL Transformer Integration

## 概述

LookDev 节点编辑器现在支持生成可注入的 GLSL 片段，可以直接注入到 Iris/Oculus shader pack 中，实现实时材质效果预览和调试。

## 功能特性

### 🎨 节点类型

#### 输入节点
- **Value Node**: 浮点数值输入
- **Color Node**: RGB 颜色输入
- **UV Coordinate Node**: 纹理坐标
- **Texture Sample Node**: 纹理采样

#### 数学节点
- **Add Node**: 加法运算
- **Multiply Node**: 乘法运算
- **Lerp Node**: 线性插值
- **Math Function Node**: 20+ 数学函数
  - 三角函数: sin, cos, tan, asin, acos, atan
  - 指数对数: sqrt, exp, log, pow
  - 取整函数: abs, sign, floor, ceil, round, fract
  - 比较函数: min, max, clamp
  - 插值函数: lerp, smoothstep
  - 取模函数: mod

#### Shader 节点
- **Time Node**: 游戏时间输入
- **Noise Node**: 程序化噪声生成 (Perlin, Simplex, Worley)
- **World Position Node**: 世界空间坐标
- **View Direction Node**: 视角方向向量
- **Light Direction Node**: 光照方向向量

#### 工具节点
- **Normal Map Node**: 法线贴图处理

#### 输出节点
- **Output Node**: 材质输出 (Base Color, Metallic, Roughness, Normal, Emission)

---

## 使用方法

### 1. 创建节点图

在 LookDev 节点编辑器中创建你的材质节点图：

```
[Time Node] → [Math Function (sin)] → [Multiply] → [Output Node (Emission)]
                                          ↑
                                    [Color Node]
```

这个简单的例子创建了一个随时间脉动的发光效果。

### 2. 生成 GLSL 代码

节点图会自动转换为 GLSL 代码：

```glsl
// Uniforms
uniform float u_time;

// Main shader code
float node_time_1 = u_time;
float node_math_2 = sin(node_time_1);
vec3 node_color_3 = vec3(1.0, 0.5, 0.0);
vec3 node_multiply_4 = node_color_3 * node_math_2;

// Output
material.emission = node_multiply_4;
```

### 3. 注入到 Shader Pack

#### 使用 Iris/Oculus (推荐)

代码会自动注入到目标 shader：

```java
OculusShaderInjector injector = new OculusShaderInjector();
if (injector.isAvailable()) {
    String modifiedShader = injector.injectFragment(
        originalShader,
        fragment,
        InjectionPoint.MAIN_END
    );
}
```

#### 使用 Photon (备用)

如果 Iris/Oculus 不可用，系统会自动切换到完整 shader 模式：

```java
DualModeShaderManager manager = new DualModeShaderManager();
String shader = manager.generateShader(nodeGraph);
```

---

## 架构设计

### 数据流

```
┌─────────────┐
│  Node Graph │
└──────┬──────┘
       │
       ↓
┌─────────────────────┐
│ GLSLFragmentEmitter │  ← 遍历节点图
└──────┬──────────────┘
       │
       ↓
┌──────────────┐
│ GLSLFragment │  ← 中间表示 (uniforms, code, injection config)
└──────┬───────┘
       │
       ↓
┌────────────────────────┐
│ GLSLFragmentGenerator  │  ← 生成 GLSL 代码
└──────┬─────────────────┘
       │
       ↓
┌──────────────────────┐
│ OculusShaderInjector │  ← 注入到 Iris/Oculus shader
└──────────────────────┘
       or
┌──────────────────────┐
│ DualModeShaderManager│  ← 生成完整 shader (Photon)
└──────────────────────┘
```

### 核心组件

#### GLSLFragment
```java
public class GLSLFragment {
    private final String code;                    // GLSL 代码
    private final Set<String> usedUniforms;       // 使用的 uniform 变量
    private final InjectionConfig injectionConfig; // 注入配置
}
```

#### GLSLFragmentEmitter
- 遍历节点图
- 收集 uniform 声明
- 生成节点代码
- 构建 GLSLFragment

#### GLSLFragmentGenerator
- 格式化 GLSL 代码
- 添加 uniform 声明
- 处理注入点配置

#### OculusShaderInjector
- 使用 glsl-transformer 解析 shader AST
- 在指定位置注入代码
- 保持原始 shader 结构

---

## 注入点类型

| 注入点 | 描述 | 用途 |
|--------|------|------|
| `BEFORE_DECLARATIONS` | 在所有声明之前 | 添加自定义 uniform/varying |
| `BEFORE_FUNCTIONS` | 在函数定义之前 | 添加辅助函数 |
| `MAIN_START` | main() 函数开始 | 初始化变量 |
| `MAIN_END` | main() 函数结束 | 修改最终输出 |

---

## 依赖项

### 必需
- **glsl-transformer** (0.30.3): Shader AST 解析和转换
- **ANTLR4 Runtime** (4.13.1): 语法解析支持

### 可选
- **Iris/Oculus**: 用于 shader 注入模式
- **Photon**: 用于完整 shader 模式

---

## 调试

### 启用调试日志

使用现有的 DebugManager 系统：

```java
DebugManager.log(DebugCategory.LOOKDEV, "Generated GLSL: " + glslCode);
```

### 检查生成的代码

生成的 GLSL 代码会包含注释，方便调试：

```glsl
// Texture Sample: Albedo
vec4 node_texture_1_color = texture(texture_node_1, vTexCoord);

// Math Function: Sin
float node_math_2 = sin(node_time_1);
```

---

## 性能优化

### Shader 缓存

系统会缓存生成的 shader 代码，避免重复生成：

```java
private final Map<String, String> shaderCache = new HashMap<>();

public String getOrGenerateShader(NodeGraph graph) {
    String cacheKey = graph.getCacheKey();
    return shaderCache.computeIfAbsent(cacheKey, 
        k -> generateShader(graph));
}
```

### 增量更新

只有当节点图发生变化时才重新生成 shader。

---

## 限制与注意事项

1. **Shader Pack 兼容性**: 不同的 shader pack 可能有不同的变量命名和结构
2. **性能影响**: 复杂的节点图可能影响渲染性能
3. **调试难度**: 注入的代码可能难以在 shader 编译错误中定位
4. **版本兼容**: 需要 Iris/Oculus 支持 glsl-transformer API

---

## 示例

### 示例 1: 简单发光效果

```
[Time] → [Sin] → [Multiply] → [Output (Emission)]
                     ↑
                 [Color (1,0.5,0)]
```

生成的效果：橙色脉动发光

### 示例 2: 程序化纹理

```
[WorldPos] → [Noise (Perlin)] → [Multiply] → [Output (BaseColor)]
                                     ↑
                                 [Color]
```

生成的效果：基于世界坐标的程序化纹理

### 示例 3: 动态法线

```
[Time] → [Noise] → [NormalMap] → [Output (Normal)]
  ↓
[WorldPos]
```

生成的效果：动态扰动的法线贴图

---

## 故障排除

### 编译错误

**问题**: `cannot find symbol: class GLSLFragment`  
**解决**: 检查包路径是否正确

**问题**: `NoClassDefFoundError: glsl_transformer`  
**解决**: 检查 build.gradle 中的依赖配置

### 运行时错误

**问题**: Shader 注入失败  
**解决**: 检查 Iris/Oculus 是否正确安装，查看日志输出

**问题**: 生成的 shader 无效  
**解决**: 检查节点连接是否正确，是否有未连接的输入

---

## 未来计划

- [ ] UI 导出对话框
- [ ] 注入配置面板
- [ ] 更多 shader 节点类型
- [ ] Shader 预设库
- [ ] 实时预览优化

---

## 参考文档

- [技术实现文档](LOOKDEV_GLSL_TRANSFORMER_INTEGRATION.md)
- [实现状态](GLSL_TRANSFORMER_INTEGRATION_STATUS.md)
- [节点编辑器概览](NODE_EDITOR_TECHNICAL_OVERVIEW.md)

---

**版本**: 1.0  
**最后更新**: 2026-04-XX
