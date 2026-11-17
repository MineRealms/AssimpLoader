package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.transform.GizmoMath;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Selection edges from the actual baked item geometry, with no world shader or framebuffer changes. */
final class EquipmentItemOutline {
    private record Quad(Vector3f[] vertices, float area) {}
    private record Vertex(int x, int y, int z) {
        static Vertex of(Vector3f p) { return new Vertex(Math.round(p.x * 100000), Math.round(p.y * 100000), Math.round(p.z * 100000)); }
        int compare(Vertex other) {
            int result = Integer.compare(x, other.x);
            if (result == 0) result = Integer.compare(y, other.y);
            return result == 0 ? Integer.compare(z, other.z) : result;
        }
    }
    private record EdgeKey(Vertex a, Vertex b) {
        static EdgeKey of(Vector3f a, Vector3f b) {
            var first = Vertex.of(a); var second = Vertex.of(b);
            return first.compare(second) <= 0 ? new EdgeKey(first, second) : new EdgeKey(second, first);
        }
    }
    private record Edge(Vector3f a, Vector3f b, int count) {}
    private static final List<Quad> quads = new ArrayList<>();
    private static final Matrix4f projection = new Matrix4f();
    private static boolean flat;
    private static long captured;

    private EquipmentItemOutline() {}

    static void clear() { quads.clear(); captured = 0; }

    static void capture(LocalPlayer player, ItemStack stack, boolean left, PoseStack poses) {
        HumanoidArm selected = EquipmentTransformGizmo.selectedHand();
        if (selected == null || (selected == HumanoidArm.LEFT) != left
                || cn.minerealms.assimploader.compat.OculusCompat.isRenderingShadowPass()) return;
        var minecraft = Minecraft.getInstance();
        var renderer = minecraft.getItemRenderer();
        var model = renderer.getModel(stack, player.level(), player, player.getId());
        clear();
        if (model.isCustomRenderer()) return;
        flat = !model.isGui3d();
        projection.set(RenderSystem.getProjectionMatrix());
        Map<RenderType, Collector> streams = new HashMap<>();
        MultiBufferSource capture = type -> streams.computeIfAbsent(type, ignored -> new Collector());
        renderer.renderStatic(player, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, poses, capture, player.level(), 15728880, OverlayTexture.NO_OVERLAY,
                player.getId() + (left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).ordinal());
        captured = System.nanoTime();
    }

    static void render(GuiGraphics graphics) {
        if (System.nanoTime() - captured > 500_000_000L || quads.isEmpty()) return;
        var window = Minecraft.getInstance().getWindow();
        float maximum = 0;
        for (var quad : quads) maximum = Math.max(maximum, quad.area);
        Map<EdgeKey, Edge> edges = new HashMap<>();
        Set<Set<Vertex>> faces = new HashSet<>();
        for (var quad : quads) {
            // Generated flat items have alpha-cut front/back rectangles; their side faces trace the true pixel contour.
            if (flat && quad.area >= maximum * .8f) continue;
            var face = new HashSet<Vertex>();
            for (var vertex : quad.vertices) face.add(Vertex.of(vertex));
            if (face.size() < 3 || !faces.add(face)) continue;
            for (int i = 0; i < 4; i++) {
                var a = quad.vertices[i]; var b = quad.vertices[(i + 1) % 4];
                var key = EdgeKey.of(a, b);
                var previous = edges.get(key);
                edges.put(key, new Edge(a, b, previous == null ? 1 : previous.count + 1));
            }
        }
        for (var edge : edges.values()) {
            if (flat && edge.count > 1) continue;
            var a = GizmoMath.project(projection, edge.a, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            var b = GizmoMath.project(projection, edge.b, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            if (a != null && b != null) EquipmentTransformGizmo.line(graphics, a.x(), a.y(), b.x(), b.y(), 0xffffc65c, 1);
        }
    }

    private static final class Collector implements VertexConsumer {
        private final Vector3f[] vertices = new Vector3f[4];
        private int count;
        private double x, y, z;
        @Override public VertexConsumer vertex(double x, double y, double z) { this.x=x;this.y=y;this.z=z;return this; }
        @Override public VertexConsumer color(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer uv(float u,float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u,int v) { return this; }
        @Override public VertexConsumer uv2(int u,int v) { return this; }
        @Override public VertexConsumer normal(float x,float y,float z) { return this; }
        @Override public void defaultColor(int r,int g,int b,int a) {}
        @Override public void unsetDefaultColor() {}
        @Override public void endVertex() {
            if (quads.size() >= 2048) return;
            vertices[count++] = new Vector3f((float)x,(float)y,(float)z);
            if (count != 4) return;
            var a = new Vector3f(vertices[1]).sub(vertices[0]);
            var b = new Vector3f(vertices[3]).sub(vertices[0]);
            quads.add(new Quad(vertices.clone(), a.cross(b).length()));
            count = 0;
        }
    }
}
