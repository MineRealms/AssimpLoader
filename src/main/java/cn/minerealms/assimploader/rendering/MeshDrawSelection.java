package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.mixin.render.VertexBufferSelectionAccess;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

import java.util.IdentityHashMap;
import java.util.Map;

/** Immutable triangle mask. Index buffers reuse the owner's deformed vertex buffer. Render-thread owned. */
public final class MeshDrawSelection implements AutoCloseable {
    private static final ThreadLocal<MeshDrawSelection> CURRENT = new ThreadLocal<>();
    private final Map<AssimpMesh, int[]> triangles = new IdentityHashMap<>();
    private final Map<AssimpMesh, Integer> indexed = new IdentityHashMap<>(), expanded = new IdentityHashMap<>();
    private boolean closed;

    public MeshDrawSelection(Map<AssimpMesh, int[]> triangles) {
        triangles.forEach((mesh, source) -> {
            int[] copy = source.clone(); int previous = -1;
            for (int triangle : copy) {
                if (triangle <= previous || triangle >= mesh.getIndices().length / 3)
                    throw new IllegalArgumentException("Triangle ordinals must be sorted, unique and inside their mesh");
                previous = triangle;
            }
            this.triangles.put(mesh, copy);
        });
    }
    public static MeshDrawSelection current() { return CURRENT.get(); }
    public boolean contains(AssimpMesh mesh) { return triangles.containsKey(mesh) && triangles.get(mesh).length > 0; }
    public int[] triangles(AssimpMesh mesh) { var value = triangles.get(mesh); return value == null ? new int[0] : value.clone(); }
    public Scope activate() {
        if (closed) throw new IllegalStateException("Mesh selection is closed");
        var previous = CURRENT.get(); CURRENT.set(this); return new Scope(previous);
    }
    public final class Scope implements AutoCloseable {
        private final MeshDrawSelection previous;
        private boolean ended;
        private Scope(MeshDrawSelection previous) { this.previous = previous; }
        @Override public void close() { if (!ended) { CURRENT.set(previous); ended = true; } }
    }

    private int elementBuffer(AssimpMesh mesh, boolean expandedVertices) {
        var cache = expandedVertices ? expanded : indexed;
        return cache.computeIfAbsent(mesh, ignored -> {
            int[] selected = triangles.get(mesh);
            var data = MemoryUtil.memAllocInt(Math.multiplyExact(selected.length, 3));
            int id = GL15.glGenBuffers();
            try {
                for (int triangle : selected) {
                    int start = triangle * 3;
                    if (expandedVertices) data.put(start).put(start + 1).put(start + 2);
                    else data.put(mesh.getIndices()[start + 2]).put(mesh.getIndices()[start + 1]).put(mesh.getIndices()[start]);
                }
                data.flip(); GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, id);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                return id;
            } catch (RuntimeException error) { GL15.glDeleteBuffers(id); throw error; }
            finally { MemoryUtil.memFree(data); }
        });
    }

    /** The caller has bound the existing mesh VAO and shader. */
    public void drawBound(AssimpMesh mesh, boolean expandedVertices) {
        if (!contains(mesh)) return;
        int previous = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer(mesh, expandedVertices));
            GL11.glDrawElements(GL11.GL_TRIANGLES, triangles.get(mesh).length * 3, GL11.GL_UNSIGNED_INT, 0L);
        } finally { GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previous); }
    }

    /** Temporarily replaces only draw indices; Minecraft/Oculus still uploads and applies its shader uniforms. */
    public void draw(VertexBuffer buffer, AssimpMesh mesh, org.joml.Matrix4f view, org.joml.Matrix4f projection,
                     net.minecraft.client.renderer.ShaderInstance shader) {
        if (!contains(mesh)) return;
        var access = (VertexBufferSelectionAccess) buffer;
        int count = access.assimploader$getIndexCount();
        var type = access.assimploader$getIndexType();
        var sequential = access.assimploader$getSequentialIndices();
        int previous = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementBuffer(mesh, true));
            access.assimploader$setSequentialIndices(null);
            access.assimploader$setIndexType(VertexFormat.IndexType.INT);
            access.assimploader$setIndexCount(triangles.get(mesh).length * 3);
            buffer.drawWithShader(view, projection, shader);
        } finally {
            access.assimploader$setIndexCount(count); access.assimploader$setIndexType(type);
            access.assimploader$setSequentialIndices(sequential);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previous);
        }
    }
    @Override public void close() {
        indexed.values().forEach(GL15::glDeleteBuffers); expanded.values().forEach(GL15::glDeleteBuffers);
        indexed.clear(); expanded.clear(); closed = true;
    }
}
