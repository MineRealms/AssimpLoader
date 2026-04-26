# Photon Integration Testing Guide

## 修复历史

### 2026-04-27 - DialogWidget API 修复

**问题：**
```
java.lang.NoSuchMethodException: com.lowdragmc.lowdraglib.gui.widget.DialogWidget.showFileDialog(java.lang.Object,...)
```

**原因：**
- 使用了错误的参数类型 `Object.class`
- 实际 API 要求第一个参数是 `WidgetGroup.class`

**修复：**
```java
// 错误：
Method showFileDialogMethod = dialogWidgetClass.getMethod(
    "showFileDialog",
    Object.class, String.class, File.class, boolean.class,
    java.util.function.Predicate.class, java.util.function.Consumer.class
);

// 正确：
Class<?> widgetGroupClass = Class.forName("com.lowdragmc.lowdraglib.gui.widget.WidgetGroup");
Method showFileDialogMethod = dialogWidgetClass.getMethod(
    "showFileDialog",
    widgetGroupClass, String.class, File.class, boolean.class,
    java.util.function.Predicate.class, java.util.function.Consumer.class
);
```

**状态：**
- ✅ MeshesResourceMixin 已修复
- ✅ MaterialsResourceMixin 已修复
- ✅ 编译成功
- ✅ 已部署到游戏
- ⏳ 等待游戏重启测试

---

## 测试步骤

### 1. 启动游戏
```powershell
cd H:\MinecraftMods\ImprovedMobs
.\testgame.ps1
```

### 2. 打开 Photon 编辑器
- 进入游戏后，按 `F12` 或使用命令打开 Photon 编辑器

### 3. 测试 Mesh 导入
1. 在 Photon 编辑器中，切换到 **Meshes** 面板
2. 在面板空白处 **右键点击**
3. 应该看到菜单项：**"Import FBX Mesh"**
4. 点击该菜单项
5. 应该弹出文件选择对话框
6. 选择一个 FBX/OBJ/GLTF 文件
7. 检查是否成功导入并显示在列表中

### 4. 测试 Material 导入
1. 切换到 **Materials** 面板
2. 在面板空白处 **右键点击**
3. 应该看到菜单项：**"Import FBX Materials"**
4. 点击该菜单项
5. 应该弹出文件选择对话框
6. 选择一个 FBX/OBJ/GLTF 文件
7. 检查是否成功导入材质并显示在列表中

---

## 日志检查

### 成功标志
在 `logs/latest.log` 中应该看到：
```
[INFO]: Adding FBX import menu to MeshesResource
[INFO]: Adding FBX material import menu to MaterialsResource
[INFO]: FBX import menu added successfully
[INFO]: Opening FBX import dialog
```

### 不应该出现的错误
```
[ERROR]: Failed to open import dialog
java.lang.NoSuchMethodException: ...
```

---

## 已知问题

### ✅ 已修复
1. **Mixin 参数类型错误** - `Object panel` → `ResourcePanel panel`
2. **API 调用错误** - `getEmissiveColor()` → `getAmbientColor()`
3. **DialogWidget API 错误** - `Object.class` → `WidgetGroup.class`

### ⏳ 待验证
1. 文件选择对话框是否正常显示
2. FBX 文件是否能正确导入
3. Mesh 和 Material 是否正确转换
4. UI 是否正确刷新

---

## 技术细节

### Mixin 注入点
- **MeshesResourceMixin**: `createContainer(ResourcePanel panel)` 方法返回时
- **MaterialsResourceMixin**: `createContainer(ResourcePanel panel)` 方法返回时

### 反射调用链
```java
1. 获取 WidgetGroup 类
2. 获取 DialogWidget.showFileDialog 方法（参数：WidgetGroup, String, File, boolean, Predicate, Consumer）
3. 调用方法显示文件选择对话框
4. 用户选择文件后，回调函数被触发
5. 加载并转换 FBX 数据
6. 添加到 Photon 资源管理器
7. 刷新 UI
```

### 数据流
```
FBX File → AssimpModelLoader → AssimpModel → Converter → Photon Format → Resource Manager → UI
```

---

## 下一步

如果测试成功：
- ✅ 标记 Phase 6 完成
- 📝 更新 PHOTON_INTEGRATION_SUMMARY.md
- 🎉 庆祝完整集成完成！

如果测试失败：
- 📋 记录错误日志
- 🔍 分析问题原因
- 🔧 继续修复
