# Photon 与 ImprovedMobs (AssimpLoader) 联动可行性分析

## 执行摘要

**结论：中等可行性，建议有限集成**

- **技术可行性：** 60-70%
- **工作量：** 中等（2-3周开发）
- **价值：** 中等（为 FBX 模型添加粒子特效支持）
- **风险：** 低（两个项目相对独立，失败不影响核心功能）

---

## 一、项目概述对比

### 1.1 Photon 项目

**定位：** Unity 风格的 VFX（视觉特效）编辑器 mod

**核心功能：**
- 粒子系统（ParticleEmitter）
- 拖尾系统（TrailEmitter）
- 光束系统（BeamEmitter）
- 材质系统（IMaterial）
- 后处理效果（BloomEffect）

**技术栈：**
- Architectury（Fabric/Forge 双平台）
- LDLib（LowDrag Library）
- Iris/Oculus 集成（Shader Pack 支持）
- Sodium/Embeddium 优化

**渲染特点：**
- 基于粒子的特效系统
- 支持自定义 Shader
- Bloom 后处理
- 与 Iris/Oculus 深度集成

### 1.2 ImprovedMobs (AssimpLoader)

**定位：** 3D 模型加载和渲染系统

**核心功能：**
- Assimp 模型加载（FBX, OBJ, GLTF 等）
- PBR 材质系统
- LookDev 材质编辑器
- Oculus PBR 纹理支持
- 节点编辑器（开发中）

**技术栈：**
- LWJGL Assimp Bindings
- ModernUI（UI 框架）
- Oculus/Iris 集成（PBR 纹理）
- 自定义渲染管线

**渲染特点：**
- 基于网格的 3D 模型渲染
- PBR 材质（Metallic/Roughness）
- 法线贴图、高度贴图
- 离屏渲染（FBO）

---

## 二、技术兼容性分析

### 2.1 材质系统兼容性

| AssimpLoader (PBRMaterial) | Photon (IMaterial) | 兼容程度 | 解决方案 |
|---------------------------|-------------------|---------|---------|
| baseColor (Vector4f) | Color/Gradient | ✅ 完全 | 直接映射 |
| baseColorTexture | TextureMaterial | ✅ 完全 | ResourceLocation 直接使用 |
| metallicFactor (0-1) | - | ⚠️ 无对应 | 丢弃或映射为颜色亮度 |
| roughnessFactor (0-1) | - | ⚠️ 无对应 | 丢弃或映射为透明度 |
| normalTexture | - | ❌ 不支持 | 需要扩展 Photon Shader |
| emissiveFactor | BloomEffect | ✅ 完全 | 映射为 Bloom 颜色 |
| emissiveTexture | TextureMaterial + Bloom | ⚠️ 部分 | 通过 Bloom 模拟 |
| alphaMode (OPAQUE/MASK/BLEND) | BlendMode | ✅ 完全 | 枚举映射 |
| doubleSided | - | ⚠️ 部分 | RenderSystem.disableCull() |

**兼容性评分：** 6/10

**关键问题：**
1. Photon 不支持 PBR 的 Metallic/Roughness 工作流
2. 法线贴图需要自定义 Shader
3. 粒子系统本质上不适合复杂 PBR 渲染

### 2.2 Shader 集成兼容性

**共同点：**
- 都支持 Iris/Oculus Shader Pack
- 都使用 `IrisApi.getInstance().isShaderPackInUse()` 检测
- 都有 Framebuffer 管理

**差异点：**

| 特性 | ImprovedMobs | Photon |
|-----|-------------|--------|
| Shader 类型 | PBR Shader (自定义) | 粒子 Shader (内置) |
| Framebuffer | OffscreenRenderer (FBO) | IrisFramebufferUtils |
| 纹理绑定 | RenderSystem.setShaderTexture | 同左 |
| Uniform 管理 | 手动设置 | ShaderInstanceMaterial |
| PBR 支持 | OculusPBRSupport (反射调用) | 无 |

**兼容性评分：** 7/10

**关键问题：**
1. Photon 的粒子 Shader 不支持 PBR Uniform
2. 需要扩展 `CustomShaderMaterial` 支持 PBR

### 2.3 渲染管线兼容性

**ImprovedMobs 渲染流程：**
```
AssimpScene → OffscreenRenderer → FBO → Pixel Buffer → ModernUI Canvas
```

**Photon 渲染流程：**
```
FX → FXRuntime → ParticleEmitter → TileParticle → PhotonParticleRenderType
```

**兼容性评分：** 4/10

**关键问题：**
1. 两者渲染管线完全独立
2. Photon 基于粒子，ImprovedMobs 基于网格
3. 难以直接复用渲染代码

---

## 三、联动方案设计

### 3.1 方案 A：材质适配器（推荐）

**目标：** 将 PBRMaterial 转换为 Photon IMaterial

**实现：**

```java
package cn.minerealms.assimploader.compat.photon;

public class PhotonMaterialAdapter implements IMaterial {
    private final PBRMaterial pbrMaterial;
    private final BlendMode blendMode;
    
    public PhotonMaterialAdapter(PBRMaterial pbr) {
        this.pbrMaterial = pbr;
        this.blendMode = convertAlphaMode(pbr.getAlphaMode());
    }
    
    @Override
    public void begin(boolean isInstancing) {
        // 1. 设置混合模式
        blendMode.apply();
        
        // 2. 绑定基础纹理
        if (pbrMaterial.getBaseColorTexture() != null) {
            RenderSystem.setShaderTexture(0, pbrMaterial.getBaseColorTexture());
        }
        
        // 3. 设置颜色
        Vector4f color = pbrMaterial.getBaseColor();
        RenderSystem.setShaderColor(color.x, color.y, color.z, color.w);
        
        // 4. 自发光 → Bloom
        if (pbrMaterial.isEmissive()) {
            // 设置 Bloom 效果
            BloomEffect.setBloomColor(pbrMaterial.getEmissiveFactor());
        }
    }
    
    @Override
    public void end(boolean isInstancing) {
        // 清理状态
    }
    
    private BlendMode convertAlphaMode(PBRMaterial.AlphaMode mode) {
        return switch (mode) {
            case OPAQUE -> BlendMode.CLOSE_CIRCUITS;
            case MASK -> BlendMode.ALPHA;
            case BLEND -> BlendMode.ADDITIVE_ALPHA;
        };
    }
}
```

**优点：**
- 实现简单
- 不修改 Photon 核心代码
- 降级策略清晰

**缺点：**
- 丢失 PBR 特性（Metallic/Roughness/Normal）
- 仅支持基础材质

**工作量：** 1-2 天

### 3.2 方案 B：FBX 特效粒子化

**目标：** 将 FBX 模型中的特效元素转换为 Photon 粒子

**适用场景：**
- FBX 模型包含粒子发射器数据
- 需要动态特效（火焰、烟雾、魔法效果）

**实现：**

```java
public class FBXToPhotonConverter {
    
    public static ParticleEmitter convertEmitter(AssimpScene scene, String emitterName) {
        ParticleConfig config = new ParticleConfig();
        
        // 1. 提取发射器数据
        // （假设 FBX 中有特定命名的节点作为发射器）
        AINode emitterNode = findNodeByName(scene.getAiScene(), emitterName);
        
        // 2. 材质转换
        AssimpMesh mesh = scene.getMeshes().get(0);
        PBRMaterial pbr = extractPBRMaterial(mesh);
        config.material.setMaterial(new PhotonMaterialAdapter(pbr));
        
        // 3. 形状设置
        config.shape.setShape(new Sphere(1.0f)); // 或其他形状
        
        // 4. 发射设置
        config.emission.setRateOverTime(10); // 每秒10个粒子
        
        // 5. 生命周期
        config.startLifetime.setValue(2.0f); // 2秒生命周期
        
        // 6. 颜色
        Vector4f color = pbr.getBaseColor();
        config.startColor.setValue(ColorUtils.toMinecraftColor(color));
        
        return new ParticleEmitter(config);
    }
}
```

**优点：**
- 为 FBX 模型添加动态特效能力
- 利用 Photon 的粒子系统优势

**缺点：**
- FBX 模型通常不包含粒子发射器数据
- 需要手动配置发射器参数
- 适用场景有限

**工作量：** 3-5 天

### 3.3 方案 C：LookDev 特效预览（最实用）

**目标：** 在 LookDev 编辑器中预览 Photon 特效

**实现：**

```java
// 在 LookDevScreen 中添加特效预览功能
public class LookDevScreen extends Fragment {
    
    private FXRuntime currentEffect;
    
    public void attachEffect(ResourceLocation fxLocation) {
        // 1. 加载 Photon FX
        FX fx = FXHelper.getFX(fxLocation);
        if (fx == null) return;
        
        // 2. 创建运行时
        currentEffect = fx.createRuntime();
        
        // 3. 附着到模型位置
        if (currentModel != null) {
            Vector3f modelPos = getModelCenter();
            currentEffect.setPosition(modelPos.x, modelPos.y, modelPos.z);
        }
        
        // 4. 启动特效
        currentEffect.emmit(new BlockEffect(level, blockPos));
    }
    
    public void detachEffect() {
        if (currentEffect != null) {
            currentEffect.destroy(false);
            currentEffect = null;
        }
    }
}
```

**UI 集成：**
- 在 LookDev 右侧参数面板添加 "Effects" 标签页
- 列出可用的 Photon FX
- 点击预览特效
- 调整特效位置、旋转、缩放

**优点：**
- 实用性强，帮助美术预览特效
- 不修改核心渲染逻辑
- 可独立开关

**缺点：**
- 仅限预览，不影响实际渲染
- 需要 Photon 作为依赖

**工作量：** 5-7 天

### 3.4 方案 D：扩展 Photon 支持 PBR（高级）

**目标：** 为 Photon 添加完整 PBR 材质支持

**实现：**

```java
// 新建 PBRMaterial 类继承 ShaderInstanceMaterial
public class PBRParticleMaterial extends ShaderInstanceMaterial {
    
    @Configurable
    public ResourceLocation baseColorTexture;
    
    @Configurable
    public ResourceLocation normalTexture;
    
    @Configurable
    public ResourceLocation metallicRoughnessTexture;
    
    @Configurable
    @NumberRange(range = {0, 1})
    public float metallicFactor = 0.0f;
    
    @Configurable
    @NumberRange(range = {0, 1})
    public float roughnessFactor = 0.5f;
    
    @Override
    public ShaderInstance getShader() {
        // 返回自定义 PBR Shader
        return PBRShaders.getParticlePBRShader();
    }
    
    @Override
    public void setupUniform() {
        // 设置 PBR Uniform
        RenderSystem.setShaderTexture(0, baseColorTexture);
        RenderSystem.setShaderTexture(1, normalTexture);
        RenderSystem.setShaderTexture(2, metallicRoughnessTexture);
        
        getShader().safeGetUniform("MetallicFactor").set(metallicFactor);
        getShader().safeGetUniform("RoughnessFactor").set(roughnessFactor);
    }
}
```

**需要：**
1. 编写 PBR Particle Shader（GLSL）
2. 修改 Photon 核心代码
3. 提交 PR 到 Photon 仓库

**优点：**
- 完整 PBR 支持
- 提升 Photon 功能
- 社区贡献

**缺点：**
- 工作量巨大
- 需要 Photon 维护者同意
- 可能影响性能

**工作量：** 2-3 周

---

## 四、推荐方案

### 优先级排序

1. **方案 C：LookDev 特效预览**（推荐优先实现）
   - 实用性最强
   - 工作量适中
   - 风险最低
   - 为美术提供直接价值

2. **方案 A：材质适配器**（作为基础）
   - 实现简单
   - 为其他方案提供基础
   - 可独立使用

3. **方案 B：FBX 特效粒子化**（可选）
   - 适用场景有限
   - 需要特定 FBX 数据
   - 可作为高级功能

4. **方案 D：扩展 Photon PBR**（长期目标）
   - 工作量大
   - 需要社区合作
   - 可作为未来改进

### 实施路线图

**Phase 1：基础集成（1 周）**
- 实现 `PhotonMaterialAdapter`
- 添加 Photon 依赖到 `build.gradle`
- 基础测试

**Phase 2：LookDev 集成（1 周）**
- 在 LookDev 中添加特效预览功能
- UI 集成（Effects 标签页）
- 特效列表和预览

**Phase 3：优化和文档（3-5 天）**
- 性能优化
- 用户文档
- 示例特效

**总工作量：** 2-3 周

---

## 五、技术风险评估

### 5.1 依赖风险

| 风险 | 影响 | 概率 | 缓解措施 |
|-----|------|------|---------|
| Photon API 变更 | 高 | 中 | 使用反射 + 版本检测 |
| LDLib 版本冲突 | 中 | 低 | 统一依赖版本 |
| Iris/Oculus 兼容性 | 中 | 低 | 两者都已集成 |

### 5.2 性能风险

| 风险 | 影响 | 概率 | 缓解措施 |
|-----|------|------|---------|
| 粒子数量过多 | 高 | 中 | 限制粒子数量 |
| 双重渲染开销 | 中 | 高 | 仅在 LookDev 中启用 |
| 内存泄漏 | 高 | 低 | 正确清理 FXRuntime |

### 5.3 兼容性风险

| 风险 | 影响 | 概率 | 缓解措施 |
|-----|------|------|---------|
| Shader Pack 不兼容 | 中 | 中 | 降级到默认渲染 |
| 模组冲突 | 低 | 低 | 可选依赖 |
| 平台差异 (Fabric/Forge) | 中 | 低 | Photon 已支持双平台 |

---

## 六、价值评估

### 6.1 用户价值

**对美术人员：**
- ✅ 在 LookDev 中预览特效
- ✅ 快速迭代特效设计
- ✅ 统一的材质和特效工作流

**对开发者：**
- ✅ 复用 Photon 的粒子系统
- ✅ 减少自己实现粒子系统的工作
- ⚠️ 需要学习 Photon API

**对玩家：**
- ⚠️ 间接价值（更好的特效）
- ⚠️ 需要额外安装 Photon

### 6.2 技术价值

**代码复用：**
- ✅ 复用 Photon 的粒子系统
- ✅ 复用 Iris/Oculus 集成
- ⚠️ 材质系统差异较大

**生态整合：**
- ✅ 与 LowDrag 生态整合
- ✅ 与 Shader 生态整合
- ✅ 社区认可度高

### 6.3 投入产出比

**投入：** 2-3 周开发时间

**产出：**
- LookDev 特效预览功能
- 材质适配器（可复用）
- 与 Photon 生态整合

**ROI 评估：** 中等（7/10）

---

## 七、结论与建议

### 7.1 总体结论

**Photon 与 ImprovedMobs 的联动是可行的，但需要明确目标和范围。**

**最佳定位：**
- 不是完全集成，而是**互补性集成**
- ImprovedMobs 专注于 3D 模型和 PBR 材质
- Photon 专注于粒子特效
- 在 LookDev 中提供特效预览桥梁

### 7.2 建议实施方案

**推荐：方案 C（LookDev 特效预览）+ 方案 A（材质适配器）**

**理由：**
1. 实用性强，直接解决美术需求
2. 工作量适中（2-3 周）
3. 风险可控
4. 不影响核心功能
5. 可独立开关

### 7.3 不建议的方向

**不建议：**
- ❌ 完全替换 ImprovedMobs 的渲染系统为 Photon
- ❌ 强制依赖 Photon
- ❌ 尝试统一两者的材质系统

**原因：**
- 两者设计目标不同
- 技术架构差异大
- 投入产出比低

### 7.4 下一步行动

**如果决定实施：**

1. **立即行动：**
   - 添加 Photon 为可选依赖
   - 实现 `PhotonMaterialAdapter`
   - 基础测试

2. **短期（1-2 周）：**
   - LookDev 特效预览功能
   - UI 集成
   - 文档编写

3. **长期（可选）：**
   - 向 Photon 提交 PBR 支持 PR
   - 扩展更多集成功能

**如果暂不实施：**
- 保留此分析文档作为未来参考
- 继续专注于 ImprovedMobs 核心功能
- 等待 Photon 生态更成熟

---

## 八、附录

### 8.1 依赖配置

```groovy
// build.gradle
dependencies {
    // Photon (可选依赖)
    compileOnly fg.deobf("com.lowdragmc:photon-common-${minecraft_version}:${photon_version}")
    
    // LDLib (Photon 依赖)
    compileOnly fg.deobf("com.lowdragmc.ldlib:ldlib-common-${minecraft_version}:${ldlib_version}")
}
```

### 8.2 参考资源

- **Photon GitHub:** https://github.com/Low-Drag-MC/Photon
- **Photon Discord:** https://discord.com/invite/sDdf2yD9bh
- **LDLib GitHub:** https://github.com/Low-Drag-MC/LDLib
- **Iris API:** https://github.com/IrisShaders/Iris

### 8.3 联系方式

- **Photon 作者:** KilaBash (Low-Drag-MC)
- **QQ 群:** 933426877

---

*文档版本: 1.0*  
*创建日期: 2026-04-25*  
*作者: Claude (Opus 4.6)*
