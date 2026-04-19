# 能量底座 (Energy Pedestal) - 实现文档

## ✅ 已完成

### 1. 方块和方块实体

**文件:**
- `EnergyPedestalBlock.java` - 方块类
- `EnergyPedestalBlockEntity.java` - 方块实体类

**功能:**
- ✅ FE (Forge Energy) 能量存储和传输
- ✅ GTEU (GregTech Energy Units) 能量接收和转换
- ✅ 自动向上方炮塔供能
- ✅ 从周围方块接收能量（除上方外的五个面）
- ✅ 支持六面能量输入/输出

### 2. 能量参数

```java
容量: 100,000 FE
输入速率: 10,000 FE/t
输出速率: 10,000 FE/t
GTEU 转换率: 1 GTEU = 4 FE (GregTech 标准)
GT 电压等级: LV (32 EU/t)
GT 电流: 2A
```

### 3. 能量系统兼容

**FE (Forge Energy):**
- 使用 `MTEnergyStorage` 类存储能量
- 实现 `IEnergyStorage` 接口
- 通过 `ForgeCapabilities.ENERGY` 暴露

**GTEU (GregTech):**
- 实现 `IEnergyContainer` 接口
- 自动转换 GTEU 到 FE (1:4 比例)
- 支持 LV 电压，2A 电流

### 4. 工作逻辑

**每 Tick 执行:**
1. **向上供能** - 检测上方方块，如果有能量接口则传输能量
2. **接收能量** - 从周围五个面（除上方）接收 FE 或 GTEU 能量

**能量流向:**
```
侧面/底面 (输入) → 能量底座 → 上方 (输出到炮塔)
```

### 5. 注册

**BlockRegistry.java:**
```java
public static final BlockRegistryObject<EnergyPedestalBlock, BlockItem> ENERGY_PEDESTAL
```

**BlockEntityTypeRegistry.java:**
```java
public static final TileEntityTypeRegistryObject<EnergyPedestalBlockEntity> ENERGY_PEDESTAL
```

### 6. 资源文件

**Blockstate:**
- `assets/integratedindustrialcraft/blockstates/energy_pedestal.json`

**模型:**
- `assets/integratedindustrialcraft/models/block/energy_pedestal.json`
- `assets/integratedindustrialcraft/models/item/energy_pedestal.json`

**语言文件:**
- `en_us.json`: "Energy Pedestal"
- `zh_cn.json`: "能量底座"

### 7. 贴图位置

**需要创建贴图:**
```
src/main/resources/assets/integratedindustrialcraft/textures/block/energy_pedestal.png
```

**贴图建议:**
- 尺寸: 16x16 或 32x32 (推荐 16x16)
- 风格: 金属质感，带有能量指示器
- 颜色: 深灰色金属底色 + 蓝色/青色能量纹路
- 参考: Mekanism 的机器方块风格

**贴图设计元素:**
- 中心: 能量核心（发光效果）
- 边缘: 金属框架
- 侧面: 能量传输接口
- 顶部: 能量输出口（向上）

## 🎨 贴图创建指南

### 方案 1: 使用现有贴图修改

可以参考以下现有贴图进行修改：
```
assets/integratedindustrialcraft/textures/block/
├── basic_laser_turret.png
├── advanced_laser_turret.png
└── electric_fence.png
```

### 方案 2: 使用在线工具

推荐工具:
- **Blockbench** - 3D 模型和贴图编辑器
- **Piskel** - 像素画编辑器
- **GIMP** - 图像编辑软件

### 方案 3: 临时占位贴图

如果暂时没有贴图，可以使用纯色占位：
1. 创建 16x16 的纯色图片（如深灰色 #3C3C3C）
2. 添加简单的边框和中心点
3. 保存为 `energy_pedestal.png`

## 📝 使用方法

### 放置

```
[炮塔]  ← 接收能量
   ↑
[能量底座]  ← 存储和转换能量
   ↑
[能量源]  ← FE 或 GTEU 能量输入
```

### 能量输入

**FE 能量源:**
- Mekanism 能量线缆
- Thermal Expansion 能量管道
- EnderIO 能量导管
- 任何支持 Forge Energy 的设备

**GTEU 能量源:**
- GregTech 电线
- GregTech 机器
- GregTech 能量仓

### 能量输出

- 自动向上方的炮塔传输能量
- 支持所有使用 FE 的炮塔
- 传输速率: 10,000 FE/t

## 🔧 配置

当前配置硬编码在 `EnergyPedestalBlockEntity.java` 中：

```java
private static final int MAX_ENERGY = 100_000;      // 容量
private static final int MAX_RECEIVE = 10_000;      // 输入速率
private static final int MAX_EXTRACT = 10_000;      // 输出速率
private static final int GTEU_TO_FE_RATIO = 4;      // 转换率
```

如需修改，编辑这些常量即可。

## 🧪 测试

### 测试步骤

1. **放置能量底座**
   ```
   /give @p integratedindustrialcraft:energy_pedestal
   ```

2. **放置炮塔在上方**
   ```
   /give @p integratedindustrialcraft:basic_laser_turret
   ```

3. **连接能量源**
   - 使用 Mekanism 能量线缆连接到底座侧面或底面
   - 或使用 GregTech 电线连接

4. **验证能量传输**
   - 检查炮塔是否接收到能量
   - 使用 F3 调试界面查看方块实体数据

### 调试命令

```bash
# 查看方块实体数据
/data get block ~ ~ ~

# 检查能量存储
/data get block ~ ~ ~ Energy
```

## 📚 API 使用

### 获取能量存储

```java
BlockEntity be = level.getBlockEntity(pos);
if (be instanceof EnergyPedestalBlockEntity pedestal) {
    int energy = pedestal.getEnergyStored();
    int maxEnergy = pedestal.getMaxEnergyStored();
}
```

### 通过 Capability 访问

```java
// FE 能量
be.getCapability(ForgeCapabilities.ENERGY, side).ifPresent(handler -> {
    int received = handler.receiveEnergy(1000, false);
});

// GTEU 能量
IEnergyContainer gtContainer = GTCapabilityHelper.getEnergyContainer(level, pos, side);
if (gtContainer != null) {
    long received = gtContainer.acceptEnergyFromNetwork(side, 32, 1);
}
```

## 🚀 未来扩展

可能的改进方向：

1. **可视化能量存储**
   - 添加动态贴图显示能量等级
   - 使用 BlockEntityRenderer 渲染能量条

2. **升级系统**
   - 添加升级槽位
   - 支持速度升级、容量升级

3. **多方向输出**
   - 支持向多个方向输出能量
   - 可配置输入/输出面

4. **GUI 界面**
   - 显示能量存储量
   - 配置输入/输出速率
   - 显示连接的设备

5. **无线能量传输**
   - 支持无线能量传输到附近炮塔
   - 可配置传输范围

## ⚠️ 注意事项

1. **能量转换是单向的**
   - 只能从 GTEU 转换到 FE
   - 不支持 FE 转换回 GTEU

2. **上方必须是炮塔**
   - 能量底座只向上方输出
   - 其他方向只能输入

3. **需要持续供能**
   - 炮塔消耗能量较快
   - 建议使用大容量能量源

4. **兼容性**
   - 需要 Mekanism 和 GregTech CEu
   - 确保两个 mod 都已安装

## 📦 构建

```bash
./gradlew build
```

构建后的 JAR 会自动复制到游戏 mods 目录。
