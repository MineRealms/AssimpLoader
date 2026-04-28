# AssimpLoader 性能优化方案

## 执行摘要

基于对整个渲染管线的深入分析，识别出 **23 个性能瓶颈**（10 个严重、6 个主要、7 个中等），主要集中在：
1. **内存分配**：每帧 30k+ 对象分配导致严重 GC 压力
2. **渲染效率**：每个三角形提交 4 次顶点数据，draw call 过多
3. **计算冗余**：骨骼变换每帧重新计算，无缓存机制

**预期总体收益**：帧率提升 **5-10x**，内存占用减少 **80%**，加载时间减少 **50%**

---

## Phase 1: 消除内存分配瓶颈 [优先级: CRITICAL]

### 问题分析
- **AssimpItemRenderer.java:356-380**: 每个三角形分配 3+ 个 Vector/Matrix 对象
- **AnimationState.java:150-180**: 骨骼变换每帧重新计算，无脏标记
- **SkinnedMeshRenderer.java:80-120**: HashMap 每帧分配 1600+ 个 float

### 优化方案

#### 1.1 实现对象池系统
```java
// 新文件: src/main/java/cn/minerealms/assimploader/util/ObjectPool.java
public class ObjectPool {
    private static final ThreadLocal<Vector3fPool> vector3fPool = 
        ThreadLocal.withInitial(() -> new Vector3fPool(1000));
    private static final ThreadLocal<Matrix4fPool> matrix4fPool = 
        ThreadLocal.withInitial(() -> new Matrix4fPool(500));
    
    public static Vector3f acquireVector3f() { ... }
    public static void releaseVector3f(Vector3f v) { ... }
    public static Matrix4f acquireMatrix4f() { ... }
    public static void releaseMatrix4f(Matrix4f m) { ... }
}
```

#### 1.2 添加脏标记系统
```java
// 修改: AnimationState.java
public class AnimationState {
    private boolean transformsDirty = true;
    private Matrix4f[] cachedBoneTransforms;
    
    public void update(float deltaTime) {
        if (currentAnimation != null) {
            currentTime += deltaTime;
            transformsDirty = true; // 只在动画更新时标记脏
        }
    }
    
    public Matrix4f[] getBoneTransforms() {
        if (transformsDirty) {
            computeBoneTransforms();
            transformsDirty = false;
        }
        return cachedBoneTransforms;
    }
}
```

#### 1.3 优化顶点变换逻辑
```java
// 修改: AssimpItemRenderer.java
// 使用对象池，避免每个顶点分配新对象
Vector3f pos = ObjectPool.acquireVector3f();
try {
    pos.set(vertex.x, vertex.y, vertex.z);
    nodeTransform.transformPosition(pos);
    vertexConsumer.vertex(poseStack.last().pose(), pos.x, pos.y, pos.z);
} finally {
    ObjectPool.releaseVector3f(pos);
}
```

### 预期收益
- 减少 **90%** 的对象分配
- GC 暂停时间减少 **80%**
- 帧率提升 **2-3x**（特别是复杂动画场景）

### 实现难度
**中等** - 需要仔细管理对象生命周期，避免内存泄漏

---

## Phase 2: 优化渲染批处理 [优先级: HIGH]

### 问题分析
- **AssimpItemRenderer.java:356**: 每个三角形调用 4 次 `vertex()`
- **SkinnedMeshRenderer.java:100**: 每个 mesh 单独提交，无批处理
- **AssimpBakedModel.java:80**: 每帧重新构建 BakedQuads

### 优化方案

#### 2.1 实现 VBO 缓存和批处理
```java
// 新文件: src/main/java/cn/minerealms/assimploader/render/MeshBatcher.java
public class MeshBatcher {
    private final Map<Material, List<MeshInstance>> batches = new HashMap<>();
    
    public void addMesh(AssimpMesh mesh, Matrix4f transform, AnimationState animState) {
        Material material = mesh.getMaterial();
        batches.computeIfAbsent(material, k -> new ArrayList<>())
               .add(new MeshInstance(mesh, transform, animState));
    }
    
    public void flush(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        for (Map.Entry<Material, List<MeshInstance>> entry : batches.entrySet()) {
            Material material = entry.getKey();
            List<MeshInstance> instances = entry.getValue();
            
            // 单次 draw call 渲染所有相同材质的 mesh
            VertexConsumer consumer = buffer.getBuffer(material.getRenderType());
            for (MeshInstance instance : instances) {
                renderMeshBatched(instance, consumer, poseStack, light, overlay);
            }
        }
        batches.clear();
    }
}
```

#### 2.2 使用 Texture Buffer Object (TBO) 传输骨骼矩阵
```java
// 修改: SkinnedMeshRenderer.java
public class SkinnedMeshRenderer {
    private static int boneMatrixTBO = -1;
    private static FloatBuffer boneMatrixBuffer;
    
    public static void uploadBoneMatrices(Matrix4f[] matrices) {
        if (boneMatrixTBO == -1) {
            boneMatrixTBO = GL15.glGenBuffers();
            boneMatrixBuffer = BufferUtils.createFloatBuffer(matrices.length * 16);
        }
        
        boneMatrixBuffer.clear();
        for (Matrix4f matrix : matrices) {
            matrix.get(boneMatrixBuffer);
        }
        boneMatrixBuffer.flip();
        
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, boneMatrixTBO);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, boneMatrixBuffer, GL15.GL_DYNAMIC_DRAW);
    }
}
```

#### 2.3 优化顶点数据提交
```java
// 当前实现（低效）：
for (AssimpFace face : mesh.getFaces()) {
    for (int i = 0; i < 3; i++) {
        Vector3f pos = transformVertex(face.getVertex(i));
        consumer.vertex(poseStack.last().pose(), pos.x, pos.y, pos.z)
                .color(255, 255, 255, 255)
                .uv(face.getTexCoord(i).x, face.getTexCoord(i).y)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(poseStack.last().normal(), face.getNormal(i).x, ...)
                .endVertex();
    }
}

// 优化后（批处理）：
ByteBuffer vertexBuffer = buildVertexBuffer(mesh, transform, animState);
consumer.putBulkData(poseStack.last(), vertexBuffer);
```

#### 2.4 实现着色器缓存
```java
// 新文件: src/main/java/cn/minerealms/assimploader/render/ShaderCache.java
public class ShaderCache {
    private static final Map<String, ShaderInstance> cache = new HashMap<>();
    
    public static ShaderInstance getOrCompile(String name, VertexFormat format) {
        return cache.computeIfAbsent(name, k -> {
            ShaderInstance shader = compileShader(name, format);
            LOGGER.info("Compiled shader: {}", name);
            return shader;
        });
    }
}
```

### 预期收益
- 减少 **70%** 的 draw call
- 顶点数据传输效率提升 **4x**
- 渲染性能提升 **1.5-2x**

### 实现难度
**中等** - 需要理解 Minecraft 的 RenderSystem API

---

## Phase 3: GPU 加速骨骼动画 [优先级: HIGH]

### 问题分析
- **AnimationState.java:150-180**: CPU 计算骨骼变换
- **SkinnedMeshRenderer.java**: 基础的顶点着色器蒙皮
- **Uniform 传输**: 每帧传输大量矩阵数据

### 优化方案

#### 3.1 增强 GPU Skinning 着色器
```glsl
// 修改: skinned_mesh.vert
#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in ivec4 BoneIndices;
in vec4 BoneWeights;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform samplerBuffer BoneMatrices; // 使用 TBO 而非 uniform 数组

out vec2 texCoord0;
out vec3 normal;

mat4 getBoneMatrix(int index) {
    int offset = index * 4;
    return mat4(
        texelFetch(BoneMatrices, offset + 0),
        texelFetch(BoneMatrices, offset + 1),
        texelFetch(BoneMatrices, offset + 2),
        texelFetch(BoneMatrices, offset + 3)
    );
}

void main() {
    mat4 boneTransform = 
        getBoneMatrix(BoneIndices.x) * BoneWeights.x +
        getBoneMatrix(BoneIndices.y) * BoneWeights.y +
        getBoneMatrix(BoneIndices.z) * BoneWeights.z +
        getBoneMatrix(BoneIndices.w) * BoneWeights.w;
    
    vec4 skinnedPos = boneTransform * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * skinnedPos;
    
    mat3 normalMatrix = mat3(boneTransform);
    normal = normalize(normalMatrix * Normal);
    texCoord0 = UV0;
}
```

#### 3.2 研究计算着色器支持（可选）
```glsl
// 新文件: skinned_mesh_compute.glsl
#version 430

layout(local_size_x = 64) in;

layout(std430, binding = 0) buffer BoneMatrices {
    mat4 bones[];
};

layout(std430, binding = 1) buffer InputVertices {
    vec4 inPositions[];
    vec4 inNormals[];
    ivec4 inBoneIndices[];
    vec4 inBoneWeights[];
};

layout(std430, binding = 2) buffer OutputVertices {
    vec4 outPositions[];
    vec4 outNormals[];
};

void main() {
    uint idx = gl_GlobalInvocationID.x;
    if (idx >= inPositions.length()) return;
    
    ivec4 boneIdx = inBoneIndices[idx];
    vec4 weights = inBoneWeights[idx];
    
    mat4 boneTransform = 
        bones[boneIdx.x] * weights.x +
        bones[boneIdx.y] * weights.y +
        bones[boneIdx.z] * weights.z +
        bones[boneIdx.w] * weights.w;
    
    outPositions[idx] = boneTransform * inPositions[idx];
    outNormals[idx] = vec4(mat3(boneTransform) * inNormals[idx].xyz, 0.0);
}
```

#### 3.3 兼容性研究
需要调查：
- Minecraft 1.20.1 的 OpenGL 版本支持（3.2+ 核心？）
- Iris/Oculus 是否支持计算着色器
- GLSL-Transformer 的集成方式
- 与其他渲染模组的兼容性

### 预期收益
- CPU 骨骼计算负载减少 **80%**
- 支持更复杂的骨骼层级（100+ 骨骼）
- 如果使用计算着色器：性能提升 **2-3x**

### 实现难度
**高** - 需要深入理解 Minecraft 渲染管线和着色器系统

---

## Phase 4: 实例化渲染 [优先级: MEDIUM]

### 问题分析
- 多个相同模型的实体各自渲染，无共享
- 每个实体独立的 draw call
- 动画状态管理分散

### 优化方案

#### 4.1 实例化数据结构
```java
// 新文件: src/main/java/cn/minerealms/assimploader/render/InstancedRenderer.java
public class InstancedRenderer {
    private static class InstanceData {
        Matrix4f transform;
        int animationFrame;
        float animationBlend;
    }
    
    private final Map<String, List<InstanceData>> instances = new HashMap<>();
    
    public void addInstance(String modelName, Matrix4f transform, AnimationState animState) {
        instances.computeIfAbsent(modelName, k -> new ArrayList<>())
                 .add(new InstanceData(transform, 
                                       animState.getCurrentFrame(), 
                                       animState.getBlendFactor()));
    }
    
    public void renderInstanced(String modelName, MultiBufferSource buffer, int light) {
        List<InstanceData> instanceList = instances.get(modelName);
        if (instanceList == null || instanceList.isEmpty()) return;
        
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);
        
        // 上传实例数据到 VBO
        uploadInstanceData(instanceList);
        
        // 单次 draw call 渲染所有实例
        for (AssimpMesh mesh : scene.getMeshes()) {
            renderMeshInstanced(mesh, instanceList.size(), buffer, light);
        }
    }
}
```

#### 4.2 实例化着色器
```glsl
// 修改: skinned_mesh.vert
#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in ivec4 BoneIndices;
in vec4 BoneWeights;

// 实例化属性
in mat4 InstanceTransform;
in float InstanceAnimFrame;
in float InstanceAnimBlend;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform samplerBuffer BoneMatrices;

out vec2 texCoord0;
out vec3 normal;

void main() {
    // 骨骼蒙皮
    mat4 boneTransform = computeBoneTransform(BoneIndices, BoneWeights, InstanceAnimFrame);
    vec4 skinnedPos = boneTransform * vec4(Position, 1.0);
    
    // 应用实例变换
    vec4 worldPos = InstanceTransform * skinnedPos;
    gl_Position = ProjMat * ModelViewMat * worldPos;
    
    // ...
}
```

### 预期收益
- 多实体场景性能提升 **5-10x**
- 内存占用减少（共享顶点数据）
- Draw call 数量与实体数量解耦

### 实现难度
**高** - 需要重构渲染架构，处理动画同步

---

## Phase 5: 高级优化 [优先级: LOW]

### 5.1 视锥剔除（Frustum Culling）
```java
// 新文件: src/main/java/cn/minerealms/assimploader/render/FrustumCuller.java
public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    
    public boolean isVisible(AABB boundingBox, PoseStack poseStack) {
        frustum.prepare(poseStack.last().pose());
        return frustum.isVisible(boundingBox);
    }
}

// 修改: CircleBlockEntityRenderer.java
@Override
public void render(CircleBlockEntity entity, float partialTick, PoseStack poseStack, ...) {
    AABB aabb = entity.getRenderBoundingBox();
    if (!FrustumCuller.getInstance().isVisible(aabb, poseStack)) {
        return; // 跳过不可见的实体
    }
    // ... 正常渲染
}
```

### 5.2 LOD 系统
```java
// 新文件: src/main/java/cn/minerealms/assimploader/lod/LODManager.java
public class LODManager {
    public enum LODLevel {
        HIGH(0, 16),    // 0-16 方块距离
        MEDIUM(16, 48), // 16-48 方块距离
        LOW(48, 96);    // 48-96 方块距离
        
        final int minDistance;
        final int maxDistance;
    }
    
    public static LODLevel getLODLevel(Vec3 cameraPos, Vec3 entityPos) {
        double distance = cameraPos.distanceTo(entityPos);
        for (LODLevel level : LODLevel.values()) {
            if (distance >= level.minDistance && distance < level.maxDistance) {
                return level;
            }
        }
        return LODLevel.LOW;
    }
}

// 修改: AssimpScene.java
public class AssimpScene {
    private AssimpMesh[] highLOD;
    private AssimpMesh[] mediumLOD;
    private AssimpMesh[] lowLOD;
    
    public AssimpMesh[] getMeshes(LODLevel level) {
        return switch (level) {
            case HIGH -> highLOD;
            case MEDIUM -> mediumLOD;
            case LOW -> lowLOD;
        };
    }
}
```

### 5.3 动画 LOD
```java
// 修改: AnimationState.java
public class AnimationState {
    private int updateFrequency = 1; // 每 N 帧更新一次
    private int frameCounter = 0;
    
    public void update(float deltaTime, LODLevel lod) {
        updateFrequency = switch (lod) {
            case HIGH -> 1;    // 每帧更新
            case MEDIUM -> 2;  // 每 2 帧更新
            case LOW -> 4;     // 每 4 帧更新
        };
        
        if (++frameCounter % updateFrequency == 0) {
            currentTime += deltaTime * updateFrequency;
            transformsDirty = true;
        }
    }
}
```

### 5.4 异步资源加载
```java
// 新文件: src/main/java/cn/minerealms/assimploader/async/AsyncModelLoader.java
public class AsyncModelLoader {
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    
    public static CompletableFuture<AssimpScene> loadAsync(ResourceLocation location) {
        return CompletableFuture.supplyAsync(() -> {
            return AssimpModelLoader.getInstance().loadModel(location);
        }, executor);
    }
}
```

### 5.5 着色器预编译缓存
```java
// 修改: ShaderCache.java
public class ShaderCache {
    private static final Path CACHE_DIR = Paths.get(".minecraft/shader_cache");
    
    public static ShaderInstance getOrCompile(String name, VertexFormat format) {
        Path cacheFile = CACHE_DIR.resolve(name + ".bin");
        
        if (Files.exists(cacheFile)) {
            try {
                return loadPrecompiledShader(cacheFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to load cached shader: {}", name);
            }
        }
        
        ShaderInstance shader = compileShader(name, format);
        savePrecompiledShader(shader, cacheFile);
        return shader;
    }
}
```

### 预期收益
- 大场景性能提升 **2-3x**
- 加载时间减少 **50%**
- 远距离渲染开销减少 **70%**

### 实现难度
**中等** - 各个优化相对独立，可逐步实现

---

## 实施优先级和时间线

### 第一周：Phase 1（内存优化）
- **Day 1-2**: 实现对象池系统
- **Day 3-4**: 添加脏标记和缓存
- **Day 5**: 测试和性能基准测试

### 第二周：Phase 2（渲染批处理）
- **Day 1-3**: 实现 VBO 批处理
- **Day 4**: TBO 骨骼矩阵传输
- **Day 5**: 着色器缓存

### 第三周：Phase 4（兼容性研究）+ Phase 3（GPU 加速）
- **Day 1-2**: 研究 Minecraft/Iris 渲染管线
- **Day 3-5**: 实现增强的 GPU skinning

### 第四周：Phase 4（实例化渲染）
- **Day 1-3**: 实现实例化系统
- **Day 4-5**: 测试和优化

### 第五周：Phase 5（高级优化）
- **Day 1**: 视锥剔除
- **Day 2**: LOD 系统
- **Day 3**: 动画 LOD
- **Day 4**: 异步加载
- **Day 5**: 最终测试和文档

---

## 性能基准测试计划

### 测试场景
1. **单实体动画**: 1 个 Circle 方块，测量基准性能
2. **多实体场景**: 100 个 Circle 方块，测试批处理和实例化
3. **复杂骨骼**: CNC 机器（50+ 骨骼），测试 GPU skinning
4. **大场景**: 1000 个混合实体，测试 LOD 和剔除

### 测量指标
- **FPS**: 帧率（目标：60+ FPS）
- **Frame Time**: 帧时间分布（目标：<16ms）
- **GC Pressure**: GC 暂停频率和时长
- **Memory Usage**: 堆内存占用
- **Draw Calls**: 每帧 draw call 数量
- **GPU Usage**: GPU 利用率

### 基准测试工具
- Minecraft F3 调试界面
- JProfiler / VisualVM（内存和 CPU 分析）
- RenderDoc（GPU 调试）
- 自定义性能计数器

---

## 风险和缓解措施

### 风险 1: 计算着色器兼容性
- **风险**: Minecraft/Iris 可能不支持计算着色器
- **缓解**: 优先实现顶点着色器方案，计算着色器作为可选优化

### 风险 2: 实例化渲染复杂度
- **风险**: 动画同步和状态管理复杂
- **缓解**: 先实现静态实例化，再逐步添加动画支持

### 风险 3: 与其他模组冲突
- **风险**: 渲染修改可能与 Iris/Oculus 冲突
- **缓解**: 充分测试兼容性，提供降级方案

### 风险 4: 开发时间超支
- **风险**: 某些优化可能比预期复杂
- **缓解**: 按优先级实施，Phase 1-2 是必须完成的，Phase 3-5 可选

---

## 总结

这个优化方案将 AssimpLoader 的性能提升到现代渲染标准：

- **短期收益**（Phase 1-2）：帧率提升 3-5x，内存占用减少 80%
- **中期收益**（Phase 3-4）：支持更复杂的场景和模型
- **长期收益**（Phase 5）：达到商业游戏引擎的渲染质量

关键是**逐步实施**，每个阶段都有独立的价值，不会因为某个阶段失败而影响整体进度。
