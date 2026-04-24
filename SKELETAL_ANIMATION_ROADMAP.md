# 骨骼动画系统实现路线图

## 概述

为 AssimpLoader 添加完整的骨骼动画支持，包括蒙皮动画、关键帧播放和 IK 系统。

## 依赖库

### 已有
- ✅ LWJGL Assimp - 读取 FBX 骨骼/动画数据
- ✅ JOML - 矩阵和四元数运算

### 需要添加
```gradle
// IK 求解器
implementation 'au.edu.federation.caliko:caliko:1.3.8'

// 可选：高级插值
implementation 'org.apache.commons:commons-math3:3.6.1'
```

## 实现阶段

### 阶段 1: 骨骼蒙皮动画（核心）
**工作量**: 2-3 周 | **难度**: ⭐⭐⭐⭐

#### 核心组件
1. **骨骼系统**
   - `Bone.java` - 骨骼节点（名称、ID、偏移矩阵、父子关系）
   - `Skeleton.java` - 骨骼树管理器
   - 从 Assimp 读取 `AIBone` 和 `AIVertexWeight`

2. **蒙皮网格**
   - `SkinnedVertex.java` - 扩展顶点格式（添加 boneIds[4] 和 weights[4]）
   - `SkinnedMesh.java` - 支持骨骼权重的网格
   - 顶点缓冲区布局更新

3. **蒙皮着色器**
   ```glsl
   // vertex shader 核心逻辑
   mat4 boneTransform = 
       boneMatrices[boneIds[0]] * boneWeights[0] +
       boneMatrices[boneIds[1]] * boneWeights[1] +
       boneMatrices[boneIds[2]] * boneWeights[2] +
       boneMatrices[boneIds[3]] * boneWeights[3];
   
   vec4 skinnedPosition = boneTransform * vec4(position, 1.0);
   ```

4. **渲染器集成**
   - 扩展 `AssimpBlockEntityRenderer` 和 `AssimpItemRenderer`
   - 传递骨骼矩阵数组到着色器（uniform mat4 boneMatrices[100]）
   - 每帧更新骨骼变换

#### 关键挑战
- Minecraft 渲染管线的着色器集成
- 顶点格式扩展（需要修改 VertexConsumer）
- 骨骼矩阵传递到 GPU（可能需要 UBO）

---

### 阶段 2: 关键帧动画播放
**工作量**: 1-2 周 | **难度**: ⭐⭐⭐

#### 核心组件
1. **动画数据结构**
   - `Animation.java` - 动画容器（名称、时长、帧率）
   - `NodeAnimation.java` - 单个骨骼的动画通道
   - `VectorKey` / `QuaternionKey` - 关键帧数据

2. **动画加载**
   - 从 Assimp 读取 `AIAnimation` 和 `AINodeAnim`
   - 提取位置/旋转/缩放关键帧（`AIVectorKey`, `AIQuatKey`）

3. **动画播放器**
   - `AnimationPlayer.java` - 时间轴控制（播放/暂停/循环）
   - 关键帧插值：
     * 位置/缩放：线性插值（lerp）
     * 旋转：球面线性插值（slerp）
   - 输出每帧的骨骼变换矩阵

4. **骨骼矩阵更新**
   ```java
   // 伪代码
   for each bone:
       nodeTransform = interpolate(animation, currentTime, bone.name)
       parentTransform = bone.parent ? bone.parent.finalTransform : identity
       bone.finalTransform = parentTransform * nodeTransform * bone.offsetMatrix
   ```

#### 关键挑战
- 插值算法正确性（特别是四元数 slerp）
- 动画循环和过渡混合
- 性能优化（缓存计算结果）

---

### 阶段 3: IK 系统
**工作量**: 1 周 | **难度**: ⭐⭐

#### 核心组件
1. **IK 链管理**
   - `IKChainManager.java` - 管理多个 IK 链
   - 从骨骼树构建 Caliko 的 `FabrikChain3D`
   - 支持关节约束（铰链、球形关节、角度限制）

2. **IK 求解**
   ```java
   // 使用 Caliko
   FabrikChain3D chain = new FabrikChain3D();
   chain.addBone(new FabrikBone3D(start, end));
   chain.solveForTarget(targetPosition);
   ```

3. **IK/FK 混合**
   - `AnimationBlender.java` - 混合 IK 和关键帧动画
   - 使用 slerp 在 IK 旋转和 FK 旋转之间插值
   - 支持权重控制（0.0 = 纯 FK，1.0 = 纯 IK）

4. **应用到骨骼**
   - 从 Caliko 结果提取骨骼旋转
   - 更新骨骼变换矩阵
   - 传递到蒙皮着色器

#### 关键挑战
- Caliko 坐标系与 Minecraft 坐标系转换
- IK 链的根节点固定
- 实时求解性能

---

## 文件结构

```
src/main/java/cn/minerealms/assimploader/
├── animation/
│   ├── Animation.java
│   ├── NodeAnimation.java
│   ├── AnimationPlayer.java
│   ├── KeyframeInterpolator.java
│   └── AnimationBlender.java
├── skeleton/
│   ├── Bone.java
│   ├── Skeleton.java
│   ├── SkinnedVertex.java
│   └── SkinnedMesh.java
├── ik/
│   ├── IKChainManager.java
│   └── IKConstraint.java
└── render/
    ├── SkinnedRenderer.java (扩展现有渲染器)
    └── shaders/
        ├── skinning.vert
        └── skinning.frag
```

## 使用示例

### 播放骨骼动画
```java
// 加载带动画的模型
AssimpScene scene = loader.loadModel(path, ModelImportMode.DYNAMIC);
Animation walkAnim = scene.getAnimation("Walk");

// 创建播放器
AnimationPlayer player = new AnimationPlayer(walkAnim);
player.play();

// 每帧更新
player.update(deltaTime);
Map<String, Matrix4f> boneTransforms = player.calculateBoneTransforms();
skeleton.updateBoneMatrices(boneTransforms);

// 渲染
renderer.render(scene, skeleton);
```

### 使用 IK
```java
// 创建 IK 链（手臂：肩膀 -> 肘部 -> 手腕）
IKChainManager ikManager = new IKChainManager();
ikManager.createChain("right_arm", Arrays.asList(
    skeleton.getBone("Shoulder_R"),
    skeleton.getBone("Elbow_R"),
    skeleton.getBone("Wrist_R")
));

// 求解 IK（让手触摸目标点）
Vector3f targetPos = new Vector3f(1.0f, 2.0f, 0.5f);
ikManager.solveIK("right_arm", targetPos);

// 混合 IK 和动画
AnimationBlender blender = new AnimationBlender();
blender.blendIKAndFK(skeleton, animTransforms, ikRotations, 0.7f); // 70% IK
```

### Maya 导出设置
- 勾选 "Skins"（蒙皮）
- 勾选 "Animation"（动画）
- 勾选 "Deformed Models"（变形模型）
- FBX 版本：2014 或更高
- 单位：厘米
- 轴向：Y-up

## 性能考虑

### 优化策略
1. **骨骼矩阵缓存** - 只在动画更新时重新计算
2. **LOD 系统** - 远距离减少骨骼数量
3. **动画采样** - 预计算关键帧，运行时只做查表
4. **GPU 蒙皮** - 所有顶点变换在着色器中完成
5. **批量渲染** - 相同骨架的实例使用 instancing

### 性能目标
- 单个角色（50 骨骼）：< 1ms/帧
- 10 个角色同屏：< 5ms/帧
- IK 求解（3 关节）：< 0.1ms

## 测试计划

### 阶段 1 测试
- [ ] 加载带骨骼的 FBX 模型
- [ ] 验证骨骼层级正确
- [ ] 验证顶点权重正确分配
- [ ] T-Pose 静态显示正确

### 阶段 2 测试
- [ ] 播放简单动画（旋转立方体）
- [ ] 播放角色行走动画
- [ ] 动画循环无缝衔接
- [ ] 多个动画切换

### 阶段 3 测试
- [ ] 2 关节 IK（简单手臂）
- [ ] 3 关节 IK（带肘部）
- [ ] IK 约束生效（角度限制）
- [ ] IK/FK 混合平滑

## 参考资源

### 教程
- [LearnOpenGL - Skeletal Animation](https://learnopengl.com/Guest-Articles/2020/Skeletal-Animation)
- [OGLdev - Skeletal Animation Tutorial](https://ogldev.org/www/tutorial38/tutorial38.html)
- [Caliko Documentation](https://github.com/FedUni/caliko/wiki)

### Assimp API
- [AIBone](http://assimp.sourceforge.net/lib_html/structai_bone.html)
- [AIAnimation](http://assimp.sourceforge.net/lib_html/structai_animation.html)
- [AINodeAnim](http://assimp.sourceforge.net/lib_html/structai_node_anim.html)

### 算法
- FABRIK: [Paper](http://www.andreasaristidou.com/FABRIK.html)
- Quaternion Slerp: [Understanding Slerp](https://www.euclideanspace.com/maths/algebra/realNormedAlgebra/quaternions/slerp/)

## 总工作量

- **最小可用版本**（蒙皮 + 简单播放）：2-3 周
- **完整版本**（蒙皮 + 播放 + IK）：4-6 周
- **生产就绪**（优化 + 测试 + 文档）：8-10 周

## 下一步

1. 决定是否需要完整实现还是分阶段
2. 添加 Caliko 依赖到 build.gradle
3. 创建 `animation/` 和 `skeleton/` 包
4. 从阶段 1 的 `Bone.java` 开始实现
