# 能量底座实现 - 完整状态

## ✅ 已完成的功能

### 1. 核心功能
- **能量存储**: 128k FE 容量
- **能量输入**: 从一个面接收 FE 和 GTEU 能量
- **能量输出**: 向其他五个面输出 FE 能量（供炮塔使用）
- **自动供能**: 每 tick 向上方炮塔传输最多 10k FE

### 2. 方向性系统
- **FACING 属性**: 根据玩家放置时点击的面确定能量输入面
- **5面放炮塔**: 除了能量输入面外的其他5个面可以放置炮塔
- **1面接能量**: 能量输入面只接收能量，不能放置炮塔

### 3. 能量兼容性
- **FE (Forge Energy)**: 完全支持，使用标准 IEnergyStorage
- **GTEU (GregTech Energy)**: 完全支持，使用 IEnergyContainer
- **转换比例**: 1 GTEU = 4 FE (默认，可通过 GTCEU 配置调整)
- **动态加载**: 仅在 GTCEU 模组存在时启用 GTEU 支持

### 4. 炮塔放置限制
- **事件处理器**: `TurretPlacementHandler` 监听方块放置事件
- **限制1**: 炮塔只能放置在能量底座上
- **限制2**: 炮塔不能放置在能量输入面
- **用户提示**: 中英文错误提示信息

## 📝 已创建的文件

### Java 类
1. `EnergyPedestalBlock.java` - 方块类，支持 FACING 属性
2. `EnergyPedestalBlockEntity.java` - 方块实体，能量存储和传输逻辑
3. `GTEUEnergyWrapper.java` - GTEU 能量包装器（条件编译）
4. `TurretPlacementHandler.java` - 炮塔放置限制事件处理器

### 资源文件
1. `blockstates/energy_pedestal.json` - 支持6个方向的 blockstate
2. `models/block/energy_pedestal.json` - 方块模型
3. `models/item/energy_pedestal.json` - 物品模型
4. `lang/en_us.json` - 英文翻译（方块名 + 错误提示）
5. `lang/zh_cn.json` - 中文翻译（方块名 + 错误提示）

### 注册
- `BlockRegistry.java` - 方块注册
- `BlockEntityTypeRegistry.java` - 方块实体注册（使用标准 Forge 注册）
- `IntegratedIndustrialCraft.java` - 主类中注册 DeferredRegister

## 🎨 贴图设计指南

### 文件位置
```
src/main/resources/assets/integratedindustrialcraft/textures/block/energy_pedestal.png
```

### 设计规格
- **尺寸**: 16x16 像素
- **风格**: 工业金属质感

### 配色方案
- **底色**: 深灰色金属 `#3C3C3C`
- **能量纹路**: 青色发光 `#00FFFF`
- **边框**: 较深灰色 `#2A2A2A`
- **高光**: 浅灰色 `#5A5A5A`

### 设计元素
1. **中心能量核心**: 4x4 像素圆形，青色发光
2. **能量线路**: 从中心向外延伸的发光线条（2-3条）
3. **金属螺栓**: 四角各1x1像素深色点
4. **边框**: 1像素宽的深色边框
5. **金属纹理**: 添加细微的噪点模拟金属质感

### 方向性视觉提示（可选）
- 能量输入面可以有特殊标记（如箭头或接口图案）
- 其他面保持统一的金属纹理

## 🔧 技术实现细节

### 能量系统架构
```
EnergyPedestalBlockEntity
├── EnergyStorage (内部类)
│   ├── 存储 FE 能量
│   └── 提供基础能量操作
├── OutputOnlyEnergyStorage (包装器)
│   ├── 用于非输入面
│   └── 只允许提取能量
└── GTEUEnergyWrapper (独立类)
    ├── 实现 IEnergyContainer
    ├── FE ↔ GTEU 转换
    └── 条件编译（仅 GTCEU 存在时）
```

### Capability 系统
- **输入面**: 提供 FE 和 GTEU capability（可接收能量）
- **输出面**: 提供 FE capability（只能提取能量）
- **方向检测**: 通过 BlockState 的 FACING 属性判断

### 事件处理
```java
PlayerInteractEvent.RightClickBlock
├── 检查手持物品是否为炮塔
├── 检查点击方块是否为能量底座
└── 检查点击面是否为能量输入面
```

## 🎯 用户需求对照

| 需求 | 状态 | 实现方式 |
|------|------|----------|
| 炮塔只能放置在能量底座上 | ✅ | TurretPlacementHandler 事件监听 |
| 5面放炮塔，1面接能量 | ✅ | FACING 属性 + Capability 方向检测 |
| 支持 FE 能量输入 | ✅ | IEnergyStorage 实现 |
| 支持 GTEU 能量输入 | ✅ | IEnergyContainer 实现 + 动态加载 |
| 向其他面输出 FE | ✅ | OutputOnlyEnergyStorage 包装器 |
| 储能 128k FE | ✅ | MAX_ENERGY = 128_000 |
| 根据玩家放置方向确定输入面 | ✅ | getStateForPlacement 使用 clickedFace |

## 🚀 下一步

1. **等待构建完成** - 检查是否有编译错误
2. **创建贴图** - 按照设计指南创建 PNG 文件
3. **游戏内测试**:
   - 放置能量底座，检查方向性
   - 连接 FE 能量源（如 Mekanism 能量立方）
   - 连接 GTEU 能量源（如 GTCEU 电池缓存）
   - 尝试在不同面放置炮塔
   - 验证能量传输和炮塔供能

## 📋 测试清单

- [ ] 能量底座可以正常放置
- [ ] 方向性正确（能量输入面为玩家点击的面）
- [ ] FE 能量可以输入
- [ ] GTEU 能量可以输入（需要 GTCEU）
- [ ] 能量可以向上方炮塔传输
- [ ] 炮塔无法放置在非能量底座上
- [ ] 炮塔无法放置在能量输入面
- [ ] 炮塔可以放置在其他5个面
- [ ] 错误提示信息正确显示
- [ ] 方块可以正常破坏和掉落
- [ ] 能量数据正确保存和加载
