# 装甲车实体

当前示例实体 ID 为 `assimploader:armored_car`。模型以资源包交付，运行时读取资源位置，不依赖美术电脑的绝对路径。第三方车模和原始贴图保留在本机，不随源码公开发布。

## 使用

启用含有车模的资源包后，在空地执行：

```mcfunction
/summon assimploader:armored_car ~ ~ ~
```

右键上车，Shift 下车。W/S/A/D 沿用玩家移动键的重绑定，Space 为可重绑定的车辆手刹。前进中按 S 先刹停再倒车；松开油门保留滑行惯性。失焦、菜单、输入超时和无人驾驶会自动制动。

上车自动保存原视角并切到背后第三人称，初始俯仰为 12 度。镜头距离为 15 格，锚点约为车底上方 4.26 格，保留原版方块遮挡检测；下车恢复原视角。驾驶员模型在车内隐藏，避免四肢穿过车壳。视角可以独立环绕车辆。

## 物理与动画

| 参数 | 当前值 |
| --- | --- |
| 游戏尺寸 | 宽 4、长 9、高 5 格，含天线；原 FBX 整体放大 2 倍 |
| 质量 | 7,500 kg，影响发动机/刹车力对应的加速度 |
| 前进上限 | 0.32 格/tick，约 23 km/h |
| 倒车上限 | 0.12 格/tick，约 8.6 km/h |
| 轮半径 | 0.77236652 格 |
| 轴距 | 5.13816 格 |
| 台阶能力 | 0.6 格；没有跳跃驱动 |

服务端计算运动，客户端只发送受限的驾驶输入。数据包验证当前驾驶员、车辆 ID 和递增序号；原版车辆位置包不能直接指定这辆车的位置。客户端插值服务端位置。

轮子滚动角由碰撞后的实际有符号位移除以轮半径得到，而非固定转速计时器。加速、减速、倒车、撞墙和停驶自然反映到轮子。前轮渐进打方向并回正，车速较高时转角收小，转弯使用轴距和转角；空中没有轮胎牵引和转向。备胎不转动。

碰撞使用随朝向变化的有限轴对齐包围盒。它不是精确的旋转车身碰撞体，因此狭窄转弯处较保守；当前没有悬架刚体、侧翻、损伤或引擎音效。

## FBX 资源约定

```text
ArmoredCar_Local_PBR/
  pack.mcmeta
  assets/assimploader/models/entity/armored_car/
    armored_car.fbx
    textures/armor_*.png
    textures/runninggear_*.png
```

`pack_format=15`。资源包启用后，模型烘焙会额外加载 `models/vehicle/armored_car.json`，以 `DYNAMIC` 保留层级，资源身份为 `assimploader:models/entity/armored_car/armored_car`。没有这个可选资产时不会尝试导入缺失的 FBX。修改模型或贴图可按 F3+T；修改实体或渲染 Java 代码需要重启。

FBX 源文件保持宽 2、长 4.5、高 2.5 的基准单位。`ArmoredCarEntity.MODEL_SCALE=2` 统一控制游戏渲染、碰撞尺寸、轴距、轮半径、座位和镜头距离，避免只放大外观而使轮轴和物理错位。

模型根原点在占地底面中心，运行坐标为 +X 右、+Y 上、+Z 前。四个独立轮轴使用下列名字，每个节点下放同名轮网格：

| 枢轴节点 | 作用 |
| --- | --- |
| `Wheel_Front_Left_SteerPivot` | 前左轮转向和滚动中心 |
| `Wheel_Front_Right_SteerPivot` | 前右轮转向和滚动中心 |
| `Wheel_Rear_Left_SteerPivot` | 后左轮滚动中心 |
| `Wheel_Rear_Right_SteerPivot` | 后右轮滚动中心 |

渲染器在导入场景的全局坐标中绕轮心旋转，因此不会把 FBX 单位缩放或辅助根节点的轴当作轮轴。静态车身与备胎保持绑定变换。每个材质连接 UV0 上的 Base Color、OpenGL Normal、Roughness 和 Metallic；源 AO 图片单独保存，普通 Blender FBX 没有可靠 AO 插槽，不能据图片存在宣称它参与采样。完整规范见 [PBR 资产规范](../guides/PBR_ASSET_SPEC.md)。

Oculus 光影使用首次生成并持久上传的扩展 ENTITY 顶点；行驶时更新矩阵、光照和实体材质 ID。轮子不需要每帧重建切线和几何。没有光影时使用现有网格 GPU 缓存；不支持的材质或格式保留普通提交路径。

## 验收

```powershell
.\gradlew.bat test build jarJar '-Pforge_version=47.4.10' '-PwithOculus' --no-daemon --no-parallel
.\tools\verification\start-vehicle-client.ps1 -World Scaffold_PBR_Test
```

`clientVehicleSmoke` 是显式开发测试，会在指定本地世界生成平台、车并模拟驾驶，测试结束保留客户端和车辆。请使用测试存档。检查 `logs/armored-car-client.log` 中的 `[VehicleSmoke] SUCCESS`，并查看 `run/screenshots/assimp-vehicle-*.png`。测试覆盖加速、惯性、手刹、倒车、轮子状态、PBR 资源、第三人称相机和资源重载；构建成功本身不代表实机通过。

`ArmoredCarAssetTest` 在本地资产包存在时实际导入 FBX，校验尺寸、PBR 槽位、轮心不移动、两前轮转向、四轮滚动和静态部件不动；公开源码缺少用户车模时该项跳过，其余物理测试仍运行。

## 2026-11-13 实机结果

Forge 47.4.10、Oculus 1.8.0、`ComplementaryUnbound_r5.3.zip` 下，`test build jarJar` 通过，164 项测试全部通过。2 倍尺寸的实体完成加速、滑行、手刹、倒车、前轮转向、滚动、第三人称相机、PBR 和资源重载验收，日志出现 `[VehicleSmoke] SUCCESS`；本次日志没有 `GL_INVALID` 或缓存回退错误。

缓存使用 Oculus 实际扩展后的 56 字节 ENTITY 顶点。重载后车模重新上传 7 个缓冲，共 329,637 个展开顶点、18,459,672 字节；截图时已绘制 735 次，其中阴影 245 次，上传次数仍为 7。此结果确认缓冲复用，不代表 FPS 基准，也不能代替其他光影包验收。

本地截图位于 `run/screenshots/`：`assimp-vehicle-1794584309487-first_image.png`、`assimp-vehicle-1794584309487-last_image.png`、`assimp-vehicle-1794584309487-first_image-reload.png`。已目视检查车壳、轮子、贴图及阴影，客户端保留在可试驾状态。
