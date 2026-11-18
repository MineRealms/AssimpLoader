# Oculus PBR 与传送门实机验收

日期：2026-11-13，北京时间。记录基于 `feat/oculus-injector` / `ab9318e` 的修复与实机验收。本文接续[首轮修复报告](FIXES_2026-11-13.md)，美术制作入口为 [PBR 资产规范](../guides/PBR_ASSET_SPEC.md)，游戏配置见 [PBR 使用说明](../guides/OCULUS_PBR_USAGE.md)。

## 已完成

- Blender FBX 与 glTF/GLB 材质保存法线、粗糙度、金属度、AO、发光和相应数值参数，按源工作流转换为 LabPBR。
- 内嵌、磁盘和资源图片共用材质级纹理身份；相同底色、不同 PBR 参数不会串用 holder。
- 有界后台任务解码、打包图片；客户端正常渲染流程上传。自有动态纹理通过 Oculus 的公开 loader registry 注册，子纹理由 Oculus 管理。
- 世界渲染使用标准 NEW_ENTITY 三角形路径，保留 Oculus 的平滑法线和切线计算。未覆盖全局 DynamicTexture loader 或修改私有 holder map。
- 修复 glTF `alphaMode=BLEND`、先不透明后透明的提交顺序，覆盖静态模型和 CPU 蒙皮。玻璃使用 alpha 混合与 PBR 高光。
- 修复测试天气的客户端同步、断线后世界清理及恢复阶段异常/超时收尾。截图、资源重载和光影切换由正常客户端入口执行。
- 开发 Rosetta 依赖通过可配置 Maven 坐标 remap，路径和 token 不进入产品代码；未捆绑进发布包。

## 原创低面数传送门

文件在 [`model-tests/portal/`](../../model-tests/portal)，包括可编辑 `.blend`、嵌入贴图的 FBX/GLB、PNG、预览和 manifest。生成脚本为 [`build_portal_pbr.py`](../../model-tests/scripts/build_portal_pbr.py)。

| 项目 | 实际内容 |
| --- | --- |
| 几何 | 77 个源网格部件，3,266 个三角面；STATIC 导入合并为 8 个材质网格 |
| 尺寸 | 宽 3.48、高 3.8975、厚 1.2625 格；底面中央根枢轴 `(0,0,0)` |
| 材质 | 钛框、陶瓷护板、抛光钢边、铜线圈、橡胶、玻璃、灯条、能量膜，共 8 套 |
| 图片 | 每套均有真实 512x512 albedo、OpenGL normal、roughness、metallic PNG，全部 UV0 |
| 法线细节 | 非对称 L 形槽、斜槽、螺钉和细颗粒，直接从确定性高度场生成，不是仅数值材质 |
| 透明 | 两扇薄片玻璃 alpha0.24，后方有铜线圈和发光芯；能量膜最终 alpha0.18，避开实体着色器的 alpha 裁切阈值 |
| 归属 | 原创几何和生成贴图，CC0；未调用外部生图 API |

世界放置 ID 为 `assimploader:showcase_portal_pbr`，可使用 `/give @s assimploader:showcase_portal_pbr` 或 `/assimpdemo`。八组展台预检空间已扩大为最多 17x6x17，包含传送门高度。

## 统一构建

主线统一运行 Gradle，子代理未并发构建。

```powershell
.\gradlew.bat test build '-Pforge_version=47.4.10' --console=plain --no-daemon --no-parallel
```

截图验收对应的构建在 16:43 完成，耗时 56 秒，`BUILD SUCCESSFUL`。JUnit XML：43 个测试类、151 项测试，0 失败、0 错误、0 跳过。较早的 Forge 47.1.3 构建同样通过 151 项测试。下列包大小和哈希对应这次构建；清理后的发布包见下一节。

新增或更新的关键覆盖：真实 FBX/GLB 的八材质图像引用、77 部件与三角面、玻璃透明度和单位尺寸；共享底色的独立 PBR 材质；LabPBR 通道/法线转换；glTF BLEND/MASK/OPAQUE 分类；静态与 CPU 蒙皮的透明提交顺序。

发布包：`build/libs/assimploader-1.0.0-all.jar`，28,964,867 字节。

SHA-256：`D3D0F79F68A3BE09663E9FD282DCE776EA7AE62FD2D75166B8899F64323DB720`。

核对了内嵌的探针和传送门 FBX、Assimp 3.3.1 绑定与 Windows/Linux/macOS 的 x64/ARM64 原生库。Oculus、光影包和 Rosetta 不捆绑进发布 jar。测试报告为 `build/reports/tests/test/index.html`；构建日志为 `logs/pbr-final-build-20261113.log`。

### 发布前清理与再次构建

按用户要求移除 CNC/CCB 方块、方块实体与 renderer、独占注册类、FBX、资源 JSON、翻译、外置美术图片及相关调试截图；缓存和原生导入测试改用保留的展示资源。CIWS、刀、测试件、PBR 探针和传送门保留。旧存档中的 `assimploader:cnc` / `assimploader:cnc_block_entity` 会成为缺失注册项。

预览 smoke 从展示枚举获取全部八个模型，GUI 物品布局同时覆盖八项。移除已完成的代理任务/交接草稿和 Blender 自动备份；当前使用入口统一指向资产规范、PBR 使用及本验收记录。

清理后重新执行上面的 Forge 47.4.10 `test build`，`BUILD SUCCESSFUL in 1m 23s`；43 个测试类、151 项测试，0 失败、0 错误、0 跳过。构建日志：`logs/release-cleanup-build-20261113.log`。

清理后发布包 `build/libs/assimploader-1.0.0-all.jar` 为 27,987,402 字节，SHA-256 为 `9FA4CF70D69B5F7C3F97C3E976D8C5D1439722A371F82B329CF86283F545F904`。归档检查确认 CNC/CCB 条目为零，传送门资源及六平台原生库仍在。Git 差异空白检查通过。

本节为源码、测试和发布归档验收；下方截图来自清理前已通过的实际客户端。本次没有为了清理重启客户端，也不把新预览 smoke 的八场景布局称为已重新实机验证。

### 文档整理与提交拆分

按用户要求将原大提交拆为 30 个按模块组织的提交；指南、项目说明、资产说明和阶段报告统一整理到 `docs/`，历史报告位于 `docs/archive/`，根 README 仅保留导航。除文档、链接及第三方声明的两个打包输入路径外，Java、资源与模型内容保持一致。

文档迁移后再次执行相同 `test build`，`BUILD SUCCESSFUL in 40s`；源码和测试未变，测试任务为 `UP-TO-DATE`，沿用此前 43 类、151 项测试全部通过的结果。日志为 `logs/docs-history-build-20261113.log`。检查发布 jar 仍包含 `META-INF/LICENSE`、`META-INF/THIRD_PARTY_NOTICES.md`、依赖许可和传送门 FBX。

本次重新打包 SHA-256：`659FB6BD0F92F0EBD0D9F12065A512D164FD5BC51F2ADDD031EBE1A410E4E511`。截图和客户端运行记录仍对应之前注明的构建，没有因提交拆分重新启动游戏。

## 世界光影验证

组合：Windows x64、Java 17.0.18、Oculus 1.8.0、Embeddium 0.3.31、Complementary Unbound r5.3，`RP_MODE=3`、`BLOCK_REFLECT_QUALITY=3`、`NORMAL_MAP_STRENGTH=100`。运行分辨率为 1280x720。

完整旧 Forge 验证于 16:34 通过，run ID `1794558736420`，日志为 `logs/pbr-rosetta-legacy-client-20261113.log`。该次使用 Forge 47.1.3 和初版能量膜；完成十张世界截图及默认的世界/玩家状态恢复。初版雨雾问题已经通过标准天气同步包修正。

| 检查 | 结果 |
| --- | --- |
| 材质区分 | 哑光/抛光介质与抛光金属共享底色，转换后 smoothness 为 31/224/224，reflectance 为 10/10/255 |
| 独立纹理 | 三套 base、normal、specular 子 ID 各自独立且为正；来源为 `converted:BLENDER_FBX` |
| PBR 开关 | 同一光影下关闭 authored PBR 后，球体反射差异和传送门材质细节/发光变化可见 |
| 资源重载 | 走 `reloadResourcePacks()`，与 F3+T 相同资源重载链；重新取得纹理对象，材质图像与效果恢复 |
| 光影切换 | 光影关闭后拍原版画面，再启用光影并检查 child ID 与画面 |
| 其他资产 | 带图集的 CIWS 与刀在世界中正常显示；不把它们的标量材质称为作者制作的法线图 |
| 传送门 | 8 个运行时网格、8 个材质、8 套 normal/roughness/metallic，2 个透明材质；PBR 开关与重载后画面一致恢复 |
| GL 检查 | 截图边界未检测到 OpenGL 错误；不据此宣称整份日志零警告 |

保留的实际图片：

| 场景 | 图片 |
| --- | --- |
| 探针 PBR 开 | [probe-on](../images/pbr/probe-on-20261113.png) |
| 探针 PBR 关 | [probe-off](../images/pbr/probe-off-20261113.png) |
| 光影关 | [shaders-off](../images/pbr/shaders-off-20261113.png) |
| CIWS | [ciws](../images/pbr/ciws-20261113.png) |
| 刀 | [knife](../images/pbr/knife-20261113.png) |
| 传送门 PBR 开，最终近景 | [portal-on](../images/pbr/portal-on-20261113.png) |
| 传送门 PBR 关，同一光影 | [portal-off](../images/pbr/portal-off-20261113.png) |
| 传送门资源重载后 | [portal-reload](../images/pbr/portal-reload-20261113.png) |

最终展示使用 Forge 47.4.10 + F3Tweaker + 修复后的 Rosetta，run ID `1794559831218`，16:52:56 完成全部十张截图，日志标记 `SUCCESS ... keepOpen=true`。世界光影、八套材质和两次资源重载检查通过。使用更近的传送门视角和最终 alpha0.18 能量膜；上表三张传送门图片来自这次运行。

客户端验收结束时按用户要求保持打开，展示模型方块位于 `(61,-52,-160)`，玩家停在其前方，世界保持晴天。该模式没有执行默认的展台/观察位置回滚。独立启动器的 PowerShell PID 为 43900，验收时 Java 客户端 PID 为 48144；这些 PID 仅为本次状态记录，不供以后直接停止进程。发布前复核发现该客户端已退出；日志记录 17:31 正常保存世界并停止，本轮清理未执行客户端停止命令。

当前实例安装 `run/mods/F3Tweaker-1.20.1-1.0.0.jar`。原 jar 的 `mods.toml` 两个依赖项误用 `type="required"`，在两版 Forge 均因缺少 `mandatory` 被拒绝。只将这两个字段改成 `mandatory=true`，保留其 Forge 47.4.10 最低版本要求；F3Tweaker 自身 `build` 在 19 秒内成功，未改功能实现。安装 jar 与重建产物 SHA-256 均为 `B30C34698902591B71DA3A0C95EA79C47ED18ECAA772A1245A9E503A64898411`。只读 ModList 查询已确认 `f3TweakerLoaded=true`；未额外验收其 F3 页面配置行为。

最终运行的全部 PNG 位于 `run/screenshots/assimp-pbr-smoke-1794559831218-*.png`，日志为 `logs/pbr-display-client-20261113.log` 和 `logs/pbr-display-game-20261113.log`。截图不会因通过构建而自动判为成功，主线已检查图片内容。

## Rosetta 修复与调试

Rosetta 使用 Forge 47.4.10 编译，声明支持 `[47,)`，但自动事件订阅器的方法签名直接引用了 Forge 47.1.3 不存在的 7 种事件类型，因此在注册阶段抛出 `NoClassDefFoundError`。这属于 Forge API 版本兼容问题，生产环境也可能触发，不是仅开发环境 remap 的问题。

修复位于 `RainJava-work/RosettaRemoteDebugBridge`：可选事件按类存在性注册，自动订阅器保留基础事件；swap 基类监听同时覆盖 Hands，避免同一事件重复转发。原有其他工作区改动保持原样。

Rosetta 自身 `build` 在 44 秒内通过；没有现成 JUnit 测试，未将 `NO-SOURCE` 称为单元测试通过。新 jar 为 8,824,218 字节，SHA-256 `9FC14030EA13CF5FA5B71115B9FA4B69FAFE5A39B25175CEF018F8E67A227858`。类文件检查器对两版 Forge 均检查 184 种自动订阅事件，`missing=0`。

已在真实 Forge 47.1.3 客户端验证：旧版本跳过缺失事件，集成世界启动，`127.0.0.1:48801` 的鉴权 ping 返回 `ok=true`；只读 Java 查询在 `Server thread` 上返回九个已加载场景，含传送门全部 PBR 输入和两个透明材质。证据为 `logs/rosetta-ping-47.1.3.json`、`logs/rosetta-models-47.1.3.json`。未通过桥调用 GL、纹理上传、客户端截图或资源重载。

最终 Forge 47.4.10 实例的 ping 与相同只读查询也返回 `ok=true`，并确认 F3Tweaker 已加载。证据为 `logs/rosetta-ping-47.4.10.json`、`logs/rosetta-models-47.4.10.json`。桥仍绑定回环地址，token 仅从实例目录读取。

详细 Rosetta 修改与复现在本机相邻项目 `RainJava-work/RosettaRemoteDebugBridge/docs/FORGE_COMPATIBILITY_2026-11-13.md`，不属于本仓库上传内容。

## 限制

- 玻璃为 alpha 混合与 PBR 高光，没有转换 Blender Transmission/IOR/厚度或真实折射；多层透明三角形仍无通用排序。
- LookDev 仍是底色 studio 预览；完整光影检查使用世界渲染。
- 仅 UV0。部分金属度、彩色/HDR 发光有文档记录的近似；未实现位移几何和完整节点图。
- 当前有非对称法线贴图和纯像素转换测试，但未完成带方向标记的绝对法线方向、镜像 UV 全套视觉验收。
- 未测试其他光影包、操作系统或 GPU；打包原生库不等于跨平台实机通过。没有运行 benchmark。
- 日志仍有外部模组的开发适配器、可选 mixin、动态 Mixin agent 和离线 Realms 等警告，不声称日志完全无警告。
