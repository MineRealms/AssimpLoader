---
name: mc-debug
description: 🎮 Minecraft 深度调试工具 - 整合 JDWP 和 Mod 内置调试功能
tags: [minecraft, debug, jdwp, opengl, performance]
runsCommands: false
---

# 🎮 Minecraft 深度调试工具

强大的 Minecraft 调试 Skill，整合 JDWP 协议和 Mod 内置调试功能，提供全方位的游戏状态分析。

## 使用方法

```
/mc-debug [命令] [参数]
```

## 可用命令

### 🎯 快速诊断
- `/mc-debug status` - 完整状态报告（游戏状态 + 性能 + GL 错误）
- `/mc-debug health` - 健康检查（FPS、内存、错误日志）
- `/mc-debug crash` - 崩溃分析（错误日志 + 主线程堆栈 + GL 错误）

### ⏸️ 游戏控制
- `/mc-debug pause` - 暂停游戏（使用 Mod）
- `/mc-debug resume` - 恢复游戏
- `/mc-debug freeze` - 冻结 JVM（使用 JDWP，更底层）
- `/mc-debug unfreeze` - 解冻 JVM

### 🔍 线程分析
- `/mc-debug threads` - 列出所有线程（JDWP）
- `/mc-debug stack [线程名]` - 查看指定线程调用栈（默认 Render thread）
- `/mc-debug stacks` - 所有线程堆栈（Mod，已反混淆）
- `/mc-debug render-stack` - 渲染线程详细堆栈

### 🎨 OpenGL 调试
- `/mc-debug gl` - OpenGL 完整信息（驱动、版本、扩展）
- `/mc-debug gl-state` - 当前 GL 状态（绑定、启用的功能）
- `/mc-debug gl-errors` - GL 错误列表
- `/mc-debug splat-debug` - Gaussian Splatting 专用调试（GL 状态 + 错误 + 渲染堆栈）

### 📊 性能分析
- `/mc-debug perf` - 性能指标（FPS、MSPT、内存使用）
- `/mc-debug memory` - 内存详情（堆使用、GC 状态）
- `/mc-debug entities` - 实体列表（类型、数量、位置）

### 🔎 类和对象
- `/mc-debug class <类名>` - 查看类信息（Mod）
- `/mc-debug classes <模式>` - 搜索已加载的类（JDWP）
- `/mc-debug find-splat` - 查找 GaussianSplat 相关类

### 📝 日志分析
- `/mc-debug logs` - 最近日志
- `/mc-debug errors` - 最近错误日志
- `/mc-debug player` - 玩家信息（位置、维度、游戏模式）

### 🚀 高级功能
- `/mc-debug deep-dive` - 深度分析（所有信息汇总）
- `/mc-debug compare-before-after` - 执行操作前后对比（暂停 → 获取状态 → 恢复 → 再次获取）

---

## 实现逻辑

当用户调用 `/mc-debug [命令]` 时，你需要：

1. **解析命令参数**
2. **调用对应的 MCP 工具**（可能需要多个工具组合）
3. **格式化输出结果**

### 工具映射

#### Mod 内置工具（minecraft-debug）
- `mc_pause` - 暂停游戏
- `mc_resume` - 恢复游戏
- `mc_toggle` - 切换暂停
- `mc_status` - 游戏状态
- `mc_get_stack` - 主线程堆栈
- `mc_get_all_stacks` - 所有线程堆栈
- `mc_get_logs` - 日志
- `mc_get_errors` - 错误日志
- `mc_get_performance` - 性能数据
- `mc_get_entities` - 实体列表
- `mc_get_player` - 玩家信息
- `mc_get_gl_info` - OpenGL 信息
- `mc_get_gl_state` - GL 状态
- `mc_get_gl_errors` - GL 错误
- `mc_get_class_info` - 类信息

#### JDWP 工具（minecraft-jdwp）
- `mc_suspend` - 暂停 JVM
- `mc_resume` - 恢复 JVM
- `mc_threads` - 线程列表
- `mc_thread_frames` - 线程调用栈
- `mc_classes` - 搜索类
- `mc_version` - JVM 版本

### 命令实现示例

#### `/mc-debug status`
```
1. 调用 mc_status（游戏状态）
2. 调用 mc_get_performance（FPS/内存）
3. 调用 mc_get_gl_errors（GL 错误）
4. 格式化输出：
   🎮 游戏状态: RUNNING
   📊 FPS: 60 | MSPT: 15ms
   💾 内存: 2.5GB / 8GB
   🎨 GL 错误: 无
```

#### `/mc-debug crash`
```
1. 调用 mc_get_errors（错误日志）
2. 调用 mc_get_stack（主线程堆栈）
3. 调用 mc_get_gl_errors（GL 错误）
4. 分析并输出：
   ❌ 最近错误:
   [错误列表]
   
   📍 主线程堆栈:
   [堆栈跟踪]
   
   🎨 GL 错误:
   [GL 错误列表]
   
   💡 可能原因: [AI 分析]
```

#### `/mc-debug splat-debug`
```
1. 调用 mc_pause（暂停游戏）
2. 调用 mc_get_gl_state（获取 GL 状态）
3. 调用 mc_get_gl_errors（获取 GL 错误）
4. 调用 mc_thread_frames({ threadName: "Render thread" })（渲染堆栈）
5. 调用 mc_resume（恢复游戏）
6. 分析 Gaussian Splatting 相关问题
```

#### `/mc-debug deep-dive`
```
1. 调用 mc_pause
2. 并行调用所有诊断工具：
   - mc_status
   - mc_get_performance
   - mc_get_all_stacks
   - mc_get_gl_info
   - mc_get_gl_state
   - mc_get_gl_errors
   - mc_get_entities
   - mc_get_player
   - mc_threads
   - mc_version
3. 调用 mc_resume
4. 生成完整诊断报告
```

### 输出格式规范

使用清晰的 emoji 和分段：

```
🎮 Minecraft 调试报告
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📊 性能指标
  FPS: 60 (目标: 60)
  MSPT: 15ms (目标: <50ms)
  内存: 2.5GB / 8GB (31%)

🎨 OpenGL 状态
  驱动: NVIDIA GeForce RTX 3080
  版本: 4.6.0
  错误: 无

🧵 线程状态
  总线程数: 42
  渲染线程: RUNNING
  主线程: RUNNING

💡 建议
  ✅ 性能正常
  ⚠️ 内存使用偏高，建议检查实体数量
```

### 错误处理

如果 MCP 工具调用失败：
1. 检查游戏是否运行（`mc_status`）
2. 检查 JDWP 是否连接（`mc_version`）
3. 给出友好的错误提示：
   ```
   ❌ 无法连接到 Minecraft
   
   请确保：
   1. 游戏正在运行
   2. mcpisnotmcp-1.0.0.jar 已安装
   3. testgame.ps1 包含 JDWP 参数
   
   尝试运行: ./testgame.ps1
   ```

### 智能分析

当发现问题时，提供 AI 分析：

- **GL_INVALID_VALUE** → 检查 buffer 大小、绑定状态
- **高 MSPT** → 检查实体数量、区块加载
- **内存泄漏** → 分析堆栈中的对象创建
- **渲染卡顿** → 检查 GL 状态、shader 编译

---

## 使用场景

### 场景 1: 调试 Gaussian Splatting 渲染问题
```
/mc-debug splat-debug
```
→ 暂停游戏，获取 GL 状态和错误，分析渲染管线

### 场景 2: 性能优化
```
/mc-debug perf
/mc-debug entities
```
→ 查看 FPS 和实体数量，找出性能瓶颈

### 场景 3: 崩溃分析
```
/mc-debug crash
```
→ 获取错误日志、堆栈跟踪、GL 错误，定位崩溃原因

### 场景 4: 深度诊断
```
/mc-debug deep-dive
```
→ 生成完整的系统状态报告

---

## 注意事项

1. **JDWP 暂停 vs Mod 暂停**
   - `mc_suspend`（JDWP）：冻结整个 JVM，包括所有线程
   - `mc_pause`（Mod）：只暂停游戏逻辑，渲染线程可能继续运行

2. **性能影响**
   - 频繁调用 `mc_get_all_stacks` 可能影响性能
   - 建议在暂停状态下进行深度分析

3. **线程安全**
   - 使用 JDWP 查看堆栈时，建议先暂停 JVM
   - Mod 工具已处理线程安全问题

---

## 快速参考

| 命令 | 用途 | 工具 |
|------|------|------|
| `status` | 快速状态检查 | Mod |
| `health` | 健康检查 | Mod |
| `crash` | 崩溃分析 | Mod + JDWP |
| `pause/resume` | 游戏控制 | Mod |
| `freeze/unfreeze` | JVM 控制 | JDWP |
| `threads` | 线程列表 | JDWP |
| `stack` | 线程堆栈 | JDWP |
| `gl` | OpenGL 信息 | Mod |
| `gl-errors` | GL 错误 | Mod |
| `perf` | 性能指标 | Mod |
| `deep-dive` | 完整诊断 | 全部 |

---

**提示**: 这是一个智能 Skill，我会根据你的需求自动选择最合适的工具组合！
