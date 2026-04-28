# GPU 加速和现代渲染技术研究

## 研究目标

评估在 Minecraft 1.20.1 Forge 环境中实现以下技术的可行性：
1. 计算着色器（Compute Shader）
2. GLSL-Transformer 集成
3. 与 Iris/Oculus 的兼容性
4. 现代 GPU 加速技术

---

## Minecraft 渲染管线分析

### OpenGL 版本支持

**Minecraft 1.20.1 要求**：
- 最低：OpenGL 3.2 Core Profile
- 推荐：OpenGL 4.5+（用于高级特性）

**关键发现**：
- ✅ 顶点着色器（Vertex Shader）：完全支持
- ✅ 片段着色器（Fragment Shader）：完全支持
- ✅ Uniform Buffer Objects (UBO)：OpenGL 3.1+，支持
- ⚠️ Shader Storage Buffer Objects (SSBO)：OpenGL 4.3+，需要检测
- ⚠️ 计算着色器（Compute Shader）：OpenGL 4.3+，需要检测
- ✅ Texture Buffer Objects (TBO)：OpenGL 3.0+，支持

### RenderSystem API 限制

Minecraft 使用 `com.mojang.blaze3d.systems.RenderSystem` 封装 OpenGL 调用：

```java
// 支持的操作
RenderSystem.setShader(Supplier<ShaderInstance> shader);
RenderSystem.setShaderTexture(int unit, ResourceLocation texture);
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();

// 限制
// - 不直接暴露计算着色器 API
// - 需要通过 GL43 直接调用
// - 可能与 Iris 冲突
```

**结论**：可以使用原生 OpenGL 调用，但需要小心管理状态。

---

## Iris/Oculus 兼容性研究

### Iris Shader Mod 架构

**Iris 1.7.x (for MC 1.20.1)**：
- 基于 OptiFine 着色器包格式
- 支持自定义顶点/片段着色器
- **关键发现**：Iris 拦截并修改所有着色器调用

### 兼容性策略

#### 策略 1: 使用 Iris 的着色器系统（推荐）
```java
// 通过 Iris API 注册自定义着色器
public class AssimpShaderRegistry {
    public static void register() {
        // 检测 Iris 是否存在
        if (ModList.get().isLoaded("iris")) {
            registerIrisShaders();
        } else {
            registerVanillaShaders();
        }
    }
    
    private static void registerIrisShaders() {
        // 使用 Iris 的着色器注册 API
        // 需要研究 Iris 的具体 API
    }
}
```

**优点**：
- 与 Iris 完全兼容
- 可以利用 Iris 的优化

**缺点**：
- 依赖 Iris API（可能不稳定）
- 功能受限于 Iris 支持的特性

#### 策略 2: 直接 OpenGL 调用 + 状态管理
```java
public class SafeGLContext {
    public static void withIsolatedContext(Runnable task) {
        // 保存当前 GL 状态
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        // ... 保存其他状态
        
        try {
            task.run();
        } finally {
            // 恢复 GL 状态
            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVAO);
            // ... 恢复其他状态
        }
    }
}
```

**优点**：
- 完全控制渲染管线
- 不依赖外部 API

**缺点**：
- 可能与 Iris 冲突
- 需要仔细管理状态

**推荐**：混合策略 - 优先使用 Iris API，降级到直接调用

---

## 计算着色器可行性分析

### 技术要求

**OpenGL 4.3+ 特性**：
```glsl
#version 430

layout(local_size_x = 64) in;

layout(std430, binding = 0) buffer InputData {
    vec4 positions[];
};

layout(std430, binding = 1) buffer OutputData {
    vec4 transformedPositions[];
};

void main() {
    uint idx = gl_GlobalInvocationID.x;
    // 计算逻辑
}
```

### 运行时检测

```java
public class ComputeShaderSupport {
    private static Boolean supported = null;
    
    public static boolean isSupported() {
        if (supported == null) {
            GLCapabilities caps = GL.getCapabilities();
            supported = caps.OpenGL43 && caps.GL_ARB_compute_shader;
            
            if (supported) {
                int maxWorkGroupSize = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE);
                LOGGER.info("Compute shader supported, max work group size: {}", maxWorkGroupSize);
            } else {
                LOGGER.warn("Compute shader not supported, falling back to vertex shader");
            }
        }
        return supported;
    }
}
```

### 降级方案

```java
public interface SkeletalAnimationProcessor {
    void processBones(Matrix4f[] bones, VertexData vertices);
}

public class ComputeShaderProcessor implements SkeletalAnimationProcessor {
    @Override
    public void processBones(Matrix4f[] bones, VertexData vertices) {
        // 使用计算着色器
        uploadToSSBO(bones, vertices);
        GL43.glDispatchCompute(vertices.count / 64, 1, 1);
        GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        downloadFromSSBO(vertices);
    }
}

public class VertexShaderProcessor implements SkeletalAnimationProcessor {
    @Override
    public void processBones(Matrix4f[] bones, VertexData vertices) {
        // 使用顶点着色器（当前实现）
        uploadBoneMatrices(bones);
        renderWithVertexShader(vertices);
    }
}

// 自动选择最佳实现
public class AnimationProcessorFactory {
    public static SkeletalAnimationProcessor create() {
        if (ComputeShaderSupport.isSupported()) {
            return new ComputeShaderProcessor();
        } else {
            return new VertexShaderProcessor();
        }
    }
}
```

---

## GLSL-Transformer 集成研究

### 什么是 GLSL-Transformer？

GLSL-Transformer 是一个用于在运行时修改和优化 GLSL 着色器的库。

**潜在用途**：
1. 动态注入骨骼数量（避免硬编码 `MAX_BONES`）
2. 根据硬件能力优化着色器
3. 添加调试代码（如骨骼可视化）

### 集成示例

```java
// 添加依赖: build.gradle
dependencies {
    implementation 'io.github.douira:glsl-transformer:2.0.0'
}

// 使用示例
public class DynamicShaderGenerator {
    public static String generateSkinnedShader(int boneCount) {
        String template = loadShaderTemplate("skinned_mesh.vert");
        
        // 使用 GLSL-Transformer 修改着色器
        TransformationPhase<TranslationUnit> phase = new TransformationPhase<>();
        phase.addTransformation(new ParameterInjection("MAX_BONES", boneCount));
        
        String optimized = phase.transform(template);
        return optimized;
    }
}

// 着色器模板
/*
#version 150

#define MAX_BONES ${MAX_BONES} // 将被替换

uniform mat4 BoneMatrices[MAX_BONES];

// ...
*/
```

### 优势分析

**优点**：
- 动态适应不同模型的骨骼数量
- 运行时优化（移除未使用的代码）
- 便于调试和实验

**缺点**：
- 增加依赖
- 运行时开销（首次编译）
- 可能与 Iris 冲突

**推荐**：作为可选优化，不作为核心依赖

---

## 现代 GPU 加速技术评估

### 1. Texture Buffer Objects (TBO) ✅ 推荐

**优势**：
- OpenGL 3.0+，广泛支持
- 比 uniform 数组更大的容量（数百万元素）
- 适合传输骨骼矩阵

**实现**：
```java
public class BoneMatrixTBO {
    private int tboId = -1;
    private int textureId = -1;
    private FloatBuffer buffer;
    
    public void upload(Matrix4f[] matrices) {
        if (tboId == -1) {
            tboId = GL15.glGenBuffers();
            textureId = GL11.glGenTextures();
            buffer = BufferUtils.createFloatBuffer(matrices.length * 16);
        }
        
        // 上传数据到 TBO
        buffer.clear();
        for (Matrix4f mat : matrices) {
            mat.get(buffer);
        }
        buffer.flip();
        
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        
        // 绑定为纹理
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, tboId);
    }
    
    public void bind(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
    }
}
```

**着色器使用**：
```glsl
#version 150

uniform samplerBuffer BoneMatrices;

mat4 getBoneMatrix(int index) {
    int offset = index * 4;
    return mat4(
        texelFetch(BoneMatrices, offset + 0),
        texelFetch(BoneMatrices, offset + 1),
        texelFetch(BoneMatrices, offset + 2),
        texelFetch(BoneMatrices, offset + 3)
    );
}
```

### 2. Uniform Buffer Objects (UBO) ✅ 推荐

**优势**：
- OpenGL 3.1+，广泛支持
- 比 uniform 更高效（一次上传，多次使用）
- 适合共享数据（相机矩阵、光照参数）

**实现**：
```java
public class SharedUBO {
    private int uboId = -1;
    
    public void upload(Matrix4f viewMatrix, Matrix4f projMatrix, Vector3f cameraPos) {
        if (uboId == -1) {
            uboId = GL15.glGenBuffers();
        }
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(4 * 16 + 4 * 16 + 4 * 4);
        viewMatrix.get(buffer);
        projMatrix.get(16 * 4, buffer);
        buffer.putFloat(32 * 4, cameraPos.x);
        buffer.putFloat(32 * 4 + 4, cameraPos.y);
        buffer.putFloat(32 * 4 + 8, cameraPos.z);
        buffer.flip();
        
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, uboId);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, uboId);
    }
}
```

**着色器使用**：
```glsl
#version 150

layout(std140) uniform SharedData {
    mat4 ViewMatrix;
    mat4 ProjMatrix;
    vec3 CameraPos;
};

void main() {
    gl_Position = ProjMatrix * ViewMatrix * vec4(Position, 1.0);
}
```

### 3. Shader Storage Buffer Objects (SSBO) ⚠️ 有条件推荐

**优势**：
- 可读写（与 TBO 不同）
- 更大的容量（GB 级别）
- 适合计算着色器

**限制**：
- 需要 OpenGL 4.3+
- 需要运行时检测

**推荐**：作为可选优化，降级到 TBO

### 4. 实例化渲染（Instanced Rendering）✅ 强烈推荐

**优势**：
- OpenGL 3.3+，广泛支持
- 显著减少 draw call
- 适合多个相同模型的场景

**实现**：
```java
public class InstancedMeshRenderer {
    private int instanceVBO = -1;
    
    public void render(AssimpMesh mesh, List<Matrix4f> transforms, MultiBufferSource buffer) {
        if (instanceVBO == -1) {
            instanceVBO = GL15.glGenBuffers();
        }
        
        // 上传实例变换矩阵
        FloatBuffer instanceData = BufferUtils.createFloatBuffer(transforms.size() * 16);
        for (Matrix4f transform : transforms) {
            transform.get(instanceData);
        }
        instanceData.flip();
        
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceData, GL15.GL_DYNAMIC_DRAW);
        
        // 配置实例化属性
        for (int i = 0; i < 4; i++) {
            int location = 5 + i; // 假设 location 5-8 用于实例矩阵
            GL20.glEnableVertexAttribArray(location);
            GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false, 64, i * 16);
            GL33.glVertexAttribDivisor(location, 1); // 每个实例一个值
        }
        
        // 实例化绘制
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, mesh.getIndexCount(), 
                                      GL11.GL_UNSIGNED_INT, 0, transforms.size());
    }
}
```

### 5. 多重绘制（Multi-Draw Indirect）⚠️ 高级优化

**优势**：
- 单次调用绘制多个不同的网格
- GPU 驱动的渲染（减少 CPU-GPU 同步）

**限制**：
- 需要 OpenGL 4.3+
- 实现复杂度高

**推荐**：作为长期优化目标

---

## 推荐的技术栈

### 立即实施（Phase 1-2）
1. ✅ **Texture Buffer Objects (TBO)** - 传输骨骼矩阵
2. ✅ **Uniform Buffer Objects (UBO)** - 共享渲染参数
3. ✅ **增强的顶点着色器** - GPU skinning
4. ✅ **VBO 缓存和批处理** - 减少 draw call

### 中期实施（Phase 3-4）
5. ✅ **实例化渲染** - 多实体优化
6. ⚠️ **计算着色器**（可选）- 如果硬件支持
7. ⚠️ **SSBO**（可选）- 如果硬件支持

### 长期优化（Phase 5）
8. ⚠️ **Multi-Draw Indirect** - 高级批处理
9. ⚠️ **GLSL-Transformer** - 动态着色器优化

---

## 兼容性测试计划

### 测试矩阵

| 配置 | OpenGL 版本 | Iris/Oculus | 预期结果 |
|------|-------------|-------------|----------|
| 最低配置 | 3.2 | 无 | TBO + UBO + 顶点着色器 |
| 推荐配置 | 4.5 | 无 | 全部特性 |
| Iris 1.7 | 4.5 | Iris | TBO + UBO + 顶点着色器 |
| Oculus 1.8 | 4.5 | Oculus | TBO + UBO + 顶点着色器 |
| 低端硬件 | 3.2 | 无 | 降级到 uniform 数组 |

### 测试步骤

1. **硬件检测**：
   ```java
   GLCapabilities caps = GL.getCapabilities();
   LOGGER.info("OpenGL Version: {}", GL11.glGetString(GL11.GL_VERSION));
   LOGGER.info("Compute Shader: {}", caps.GL_ARB_compute_shader);
   LOGGER.info("SSBO: {}", caps.OpenGL43);
   ```

2. **功能降级**：
   ```java
   if (caps.OpenGL43) {
       useComputeShader();
   } else if (caps.OpenGL31) {
       useTBO();
   } else {
       useUniformArray();
   }
   ```

3. **Iris 兼容性测试**：
   - 启用 Iris，加载基础着色器包
   - 渲染 Circle 方块，检查是否正常
   - 检查日志中的错误和警告

4. **性能基准测试**：
   - 测量每种配置的 FPS
   - 记录 GPU 利用率
   - 分析 draw call 数量

---

## 风险评估

### 高风险
- ❌ **计算着色器与 Iris 冲突** - 可能性：高，影响：严重
  - 缓解：提供降级方案，默认禁用

### 中风险
- ⚠️ **TBO 在某些驱动上不稳定** - 可能性：中，影响：中等
  - 缓解：运行时检测，降级到 uniform 数组

### 低风险
- ✅ **UBO 兼容性问题** - 可能性：低，影响：低
  - 缓解：OpenGL 3.1+ 广泛支持

---

## 结论

**推荐的实施路径**：

1. **Phase 1（立即）**：TBO + UBO + 增强顶点着色器
   - 风险：低
   - 收益：高（2-3x 性能提升）
   - 兼容性：优秀

2. **Phase 2（短期）**：实例化渲染
   - 风险：低
   - 收益：高（多实体场景 5-10x）
   - 兼容性：优秀

3. **Phase 3（中期）**：计算着色器（可选）
   - 风险：高
   - 收益：中（额外 1.5-2x）
   - 兼容性：需要检测和降级

4. **Phase 4（长期）**：GLSL-Transformer + Multi-Draw
   - 风险：中
   - 收益：中
   - 兼容性：需要测试

**关键原则**：
- ✅ 优先使用广泛支持的技术（TBO, UBO, 实例化）
- ✅ 提供完整的降级路径
- ✅ 运行时检测硬件能力
- ✅ 与 Iris/Oculus 和平共处
- ❌ 不依赖实验性特性
