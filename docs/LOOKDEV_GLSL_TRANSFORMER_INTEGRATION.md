# ASSIMPLOADER LookDev 节点编辑器扩展 - 整合 glsl-transformer 与 Iris/Oculus 渲染管线

## 一、项目背景与目标

### 1.1 背景

ASSIMPLOADER 的 LookDev 模块已实现了基础的节点编辑器，能够：
- 可视化编辑材质节点图
- 通过 `ShaderCodeGenerator` 生成 GLSL 代码
- 通过 `PhotonShaderCompiler` 编译为 Minecraft Shader

但当前存在以下局限：
1. **仅能生成完整 Shader**，无法注入到现有 Shader 系统（Iris/Oculus）
2. **节点类型有限**，缺乏高级 Shader 操作节点
3. **与 Photon 绑定**，无法独立于 Photon 使用

### 1.2 目标

扩展 LookDev 节点编辑器，使其能够：
1. **生成可注入的 GLSL 片段**（而非完整 Shader）
2. **通过 glsl-transformer 注入到 Oculus/Iris 渲染管线**
3. **扩展节点类型**，支持更多 Shader 操作
4. **保持与 Photon 的兼容性**，同时支持独立运行

---

## 二、现有代码架构分析

### 2.1 核心文件结构

```
lookdev/nodeeditor/
├── Node.java                          # 节点基类
├── NodeGraphExecutor.java             # 图执行器（拓扑排序）
├── NodeEditorCanvas.java              # 画布渲染
├── NodeSocket.java                   # 节点端口
├── NodeConnection.java                # 连接线
├── NodeRegistry.java                  # 节点注册表
├── shader/
│   ├── ShaderCodeGenerator.java       # 完整Shader生成器
│   ├── GLSLEmitter.java               # 节点→GLSL转换
│   ├── ShaderTemplate.java            # Shader模板
│   ├── PhotonShaderCompiler.java      # Photon编译
│   └── NodeBasedMaterial.java         # 材质实现
└── nodes/
    ├── input/
    │   ├── ColorNode.java             # 颜色节点
    │   ├── ValueNode.java             # 数值节点
    │   ├── TextureSampleNode.java     # 纹理采样
    │   └── UVCoordinateNode.java      # UV坐标
    ├── math/
    │   ├── AddNode.java               # 加法
    │   ├── MultiplyNode.java          # 乘法
    │   └── LerpNode.java              # 插值
    ├── utility/
    │   └── NormalMapNode.java         # 法线贴图
    └── output/
        └── MaterialOutputNode.java     # 材质输出
```

### 2.2 关键类职责

| 类 | 职责 |
|---|------|
| `NodeGraphExecutor` | 拓扑排序、循环检测、图执行 |
| `ShaderCodeGenerator` | 从节点图生成完整 Vertex+Fragment Shader |
| `GLSLEmitter` | 单个节点转换为 GLSL 代码 |
| `PhotonShaderCompiler` | 写入资源包并编译为 Minecraft Shader |

### 2.3 数据流

```
Node Graph → NodeGraphExecutor (排序)
              ↓
         ShaderCodeGenerator.generate()
              ↓
           GLSLEmitter.emitNode() (逐节点转换)
              ↓
        生成完整 GLSL 字符串
              ↓
        PhotonShaderCompiler.compile()
              ↓
        写入资源包 + 编译
```

---

## 三、整合方案

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      扩展后的架构                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │  Node Graph │───▶│NodeExecutor │───▶│CodeGenerator│         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│                                             │                   │
│                       ┌─────────────────────┼─────────────────┐  │
│                       │                     │                 │  │
│                       ▼                     ▼                 ▼  │
│              ┌────────────────┐   ┌────────────────┐  ┌──────────────┐
│              │ CompleteShader│   │ GLSL Fragment  │  │ GLSL Fragment │
│              │   (Photon)     │   │  (Oculus注入)  │  │ (验证/工具)   │
│              └────────────────┘   └────────────────┘  └──────────────┘
│                     │                     │                     │
│                     ▼                     ▼                     │
│              ┌────────────────┐   ┌────────────────┐             │
│              │PhotonShader   │   │OculusPipeline │             │
│              │   Compiler     │   │   (Inject)    │             │
│              └────────────────┘   └────────────────┘             │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 新增核心组件

#### 3.2.1 GLSLFragmentGenerator

```java
package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import java.util.*;

/**
 * 生成可注入的 GLSL 代码片段
 *
 * 与 ShaderCodeGenerator 的区别：
 * - 不生成完整 Shader（无 #version、无 main 函数）
 * - 输出声明语句和计算语句
 * - 包含注入点元数据
 */
public class GLSLFragmentGenerator {

    private final NodeGraphExecutor executor;
    private final GLSLFragmentEmitter emitter;
    private final Map<String, String> nodeVariables;
    private int variableCounter = 0;

    public GLSLFragmentGenerator(List<Node> nodes, List<NodeConnection> connections) {
        this.executor = new NodeGraphExecutor(nodes, connections);
        this.emitter = new GLSLFragmentEmitter();
        this.nodeVariables = new HashMap<>();
    }

    /**
     * 生成 GLSL 片段
     * @param outputNodeMaterial 输出节点的材质配置
     * @param injectionConfig 注入配置
     * @return GLSLFragment 包含代码和元数据
     */
    public GLSLFragment generate(MaterialOutputConfig outputNodeMaterial,
                                  InjectionConfig injectionConfig) {
        if (executor.hasCycles()) {
            return null;
        }

        List<Node> executionOrder = executor.getExecutionOrder();

        emitter.reset();
        nodeVariables.clear();

        // 生成 uniform 声明
        String uniforms = emitter.emitUniforms();

        // 生成节点计算代码
        StringBuilder code = new StringBuilder();
        for (Node node : executionOrder) {
            String nodeCode = emitter.emitNode(node, this);
            if (nodeCode != null && !nodeCode.isEmpty()) {
                code.append(nodeCode).append("\n");
            }
        }

        // 生成输出赋值
        String outputAssignment = generateOutputAssignment(outputNodeMaterial);

        return new GLSLFragment(
            uniforms + "\n" + code + "\n" + outputAssignment,
            emitter.getUsedUniforms(),
            injectionConfig
        );
    }

    /**
     * 生成输出赋值语句
     */
    private String generateOutputAssignment(MaterialOutputConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Output assignments\n");

        if (config.getBaseColor() != null) {
            sb.append("material.baseColor = ").append(config.getBaseColor()).append(";\n");
        }
        if (config.getMetallic() != null) {
            sb.append("material.metallic = ").append(config.getMetallic()).append(";\n");
        }
        return sb.toString();
    }

    // ========== 内部类 ==========

    public static class GLSLFragment {
        private final String code;
        private final Set<String> usedUniforms;
        private final InjectionConfig injectionConfig;

        public GLSLFragment(String code, Set<String> uniforms, InjectionConfig config) {
            this.code = code;
            this.usedUniforms = uniforms;
            this.injectionConfig = config;
        }

        public String getCode() { return code; }
        public Set<String> getUsedUniforms() { return usedUniforms; }
        public InjectionConfig getInjectionConfig() { return injectionConfig; }
    }

    public static class InjectionConfig {
        private InjectionPoint point;
        private String targetShader;
        private String targetFunction;
        private boolean replaceOriginal;

        public enum InjectionPoint {
            BEFORE_DECLARATIONS,
            BEFORE_FUNCTIONS,
            MAIN_START,
            MAIN_END,
            AFTER_FUNCTION,
            REPLACE_BLOCK
        }

        public InjectionPoint getPoint() { return point; }
        public void setPoint(InjectionPoint point) { this.point = point; }
        public String getTargetShader() { return targetShader; }
        public void setTargetShader(String targetShader) { this.targetShader = targetShader; }
        public String getTargetFunction() { return targetFunction; }
        public void setTargetFunction(String targetFunction) { this.targetFunction = targetFunction; }
        public boolean isReplaceOriginal() { return replaceOriginal; }
        public void setReplaceOriginal(boolean replaceOriginal) { this.replaceOriginal = replaceOriginal; }
    }
}
```

#### 3.2.2 GLSLFragmentEmitter

```java
package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import java.util.*;

/**
 * 节点 → GLSL 片段转换器
 *
 * 继承 GLSLEmitter 的转换逻辑，但输出片段而非完整 Shader
 */
public class GLSLFragmentEmitter extends GLSLEmitter {

    private Set<String> usedUniforms = new HashSet<>();

    @Override
    public void reset() {
        super.reset();
        usedUniforms.clear();
    }

    @Override
    public String emitNode(Node node, ShaderCodeGenerator generator) {
        return super.emitNode(node, (ShaderCodeGenerator) generator);
    }

    @Override
    protected String emitTextureSample(TextureSampleNode node, ShaderCodeGenerator generator) {
        usedUniforms.add("sampler2D " + getTextureUniformName(node));
        return super.emitTextureSample(node, generator);
    }

    public Set<String> getUsedUniforms() {
        return usedUniforms;
    }
}
```

---

## 四、glsl-transformer 集成

### 4.1 依赖配置

在 `build.gradle` 中添加依赖：

```groovy
dependencies {
    // glsl-transformer (AGPLv3 许可证)
    implementation 'io.github.douira:glsl-transformer:1.0.0'

    // ANTLR4 运行时
    implementation 'org.antlr:antlr4-runtime:4.13.1'
}
```

### 4.2 OculusShaderInjector

```java
package cn.minerealms.assimploader.lookdev.iris;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.ast.transform.EnumASTTransformer;
import io.github.douira.glsl_transformer.parser.ParseShape;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 将节点图生成的 GLSL 注入到 Oculus Shader 管线
 */
public class OculusShaderInjector {

    private final EnumASTTransformer<PatchShaderType> transformer;

    public OculusShaderInjector() {
        this.transformer = new EnumASTTransformer<>(PatchShaderType.class) {{
            setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
        }};
    }

    /**
     * 注入 GLSL 片段到目标 Shader
     *
     * @param targetShaderName 目标 Shader 类型
     * @param fragmentCode 节点图生成的 GLSL 片段
     * @param uniforms 需要声明的 uniform
     * @param injectionPoint 注入点
     * @return 转换后的 Shader 代码
     */
    public String inject(String targetShaderName,
                         String fragmentCode,
                         Map<String, String> uniforms,
                         InjectionPoint injectionPoint) {

        EnumMap<PatchShaderType, String> inputs = new EnumMap<>(PatchShaderType.class);
        inputs.put(PatchShaderType.FRAGMENT, getBaseFragmentShader());

        transformer.setTransformation((trees, params) -> {
            TranslationUnit tree = trees.get(PatchShaderType.FRAGMENT);
            Root root = tree.getRoot();
            ASTParser t = transformer.getLexer();

            // 1. 注入 uniform 声明
            for (Map.Entry<String, String> uniform : uniforms.entrySet()) {
                tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
                    "uniform " + uniform.getKey() + " " + uniform.getValue() + ";");
            }

            // 2. 注入 GLSL 片段
            ASTInjectionPoint astPoint = convertInjectionPoint(injectionPoint);
            tree.parseAndInjectNode(t, astPoint, fragmentCode);

            // 3. 替换变量引用
            if (params.containsKey("targetFunction")) {
                String functionName = params.get("targetFunction");
                root.replaceReferenceExpressions(t, functionName + "_result",
                    getOutputVariableName());
            }
        });

        return transformer.transform(inputs, PatchShaderType.FRAGMENT);
    }

    public String injectToGBuffer(String gbufferName,
                                  String fragmentCode,
                                  Map<String, String> uniforms) {
        return inject(gbufferName, fragmentCode, uniforms,
            InjectionPoint.MAIN_END);
    }

    public String injectToComposite(String compositeName,
                                    String fragmentCode,
                                    Map<String, String> uniforms) {
        return inject("composite_" + compositeName, fragmentCode, uniforms,
            InjectionPoint.MAIN_START);
    }

    private ASTInjectionPoint convertInjectionPoint(InjectionPoint point) {
        return switch (point) {
            case BEFORE_DECLARATIONS -> ASTInjectionPoint.BEFORE_DECLARATIONS;
            case BEFORE_FUNCTIONS -> ASTInjectionPoint.BEFORE_FUNCTIONS;
            case MAIN_START -> ASTInjectionPoint.BEFORE_MAIN;
            case MAIN_END -> ASTInjectionPoint.END;
            case AFTER_FUNCTION -> ASTInjectionPoint.AFTER_FUNCTIONS;
            default -> ASTInjectionPoint.BEFORE_DECLARATIONS;
        };
    }

    private String getBaseFragmentShader() {
        return """
            #version 330 core
            precision highp float;

            in vec2 texCoord;
            in vec3 normal;
            in vec3 viewDir;

            out vec4 fragColor;

            void main() {
            }
            """;
    }

    private String loadGBufferShader(String name) {
        return "";
    }

    public enum InjectionPoint {
        BEFORE_DECLARATIONS,
        BEFORE_FUNCTIONS,
        MAIN_START,
        MAIN_END,
        AFTER_FUNCTION
    }

    private String getOutputVariableName() {
        return "node_output";
    }
}
```

---

## 五、节点扩展

### 5.1 新增节点类型

| 节点 | 描述 | GLSL 输出 |
|-----|------|----------|
| `NoiseNode` | 噪声生成器 | `noise(uv, type)` |
| `GradientNode` | 渐变生成 | `gradient(pos, colors)` |
| `TimeNode` | 时间输入 | `uTime` uniform |
| `WorldPosNode` | 世界坐标 | `getWorldPosition()` |
| `ViewDirNode` | 视线方向 | `viewDir` |
| `LightDirNode` | 光照方向 | `lightDir` |
| `FogNode` | 雾效计算 | `applyFog(color, depth)` |
| `MathFuncNode` | 数学函数 | `sin/cos/pow/...` |
| `SwizzleNode` | 向量分量提取 | `.x/.y/.z/.w` |
| `CombineNode` | 向量合并 | `vec3(a,b,c)` |
| `ShaderFunctionNode` | 自定义函数 | 用户定义的 GLSL 函数 |
| `UniformNode` | 自定义 Uniform | `uniform xxx` |

### 5.2 NoiseNode 示例

```java
package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.noise;

import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Noise Generation Node
 *
 * Inputs:
 * - UV (vec2): Texture coordinates
 * - Scale (float): Noise scale
 * - Octaves (int): Noise octaves for FBM
 *
 * Outputs:
 * - Value (float): Noise value 0-1
 */
public class NoiseNode extends Node {

    public enum NoiseType {
        PERLIN,
        SIMPLEX,
        VORONOI,
        FBM
    }

    private NoiseType noiseType = NoiseType.PERLIN;
    private float scale = 10.0f;
    private int octaves = 4;

    public NoiseNode() {
        super("Noise");

        addInput(new EnhancedNodeSocket("UV", EnhancedSocketType.VEC2));
        addInput(new EnhancedNodeSocket("Scale", EnhancedSocketType.FLOAT));
        addInput(new EnhancedNodeSocket("Octaves", EnhancedSocketType.INT));

        addOutput(new EnhancedNodeSocket("Value", EnhancedSocketType.FLOAT));

        this.titleBarColor = 0xFF4A4A6A;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // 绘制噪声类型指示器
    }

    public NoiseType getNoiseType() {
        return noiseType;
    }

    public void setNoiseType(NoiseType type) {
        this.noiseType = type;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public int getOctaves() {
        return octaves;
    }

    public void setOctaves(int octaves) {
        this.octaves = octaves;
    }
}
```

### 5.3 GLSLEmitter 扩展

```java
// 在 GLSLEmitter 中添加新节点处理

@Override
public String emitNode(Node node, ShaderCodeGenerator generator) {
    // 现有节点处理...

    if (node instanceof NoiseNode) {
        return emitNoise((NoiseNode) node, generator);
    }
    if (node instanceof TimeNode) {
        return emitTime((TimeNode) node, generator);
    }
    if (node instanceof WorldPosNode) {
        return emitWorldPos((WorldPosNode) node, generator);
    }
    if (node instanceof FogNode) {
        return emitFog((FogNode) node, generator);
    }

    return super.emitNode(node, generator);
}

private String emitNoise(NoiseNode node, ShaderCodeGenerator generator) {
    String uvVar = getInputVariable(node, "UV", generator, "vTexCoord");
    String scaleVar = getInputVariable(node, "Scale", generator,
        String.valueOf(node.getScale()));
    String resultVar = generator.getNodeVariable(node);

    String functionName = switch (node.getNoiseType()) {
        case PERLIN -> "noise_perlin";
        case SIMPLEX -> "noise_simplex";
        case VORONOI -> "noise_voronoi";
        case FBM -> "noise_fbm";
    };

    return "float " + resultVar + " = " + functionName + "(" + uvVar + ", " + scaleVar + ");\n";
}

private String emitTime(TimeNode node, ShaderCodeGenerator generator) {
    String resultVar = generator.getNodeVariable(node);
    declareUniform("float", "uTime");
    return "float " + resultVar + " = uTime;\n";
}

private String emitWorldPos(WorldPosNode node, ShaderCodeGenerator generator) {
    String resultVar = generator.getNodeVariable(node);
    return "vec3 " + resultVar + " = getWorldPosition();\n";
}

private String emitFog(FogNode node, ShaderCodeGenerator generator) {
    String colorVar = getInputVariable(node, "Color", generator, "vec3(1.0)");
    String depthVar = getInputVariable(node, "Depth", generator, "gl_FragCoord.z");
    String resultVar = generator.getNodeVariable(node);

    return "vec3 " + resultVar + " = applyFog(" + colorVar + ", " + depthVar + ");\n";
}
```

---

## 六、Photon 扩展

### 6.1 新的材质类型

```java
package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import com.lowdragmc.photon.client.material.*;

/**
 * 支持注入的材质
 *
 * 当 Oculus 可用时，生成的 Shader 可以注入到渲染管线
 * 当 Photon 可用时，回退到原有的完整 Shader 模式
 */
public class InjectableMaterial extends CustomShaderMaterial {

    private String fragmentCode;
    private String vertexCode;
    private InjectionTarget target;

    public enum InjectionTarget {
        NONE,
        GBUFFER,
        COMPOSITE,
        SHADOW
    }

    public void setFragmentCode(String code) {
        this.fragmentCode = code;
    }

    public void setVertexCode(String code) {
        this.vertexCode = code;
    }

    public void setInjectionTarget(InjectionTarget target) {
        this.target = target;
    }

    public InjectionTarget getInjectionTarget() {
        return target;
    }

    public String getFragmentCode() {
        return fragmentCode;
    }

    public String getVertexCode() {
        return vertexCode;
    }
}
```

### 6.2 DualModeShaderManager

```java
package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

/**
 * 双模式 Shader 管理器
 *
 * 根据运行时环境选择：
 * - Oculus 存在：使用注入模式
 * - Photon 存在：使用完整 Shader 模式
 * - 都存在：优先注入模式
 */
public class DualModeShaderManager {

    private static boolean oculusAvailable;
    private static boolean photonAvailable;

    public static void init() {
        oculusAvailable = checkOculus();
        photonAvailable = checkPhoton();
    }

    public static boolean isOculusAvailable() {
        return oculusAvailable;
    }

    public static boolean isPhotonAvailable() {
        return photonAvailable;
    }

    public static CompiledShader compile(NodeGraph graph, MaterialOutputConfig config) {
        GLSLFragmentGenerator fragmentGen = new GLSLFragmentGenerator(
            graph.getNodes(), graph.getConnections());
        GLSLFragment fragment = fragmentGen.generate(config, null);

        if (fragment == null) {
            return null;
        }

        if (oculusAvailable) {
            return compileInjectMode(fragment);
        } else if (photonAvailable) {
            return compileFullMode(fragment);
        }

        return null;
    }

    private static CompiledShader compileInjectMode(GLSLFragment fragment) {
        // 使用 OculusShaderInjector 注入
        return null;
    }

    private static CompiledShader compileFullMode(GLSLFragment fragment) {
        // 使用 ShaderCodeGenerator + PhotonShaderCompiler
        String shaderName = "node_based_" + System.currentTimeMillis();
        ShaderCodeGenerator fullGen = new ShaderCodeGenerator(null, null);
        // 完整实现需要传入节点列表
        return null;
    }

    private static boolean checkOculus() {
        try {
            Class.forName("net.irisshaders.iris.Iris");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean checkPhoton() {
        try {
            Class.forName("com.lowdragmc.photon.Photon");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

---

## 七、任务拆分

### Phase 1: 基础框架搭建

| 任务 | 文件 | 描述 |
|-----|------|------|
| 1.1 | build.gradle | 添加 glsl-transformer 依赖 |
| 1.2 | GLSLFragment.java | 片段数据结构 |
| 1.3 | GLSLFragmentGenerator.java | 片段生成器 |
| 1.4 | GLSLFragmentEmitter.java | 片段发射器 |
| 1.5 | ShaderTypeDef.java | GLSL 类型定义 |

### Phase 2: glsl-transformer 集成

| 任务 | 文件 | 描述 |
|-----|------|------|
| 2.1 | OculusShaderInjector.java | 注入器主类 |
| 2.2 | InjectionConfig.java | 注入配置 |
| 2.3 | ShaderTargetRegistry.java | 目标 Shader 注册 |
| 2.4 | GlslTransformerMixin.java | Mixin 扩展点 |

### Phase 3: 节点扩展

| 任务 | 文件 | 描述 |
|-----|------|------|
| 3.1 | NoiseNode.java | 噪声节点 |
| 3.2 | TimeNode.java | 时间节点 |
| 3.3 | WorldPosNode.java | 世界坐标节点 |
| 3.4 | ViewDirNode.java | 视线方向节点 |
| 3.5 | FogNode.java | 雾效节点 |
| 3.6 | MathFuncNode.java | 数学函数节点 |

### Phase 4: Photon 扩展

| 任务 | 文件 | 描述 |
|-----|------|------|
| 4.1 | InjectableMaterial.java | 可注入材质 |
| 4.2 | DualModeShaderManager.java | 双模式管理 |
| 4.3 | NodeBasedMaterial.java | 扩展现有材质 |
| 4.4 | MaterialPreviewRenderer.java | 更新预览 |

### Phase 5: UI 集成

| 任务 | 文件 | 描述 |
|-----|------|------|
| 5.1 | ExportDialog.java | 导出对话框 |
| 5.2 | InjectionConfigPanel.java | 注入配置面板 |
| 5.3 | NodeEditorMenuBar.java | 菜单栏扩展 |
| 5.4 | ShaderTargetSelector.java | 目标选择器 |

### Phase 6: 测试与优化

| 任务 | 描述 |
|-----|------|
| 6.1 | 单元测试：GLSLFragmentGenerator |
| 6.2 | 集成测试：注入到 Oculus |
| 6.3 | 性能优化：缓存机制 |
| 6.4 | Bug 修复与边界情况 |

---

## 八、编译验证流程

### 8.1 编译命令

```bash
# 完整编译
./gradlew build

# 快速编译（不运行）
./gradlew compileJava

# 仅编译客户端
./gradlew compileClientJava

# 运行开发环境
./gradlew runClient
```

### 8.2 验证步骤

1. **编译检查**
   ```bash
   ./gradlew compileJava 2>&1 | grep -i error
   ```

2. **运行测试**
   ```bash
   ./gradlew runClient
   # 在游戏中打开 LookDev，测试节点编辑
   ```

3. **检查日志**
   - 查看 `logs/assimploader.log`
   - 确认 glsl-transformer 初始化成功

### 8.3 常见编译错误

| 错误 | 解决方案 |
|-----|---------|
| `cannot find symbol: class GLSLFragment` | 检查文件是否在正确包路径 |
| `NoClassDefFoundError: glsl_transformer` | 检查依赖是否正确引入 |
| `Mixin injection error` | 检查 Mixin 配置和目标类 |

---

## 九、提交规范

### 9.1 提交信息格式

```
[TYPE] Subject

Description...

Refs: #issue
```

### 9.2 类型前缀

| 前缀 | 含义 |
|-----|------|
| `[FEAT]` | 新功能 |
| `[FIX]` | Bug 修复 |
| `[REFACTOR]` | 重构 |
| `[DOC]` | 文档 |
| `[TEST]` | 测试 |

### 9.3 示例

```
[FEAT] Add GLSLFragmentGenerator for shader injection

- Implement GLSLFragment class for code and metadata
- Add GLSLFragmentGenerator to convert node graph to GLSL
- Integrate glsl-transformer for Oculus shader injection

Refs: #lookdev-extension
```

---

## 十、参考代码库

### 10.1 主要参考

1. **Oculus (Iris)**
   - `TransformPatcher.java` - Shader 转换核心
   - `VanillaTransformer.java` - 注入示例
   - 路径: `H:\MinecraftMods\Oculus\src\main\java\net\irisshaders\iris\pipeline\transform\`

2. **glsl-transformer**
   - `ASTTransformer.java` - 主转换器
   - `ASTInjectionPoint.java` - 注入点枚举
   - 路径: `H:\MinecraftMods\glsl-transformer\glsl-transformer\src\main\java\`

3. **现有 ASSIMPLOADER**
   - `ShaderCodeGenerator.java` - 现有生成器
   - `GLSLEmitter.java` - 现有发射器

### 10.2 关键方法对应

| glsl-transformer | 用途 |
|-----------------|------|
| `tree.parseAndInjectNode()` | 注入代码到指定位置 |
| `root.replaceReferenceExpressions()` | 替换变量引用 |
| `root.rename()` | 重命名标识符 |
| `root.nodeIndex.getStream()` | 查询特定节点 |

---

## 十一、后续规划

### 11.1 功能扩展

1. **节点模板系统**
   - 保存/加载节点图为模板
   - 社区分享节点库

2. **实时预览增强**
   - 分屏对比（原始 vs 注入后）
   - 时间/动画预览

3. **Shader 库**
   - 导入外部 Shader 片段
   - 节点图导出为独立 Shader 库

### 11.2 优化方向

1. **性能**
   - GLSL 缓存机制
   - 增量编译

2. **用户体验**
   - 节点搜索
   - 快捷键
   - 节点分组

---

*文档版本: 1.0*
*最后更新: 2026-04-27*
*作者: Claude Code*

---

## 附录：Mixin 配置

在 `assimploader.mixins.json` 中添加：

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "cn.minerealms.assimploader.mixin.iris",
  "compatibilityLevel": "JAVA_17",
  "mixins": [
    "IrisTransformPatcherAccessor"
  ],
  "client": [
  ],
  "injectors": {
    "defaultPriority": "HIGH"
  }
}
```