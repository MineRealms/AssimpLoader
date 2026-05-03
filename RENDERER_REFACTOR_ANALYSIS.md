# Renderer 重构分析

## 现有 Renderer 清单

### Block Entity Renderers
1. **AssimpBlockEntityRenderer** (抽象基类)
   - 特性：通用 Assimp 模型渲染
   - 模式：STATIC/DYNAMIC
   - 动画：无
   - 材质：支持 PBR（Oculus）
   - 纹理：嵌入纹理支持
   - AABB：✅ 动态计算（新增）
   - 热重载：✅ 场景哈希检测
   - 视锥剔除：✅
   - LOD：❌

2. **CCBBlockEntityRenderer** (继承 AssimpBlockEntityRenderer)
   - 特性：简单继承，只提供模型名
   - 模型：ccb
   - 自定义：无

3. **CircleBlockEntityRenderer** (独立实现)
   - 特性：骨骼动画支持
   - 动画：✅ 骨骼动画（SkinnedMeshRenderer）
   - 渲染器：SkinnedMeshRenderer + SimpleMeshRenderer
   - AABB：❌ 固定 1x1x1
   - 视锥剔除：✅
   - LOD：✅ 4级LOD
   - 性能分析：✅

4. **GaussianSplatBlockEntityRenderer** (独立实现)
   - 特性：3D Gaussian Splatting
   - 渲染器：GaussianSplatRenderer
   - AABB：✅ 动态计算（新增）
   - 视锥剔除：✅（新增）
   - 特殊：自定义着色器

5. **TestModelBlockEntityRenderer** (继承 AssimpBlockEntityRenderer)
   - 特性：测试模型
   - 模型：test_model

### Item Renderers
1. **AssimpItemRenderer** (继承 BlockEntityWithoutLevelRenderer)
   - 特性：物品渲染（手持、GUI、物品栏）
   - 模式：STATIC/DYNAMIC
   - 动画：✅ 骨骼动画支持
   - 渲染器：SkinnedMeshRenderer + 内置顶点渲染
   - 材质：支持 PBR
   - 纹理：嵌入纹理支持
   - 热重载：✅
   - 变换：根据 DisplayContext 调整

### Entity Renderers
1. **AnimatedEntityRenderer** (继承 EntityRenderer)
   - 特性：实体渲染
   - 动画：✅ 骨骼动画
   - 渲染器：SkinnedMeshRenderer

## 共同特性分析

### 核心功能
1. **模型加载**
   - AssimpModelLoader.getCachedModel()
   - 场景哈希检测（热重载）

2. **纹理管理**
   - 嵌入纹理加载
   - 纹理缓存（Map<String, ResourceLocation>）
   - 材质透明度应用

3. **渲染模式**
   - STATIC：直接遍历 mesh 列表
   - DYNAMIC：遍历场景图（保留节点变换）

4. **材质系统**
   - 材质索引
   - 漫反射颜色
   - 透明度
   - PBR 支持（Oculus）

5. **RenderType 选择**
   - 根据材质透明度
   - 根据 Shader 状态
   - entityCutout / entityTranslucent / entityTranslucentCull

### 差异化功能

| 功能 | Assimp | Circle | GaussianSplat | Item |
|------|--------|--------|---------------|------|
| 骨骼动画 | ❌ | ✅ | ❌ | ✅ |
| 顶点动画 | ❌ | ❌ | ❌ | ❌ |
| LOD | ❌ | ✅ | ❌ | ❌ |
| 视锥剔除 | ✅ | ✅ | ✅ | ❌ |
| AABB计算 | ✅ | ❌ | ✅ | ❌ |
| 性能分析 | ❌ | ✅ | ❌ | ❌ |
| 自定义着色器 | ❌ | ❌ | ✅ | ❌ |

## 重构设计

### 基类架构

```
BaseAssimpRenderer (抽象基类)
├── 纹理管理（textureCache, sceneHashCache）
├── 热重载检测
├── 材质系统
├── RenderType 选择
├── 顶点渲染逻辑
└── 抽象方法：getModelName(), applyTransformations()

BaseBlockEntityRenderer<T extends BlockEntity> extends BaseAssimpRenderer
├── AABB 动态计算（缓存）
├── 视锥剔除
├── LOD 支持（可选）
├── 性能分析（可选）
└── 实现 BlockEntityRenderer<T>

BaseItemRenderer extends BaseAssimpRenderer
├── DisplayContext 变换
├── 物品特定逻辑
└── 继承 BlockEntityWithoutLevelRenderer

AnimatedBlockEntityRenderer<T> extends BaseBlockEntityRenderer<T>
├── AnimationState 管理
├── SkinnedMeshRenderer 集成
├── 骨骼动画支持
└── 混合渲染（骨骼 + 静态）

StaticBlockEntityRenderer<T> extends BaseBlockEntityRenderer<T>
└── 纯静态模型渲染（无动画）
```

### 具体实现类

```
CCBBlockEntityRenderer extends StaticBlockEntityRenderer<CCBBlockEntity>
└── getModelName() = "ccb"

CircleBlockEntityRenderer extends AnimatedBlockEntityRenderer<CircleBlockEntity>
├── getModelName() = "circle"
├── enableLOD() = true
└── enableProfiling() = true

TestModelBlockEntityRenderer extends StaticBlockEntityRenderer<TestModelBlockEntity>
└── getModelName() = "test_model"

GaussianSplatBlockEntityRenderer (保持独立)
└── 特殊渲染管线，不适合继承

AssimpItemRenderer extends BaseItemRenderer
└── 支持动画和静态模型
```

## 重构步骤

### Phase 1: 创建基类
1. ✅ BaseAssimpRenderer - 核心渲染逻辑
2. ✅ BaseBlockEntityRenderer - Block 渲染基类
3. ✅ BaseItemRenderer - Item 渲染基类
4. ✅ AnimatedBlockEntityRenderer - 动画支持
5. ✅ StaticBlockEntityRenderer - 静态模型

### Phase 2: 重构现有 Renderer
1. ✅ 重构 CCBBlockEntityRenderer
2. ✅ 重构 CircleBlockEntityRenderer
3. ✅ 重构 TestModelBlockEntityRenderer
4. ✅ 重构 AssimpItemRenderer
5. ⚠️ GaussianSplatBlockEntityRenderer 保持独立

### Phase 3: 测试验证
1. ✅ 编译测试
2. ✅ 游戏内测试各个方块
3. ✅ 测试物品渲染
4. ✅ 测试动画
5. ✅ 测试热重载

## 优化点

### 性能优化
1. **AABB 缓存** - 避免每帧重新计算
2. **纹理缓存** - 避免重复加载
3. **对象池** - 复用 Vector/Matrix 对象
4. **LOD 系统** - 根据距离调整细节
5. **视锥剔除** - 跳过不可见对象

### 代码优化
1. **消除重复代码** - 提取到基类
2. **统一接口** - 所有 Renderer 使用相同模式
3. **配置化** - 通过方法返回值配置特性
4. **文档化** - 清晰的注释和示例

### 扩展性
1. **插件化动画系统** - 支持多种动画类型
2. **材质系统扩展** - 支持更多 PBR 特性
3. **自定义着色器** - 预留接口
4. **多渲染管线** - 支持不同渲染后端

## 配置示例

```java
// 静态模型（最简单）
public class CCBBlockEntityRenderer extends StaticBlockEntityRenderer<CCBBlockEntity> {
    @Override
    protected String getModelName() {
        return "ccb";
    }
}

// 带动画和LOD
public class CircleBlockEntityRenderer extends AnimatedBlockEntityRenderer<CircleBlockEntity> {
    @Override
    protected String getModelName() {
        return "circle";
    }
    
    @Override
    protected boolean enableLOD() {
        return true;
    }
    
    @Override
    protected boolean enableProfiling() {
        return true;
    }
    
    @Override
    protected void applyTransformations(PoseStack poseStack, CircleBlockEntity entity, float partialTick) {
        poseStack.translate(0.5, 0.0, 0.5);
    }
}

// 自定义AABB
public class LargeModelRenderer extends StaticBlockEntityRenderer<LargeModelBlockEntity> {
    @Override
    protected String getModelName() {
        return "large_model";
    }
    
    @Override
    protected AABB getCustomBoundingBox(LargeModelBlockEntity entity) {
        // 返回自定义包围盒，或 null 使用自动计算
        return null; // 使用自动计算
    }
}
```

## 总结

通过这次重构，我们将：
1. **减少代码重复** - 从 ~500 行/Renderer 降到 ~20 行
2. **提高可维护性** - 统一的基类，易于修改
3. **增强扩展性** - 新 Renderer 只需继承并配置
4. **优化性能** - AABB缓存、LOD、视锥剔除
5. **改善代码质量** - 清晰的继承关系，易于理解
