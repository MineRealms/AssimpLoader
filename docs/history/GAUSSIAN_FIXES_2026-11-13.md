# Gaussian 原型修复记录

此模块仍是独立渲染原型，没有新增世界方块、自动加载入口、射击行为或 Iris/Oculus shader hook。

## 数据与绘制

- CPU 与 shader 统一为 `11 + 3 * shDim` 个 float，`shDim` 是系数数量 `1/4/9/16`。SH 数据过长截断、过短补零，避免写越下一个点；度数限制为 0-3。
- SH degree 1/2/3 的判断分别使用 4/9/16 个系数。零长度 quaternion 恢复单位旋转，其他 quaternion 归一化；scale 指数做有限范围处理。
- LOD 只产生原始点的索引，并明确返回有效数量；排序和 GPU instanced draw 都使用该数量。不再为 LOD 重建整份点云，不再用最后一个点填满剩余绘制槽。
- 深度使用 JOML view 矩阵的第二行 `m02/m12/m22/m32`，按后到前排序。采用已有 fastutil 的 primitive `IntArrays.quickSort`，相同深度用原始索引保证确定性，复用深度/索引工作数组。
- 云数据仅首次上传，后续只更新有效排序索引；排序索引的 native staging buffer 按最大容量复用。GPU 缓存和加载缓存遵循 `splatCacheSize`。

## OpenGL 路径

- OpenGL 4.3 使用 SSBO shader variant。OpenGL 3.3 使用实际 TBO variant：云数据 `R32F + samplerBuffer`，排序索引 `R32I + isamplerBuffer`，分别绑定 texture unit 0/1。
- 不再无条件 require SSBO 扩展，也不把整数索引转换为 float。TBO 以标量为 texel，shader 的 float offset 与 GPU 格式一致。
- 无 OpenGL 3.3 时在分配 GPU 对象前明确拒绝初始化。TBO/SSBO 超出设备容量时拒绝上传。
- 编译和分配失败会释放已经创建的 shader/program/buffer/texture。渲染使用 finally 恢复程序、VAO、buffer、texture unit、SSBO 绑定范围、深度、混合和剔除状态。
- 删除伪 Oculus 模板注入行为；`GaussianSplatOculusInjector.isInjected()` 明确返回 false，不打印已成功注入日志。

## 文件导入

原 PLY 解析还有三处格式错误一并修复：`rot_0` 现在映射到 quaternion 的 w；opacity 保持文件中保存的 logit，不再当成概率重新转 logit；`f_rest_*` 从按颜色通道排列转换为 shader 使用的逐系数 RGB 排列。

字段含义通过 [Graphdeco 原始实现的保存/读取代码](https://github.com/graphdeco-inria/gaussian-splatting/blob/main/scene/gaussian_model.py) 核对，仅按格式实现解析，没有复制其算法代码。

PLY 支持未压缩的 ASCII、little endian 和 big endian 标量 vertex 数据；按属性名解析，不依赖固定属性顺序。损坏计数、缺失属性、截断数据、错误 SH 数量和不支持的压缩/顶点列表会返回 IOException，不会逐点刷日志或悄悄返回部分点云。`.splat` 保持仓库原有的 count-prefixed、degree 3 自定义格式，不将它冒充其他工具的通用 32-byte `.splat` 格式。

## 验证范围

新增 9 项测试覆盖 SH 截断/补零与跨点步长、有限数据、视空间矩阵行、稳定索引、十万同深点不发生递归栈溢出、LOD 原索引与数量、禁用 LOD/未加载配置、PLY 属性映射/大小端/截断和损坏自定义头。

未做 benchmark，没有声称任何加速百分比。由主任务统一执行编译、测试与客户端验收；真实 SSBO/TBO 图像仍需 OpenGL 上下文验证。
