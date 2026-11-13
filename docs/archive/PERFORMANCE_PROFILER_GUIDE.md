# Performance Profiler Usage Guide

## 概述

AssimpLoader集成了完整的性能分析系统，包括：
- **PerformanceProfiler**: 独立的性能计数器系统，输出到专用日志文件
- **DebugManager**: 统一的调试日志系统，支持频率限制
- **自动报告**: 每60秒自动生成性能报告

## 启用性能分析

### 方法1: JVM参数（推荐）

在启动参数中添加：
```
-Dassimploader.profiling=true
```

### 方法2: 代码中启用

```java
DebugManager.enableProfiling();
```

### 方法3: 仅启用调试日志（不启用性能分析）

```java
DebugManager.enable();  // 默认已启用
```

## 性能日志文件

性能数据输出到独立文件：
```
logs/assimploader_performance.log
```

## 性能报告格式

```
================================================================================
PERFORMANCE REPORT
Timestamp: 2026-04-28 15:30:45
================================================================================
Operation                                     Calls    Total(ms)    Avg(ms)    Min(ms)    Max(ms)
--------------------------------------------------------------------------------
circle_render_total                            1500      1250.50      0.834      0.120      5.230
circle_skinned_mesh                            1500       980.20      0.653      0.080      4.100
bone_matrix_upload                             1500       120.30      0.080      0.010      0.450
vbo_draw                                       1500       850.10      0.567      0.050      3.800
circle_baked_mesh                               300        45.80      0.153      0.020      0.890
animation_update                               1500       180.40      0.120      0.015      1.200
bone_transform_compute                          450        90.20      0.200      0.030      2.100
================================================================================
```

## 已集成的性能计数器

### 渲染系统
- `circle_render_total` - Circle方块实体总渲染时间
- `circle_skinned_mesh` - 骨骼网格渲染时间
- `circle_baked_mesh` - 烘焙网格渲染时间
- `skinned_mesh_render` - SkinnedMeshRenderer总时间
- `bone_matrix_upload` - 骨骼矩阵上传时间（TBO/Uniform）
- `vbo_draw` - VBO绘制时间

### 动画系统
- `animation_update` - 动画状态更新时间
- `bone_transform_compute` - 骨骼变换计算时间

## 在代码中使用

### 方法1: 手动计时

```java
if (DebugManager.isProfilingEnabled()) {
    PerformanceProfiler.start("my_operation");
}

// ... 执行操作 ...

if (DebugManager.isProfilingEnabled()) {
    PerformanceProfiler.end("my_operation");
}
```

### 方法2: Try-with-resources（推荐）

```java
if (DebugManager.isProfilingEnabled()) {
    try (var timer = PerformanceProfiler.time("my_operation")) {
        // ... 执行操作 ...
    }
}
```

### 方法3: 使用DebugManager包装

```java
DebugManager.timeOperation("my_operation", () -> {
    // ... 执行操作 ...
});
```

## 调试日志频率限制

为防止高频渲染操作刷屏，DebugManager自动限制相同消息的输出频率：

```java
// 自动限制：相同消息最多每秒输出一次
DebugManager.debug(DebugCategory.RENDER, "Rendering entity at {}", pos);

// 不限制：强制输出（谨慎使用）
DebugManager.debugUnlimited(DebugCategory.RENDER, "Critical message");
```

**频率限制规则**：
- 相同消息内容（基于hashCode）最多每秒输出一次
- 不同消息不受影响
- info/warn/error级别不受限制
- 仅debug级别受限制

## 性能分析最佳实践

### 1. 仅在需要时启用

性能分析有轻微开销，建议：
- 开发/调试时启用
- 生产环境默认禁用
- 性能测试时启用

### 2. 合理命名操作

使用清晰的操作名称：
```java
// 好的命名
PerformanceProfiler.start("entity_render_skinned");
PerformanceProfiler.start("bone_matrix_tbo_upload");

// 不好的命名
PerformanceProfiler.start("render");
PerformanceProfiler.start("update");
```

### 3. 避免过度细粒度

不要为每个小操作都计时：
```java
// 过度细粒度（不推荐）
PerformanceProfiler.start("vector_add");
vector.add(other);
PerformanceProfiler.end("vector_add");

// 合理粒度（推荐）
PerformanceProfiler.start("bone_transform_batch");
for (Bone bone : bones) {
    bone.transform();
}
PerformanceProfiler.end("bone_transform_batch");
```

### 4. 使用分层命名

使用下划线分隔层级：
```java
PerformanceProfiler.start("render_entity");
    PerformanceProfiler.start("render_entity_skinned");
    PerformanceProfiler.end("render_entity_skinned");
PerformanceProfiler.end("render_entity");
```

## 手动生成报告

```java
// 生成报告（不重置计数器）
PerformanceProfiler.generateReport(false);

// 生成最终报告（通常在关闭时）
PerformanceProfiler.generateReport(true);

// 重置所有计数器
PerformanceProfiler.reset();
```

## 查询性能数据

```java
// 获取操作统计信息
String stats = PerformanceProfiler.getStats("circle_render_total");
System.out.println(stats);

// 获取调用次数
long calls = PerformanceProfiler.getCallCount("circle_render_total");

// 获取平均时间
double avgMs = PerformanceProfiler.getAverageTimeMs("circle_render_total");

// 获取所有计数器名称
Set<String> counters = PerformanceProfiler.getCounterNames();
```

## 自动报告配置

```java
// 禁用自动报告（默认启用，每60秒）
PerformanceProfiler.setAutoReportEnabled(false);

// 重新启用自动报告
PerformanceProfiler.setAutoReportEnabled(true);
```

## 调试类别

DebugManager支持以下类别：

```java
public enum DebugCategory {
    CORE,       // 核心加载和初始化
    RENDER,     // 渲染系统
    MATERIAL,   // 材质系统
    ANIMATION,  // 动画系统
    LOOKDEV,    // LookDev查看器
    MODERNUI,   // ModernUI集成
    CACHE,      // 缓存系统
    LIGHT,      // 光照系统
    PERF        // 性能分析
}
```

使用示例：
```java
DebugManager.debug(DebugCategory.RENDER, "Rendering mesh: {}", meshName);
DebugManager.info(DebugCategory.ANIMATION, "Animation started: {}", animName);
DebugManager.warn(DebugCategory.PERF, "High frame time detected: {}ms", frameTime);
```

## 性能优化建议

根据性能报告识别瓶颈：

### 1. 骨骼矩阵上传慢
- 检查是否启用TBO（应该 < 0.1ms）
- 考虑减少骨骼数量
- 使用LOD降低远处实体的骨骼数

### 2. VBO绘制慢
- 检查顶点数量
- 考虑使用LOD
- 启用frustum culling

### 3. 动画更新慢
- 启用脏标记系统（已默认启用）
- 使用LOD降低更新频率
- 缓存骨骼变换

### 4. 总渲染时间慢
- 启用frustum culling（已集成）
- 启用LOD系统（已集成）
- 减少同时渲染的实体数量

## 示例：性能测试场景

```java
// 1. 启用性能分析
DebugManager.enableProfiling();

// 2. 放置100个Circle方块

// 3. 等待60秒，查看自动报告

// 4. 手动生成报告
PerformanceProfiler.generateReport(false);

// 5. 查看日志文件
// logs/assimploader_performance.log

// 6. 分析瓶颈并优化

// 7. 重置计数器重新测试
PerformanceProfiler.reset();
```

## 故障排除

### 性能日志文件未生成
- 检查是否启用了性能分析：`DebugManager.isProfilingEnabled()`
- 检查logs目录是否有写权限
- 查看主日志是否有错误信息

### 性能计数器为空
- 确保在操作前调用了`start()`
- 确保在操作后调用了`end()`
- 检查操作名称是否匹配

### 日志刷屏
- 使用`DebugManager.debug()`而不是`debugUnlimited()`
- 频率限制会自动生效（每秒最多一次）
- 考虑降低日志级别

## 性能基准

**目标性能（100个Circle方块实体）**：
- `circle_render_total`: < 1.0ms/entity
- `bone_matrix_upload`: < 0.1ms/entity（TBO模式）
- `vbo_draw`: < 0.5ms/entity
- `animation_update`: < 0.2ms/entity

**如果超过这些值**：
- 检查GPU能力检测结果
- 确认TBO已启用
- 验证LOD和frustum culling工作正常
- 查看性能报告中的最大值（Max）是否异常
