# AssimpLoader → Photon 集成实施计划

**项目目标**: 让 Photon 编辑器能够直接导入和使用 FBX 模型的 Mesh、Material、Emitter 数据

**总工作量**: 2-3 周  
**开始日期**: 2026-04-26

---

## 项目架构

```
AssimpLoader (数据提供者)
    ↓
    ├─ Mesh 转换器 → Photon MeshData
    ├─ Material 转换器 → Photon IMaterial
    └─ Emitter 提取器 → Photon ParticleEmitter
    ↓
Photon 编辑器扩展 (Mixin)
    ├─ MeshesResource: 添加 "Import FBX Mesh" 按钮
    ├─ MaterialsResource: 添加 "Import FBX Materials" 按钮
    └─ FXProject: 添加 "Extract Emitters" 功能
```

---

## Phase 划分

### Phase 1: 基础设施搭建 (1-2 天)
- 创建 `compat.photon` 包结构
- 配置条件 Mixin 加载
- 添加 Photon 依赖
- 验证编译通过

### Phase 2: Mesh 数据转换 (2-3 天)
- 实现 `AssimpToPhotonMeshConverter`
- 单元测试：转换各种 FBX 模型
- 验证数据正确性

### Phase 3: Photon 编辑器 Mesh 导入 (3-4 天)
- Mixin: `MeshesResourceMixin`
- 添加 "Import FBX Mesh" 按钮
- 文件选择对话框
- 导入流程实现
- 测试：在 Photon 编辑器中导入 FBX

### Phase 4: Material 数据转换 (2-3 天)
- 实现 `AssimpToPhotonMaterialConverter`
- PBRMaterial → TextureMaterial
- PBRMaterial → CustomShaderMaterial (自发光)
- 测试材质转换

### Phase 5: Photon 编辑器 Material 导入 (2-3 天)
- Mixin: `MaterialsResourceMixin`
- 添加 "Import FBX Materials" 按钮
- 导入流程实现
- 测试：材质导入和预览

### Phase 6: Emitter 数据提取 (3-4 天)
- 实现 `FBXEmitterExtractor`
- 场景图遍历
- 发射器节点识别
- 预设配置（Fire, Smoke, Magic, Spark）
- 测试：提取发射器数据

### Phase 7: 完整工作流测试 (2-3 天)
- 端到端测试
- 性能优化
- 错误处理完善
- 用户文档编写

---

## Phase 1: 基础设施搭建

### 目标
- ✅ 创建包结构
- ✅ 配置 Photon 依赖
- ✅ 实现条件 Mixin 加载
- ✅ 验证编译通过

### 任务清单

#### 1.1 创建包结构
```
src/main/java/cn/minerealms/assimploader/compat/photon/
├─ PhotonCompat.java              # 兼容层入口
├─ AssimpToPhotonMeshConverter.java
├─ AssimpToPhotonMaterialConverter.java
└─ FBXEmitterExtractor.java

src/main/java/cn/minerealms/assimploader/mixin/photon/
├─ MeshesResourceMixin.java
├─ MaterialsResourceMixin.java
└─ FXProjectMixin.java
```

#### 1.2 配置 Gradle 依赖
```groovy
// build.gradle
repositories {
    maven {
        name = "LowDragMC Maven"
        url = "https://maven.lowdragmc.com/releases"
    }
}

dependencies {
    // Photon 作为可选依赖（仅编译时）
    compileOnly "com.lowdragmc:photon-forge-1.20.1:${photon_version}"
    
    // LDLib (Photon 依赖)
    compileOnly "com.lowdragmc.ldlib:ldlib-forge-1.20.1:${ldlib_version}"
}
```

#### 1.3 配置 gradle.properties
```properties
# Photon 版本
photon_version=1.0.0-beta
ldlib_version=1.0.31
```

#### 1.4 创建条件 Mixin 配置
```json
// src/main/resources/mixins.photon_compat.json
{
  "required": false,
  "minVersion": "0.8",
  "package": "cn.minerealms.assimploader.mixin.photon",
  "compatibilityLevel": "JAVA_17",
  "refmap": "assimploader.refmap.json",
  "client": [
    "MeshesResourceMixin",
    "MaterialsResourceMixin",
    "FXProjectMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

#### 1.5 实现 PhotonCompat 入口类
```java
package cn.minerealms.assimploader.compat.photon;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Photon 兼容层入口
 * 负责检测 Photon 是否存在并加载相应的 Mixin
 */
public class PhotonCompat {
    
    private static final Logger LOGGER = LogManager.getLogger("AssimpLoader-PhotonCompat");
    private static boolean PHOTON_LOADED = false;
    private static boolean INITIALIZED = false;
    
    /**
     * 初始化 Photon 集成
     * 在 AssimpLoader 主类的构造函数中调用
     */
    public static void init() {
        if (INITIALIZED) {
            return;
        }
        
        INITIALIZED = true;
        
        // 检测 Photon 是否加载
        if (!ModList.get().isLoaded("photon")) {
            LOGGER.info("Photon not detected, skipping integration");
            return;
        }
        
        try {
            // 验证关键类是否存在
            Class.forName("com.lowdragmc.photon.client.fx.FX");
            Class.forName("com.lowdragmc.photon.gui.editor.MeshesResource");
            Class.forName("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData");
            
            LOGGER.info("Photon detected, enabling integration");
            
            // 加载 Photon 兼容 Mixin
            Mixins.addConfiguration("mixins.photon_compat.json");
            
            PHOTON_LOADED = true;
            LOGGER.info("Photon integration enabled successfully");
            
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Photon mod found but required classes not available: {}", e.getMessage());
            PHOTON_LOADED = false;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Photon integration", e);
            PHOTON_LOADED = false;
        }
    }
    
    /**
     * 检查 Photon 是否已加载并可用
     */
    public static boolean isPhotonLoaded() {
        return PHOTON_LOADED;
    }
}
```

#### 1.6 在 AssimpLoader 主类中调用
```java
@Mod("assimploader")
public class AssimpLoader {
    
    public AssimpLoader() {
        // ... 其他初始化代码 ...
        
        // 初始化 Photon 集成
        PhotonCompat.init();
    }
}
```

### 验证步骤

1. **编译测试**
```powershell
# 清理并重新编译
./gradlew clean build

# 检查编译输出
# 应该看到：
# - 成功下载 Photon 和 LDLib 依赖
# - 成功编译所有类
# - 生成 JAR 文件
```

2. **运行测试（无 Photon）**
```powershell
# 运行客户端（不安装 Photon）
./gradlew runClient

# 检查日志：
# [AssimpLoader-PhotonCompat] Photon not detected, skipping integration
```

3. **运行测试（有 Photon）**
```powershell
# 1. 下载 Photon 和 LDLib JAR
# 2. 放入 run/mods/ 目录
# 3. 运行客户端
./gradlew runClient

# 检查日志：
# [AssimpLoader-PhotonCompat] Photon detected, enabling integration
# [AssimpLoader-PhotonCompat] Photon integration enabled successfully
```

### 提交清单

- [ ] 创建 `compat.photon` 包
- [ ] 创建 `mixin.photon` 包
- [ ] 更新 `build.gradle`
- [ ] 更新 `gradle.properties`
- [ ] 创建 `mixins.photon_compat.json`
- [ ] 实现 `PhotonCompat.java`
- [ ] 更新 `AssimpLoader.java`
- [ ] 验证编译通过
- [ ] 验证运行时检测正常

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 1 - Setup Photon integration infrastructure

- Add compat.photon package structure
- Add conditional Mixin configuration
- Add Photon and LDLib dependencies (compileOnly)
- Implement PhotonCompat runtime detection
- Verify compilation and runtime detection"
```

---

## Phase 2: Mesh 数据转换

### 目标
- ✅ 实现 AssimpMesh → Photon MeshData 转换
- ✅ 处理顶点、边、三角形数据
- ✅ 单元测试验证正确性

### 任务清单

#### 2.1 实现 AssimpToPhotonMeshConverter

**文件**: `src/main/java/cn/minerealms/assimploader/compat/photon/AssimpToPhotonMeshConverter.java`

**核心功能**:
- 转换顶点数据
- 转换三角形面（从索引）
- 转换边数据（去重）
- 计算面积和长度总和

#### 2.2 创建测试用例

**文件**: `src/test/java/cn/minerealms/assimploader/compat/photon/MeshConverterTest.java`

**测试内容**:
- 简单立方体转换
- 复杂 FBX 模型转换
- 边界情况（空网格、单三角形）
- 数据完整性验证

### 验证步骤

1. **单元测试**
```powershell
./gradlew test --tests "MeshConverterTest"
```

2. **手动测试**
```powershell
# 创建测试程序
# 加载 FBX → 转换 → 输出统计信息
```

### 提交清单

- [ ] 实现 `AssimpToPhotonMeshConverter.java`
- [ ] 创建 `MeshConverterTest.java`
- [ ] 验证所有测试通过
- [ ] 更新文档

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 2 - Implement Mesh data converter

- Implement AssimpToPhotonMeshConverter
- Convert vertices, triangles, edges
- Add unit tests for mesh conversion
- Verify data integrity"
```

---

## Phase 3: Photon 编辑器 Mesh 导入

### 目标
- ✅ 扩展 Photon MeshesResource
- ✅ 添加 "Import FBX Mesh" 按钮
- ✅ 实现完整导入流程
- ✅ 在 Photon 编辑器中测试

### 任务清单

#### 3.1 实现 MeshesResourceMixin

**文件**: `src/main/java/cn/minerealms/assimploader/mixin/photon/MeshesResourceMixin.java`

**功能**:
- 注入 "Import FBX Mesh" 按钮
- 文件选择对话框
- 调用转换器
- 添加到资源管理器
- 错误处理和用户反馈

#### 3.2 测试流程

1. 启动 Minecraft + Photon
2. 打开 Photon 编辑器
3. 找到 Mesh 资源面板
4. 点击 "Import FBX Mesh" 按钮
5. 选择 FBX 文件
6. 验证导入成功
7. 在粒子发射器中使用导入的 Mesh

### 验证步骤

```powershell
# 1. 编译
./gradlew build

# 2. 复制 JAR 到测试环境
Copy-Item "build/libs/assimploader-1.0.0.jar" "run/mods/"

# 3. 启动客户端
./gradlew runClient

# 4. 手动测试 Photon 编辑器
```

### 提交清单

- [ ] 实现 `MeshesResourceMixin.java`
- [ ] 测试导入流程
- [ ] 验证 Mesh 可用
- [ ] 截图记录功能

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 3 - Add FBX Mesh import to Photon editor

- Implement MeshesResourceMixin
- Add 'Import FBX Mesh' button to Photon editor
- Implement file dialog and import flow
- Add error handling and user feedback
- Test: Successfully import FBX mesh into Photon"
```

---

## Phase 4: Material 数据转换

### 目标
- ✅ 实现 PBRMaterial → Photon Material 转换
- ✅ 支持 TextureMaterial
- ✅ 支持 CustomShaderMaterial（自发光）

### 任务清单

#### 4.1 实现 AssimpToPhotonMaterialConverter

**文件**: `src/main/java/cn/minerealms/assimploader/compat/photon/AssimpToPhotonMaterialConverter.java`

**功能**:
- `convertToTextureMaterial()`: 基础纹理材质
- `convertToCustomShader()`: 自发光材质
- 混合模式映射
- 颜色转换

#### 4.2 测试用例

**测试内容**:
- 纯色材质转换
- 纹理材质转换
- 自发光材质转换
- 透明材质转换

### 验证步骤

```powershell
./gradlew test --tests "MaterialConverterTest"
```

### 提交清单

- [ ] 实现 `AssimpToPhotonMaterialConverter.java`
- [ ] 创建 `MaterialConverterTest.java`
- [ ] 验证所有测试通过

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 4 - Implement Material data converter

- Implement AssimpToPhotonMaterialConverter
- Support TextureMaterial conversion
- Support CustomShaderMaterial (emissive)
- Add blend mode mapping
- Add unit tests"
```

---

## Phase 5: Photon 编辑器 Material 导入

### 目标
- ✅ 扩展 Photon MaterialsResource
- ✅ 添加 "Import FBX Materials" 按钮
- ✅ 实现完整导入流程

### 任务清单

#### 5.1 实现 MaterialsResourceMixin

**文件**: `src/main/java/cn/minerealms/assimploader/mixin/photon/MaterialsResourceMixin.java`

#### 5.2 测试流程

1. 打开 Photon 编辑器
2. 找到 Material 资源面板
3. 点击 "Import FBX Materials" 按钮
4. 选择 FBX 文件
5. 验证材质导入
6. 在粒子发射器中使用导入的材质

### 提交清单

- [ ] 实现 `MaterialsResourceMixin.java`
- [ ] 测试导入流程
- [ ] 验证材质可用

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 5 - Add FBX Material import to Photon editor

- Implement MaterialsResourceMixin
- Add 'Import FBX Materials' button
- Implement material import flow
- Test: Successfully import materials into Photon"
```

---

## Phase 6: Emitter 数据提取

### 目标
- ✅ 从 FBX 场景图提取发射器节点
- ✅ 实现预设配置（Fire, Smoke, Magic, Spark）
- ✅ 转换为 Photon ParticleEmitter

### 任务清单

#### 6.1 实现 FBXEmitterExtractor

**文件**: `src/main/java/cn/minerealms/assimploader/compat/photon/FBXEmitterExtractor.java`

**功能**:
- 场景图递归遍历
- 识别 "Emitter_" 节点
- 提取位置、旋转、缩放
- 预设配置系统

#### 6.2 创建测试 FBX

在 Blender 中创建测试场景：
```
Scene
├─ Cube (模型)
├─ Emitter_Fire (空对象，位置 (0, 2, 0))
├─ Emitter_Smoke (空对象，位置 (2, 1, 0))
└─ Emitter_Magic (空对象，位置 (-2, 1, 0))
```

### 提交清单

- [ ] 实现 `FBXEmitterExtractor.java`
- [ ] 创建测试 FBX 文件
- [ ] 测试发射器提取
- [ ] 验证预设配置

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 6 - Implement FBX Emitter extraction

- Implement FBXEmitterExtractor
- Add scene graph traversal
- Add emitter node detection (Emitter_* naming)
- Add preset configurations (Fire, Smoke, Magic, Spark)
- Add test FBX with emitters"
```

---

## Phase 7: 完整工作流测试

### 目标
- ✅ 端到端测试
- ✅ 性能优化
- ✅ 错误处理完善
- ✅ 用户文档

### 任务清单

#### 7.1 端到端测试

**测试场景**:
1. 导入复杂 FBX 模型（多个 Mesh）
2. 导入材质
3. 提取发射器
4. 在 Photon 中创建完整特效
5. 导出并在游戏中使用

#### 7.2 性能优化

- 大型 Mesh 转换优化
- 内存管理
- 异步加载

#### 7.3 错误处理

- 文件格式错误
- 内存不足
- Photon 版本不兼容
- 用户友好的错误消息

#### 7.4 用户文档

**文件**: `PHOTON_INTEGRATION_GUIDE.md`

**内容**:
- 功能介绍
- 安装说明
- 使用教程
- FBX 导出规范
- 常见问题

### 提交清单

- [ ] 完成端到端测试
- [ ] 性能优化
- [ ] 错误处理完善
- [ ] 编写用户文档
- [ ] 创建示例 FBX 文件

### Git 提交
```bash
git add .
git commit -m "feat(photon): Phase 7 - Complete integration and documentation

- Add end-to-end tests
- Optimize performance for large meshes
- Improve error handling and user feedback
- Add user documentation (PHOTON_INTEGRATION_GUIDE.md)
- Add example FBX files
- Ready for release"
```

---

## 工作流程

每个 Phase 的标准流程：

```powershell
# 1. 开始新 Phase
Write-Host "Starting Phase X: [Phase Name]" -ForegroundColor Green

# 2. 实现功能
# ... 编写代码 ...

# 3. 编译测试
./gradlew clean build

# 4. 运行测试
./gradlew test

# 5. 手动测试（如果需要）
./gradlew runClient

# 6. 修复问题
# ... 修复编译错误和运行时错误 ...

# 7. 提交代码
git add .
git commit -m "feat(photon): Phase X - [Description]"

# 8. 更新记忆
# 记录完成的功能、遇到的问题、解决方案

# 9. 进入下一 Phase
```

---

## 依赖版本

```properties
# gradle.properties
minecraft_version=1.20.1
forge_version=47.1.3
photon_version=1.0.0-beta
ldlib_version=1.0.31
```

**注意**: 需要根据实际可用版本调整

---

## 风险和缓解措施

### 风险 1: Photon API 变更
- **影响**: 高
- **概率**: 中
- **缓解**: 使用反射 + 版本检测

### 风险 2: Mixin 冲突
- **影响**: 中
- **概率**: 低
- **缓解**: 使用 `@Inject` 而非 `@Overwrite`

### 风险 3: 性能问题
- **影响**: 中
- **概率**: 中
- **缓解**: 异步加载 + 进度条

---

## 成功标准

- ✅ 可以在 Photon 编辑器中导入 FBX Mesh
- ✅ 可以在 Photon 编辑器中导入 FBX Materials
- ✅ 可以从 FBX 提取发射器数据
- ✅ 导入的资源可以正常使用
- ✅ 无 Photon 时不影响 AssimpLoader 功能
- ✅ 有完整的用户文档

---

## 下一步

准备好开始 Phase 1 了吗？

执行以下命令开始：
```powershell
Write-Host "=== Starting Photon Integration Project ===" -ForegroundColor Cyan
Write-Host "Phase 1: Infrastructure Setup" -ForegroundColor Green
```
