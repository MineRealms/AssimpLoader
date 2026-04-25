# LookDev Viewer 测试计划

## 测试环境
- Minecraft 版本: 1.20.1
- Mod 版本: assimploader-1.0.0.jar
- 测试整合包: Create New Horizon

## 测试前准备

1. 确保游戏中已加载 FBX 模型
   - 执行 `/assimp reload` 重新加载模型
   - 检查日志确认模型加载成功

2. 打开 LookDev Viewer
   - 执行 `/lookdev` 命令
   - 验证界面正常显示

## 测试用例

### TC-001: 模型列表显示
**目标**: 验证左侧面板显示所有已加载的模型

**步骤**:
1. 打开 LookDev Viewer
2. 查看左侧 "Models" 部分

**期望结果**:
- 显示所有缓存中的模型（如 blocks/ccb, blocks/cnc）
- 每个模型显示名称和信息（网格数、材质数）
- 当前选中的模型高亮显示

**日志检查**:
```
[LOOKDEV] Found X cached models
[LOOKDEV] Displaying X materials, selected: Y
```

---

### TC-002: 模型选择
**目标**: 验证用户可以选择不同的模型

**步骤**:
1. 在模型列表中点击不同的模型
2. 观察界面变化

**期望结果**:
- 点击的模型变为高亮状态
- 材质列表更新为新模型的材质
- 预览区域更新（当前为占位符）

**日志检查**:
```
[LOOKDEV] User selected model: blocks/ccb
[LOOKDEV] Loading model: blocks/ccb
[LOOKDEV] Model loaded: X meshes, Y materials
[LOOKDEV] Extracted Y materials from model
[LOOKDEV] Preview fragment updated with model
[LOOKDEV] Model tree updated
[LOOKDEV] Auto-selected first material
```

---

### TC-003: 材质列表显示
**目标**: 验证材质列表正确显示当前模型的所有材质

**步骤**:
1. 选择一个模型
2. 查看 "Materials" 部分

**期望结果**:
- 显示所有材质（Material 1, Material 2, ...）
- 显示材质参数（R: 0.50 M: 0.00）
- 当前选中的材质高亮显示

**日志检查**:
```
[LOOKDEV] Extracting X materials from model
[LOOKDEV] Material 0: diffuse=..., normal=..., specular=...
[LOOKDEV] Extracted X materials
```

---

### TC-004: 材质选择
**目标**: 验证用户可以选择不同的材质

**步骤**:
1. 在材质列表中点击不同的材质
2. 观察界面变化

**期望结果**:
- 点击的材质变为高亮状态
- 右侧参数面板更新为新材质的参数
- 预览区域更新（当前为占位符）

**日志检查**:
```
[LOOKDEV] Material selected: Material 2 (index: 1)
[LOOKDEV] Preview fragment updated with material
[LOOKDEV] Parameter panel updated with material
```

---

### TC-005: Fragment 生命周期
**目标**: 验证 Fragment 生命周期正常，无崩溃

**步骤**:
1. 打开 LookDev Viewer
2. 检查日志

**期望结果**:
- 无 "Fragment not attached to context" 错误
- 所有 Fragment 正常创建

**日志检查**:
```
[LOOKDEV] === LookDevScreen.onCreateView START ===
[LOOKDEV] === LookDevScreen.onCreateView COMPLETE - returning root ===
[LOOKDEV] === LookDevScreen.onViewCreated START ===
[LOOKDEV] ✓ Child fragments added successfully
[LOOKDEV] === ModelTreeFragment.onCreateView START ===
[LOOKDEV] === ModelTreeFragment.onCreateView COMPLETE - returning scrollView ===
[LOOKDEV] === PreviewFragment.onCreateView START ===
[LOOKDEV] === PreviewFragment.onCreateView COMPLETE - returning root ===
[LOOKDEV] === ParameterPanelFragment.onCreateView START ===
[LOOKDEV] === ParameterPanelFragment.onCreateView COMPLETE - returning scrollView ===
```

---

### TC-006: 网格信息显示
**目标**: 验证网格数量正确显示

**步骤**:
1. 选择一个模型
2. 查看 "Meshes" 部分

**期望结果**:
- 显示正确的网格数量

**日志检查**:
```
[LOOKDEV] Model has X meshes
```

---

## 已知限制（当前版本）

1. **预览渲染**: 中央预览区域仍然是占位符，显示网格和坐标轴，不渲染真实模型
2. **参数调整**: 右侧参数面板可以显示参数，但修改参数不会实时更新预览
3. **相机交互**: 相机控制代码存在，但未完全集成到渲染管线
4. **纹理显示**: 纹理路径已提取，但未在 UI 中显示

## 下一步开发

1. **Phase 3**: 集成 Minecraft 渲染管线到 PreviewFragment
2. **Phase 4**: 实现参数修改的实时预览更新
3. **Phase 5**: 完善相机控制和交互
4. **Phase 6**: 添加纹理通道显示和编辑

## 测试报告模板

```
测试日期: YYYY-MM-DD
测试人员: 
Mod 版本: 

| 测试用例 | 状态 | 备注 |
|---------|------|------|
| TC-001  | ✓/✗  |      |
| TC-002  | ✓/✗  |      |
| TC-003  | ✓/✗  |      |
| TC-004  | ✓/✗  |      |
| TC-005  | ✓/✗  |      |
| TC-006  | ✓/✗  |      |

发现的问题:
1. 
2. 
3. 

性能指标:
- 界面打开时间: 
- 模型切换延迟: 
- 材质切换延迟: 
```
