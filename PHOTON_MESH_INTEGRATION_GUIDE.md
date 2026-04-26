# Photon Mesh 粒子形状系统集成指南

## 概述

本文档详细说明 Photon 如何将 3D 模型（Mesh）解析为粒子发射形状，以及如何将此技术应用到 ImprovedMobs (AssimpLoader) 项目中。

---

## 一、Photon Mesh 系统架构

### 1.1 核心类结构

```
IShape (接口)
  └── Mesh (实现类)
        └── MeshData (数据类)
              ├── vertices: List<Vector3f>      // 顶点列表
              ├── edges: List<Edge>              // 边列表
              ├── triangles: List<Triangle>      // 三角形列表
              ├── edgeSumLength: double          // 边总长度（用于加权随机）
              └── triangleSumArea: double        // 三角形总面积（用于加权随机）
```

### 1.2 发射模式

Photon 支持三种粒子发射模式：

| 模式 | 描述 | 用途 |
|-----|------|------|
| **Vertex** | 从顶点发射 | 稀疏粒子，轮廓效果 |
| **Edge** | 从边上随机点发射 | 线框效果，边缘高光 |
| **Triangle** | 从三角形表面随机点发射 | 表面覆盖，体积效果 |

---

## 二、Mesh 解析流程

### 2.1 从 Minecraft 模型加载

**核心方法：** `MeshData.loadFromModel(ResourceLocation modelLocation)`

```java
public void loadFromModel(ResourceLocation modelLocation) {
    // 1. 获取 UnbakedModel
    var random = RandomSource.create();
    var bakedModel = ModelFactory.getUnBakedModel(modelLocation).bake(
            ModelFactory.getModeBaker(),
            Material::sprite,
            BlockModelRotation.X0_Y0,
            modelLocation);
    
    // 2. 提取所有 BakedQuad（包括所有面）
    var quads = new ArrayList<>(bakedModel.getQuads(null, null, random));
    for (var side : Direction.values()) {
        quads.addAll(bakedModel.getQuads(null, side, random));
    }
    
    // 3. 解析 Quads 为 Mesh 数据
    loadFromQuads(quads);
}
```

**关键点：**
- 使用 LDLib 的 `ModelFactory` 加载 Minecraft JSON 模型
- 支持标准 Minecraft 模型格式（方块模型、物品模型）
- 提取所有面的 Quads（包括 null 面和六个方向面）

### 2.2 从 BakedQuad 解析几何数据

**核心方法：** `MeshData.loadFromQuads(List<BakedQuad> quads)`

```java
public void loadFromQuads(List<BakedQuad> quads) {
    clear();
    double sumLength = 0;
    double sumArea = 0;
    
    for (var quad : quads) {
        var vertices = quad.getVertices();
        Vector3f[] points = new Vector3f[4];
        
        // 1. 提取四个顶点坐标
        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            int offset = vertexIndex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            points[vertexIndex] = new Vector3f(
                Float.intBitsToFloat(vertices[offset]) - 0.5f,      // X
                Float.intBitsToFloat(vertices[offset + 1]) - 0.5f,  // Y
                Float.intBitsToFloat(vertices[offset + 2]) - 0.5f   // Z
            );
            this.vertices.add(points[vertexIndex]);
        }
        
        // 2. 添加边（Quad 的 4 条边 + 1 条对角线）
        sumLength += addEdge(points[0], points[1]);
        sumLength += addEdge(points[1], points[2]);
        sumLength += addEdge(points[2], points[3]);
        sumLength += addEdge(points[3], points[0]);
        sumLength += addEdge(points[1], points[3]);  // 对角线
        
        // 3. 添加三角形（Quad 分解为 2 个三角形）
        sumArea += addTriangle(points[0], points[1], points[2]);
        sumArea += addTriangle(points[2], points[3], points[0]);
    }
    
    this.edgeSumLength = sumLength;
    this.triangleSumArea = sumArea;
}
```

**关键技术：**

1. **顶点数据提取：**
   - `IQuadTransformer.STRIDE`：每个顶点的数据步长（通常为 8）
   - `IQuadTransformer.POSITION`：位置数据的偏移量
   - `Float.intBitsToFloat()`：将整数位模式转换为浮点数
   - 坐标中心化：`- 0.5f` 将坐标从 [0, 1] 转换为 [-0.5, 0.5]

2. **边的构建：**
   - 每个 Quad 有 4 条边 + 1 条对角线（用于三角形分解）
   - 计算边长度并累加（用于加权随机采样）

3. **三角形的构建：**
   - 每个 Quad 分解为 2 个三角形
   - 计算三角形面积并累加（用于加权随机采样）

### 2.3 几何数据结构

**Edge（边）：**

```java
public static class Edge {
    public final Vector3f a, b;      // 两个端点
    public final double length;      // 边长度
    
    public Edge(Vector3f a, Vector3f b) {
        this.a = a;
        this.b = b;
        length = new Vector3f(a).sub(b).length();
    }
}
```

**Triangle（三角形）：**

```java
public static class Triangle {
    public final Vector3f a, b, c;   // 三个顶点
    public final double area;        // 三角形面积
    
    public Triangle(Vector3f a, Vector3f b, Vector3f c) {
        this.a = a;
        this.b = b;
        this.c = c;
        
        // 使用叉积计算面积
        var nx = (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y);
        var ny = (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
        var nz = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
        area = 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }
}
```

---

## 三、粒子发射算法

### 3.1 加权随机采样

Photon 使用**加权随机采样**确保粒子分布均匀：

- **Edge 模式：** 按边长度加权，长边发射更多粒子
- **Triangle 模式：** 按三角形面积加权，大三角形发射更多粒子

### 3.2 Vertex 模式

```java
public Vector3f getRandomVertex(float t) {
    if (vertices.isEmpty()) return null;
    return vertices.get((int) (vertices.size() * t));
}
```

**算法：** 简单的均匀随机选择顶点

### 3.3 Edge 模式

```java
public Edge getRandomEdge(float t) {
    if (edges.isEmpty()) return null;
    var l = t * edgeSumLength;  // 随机长度
    var cl = 0d;                // 累计长度
    
    // 遍历边，找到累计长度超过随机长度的边
    for (Edge edge : edges) {
        if (l <= edge.length + cl) {
            return edge;
        }
        cl += edge.length;
    }
    return edges.get(edges.size() - 1);
}
```

**算法：** 按边长度加权的轮盘赌选择（Roulette Wheel Selection）

**粒子位置计算：**

```java
// 在选中的边上随机插值
var edge = meshData.getRandomEdge(t);
if (edge != null) {
    pos = new Vector3f(edge.b)
        .sub(edge.a)
        .mul(random.nextFloat())  // 随机插值因子 [0, 1]
        .add(edge.a);
}
```

### 3.4 Triangle 模式（最重要）

```java
public Triangle getRandomTriangle(float t) {
    if (triangles.isEmpty()) return null;
    var a = t * triangleSumArea;  // 随机面积
    var ca = 0d;                  // 累计面积
    
    // 遍历三角形，找到累计面积超过随机面积的三角形
    for (var triangle : triangles) {
        if (a <= triangle.area + ca) {
            return triangle;
        }
        ca += triangle.area;
    }
    return triangles.get(triangles.size() - 1);
}
```

**算法：** 按三角形面积加权的轮盘赌选择

**粒子位置计算（重心坐标插值）：**

```java
var triangle = meshData.getRandomTriangle(t);
if (triangle != null) {
    // 使用重心坐标进行均匀采样
    var sqrtR = (float) Math.sqrt(random.nextFloat());
    var A = (1 - sqrtR);
    var r2 = random.nextFloat();
    var B = (sqrtR * (1 - r2));
    var C = (sqrtR * r2);
    
    // 重心坐标插值
    var x = A * triangle.a.x + B * triangle.b.x + C * triangle.c.x;
    var y = A * triangle.a.y + B * triangle.b.y + C * triangle.c.y;
    var z = A * triangle.a.z + B * triangle.b.z + C * triangle.c.z;
    pos = new Vector3f(x, y, z);
}
```

**关键技术：**
- 使用**重心坐标（Barycentric Coordinates）**进行三角形内均匀采样
- `sqrt(random)` 确保采样点在三角形内均匀分布（而非聚集在中心）
- 三个权重 A, B, C 满足 `A + B + C = 1`

---

## 四、应用到 ImprovedMobs (AssimpLoader)

### 4.1 核心差异

| 特性 | Photon | ImprovedMobs |
|-----|--------|--------------|
| 模型格式 | Minecraft JSON 模型 | FBX/OBJ/GLTF (Assimp) |
| 几何表示 | BakedQuad | AssimpMesh (AIScene) |
| 顶点数据 | 整数位模式 | 浮点数数组 |
| 用途 | 粒子发射形状 | 3D 模型渲染 |

### 4.2 集成方案：AssimpMesh → MeshData 转换器

**目标：** 将 Assimp 加载的 FBX 模型转换为 Photon MeshData

**实现：**

```java
package cn.minerealms.assimploader.compat.photon;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData;
import org.joml.Vector3f;
import org.lwjgl.assimp.*;

import java.nio.IntBuffer;

public class AssimpToPhotonMeshConverter {
    
    /**
     * 将 AssimpScene 转换为 Photon MeshData
     * @param scene Assimp 场景
     * @param meshIndex 要转换的 Mesh 索引（-1 表示所有 Mesh）
     * @return Photon MeshData
     */
    public static MeshData convertToMeshData(AssimpScene scene, int meshIndex) {
        MeshData meshData = new MeshData();
        
        AIScene aiScene = scene.getAiScene();
        if (aiScene == null) return meshData;
        
        // 如果指定了 meshIndex，只转换该 Mesh
        if (meshIndex >= 0 && meshIndex < aiScene.mNumMeshes()) {
            AIMesh aiMesh = AIMesh.create(aiScene.mMeshes().get(meshIndex));
            convertMesh(aiMesh, meshData);
        } else {
            // 转换所有 Mesh
            for (int i = 0; i < aiScene.mNumMeshes(); i++) {
                AIMesh aiMesh = AIMesh.create(aiScene.mMeshes().get(i));
                convertMesh(aiMesh, meshData);
            }
        }
        
        meshData.meshName = scene.getModelName();
        return meshData;
    }
    
    /**
     * 转换单个 AIMesh
     */
    private static void convertMesh(AIMesh aiMesh, MeshData meshData) {
        AIVector3D.Buffer vertices = aiMesh.mVertices();
        AIFace.Buffer faces = aiMesh.mFaces();
        
        if (vertices == null || faces == null) return;
        
        double sumLength = 0;
        double sumArea = 0;
        
        // 遍历所有面（通常是三角形）
        for (int faceIdx = 0; faceIdx < aiMesh.mNumFaces(); faceIdx++) {
            AIFace face = faces.get(faceIdx);
            IntBuffer indices = face.mIndices();
            int numIndices = face.mNumIndices();
            
            if (numIndices == 3) {
                // 三角形面
                Vector3f v0 = getVertex(vertices, indices.get(0));
                Vector3f v1 = getVertex(vertices, indices.get(1));
                Vector3f v2 = getVertex(vertices, indices.get(2));
                
                // 添加顶点
                meshData.vertices.add(v0);
                meshData.vertices.add(v1);
                meshData.vertices.add(v2);
                
                // 添加边
                sumLength += addEdge(meshData, v0, v1);
                sumLength += addEdge(meshData, v1, v2);
                sumLength += addEdge(meshData, v2, v0);
                
                // 添加三角形
                sumArea += addTriangle(meshData, v0, v1, v2);
                
            } else if (numIndices == 4) {
                // 四边形面（分解为两个三角形）
                Vector3f v0 = getVertex(vertices, indices.get(0));
                Vector3f v1 = getVertex(vertices, indices.get(1));
                Vector3f v2 = getVertex(vertices, indices.get(2));
                Vector3f v3 = getVertex(vertices, indices.get(3));
                
                // 添加顶点
                meshData.vertices.add(v0);
                meshData.vertices.add(v1);
                meshData.vertices.add(v2);
                meshData.vertices.add(v3);
                
                // 添加边（4 条边 + 1 条对角线）
                sumLength += addEdge(meshData, v0, v1);
                sumLength += addEdge(meshData, v1, v2);
                sumLength += addEdge(meshData, v2, v3);
                sumLength += addEdge(meshData, v3, v0);
                sumLength += addEdge(meshData, v1, v3);  // 对角线
                
                // 添加三角形（分解为 2 个三角形）
                sumArea += addTriangle(meshData, v0, v1, v2);
                sumArea += addTriangle(meshData, v2, v3, v0);
            }
        }
        
        meshData.edgeSumLength += sumLength;
        meshData.triangleSumArea += sumArea;
    }
    
    /**
     * 从 AIVector3D.Buffer 提取顶点
     */
    private static Vector3f getVertex(AIVector3D.Buffer vertices, int index) {
        AIVector3D v = vertices.get(index);
        return new Vector3f(v.x(), v.y(), v.z());
    }
    
    /**
     * 添加边到 MeshData
     */
    private static double addEdge(MeshData meshData, Vector3f a, Vector3f b) {
        var edge = new MeshData.Edge(a, b);
        if (edge.length > 0) {
            meshData.edges.add(edge);
        }
        return edge.length;
    }
    
    /**
     * 添加三角形到 MeshData
     */
    private static double addTriangle(MeshData meshData, Vector3f a, Vector3f b, Vector3f c) {
        var triangle = new MeshData.Triangle(a, b, c);
        if (triangle.area > 0) {
            meshData.triangles.add(triangle);
        }
        return triangle.area;
    }
    
    /**
     * 便捷方法：从 AssimpMesh 转换
     */
    public static MeshData convertFromAssimpMesh(AssimpMesh assimpMesh) {
        MeshData meshData = new MeshData();
        
        AIMesh aiMesh = assimpMesh.getAiMesh();
        if (aiMesh != null) {
            convertMesh(aiMesh, meshData);
            meshData.meshName = assimpMesh.getName();
        }
        
        return meshData;
    }
}
```

### 4.3 使用示例

**在 LookDev 中预览 FBX 模型的粒子效果：**

```java
// 1. 加载 FBX 模型
AssimpScene scene = AssimpLoader.loadModel("models/character.fbx");

// 2. 转换为 Photon MeshData
MeshData meshData = AssimpToPhotonMeshConverter.convertToMeshData(scene, -1);

// 3. 创建 Mesh 形状
Mesh meshShape = new Mesh();
meshShape.getMeshData().deserializeNBT(meshData.serializeNBT());
meshShape.setType(Mesh.Type.Triangle);  // 使用三角形表面发射

// 4. 创建粒子发射器
ParticleConfig config = new ParticleConfig();
config.shape.setShape(meshShape);
config.emission.setRateOverTime(50);  // 每秒 50 个粒子
config.startLifetime.setValue(2.0f);  // 2 秒生命周期

// 5. 创建并启动发射器
ParticleEmitter emitter = new ParticleEmitter(config);
FXRuntime runtime = emitter.createRuntime();
runtime.emmit(new BlockEffect(level, blockPos));
```

---

## 五、高级应用场景

### 5.1 场景 1：模型轮廓高光

**需求：** 为 FBX 模型添加边缘发光效果

**实现：**
```java
MeshData meshData = AssimpToPhotonMeshConverter.convertToMeshData(scene, -1);
Mesh meshShape = new Mesh();
meshShape.getMeshData().deserializeNBT(meshData.serializeNBT());
meshShape.setType(Mesh.Type.Edge);  // 使用边发射

ParticleConfig config = new ParticleConfig();
config.shape.setShape(meshShape);
config.startColor.setValue(0xFFFFFF00);  // 黄色
config.emission.setRateOverTime(100);
```

### 5.2 场景 2：模型溶解效果

**需求：** 模型从表面逐渐溶解为粒子

**实现：**
```java
meshShape.setType(Mesh.Type.Triangle);  // 表面发射
config.startColor.setValue(materialColor);  // 使用材质颜色
config.startSize.setValue(0.1f);  // 小粒子
config.startLifetime.setValue(1.0f);
config.emission.setRateOverTime(500);  // 高密度
```

### 5.3 场景 3：特定部位粒子发射

**需求：** 只在模型的某个部位（如手部）发射粒子

**实现：**
```java
// 1. 找到手部 Mesh 的索引
int handMeshIndex = scene.findMeshByName("Hand_L");

// 2. 只转换手部 Mesh
MeshData handMeshData = AssimpToPhotonMeshConverter.convertToMeshData(scene, handMeshIndex);

// 3. 创建发射器
Mesh meshShape = new Mesh();
meshShape.getMeshData().deserializeNBT(handMeshData.serializeNBT());
```

---

## 六、性能优化建议

### 6.1 Mesh 数据缓存

```java
// 缓存转换后的 MeshData，避免重复转换
private static final Map<String, MeshData> MESH_CACHE = new HashMap<>();

public static MeshData getCachedMeshData(AssimpScene scene) {
    String key = scene.getModelPath();
    return MESH_CACHE.computeIfAbsent(key, k -> 
        AssimpToPhotonMeshConverter.convertToMeshData(scene, -1)
    );
}
```

### 6.2 LOD（细节层次）

```java
// 根据距离选择不同精度的 Mesh
public static MeshData convertWithLOD(AssimpScene scene, float distance) {
    if (distance > 50) {
        // 远距离：只使用顶点
        return convertToMeshData(scene, -1, Mesh.Type.Vertex);
    } else if (distance > 20) {
        // 中距离：使用边
        return convertToMeshData(scene, -1, Mesh.Type.Edge);
    } else {
        // 近距离：使用三角形
        return convertToMeshData(scene, -1, Mesh.Type.Triangle);
    }
}
```

### 6.3 粒子数量控制

```java
// 根据三角形数量动态调整发射速率
int triangleCount = meshData.triangles.size();
float emissionRate = Math.min(triangleCount * 0.1f, 500);  // 最多 500 粒子/秒
config.emission.setRateOverTime(emissionRate);
```

---

## 七、技术要点总结

### 7.1 关键算法

1. **BakedQuad 顶点提取：**
   - 使用 `IQuadTransformer.STRIDE` 和 `IQuadTransformer.POSITION`
   - `Float.intBitsToFloat()` 转换整数位模式为浮点数

2. **加权随机采样：**
   - 按边长度或三角形面积加权
   - 轮盘赌选择算法（Roulette Wheel Selection）

3. **三角形内均匀采样：**
   - 重心坐标插值（Barycentric Coordinates）
   - `sqrt(random)` 确保均匀分布

4. **Assimp 数据转换：**
   - 处理三角形面和四边形面
   - 四边形分解为两个三角形

### 7.2 适用场景

✅ **适合：**
- 模型轮廓高光
- 溶解/重组效果
- 魔法阵/符文效果
- 模型扫描效果
- 粒子拖尾

❌ **不适合：**
- 复杂 PBR 渲染（Photon 不支持）
- 高精度模型渲染（粒子精度有限）
- 实时光照计算（粒子无法接收光照）

### 7.3 性能考虑

- **顶点数量：** 建议 < 10,000 顶点
- **三角形数量：** 建议 < 5,000 三角形
- **粒子发射速率：** 建议 < 500 粒子/秒
- **缓存策略：** 必须缓存 MeshData，避免每帧转换

---

## 八、下一步行动

### 8.1 立即实施

1. **创建转换器类：**
   - `AssimpToPhotonMeshConverter.java`
   - 实现 `convertToMeshData()` 方法

2. **基础测试：**
   - 加载简单 FBX 模型
   - 转换为 MeshData
   - 验证顶点、边、三角形数据

3. **LookDev 集成：**
   - 在 LookDev 中添加 "Preview Particles" 按钮
   - 显示粒子预览

### 8.2 后续优化

1. **性能优化：**
   - 实现 MeshData 缓存
   - 添加 LOD 支持
   - 粒子数量自适应

2. **功能扩展：**
   - 支持骨骼动画的粒子跟随
   - 支持材质颜色映射到粒子
   - 支持自定义发射区域（通过节点名称过滤）

3. **用户体验：**
   - 可视化粒子参数调整
   - 预设效果库
   - 导出粒子配置

---

## 九、参考资源

### 9.1 Photon 相关

- **Photon GitHub:** https://github.com/Low-Drag-MC/Photon
- **LDLib GitHub:** https://github.com/Low-Drag-MC/LDLib
- **Photon Discord:** https://discord.com/invite/sDdf2yD9bh

### 9.2 算法参考

- **重心坐标采样:** https://mathworld.wolfram.com/BarycentricCoordinates.html
- **三角形面积计算:** https://en.wikipedia.org/wiki/Cross_product
- **轮盘赌选择:** https://en.wikipedia.org/wiki/Fitness_proportionate_selection

### 9.3 Assimp 文档

- **Assimp 官方文档:** https://assimp-docs.readthedocs.io/
- **LWJGL Assimp Bindings:** https://www.lwjgl.org/guide

---

*文档版本: 1.0*  
*创建日期: 2026-04-25*  
*作者: Claude (Opus 4.6)*
