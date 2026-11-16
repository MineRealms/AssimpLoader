# 带骨骼 FBX 实机验收

日期：2026-11-16。基于 [第一阶段动画基础](ANIMATION_FOUNDATION_2026-11-16.md)，补齐真实 Blender 资产、FBX 导入和客户端 GPU 绘制验证。

## 测试资产

本次自行制作了一个不对称的机械臂，未使用第三方下载资产。Blender 连接插件没有响应，因此使用本机 Blender 5.3.0 后台进程生成、渲染和导出。

| 内容 | 文件或数据 |
|---|---|
| 可编辑源文件 | [animation_probe.blend](../model-tests/animation_probe.blend) |
| 生成与导出脚本 | [create_animation_probe.py](../model-tests/create_animation_probe.py) |
| Blender 预览 | [animation_probe.png](../model-tests/animation_probe.png) |
| FBX | [animation_probe.fbx](../src/main/resources/assets/assimploader/models/validation/animation_probe.fbx)，168188 字节 |
| 资产清单 | [animation_probe.json](../model-tests/animation_probe.json) |
| Blender 独立参考值 | [animation_probe_reference.json](../src/test/resources/fixtures/animation_probe_reference.json) |
| 骨骼 | `Shoulder`、`Elbow` 两个变形骨骼；`ToolSocket` 为非变形挂点节点 |
| 动作 | `Idle`、`Reach`、`Fold`；24 FPS，每段 2 秒 |
| 网格 | 8 个网格；同时包含刚性单骨骼权重、双骨骼混合权重和非蒙皮底座 |
| 贴图 | 自制 64x64 棋盘纹理，嵌入 FBX；另有不同颜色的材质 |

本轮 FBX SHA256：`885D79AAEDBD36BD57492601D0EE3844A80F0FFD2533CFB690E917F9F3FF6EB4`。

导出使用 `-Z` forward、`Y` up、`FBX_SCALE_ALL`，保留三个独立 Action，不增加叶骨骼。Assimp 导入动作名带有 `AnimationProbeRig|` 前缀。参考值按本项目现有 FBX 坐标合同，将 Blender `(x,y,z)` 映射为 `(-x,z,-y)`；没有修改现有坐标转换器。

## 发现与处理

导出前停在活动动画姿态，会使双骨骼混合权重部位的初始绑定几何与节点矩阵不一致。生成脚本已改为在导出前清除活动 Action、恢复静止姿态并更新依赖图；导出后再恢复用于预览的动作。实际 FBX 的绑定蒙皮测试通过，不需要修改加载器来补偿这个资产导出问题。

## 自动测试

新增 [BlenderAnimationProbeTest](../src/test/java/cn/minerealms/assimploader/animation/BlenderAnimationProbeTest.java) 的 4 项测试：

- 通过实际 Assimp 导入 FBX，保留三个动作、变形权重和非变形挂点。
- 绑定姿态下，所有带权重网格的 CPU 蒙皮顶点与节点变换后的原始几何吻合。
- 双实例不同动作、可中断过渡、旧快照保持、停止及保守包围盒包含变形顶点。
- 三个动作在 0.5 秒的挂点位置和精确几何边界，与 Blender 依赖图直接计算的参考值比较，距离容差为 `1e-4` 模型单位。

完整测试：**200 项、52 个测试类，0 失败、0 错误、0 跳过**；`build` 通过。

## 客户端结果

验收入口：[AnimationSmokeTest](../src/main/java/cn/minerealms/assimploader/client/AnimationSmokeTest.java)，仅在 `-PclientAnimationSmoke` 时启用。它从游戏资源加载 FBX，为同一共享场景创建两个独立实例，分别绘制 CPU 与显式 GPU palette 路径。GPU 失败不会通过 CPU 回退掩盖。

每阶段先计算并冻结目标姿态，随后渲染至少 10 秒，让异步贴图完成加载；四个离屏目标均为 `384x384`。黄色十字为 `ToolSocket` 加局部偏移后的屏幕投影。

运行环境：Minecraft 1.20.1、Forge 47.1.3、Java 17.0.18、RTX 4060、OpenGL 4.6；GPU 使用 TBO。未加载 Oculus，运行时排除可选 Photon/LDLib。F3Tweaker 已删除。

| 阶段 | A：CPU/GPU 有效像素数 | B：CPU/GPU 有效像素数 | 轮廓掩码差异 |
|---|---:|---:|---:|
| 独立动作 | 12108 / 12108 | 12128 / 12128 | 两组均 0 |
| 过渡中点 | 12571 / 12571 | 12541 / 12541 | 两组均 0 |
| 过渡中断 | 10345 / 10345 | 10716 / 10716 | 两组均 0 |
| 停止回绑定姿态 | 12394 / 12394 | 12449 / 12449 | 两组均 0 |
| 资源重载 | 12108 / 12108 | 12128 / 12128 | 两组均 0 |

程序还检查了修改 A 不推进 B、过渡中断瞬间挂点不跳变、停止恢复绑定挂点、重载关闭旧实例、旧快照不被重载修改、新场景重新加载，以及绘制后没有 OpenGL 错误。日志在 16:48:53 报告 `[AnimationSmoke] SUCCESS`，客户端正常退出。

运行编号：`1794818859753`。共 5 张截图，最短截图间隔 **10.186 秒**：

- [独立动作](../run/screenshots/assimp-animation-1794818859753-independent.png)
- [过渡中点](../run/screenshots/assimp-animation-1794818859753-crossfade.png)
- [过渡中断](../run/screenshots/assimp-animation-1794818859753-interrupted.png)
- [停止](../run/screenshots/assimp-animation-1794818859753-stopped.png)
- [资源重载](../run/screenshots/assimp-animation-1794818859753-reloaded.png)

已人工检查独立动作和过渡中点画面。截图保存在开发运行目录，后续运行日志可能覆盖；上述结果是本次验收记录。ModernUI 的既有字体资源路径告警仍存在。

## 复现

在仓库根目录执行，生成脚本需要 Blender 的 Python 环境：

```powershell
& 'G:\Program Files\Blender Foundation\Blender 5.3\blender.exe' --background --factory-startup --python model-tests/create_animation_probe.py
.\gradlew.bat test build runClient -PclientAnimationSmoke -PwithoutOptionalMods --console=plain --no-daemon --no-parallel
```

Blender 路径可替换为本机安装位置。验收无需打开存档，会从标题界面进入专用测试画面，结束后自动退出。

## 边界

本轮确认的是这个小型 FBX 的采样、姿态、绑定矩阵、CPU/GPU 轮廓和挂点位置。CPU 与 GPU 的光照实现不同，轮廓掩码相同不代表 RGBA 完全相同。挂点十字是矩阵投影验证，尚未增加实体装备、粒子或另一模型的正式挂载 API。

本次采用冻结后的定点采样，不是连续播放流畅度、20 Hz 插值质量或性能基准。尚未验证 Oculus 阴影通道、uniform 骨骼上传回退、256+ 骨骼、大量实例、Native 或 Compute。没有因此扩大为玩家替换、自定义 Steve 全套功能或新物理后端。
