# FBX 世界展示

2026-11-13：将 Blender 导出的 CIWS、刀、坐标测试件、PBR 探针与科幻传送门接入 AssimpLoader，共八组资产。模型只用于视觉展示，没有射击、伤害或传送行为。

## 在世界中放置

在拥有命令权限的测试世界中执行：

```text
/assimpdemo
/assimpdemo ~4 ~ ~4
```

无参数版本在命令源右侧偏移 `(4, 0, 4)` 处开始。第二种写法可指定第一座展示台中心。命令需要等级 2 权限，按每排三座创建石制展示台、模型和双面说明牌。

每座台的中心间距为六格。预检范围覆盖三排，最大 17 格宽、6 格高、17 格深，范围起点是指定位置的 `(-2, 0, -2)`。必须位于已加载区块、世界边界和建筑高度内；范围中任何不可替换方块或方块实体都会使整个命令在放置前取消。命令不会清空原有建筑，适合在平坦地形的空气层执行。

也可以从建筑方块创造栏获取，或单独使用：

```text
/give @s assimploader:showcase_ciws_material
/give @s assimploader:showcase_ciws_textured
/give @s assimploader:showcase_knife_material
/give @s assimploader:showcase_knife_textured
/give @s assimploader:showcase_probe_no_uv
/give @s assimploader:showcase_probe_textured
/give @s assimploader:showcase_pbr_probe
/give @s assimploader:showcase_portal_pbr
```

这些都是展示方块及对应物品。旧 CNC/CCB 示例及关联资源已移除；旧存档中原有的 `assimploader:cnc` 注册项将缺失。

## 资产对照

| 缓存名/FBX 名 | 三角面 | 模式 | 检查内容 |
| --- | ---: | --- | --- |
| `ciws_material` | 2,572 | DYNAMIC | 多材质色、七个网格、完整关节层级 |
| `ciws_textured` | 2,572 | DYNAMIC | 同一几何、一张嵌入 128x128 图集 |
| `knife_material` | 162 | STATIC | 刀刃、护手、握柄材质色，无贴图 |
| `knife_textured` | 162 | STATIC | 同一几何、一张嵌入图集 |
| `probe_no_uv` | 48 | STATIC | 无 UV、无贴图，非对称彩色坐标测试件 |
| `probe_textured` | 48 | STATIC | 同一几何、有 UV 和嵌入图集 |
| `pbr_probe` | 低面数测试球与斜面 | STATIC | 共享底色的三种 PBR 材质、法线/粗糙度/金属度 |
| `portal_pbr` | 3,266 | STATIC | 77 个源部件、8 种材质；金属、玻璃、法线与发光 |

资源位于 `assets/assimploader/models/showcase/`，对应 blockstate 和 item JSON 触发 Forge geometry 导入。物品通过显式模型名映射到相同缓存，避免注册名与资产名不同导致空模型。

一单位对应一格，世界渲染仅平移到方块的地面中心 `(0.5, 0, 0.5)`，不额外缩放。CIWS 约为宽 1.863、高 1.675、长 2.863 格；刀约宽 0.35、高 1.73、厚 0.164 格，刀柄底部为原点、刀尖朝 +Y。展示模型会超出单格碰撞盒，周围应留出空间。

## CIWS 枢轴

模型保留 Y-up、+Z forward 的局部约定和对象父子层级。前缀为 `CIWS_Material` 或 `CIWS_Textured`：

| 节点后缀 | 局部旋转轴 | 相对模型根的枢轴 |
| --- | --- | --- |
| `_Yaw` | Y，方位角 | `(0, 0.38, 0)` |
| `_Pitch` | X，俯仰角 | `(0, 1.32, 0.02)` |
| `_Rotor` | Z，六管转子 | `(0, 1.32, 0.33)` |

Pitch 继承 Yaw，Rotor 同时继承前两者。此次展示保留静止姿态，不修改共享 scene 中的节点矩阵；这些 FBX 没有 armature 或烘焙动画片段。后续控制方位/俯仰时，应在实例的渲染变换中组合关节旋转，不能直接改全局缓存 scene，否则所有副本都会同步变形。

传送门尺寸约宽 3.48、高 3.90、厚 1.26 格，根枢轴位于底面中央。原始 77 个部件在 STATIC 导入时可按材质合并，形状与 UV 保留；需要单独驱动部件时改为 DYNAMIC 导入。玻璃为独立薄片，后方铜线圈可用于检查透明效果。当前是展示资产，不包含传送逻辑。

Blender 可编辑文件、GLB 对照文件、图集及导出约定见 [资产说明](../guides/TEST_ASSETS.md)。美术交付见 [PBR 资产规范](../guides/PBR_ASSET_SPEC.md)，光影设置见[当前材质说明](../guides/OCULUS_PBR_USAGE.md)，世界截图见[实机验收](../history/PBR_VALIDATION_2026-11-13.md)。

## 验证边界

火箭发动机使用独立的 `/assimpdemo rocket [pos]` 试车台，不占用上述紧凑画廊。
其喷口原点、默认尺寸、本地第三方 PBR 模型接入和许可边界见
[火箭发动机展示](ROCKET_ENGINE_SHOWCASE.md)。

`ShowcaseAssetsTest` 检查全部展示资源链、真实 FBX 内存导入、嵌入纹理有无、无 UV 测试件、CIWS 三个关节节点及有限坐标。`PortalAssetTest` 另外检查 FBX/GLB 的部件、三角面、PBR 图像、玻璃透明度和世界尺寸。主线统一执行 Gradle。

游戏内应检查八座模型的方向、完整面、材质/图集、物品 GUI/手持比例和 F3+T 重载。数据测试不证明画面和驱动行为；本次已完成的 PBR 世界画面、光影开关及重载检查见[实机验收](../history/PBR_VALIDATION_2026-11-13.md)。

后续主线已修复 FBX 单位换算和物品默认尺寸，并实际截图检查六个 GUI 物品、LookDev 七场景及资源重载后的贴图，见 [修复与验收](../history/FIXES_2026-11-13.md)。第一/第三人称、展示框和整套展台的所有视角未由这些截图覆盖。
