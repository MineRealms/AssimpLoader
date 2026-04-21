# AssimpLoader FBX完整支持实现计划

## 目标
完成一个支持动画、材质、灯光效果全部从FBX模型加载，可以精准解析转换为Minecraft内容的强大库，支持PBR等现代渲染管线。

## 核心任务拆解

### 阶段1: 坐标系转换核心 ✓ (进行中)
**目标**: 确保FBX→Minecraft坐标系精准转换，无任何错误

#### 子任务:
1. ✓ CoordinateSystemConverter - 通用坐标系转换器
2. ✓ VertexTransformPipeline - 顶点变换管线
3. ⚠ 修复Quaternion转换方法
4. ⚠ 集成到AssimpModelLoader
5. ⚠ 集成到AssimpScene/AssimpMesh

**关键点**:
- FBX: 右手坐标系，Y-up，Z-forward
- Minecraft: 左手坐标系，Y-up，Z-forward
- 转换: 使用aiProcess_MakeLeftHanded + aiProcess_FlipWindingOrder
- 法线: 使用inverse transpose矩阵
- 切线: 使用rotation/scale矩阵

---

### 阶段2: FBX材质系统完整支持
**目标**: 完整解析FBX材质，支持PBR工作流

#### 子任务:
1. FBXMaterialParser - FBX材质解析器
   - Diffuse/Albedo贴图
   - Normal贴图
   - Metallic贴图
   - Roughness贴图
   - Emissive贴图
   - AO (Ambient Occlusion)贴图
   - Opacity/Alpha贴图

2. PBRMaterial - PBR材质数据结构
   - BaseColor (vec4)
   - Metallic (float)
   - Roughness (float)
   - Emissive (vec3)
   - Normal (vec3)
   - AO (float)

3. MaterialConverter - 材质转换器
   - FBX材质 → Minecraft RenderType
   - 纹理路径解析
   - 嵌入纹理提取
   - 材质属性映射

4. TextureLoader - 纹理加载增强
   - 多通道纹理支持
   - 纹理压缩
   - Mipmap生成
   - 纹理atlas集成

**关键点**:
- FBX支持多种材质模型（Phong, Lambert, PBR）
- 需要检测材质类型并正确映射
- 纹理路径可能是相对路径或绝对路径
- 嵌入纹理需要从FBX中提取

---

### 阶段3: 骨骼动画完整实现
**目标**: 完整支持FBX骨骼动画，包括蒙皮和变形

#### 子任务:
1. BoneHierarchyBuilder - 骨骼层次构建器
   - 从AINode构建骨骼树
   - 计算绑定姿态矩阵
   - 处理骨骼偏移矩阵

2. AnimationDataExtractor - 动画数据提取器
   - 提取位置关键帧
   - 提取旋转关键帧（四元数）
   - 提取缩放关键帧
   - 坐标系转换应用

3. SkinnedMeshProcessor - 蒙皮网格处理器
   - 骨骼权重解析
   - 顶点蒙皮计算
   - GPU蒙皮准备（Uniform Buffer）

4. AnimationBlender - 动画混合器
   - 多动画混合
   - 过渡平滑
   - 分层动画（上半身/下半身）

**关键点**:
- 骨骼变换 = ParentTransform × LocalTransform × OffsetMatrix
- 四元数插值必须使用SLERP
- 骨骼权重归一化
- 最多支持4个骨骼影响每个顶点

---

### 阶段4: 灯光系统支持
**目标**: 解析FBX灯光并转换为Minecraft光照

#### 子任务:
1. LightParser - 灯光解析器
   - Point Light（点光源）
   - Directional Light（方向光）
   - Spot Light（聚光灯）
   - Area Light（区域光）

2. LightDataStructure - 灯光数据结构
   - 位置/方向
   - 颜色/强度
   - 衰减参数
   - 阴影参数

3. LightBaker - 光照烘焙器
   - 静态光照烘焙到顶点色
   - Lightmap生成
   - 环境光遮蔽

**关键点**:
- Minecraft不支持动态光源（需要mod如Optifine）
- 可以烘焙到顶点颜色
- 可以生成lightmap纹理

---

### 阶段5: 现代渲染管线集成
**目标**: 支持PBR渲染，集成到Minecraft渲染管线

#### 子任务:
1. PBRShaderAdapter - PBR着色器适配器
   - 自定义Shader支持
   - Uniform传递
   - 纹理绑定

2. RenderTypeFactory - RenderType工厂
   - 根据材质创建RenderType
   - 透明度排序
   - 混合模式

3. VertexFormatExtended - 扩展顶点格式
   - 添加Tangent/Bitangent
   - 添加额外UV通道
   - 添加顶点颜色

**关键点**:
- Minecraft 1.20.1使用RenderType系统
- 需要自定义ShaderInstance
- 可能需要Mixin注入

---

### 阶段6: 性能优化和验证
**目标**: 确保性能和正确性

#### 子任务:
1. 批处理优化
2. LOD支持
3. 遮挡剔除
4. 实例化渲染
5. 完整测试套件

---

## 当前进度

### 已完成:
- ✓ 基础项目结构（Phase 1-6）
- ✓ CoordinateSystemConverter
- ✓ VertexTransformPipeline

### 进行中:
- ⚠ 修复编译错误
- ⚠ 集成坐标系转换

### 待完成:
- FBX材质系统
- 骨骼动画完整实现
- 灯光系统
- PBR渲染管线
- 性能优化

---

## 实现策略

### 自下而上方法:
1. **底层接口** → 数据结构（Material, Light, Bone）
2. **解析器** → 从Assimp提取数据
3. **转换器** → 坐标系/格式转换
4. **渲染器** → 集成到Minecraft
5. **优化器** → 性能和质量

### 每个子任务流程:
1. 编写接口和数据结构
2. 实现解析逻辑
3. 编写单元测试
4. 集成到主系统
5. 编译验证
6. 更新文档
7. Git提交

---

## 技术难点

### 1. 坐标系转换
**问题**: FBX右手系 → Minecraft左手系
**解决**: 
- 使用aiProcess_MakeLeftHanded
- Z轴取反
- 翻转面片绕序

### 2. 骨骼动画
**问题**: 矩阵层次计算复杂
**解决**:
- 递归计算全局变换
- 缓存中间结果
- 使用四元数避免万向锁

### 3. PBR材质
**问题**: Minecraft不原生支持PBR
**解决**:
- 自定义Shader
- 或简化为传统光照
- 或使用Iris/Optifine

### 4. 性能
**问题**: 复杂模型可能卡顿
**解决**:
- 异步加载
- LOD系统
- 批处理
- 实例化

---

## 文档结构

### 技术文档:
- COORDINATE_SYSTEM.md - 坐标系转换详解
- MATERIAL_SYSTEM.md - 材质系统文档
- ANIMATION_SYSTEM.md - 动画系统文档
- RENDERING_PIPELINE.md - 渲染管线文档

### API文档:
- Javadoc完整覆盖
- 使用示例
- 最佳实践

---

## 验证标准

### 功能验证:
- [ ] 静态FBX模型正确显示
- [ ] 动画FBX模型正确播放
- [ ] 材质纹理正确应用
- [ ] 灯光效果正确
- [ ] 坐标系无错误
- [ ] 性能可接受（60fps+）

### 质量标准:
- [ ] 无内存泄漏
- [ ] 无NaN/Inf值
- [ ] 正确的面片剔除
- [ ] 正确的深度排序
- [ ] 正确的透明度混合

---

Last Updated: 2026-04-22
Status: 阶段1进行中
