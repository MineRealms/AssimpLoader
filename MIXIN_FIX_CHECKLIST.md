# Mixin 修复检查清单

**日期**: 2026-04-27  
**问题**: Mixin 参数类型不匹配导致加载失败

---

## ❌ 原始错误

```
Invalid descriptor on mixins.photon_compat.json:MeshesResourceMixin->@Inject::addFBXImportMenu
Expected (Lcom/lowdragmc/lowdraglib/gui/editor/ui/ResourcePanel;...)
but found (Ljava/lang/Object;...)
```

**原因**: `createContainer` 方法的第一个参数是 `ResourcePanel`，而不是 `Object`

---

## ✅ 修复内容

### 1. MeshesResourceMixin.java

**修复前**:
```java
private void addFBXImportMenu(Object panel, CallbackInfoReturnable<Object> cir)
```

**修复后**:
```java
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;

private void addFBXImportMenu(ResourcePanel panel, CallbackInfoReturnable<Object> cir)
```

### 2. MaterialsResourceMixin.java

**修复前**:
```java
private void addFBXImportMenu(Object panel, CallbackInfoReturnable<Object> cir)
```

**修复后**:
```java
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;

private void addFBXImportMenu(ResourcePanel panel, CallbackInfoReturnable<Object> cir)
```

---

## 📋 完整检查清单

### 编译时检查

- [x] **导入语句正确**
  - [x] `import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;`
  - [x] `import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;`
  - [x] 所有其他必要的导入

- [x] **Mixin 注解正确**
  - [x] `@Mixin(value = MeshesResource.class, remap = false)`
  - [x] `@Mixin(value = MaterialsResource.class, remap = false)`
  - [x] 不使用 `targets = "..."`（避免警告）

- [x] **方法签名匹配**
  - [x] 参数类型: `ResourcePanel panel`（不是 `Object panel`）
  - [x] 返回类型: `CallbackInfoReturnable<Object> cir`
  - [x] 方法名: `createContainer`

- [x] **编译通过**
  - [x] `./gradlew compileJava` 成功
  - [x] 无编译错误
  - [x] 无 Mixin 处理器错误

### 运行时检查

- [ ] **Mixin 加载成功**
  - [ ] 日志中没有 "Mixin apply failed" 错误
  - [ ] 日志中有 "Adding FBX import menu" 信息

- [ ] **功能测试**
  - [ ] Photon 编辑器可以打开
  - [ ] Meshes 面板右键菜单有 "Import FBX Mesh"
  - [ ] Materials 面板右键菜单有 "Import FBX Materials"
  - [ ] 文件选择对话框可以打开
  - [ ] FBX 文件可以成功导入

---

## 🔍 源码验证

### MeshesResource.createContainer 方法签名

**文件**: `H:/MinecraftMods/Photon/common/src/main/java/com/lowdragmc/photon/gui/editor/MeshesResource.java`

**第 52 行**:
```java
public ResourceContainer<MeshData, ImageWidget> createContainer(ResourcePanel panel) {
    // ...
}
```

**确认**: ✅ 第一个参数是 `ResourcePanel panel`

### MaterialsResource.createContainer 方法签名

**文件**: `H:/MinecraftMods/Photon/common/src/main/java/com/lowdragmc/photon/gui/editor/MaterialsResource.java`

**第 74 行**:
```java
public ResourceContainer<IMaterial, ImageWidget> createContainer(ResourcePanel panel) {
    return new MaterialsResourceContainer(this, panel);
}
```

**确认**: ✅ 第一个参数是 `ResourcePanel panel`

---

## 🧪 测试步骤

### 1. 检查日志

**日志文件**: `G:\MinecraftGames\CTNH-BaopuEdition\.minecraft\versions\Create New Horizon\logs\latest.log`

**搜索关键词**:
```bash
grep -i "MeshesResourceMixin\|MaterialsResourceMixin" latest.log
```

**期望输出**:
```
[MeshesResourceMixin] Adding FBX import menu to MeshesResource
[MeshesResourceMixin] FBX import menu added successfully
[MaterialsResourceMixin] Adding FBX material import menu to MaterialsResource
[MaterialsResourceMixin] FBX material import menu added successfully
```

**不应该出现**:
```
Mixin apply failed mixins.photon_compat.json:MeshesResourceMixin
Invalid descriptor on mixins.photon_compat.json:MeshesResourceMixin
```

### 2. 游戏内测试

#### Mesh 导入测试
1. 启动 Minecraft
2. 打开 Photon 编辑器
3. 进入 "Meshes" 面板
4. 右键点击空白区域
5. 查看是否有 "Import FBX Mesh" 选项
6. 点击后是否打开文件选择对话框
7. 选择 FBX 文件后是否成功导入

#### Material 导入测试
1. 在 Photon 编辑器中进入 "Materials" 面板
2. 右键点击空白区域
3. 查看是否有 "Import FBX Materials" 选项
4. 点击后是否打开文件选择对话框
5. 选择 FBX 文件后是否成功导入

---

## 📝 其他修复

### AssimpMaterial API 调用

**问题**: `getEmissiveColor()` 方法不存在

**修复**: 使用 `getAmbientColor()` 作为自发光的近似值

```java
// 修复前
if (assimpMat.getEmissiveColor() != null) {
    pbrMat.setEmissiveFactor(new org.joml.Vector3f(
        assimpMat.getEmissiveColor()[0],
        assimpMat.getEmissiveColor()[1],
        assimpMat.getEmissiveColor()[2]
    ));
}

// 修复后
float[] ambient = assimpMat.getAmbientColor();
if (ambient != null && (ambient[0] > 0.01f || ambient[1] > 0.01f || ambient[2] > 0.01f)) {
    pbrMat.setEmissiveFactor(new org.joml.Vector3f(
        ambient[0],
        ambient[1],
        ambient[2]
    ));
}
```

### AssimpModelLoader 实例化

**问题**: `AssimpModelLoader.loadModel()` 不是静态方法

**修复**: 使用 `new AssimpModelLoader().loadModel()`

```java
// 修复前
AssimpScene scene = AssimpModelLoader.loadModel(testFile);

// 修复后
AssimpModelLoader loader = new AssimpModelLoader();
AssimpScene scene = loader.loadModel(testFile);
```

---

## ✅ 最终状态

- [x] 所有编译错误已修复
- [x] Mixin 参数类型正确
- [x] 导入语句完整
- [x] 编译成功
- [ ] 等待运行时验证

---

**下一步**: 等待构建完成，启动游戏测试 Mixin 是否正确加载
