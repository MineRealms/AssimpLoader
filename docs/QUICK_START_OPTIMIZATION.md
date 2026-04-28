# 性能优化快速参考

## 🎯 核心问题

### 当前性能瓶颈
- **FPS**: 15 FPS（100 个实体场景）
- **内存分配**: 30,000+ 对象/帧
- **GC 暂停**: 每秒 5 次
- **Draw Calls**: 300+ 每帧

### 目标性能
- **FPS**: 150+ FPS（100 个实体场景）
- **内存分配**: 3,000 对象/帧（-90%）
- **GC 暂停**: 每秒 0.5 次（-90%）
- **Draw Calls**: 30-50 每帧（-70%）

---

## 📋 优化阶段速览

| Phase | 时间 | 优先级 | 预期收益 | 关键技术 |
|-------|------|--------|----------|----------|
| Phase 1 | 第 1 周 | CRITICAL | 2-3x FPS | 对象池 + 脏标记 |
| Phase 2 | 第 2 周 | HIGH | 1.5-2x FPS | VBO 批处理 + TBO |
| Phase 3 | 第 3 周 | HIGH | CPU -80% | GPU skinning |
| Phase 4 | 第 4 周 | MEDIUM | 5-10x (多实体) | 实例化渲染 |
| Phase 5 | 第 5 周 | LOW | 2-3x (大场景) | 剔除 + LOD |

---

## 🔧 Phase 1: 对象池（第 1 周）

### 新增文件
```
src/main/java/cn/minerealms/assimploader/util/ObjectPool.java
```

### 修改文件
```
src/main/java/cn/minerealms/assimploader/animation/AnimationState.java
src/main/java/cn/minerealms/assimploader/render/AssimpItemRenderer.java
```

### 关键代码
```java
// 使用对象池
Vector3f pos = ObjectPool.acquireVector3f();
try {
    pos.set(x, y, z);
    // 使用 pos
} finally {
    ObjectPool.releaseVector3f(pos);
}

// 脏标记
if (transformsDirty) {
    computeBoneTransforms();
    transformsDirty = false;
}
```

### 测试验证
- 放置 100 个 Circle 方块
- 测量 FPS（预期：15 → 40）
- 使用 JProfiler 检查 GC 行为

---

## 🔧 Phase 2: 渲染批处理（第 2 周）

### 新增文件
```
src/main/java/cn/minerealms/assimploader/render/MeshBatcher.java
src/main/java/cn/minerealms/assimploader/render/BoneMatrixTBO.java
src/main/java/cn/minerealms/assimploader/render/ShaderCache.java
```

### 修改文件
```
src/main/resources/assets/assimploader/shaders/skinned_mesh.vert
```

### 关键代码
```java
// TBO 上传骨骼矩阵
BoneMatrixTBO tbo = new BoneMatrixTBO();
tbo.upload(boneMatrices);
tbo.bind(0); // 绑定到纹理单元 0

// 着色器中访问
uniform samplerBuffer BoneMatrices;
mat4 bone = getBoneMatrix(boneIndex);
```

### 测试验证
- 测量 draw call 数量（预期：300 → 100）
- 测量 FPS（预期：40 → 60）

---

## 🔧 Phase 3: GPU 加速（第 3 周）

### 新增文件
```
src/main/java/cn/minerealms/assimploader/render/GLCapabilityDetector.java
src/main/java/cn/minerealms/assimploader/render/ComputeShaderProcessor.java (可选)
src/main/resources/assets/assimploader/shaders/skinned_mesh_compute.glsl (可选)
```

### 关键代码
```java
// 硬件检测
if (GLCapabilityDetector.isComputeShaderSupported()) {
    useComputeShader();
} else {
    useVertexShader();
}
```

### 测试验证
- 测量 CPU 使用率（预期：-80%）
- 测试 10 个 CNC 机器（50+ 骨骼）

---

## 🔧 Phase 4: 实例化渲染（第 4 周）

### 新增文件
```
src/main/java/cn/minerealms/assimploader/render/InstancedRenderer.java
```

### 关键代码
```java
// 实例化绘制
GL31.glDrawElementsInstanced(
    GL11.GL_TRIANGLES, 
    indexCount, 
    GL11.GL_UNSIGNED_INT, 
    0, 
    instanceCount
);
```

### 测试验证
- 放置 100 个相同模型
- 测量 FPS（预期：60 → 150）

---

## 🔧 Phase 5: 高级优化（第 5 周）

### 新增文件
```
src/main/java/cn/minerealms/assimploader/render/FrustumCuller.java
src/main/java/cn/minerealms/assimploader/lod/LODManager.java
src/main/java/cn/minerealms/assimploader/async/AsyncModelLoader.java
```

### 关键代码
```java
// 视锥剔除
if (!FrustumCuller.isVisible(aabb, poseStack)) {
    return; // 跳过不可见实体
}

// LOD 选择
LODLevel lod = LODManager.getLODLevel(cameraPos, entityPos);
AssimpMesh[] meshes = scene.getMeshes(lod);
```

### 测试验证
- 测试 1000 个实体场景
- 测量 FPS（预期：80 → 100+）

---

## 🎮 兼容性矩阵

| OpenGL 版本 | TBO | UBO | SSBO | 计算着色器 | 实例化 |
|-------------|-----|-----|------|-----------|--------|
| 3.0 | ✅ | ❌ | ❌ | ❌ | ❌ |
| 3.1 | ✅ | ✅ | ❌ | ❌ | ❌ |
| 3.3 | ✅ | ✅ | ❌ | ❌ | ✅ |
| 4.3+ | ✅ | ✅ | ✅ | ✅ | ✅ |

### 降级策略
```
OpenGL 4.3+ → 全部特性
    ↓
OpenGL 3.3+ → TBO + UBO + 实例化
    ↓
OpenGL 3.1+ → TBO + UBO
    ↓
OpenGL 3.0+ → TBO only
    ↓
OpenGL 2.1 → Uniform 数组（最低配置）
```

---

## 📊 性能基准测试

### 测试场景
1. **单实体**: 1 个 Circle
2. **多实体**: 100 个 Circle
3. **复杂骨骼**: 10 个 CNC（50+ 骨骼）
4. **混合场景**: 50 Circle + 20 CNC + 30 CCB
5. **压力测试**: 1000 个混合实体

### 预期结果

| 场景 | 当前 | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
|------|------|---------|---------|---------|---------|---------|
| 单实体 | 60 | 120 | 150 | 180 | 180 | 200 |
| 多实体 | 15 | 40 | 60 | 80 | 150 | 180 |
| 复杂骨骼 | 25 | 60 | 80 | 120 | 120 | 150 |
| 混合场景 | 20 | 50 | 70 | 100 | 140 | 160 |
| 压力测试 | 5 | 15 | 25 | 40 | 80 | 100 |

---

## 🐛 常见问题

### Q: 如果某个 Phase 失败怎么办？
A: 每个 Phase 都是独立的，可以跳过。使用 Git 回滚到上一个稳定版本。

### Q: 如何测试兼容性？
A: 运行 `GLCapabilityDetector.detect()` 查看硬件支持的特性。

### Q: Iris/Oculus 冲突怎么办？
A: 优先使用 Iris API，如果冲突则降级到基础实现。

### Q: 如何启用/禁用某个优化？
A: 在配置文件中添加开关（待实现）。

---

## 📚 详细文档

- **完整方案**: `docs/PERFORMANCE_OPTIMIZATION_PLAN.md`
- **GPU 研究**: `docs/GPU_ACCELERATION_RESEARCH.md`
- **实施路线图**: `docs/IMPLEMENTATION_ROADMAP.md`
- **总结**: `docs/OPTIMIZATION_SUMMARY.md`

---

## 🚀 立即开始

### 第 1 步：创建 ObjectPool.java
```bash
# 复制模板代码到
src/main/java/cn/minerealms/assimploader/util/ObjectPool.java
```

### 第 2 步：修改 AnimationState.java
```java
// 添加脏标记
private boolean transformsDirty = true;
```

### 第 3 步：测试
```bash
./gradlew build
# 启动游戏，放置 100 个 Circle 方块
# 按 F3 查看 FPS
```

---

## 📞 需要帮助？

- 查看详细文档: `docs/` 目录
- 检查代码示例: `IMPLEMENTATION_ROADMAP.md`
- 性能问题: 运行基准测试并对比预期结果
