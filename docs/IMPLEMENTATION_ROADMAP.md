# AssimpLoader 性能优化实施路线图

## 概览

本文档提供了详细的实施步骤，将性能优化方案分解为可执行的任务。

---

## Phase 1: 消除内存分配瓶颈（第 1 周）

### 目标
减少 90% 的对象分配，GC 暂停时间减少 80%，帧率提升 2-3x

### Task 1.1: 创建对象池系统（Day 1-2）

**文件**：`src/main/java/cn/minerealms/assimploader/util/ObjectPool.java`

```java
package cn.minerealms.assimploader.util;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.Queue;

public class ObjectPool {
    private static final ThreadLocal<Vector3fPool> vector3fPool = 
        ThreadLocal.withInitial(() -> new Vector3fPool(1000));
    private static final ThreadLocal<Vector4fPool> vector4fPool = 
        ThreadLocal.withInitial(() -> new Vector4fPool(500));
    private static final ThreadLocal<Matrix4fPool> matrix4fPool = 
        ThreadLocal.withInitial(() -> new Matrix4fPool(500));
    
    // Vector3f Pool
    public static Vector3f acquireVector3f() {
        return vector3fPool.get().acquire();
    }
    
    public static void releaseVector3f(Vector3f v) {
        vector3fPool.get().release(v);
    }
    
    // Vector4f Pool
    public static Vector4f acquireVector4f() {
        return vector4fPool.get().acquire();
    }
    
    public static void releaseVector4f(Vector4f v) {
        vector4fPool.get().release(v);
    }
    
    // Matrix4f Pool
    public static Matrix4f acquireMatrix4f() {
        return matrix4fPool.get().acquire();
    }
    
    public static void releaseMatrix4f(Matrix4f m) {
        matrix4fPool.get().release(m);
    }
    
    // Pool implementations
    private static class Vector3fPool {
        private final Queue<Vector3f> pool;
        private final int maxSize;
        
        Vector3fPool(int maxSize) {
            this.maxSize = maxSize;
            this.pool = new ArrayDeque<>(maxSize);
        }
        
        Vector3f acquire() {
            Vector3f v = pool.poll();
            return v != null ? v : new Vector3f();
        }
        
        void release(Vector3f v) {
            if (pool.size() < maxSize) {
                v.zero();
                pool.offer(v);
            }
        }
    }
    
    private static class Vector4fPool {
        private final Queue<Vector4f> pool;
        private final int maxSize;
        
        Vector4fPool(int maxSize) {
            this.maxSize = maxSize;
            this.pool = new ArrayDeque<>(maxSize);
        }
        
        Vector4f acquire() {
            Vector4f v = pool.poll();
            return v != null ? v : new Vector4f();
        }
        
        void release(Vector4f v) {
            if (pool.size() < maxSize) {
                v.zero();
                pool.offer(v);
            }
        }
    }
    
    private static class Matrix4fPool {
        private final Queue<Matrix4f> pool;
        private final int maxSize;
        
        Matrix4fPool(int maxSize) {
            this.maxSize = maxSize;
            this.pool = new ArrayDeque<>(maxSize);
        }
        
        Matrix4f acquire() {
            Matrix4f m = pool.poll();
            return m != null ? m : new Matrix4f();
        }
        
        void release(Matrix4f m) {
            if (pool.size() < maxSize) {
                m.identity();
                pool.offer(m);
            }
        }
    }
    
    // Cleanup method (call on world unload)
    public static void clear() {
        vector3fPool.remove();
        vector4fPool.remove();
        matrix4fPool.remove();
    }
}
```

**测试**：
```java
// 添加到测试类
@Test
public void testObjectPoolPerformance() {
    long start = System.nanoTime();
    
    // 无对象池：分配 10000 个 Vector3f
    for (int i = 0; i < 10000; i++) {
        Vector3f v = new Vector3f(1, 2, 3);
    }
    
    long withoutPool = System.nanoTime() - start;
    
    start = System.nanoTime();
    
    // 有对象池：复用 Vector3f
    for (int i = 0; i < 10000; i++) {
        Vector3f v = ObjectPool.acquireVector3f();
        v.set(1, 2, 3);
        ObjectPool.releaseVector3f(v);
    }
    
    long withPool = System.nanoTime() - start;
    
    System.out.println("Without pool: " + withoutPool / 1000000.0 + "ms");
    System.out.println("With pool: " + withPool / 1000000.0 + "ms");
    System.out.println("Speedup: " + (double)withoutPool / withPool + "x");
}
```

### Task 1.2: 添加脏标记系统（Day 3）

**修改文件**：`src/main/java/cn/minerealms/assimploader/animation/AnimationState.java`

```java
public class AnimationState {
    // 添加字段
    private boolean transformsDirty = true;
    private Matrix4f[] cachedBoneTransforms;
    private Map<String, Matrix4f> cachedNodeTransforms;
    
    // 修改 update 方法
    public void update(float deltaTime) {
        if (currentAnimation == null || !initialized) {
            return;
        }
        
        currentTime += deltaTime;
        
        // 循环播放
        if (looping && currentTime > currentAnimation.getDuration()) {
            currentTime = currentTime % currentAnimation.getDuration();
        }
        
        // 标记为脏，需要重新计算
        transformsDirty = true;
    }
    
    // 修改 getBoneTransforms 方法
    public Matrix4f[] getBoneTransforms() {
        if (transformsDirty) {
            computeBoneTransforms();
            transformsDirty = false;
        }
        return cachedBoneTransforms;
    }
    
    // 修改 getNodeTransforms 方法
    public Map<String, Matrix4f> getNodeTransforms() {
        if (transformsDirty) {
            computeNodeTransforms();
            transformsDirty = false;
        }
        return cachedNodeTransforms;
    }
    
    // 添加方法：强制重新计算（用于热重载）
    public void invalidateCache() {
        transformsDirty = true;
    }
}
```

### Task 1.3: 优化 AssimpItemRenderer（Day 4）

**修改文件**：`src/main/java/cn/minerealms/assimploader/render/AssimpItemRenderer.java`

在 `renderNodeAnimatedMesh` 方法中使用对象池：

```java
private void renderNodeAnimatedMesh(AssimpMesh mesh, Matrix4f nodeTransform, 
                                    PoseStack poseStack, MultiBufferSource buffer, 
                                    int light, int overlay) {
    VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
    
    for (AssimpFace face : mesh.getFaces()) {
        for (int i = 0; i < 3; i++) {
            AssimpVertex vertex = face.getVertex(i);
            
            // 使用对象池
            Vector3f pos = ObjectPool.acquireVector3f();
            Vector3f normal = ObjectPool.acquireVector3f();
            
            try {
                // 变换顶点位置
                pos.set(vertex.getPosition().x, vertex.getPosition().y, vertex.getPosition().z);
                nodeTransform.transformPosition(pos);
                
                // 变换法线
                normal.set(vertex.getNormal().x, vertex.getNormal().y, vertex.getNormal().z);
                nodeTransform.transformDirection(normal);
                normal.normalize();
                
                // 提交顶点
                consumer.vertex(poseStack.last().pose(), pos.x, pos.y, pos.z)
                        .color(255, 255, 255, 255)
                        .uv(vertex.getTexCoord().x, vertex.getTexCoord().y)
                        .overlayCoords(overlay)
                        .uv2(light)
                        .normal(poseStack.last().normal(), normal.x, normal.y, normal.z)
                        .endVertex();
            } finally {
                // 释放回对象池
                ObjectPool.releaseVector3f(pos);
                ObjectPool.releaseVector3f(normal);
            }
        }
    }
}
```

### Task 1.4: 性能测试（Day 5）

创建性能测试场景：
1. 放置 100 个 Circle 方块
2. 测量 FPS 和内存使用
3. 使用 JProfiler 分析 GC 行为
4. 记录基准数据

**预期结果**：
- FPS 从 ~30 提升到 ~80
- GC 暂停从每秒 5 次减少到每秒 1 次
- 堆内存分配速率减少 90%

---

## Phase 2: 优化渲染批处理（第 2 周）

### 目标
减少 70% 的 draw call，渲染性能提升 1.5-2x

### Task 2.1: 实现 MeshBatcher（Day 1-2）

**新文件**：`src/main/java/cn/minerealms/assimploader/render/MeshBatcher.java`

```java
package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.animation.AnimationState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.*;

public class MeshBatcher {
    private static class MeshInstance {
        final AssimpMesh mesh;
        final Matrix4f transform;
        final AnimationState animState;
        
        MeshInstance(AssimpMesh mesh, Matrix4f transform, AnimationState animState) {
            this.mesh = mesh;
            this.transform = transform;
            this.animState = animState;
        }
    }
    
    private final Map<RenderType, List<MeshInstance>> batches = new HashMap<>();
    
    public void addMesh(AssimpMesh mesh, Matrix4f transform, AnimationState animState) {
        RenderType renderType = getRenderType(mesh);
        batches.computeIfAbsent(renderType, k -> new ArrayList<>())
               .add(new MeshInstance(mesh, transform, animState));
    }
    
    public void flush(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        for (Map.Entry<RenderType, List<MeshInstance>> entry : batches.entrySet()) {
            RenderType renderType = entry.getKey();
            List<MeshInstance> instances = entry.getValue();
            
            VertexConsumer consumer = buffer.getBuffer(renderType);
            
            for (MeshInstance instance : instances) {
                renderMeshBatched(instance, consumer, poseStack, light, overlay);
            }
        }
        
        batches.clear();
    }
    
    private void renderMeshBatched(MeshInstance instance, VertexConsumer consumer,
                                   PoseStack poseStack, int light, int overlay) {
        // 实现批处理渲染逻辑
        // 类似 AssimpItemRenderer 但直接写入 consumer
    }
    
    private RenderType getRenderType(AssimpMesh mesh) {
        // 根据材质属性返回 RenderType
        return RenderType.solid();
    }
}
```

### Task 2.2: 实现 TBO 骨骼矩阵传输（Day 3）

**新文件**：`src/main/java/cn/minerealms/assimploader/render/BoneMatrixTBO.java`

```java
package cn.minerealms.assimploader.render;

import com.mojang.blaze3d.platform.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

public class BoneMatrixTBO {
    private int tboId = -1;
    private int textureId = -1;
    private FloatBuffer buffer;
    private int capacity = 0;
    
    public void upload(Matrix4f[] matrices) {
        if (matrices.length == 0) return;
        
        // 初始化或扩容
        if (tboId == -1 || capacity < matrices.length) {
            if (tboId != -1) {
                cleanup();
            }
            
            capacity = matrices.length;
            tboId = GL15.glGenBuffers();
            textureId = GL11.glGenTextures();
            buffer = BufferUtils.createFloatBuffer(capacity * 16);
        }
        
        // 上传矩阵数据
        buffer.clear();
        for (Matrix4f matrix : matrices) {
            matrix.get(buffer);
        }
        buffer.flip();
        
        // 绑定并上传到 TBO
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        
        // 绑定为纹理
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, tboId);
    }
    
    public void bind(int textureUnit) {
        if (textureId == -1) return;
        
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
    }
    
    public void cleanup() {
        if (tboId != -1) {
            GL15.glDeleteBuffers(tboId);
            tboId = -1;
        }
        if (textureId != -1) {
            GL11.glDeleteTextures(textureId);
            textureId = -1;
        }
    }
}
```

**修改着色器**：`src/main/resources/assets/assimploader/shaders/skinned_mesh.vert`

```glsl
#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in ivec4 BoneIndices;
in vec4 BoneWeights;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform samplerBuffer BoneMatrices; // 使用 TBO

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

### Task 2.3: 实现着色器缓存（Day 4）

**新文件**：`src/main/java/cn/minerealms/assimploader/render/ShaderCache.java`

```java
package cn.minerealms.assimploader.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ShaderCache {
    private static final Map<ResourceLocation, ShaderInstance> cache = new HashMap<>();
    
    public static ShaderInstance getOrLoad(ResourceLocation location) {
        return cache.computeIfAbsent(location, loc -> {
            // 加载着色器
            ShaderInstance shader = loadShader(loc);
            AssimpLoader.LOGGER.info("Loaded and cached shader: {}", loc);
            return shader;
        });
    }
    
    public static void clear() {
        cache.values().forEach(ShaderInstance::close);
        cache.clear();
    }
    
    private static ShaderInstance loadShader(ResourceLocation location) {
        // 实现着色器加载逻辑
        return null; // TODO
    }
}
```

### Task 2.4: 集成和测试（Day 5）

修改渲染器使用新的批处理系统，测试性能提升。

---

## Phase 3: GPU 加速骨骼动画（第 3 周）

### Task 3.1: 硬件能力检测（Day 1）

**新文件**：`src/main/java/cn/minerealms/assimploader/render/GLCapabilityDetector.java`

```java
package cn.minerealms.assimploader.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;

public class GLCapabilityDetector {
    private static Boolean computeShaderSupported = null;
    private static Boolean ssboSupported = null;
    private static Boolean tboSupported = null;
    
    public static void detect() {
        GLCapabilities caps = GL.getCapabilities();
        
        String version = GL11.glGetString(GL11.GL_VERSION);
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        
        computeShaderSupported = caps.OpenGL43 && caps.GL_ARB_compute_shader;
        ssboSupported = caps.OpenGL43;
        tboSupported = caps.OpenGL31 && caps.GL_ARB_texture_buffer_object;
        
        AssimpLoader.LOGGER.info("=== GPU Capabilities ===");
        AssimpLoader.LOGGER.info("OpenGL Version: {}", version);
        AssimpLoader.LOGGER.info("Renderer: {}", renderer);
        AssimpLoader.LOGGER.info("Compute Shader: {}", computeShaderSupported);
        AssimpLoader.LOGGER.info("SSBO: {}", ssboSupported);
        AssimpLoader.LOGGER.info("TBO: {}", tboSupported);
        
        if (computeShaderSupported) {
            int maxWorkGroupSize = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_SIZE);
            AssimpLoader.LOGGER.info("Max Compute Work Group Size: {}", maxWorkGroupSize);
        }
    }
    
    public static boolean isComputeShaderSupported() {
        if (computeShaderSupported == null) detect();
        return computeShaderSupported;
    }
    
    public static boolean isSSBOSupported() {
        if (ssboSupported == null) detect();
        return ssboSupported;
    }
    
    public static boolean isTBOSupported() {
        if (tboSupported == null) detect();
        return tboSupported;
    }
}
```

### Task 3.2-3.5: 实现计算着色器（Day 2-5）

详细实现见 `GPU_ACCELERATION_RESEARCH.md`

---

## Phase 4: 实例化渲染（第 4 周）

详细实现见 `PERFORMANCE_OPTIMIZATION_PLAN.md` Phase 4

---

## Phase 5: 高级优化（第 5 周）

详细实现见 `PERFORMANCE_OPTIMIZATION_PLAN.md` Phase 5

---

## 性能基准测试

### 测试场景

1. **单实体基准**：1 个 Circle 方块
2. **多实体场景**：100 个 Circle 方块
3. **复杂骨骼**：10 个 CNC 机器（50+ 骨骼）
4. **混合场景**：50 Circle + 20 CNC + 30 CCB

### 测量指标

| 指标 | 当前 | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
|------|------|---------|---------|---------|---------|---------|
| FPS (单实体) | 60 | 120 | 150 | 180 | 180 | 200 |
| FPS (100 实体) | 15 | 40 | 60 | 80 | 150 | 180 |
| GC 暂停/秒 | 5 | 1 | 0.5 | 0.5 | 0.3 | 0.2 |
| 内存分配 MB/s | 500 | 50 | 30 | 20 | 15 | 10 |
| Draw Calls | 300 | 300 | 100 | 100 | 10 | 5 |

---

## 提交和文档

每个 Phase 完成后：
1. 提交代码到 Git
2. 更新 CHANGELOG.md
3. 记录性能测试结果
4. 更新用户文档

---

## 回滚计划

如果某个优化导致问题：
1. 使用 Git 回滚到上一个稳定版本
2. 分析失败原因
3. 修复后重新实施
4. 所有优化都有降级开关（通过配置文件）
