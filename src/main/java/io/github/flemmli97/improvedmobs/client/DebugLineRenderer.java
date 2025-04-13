package io.github.flemmli97.improvedmobs.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.flemmli97.improvedmobs.ImprovedMobs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ImprovedMobs.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DebugLineRenderer {

    // 存储 怪物ID -> 目标方块位置 的映射
    private static final Map<Integer, TargetData> targets = new ConcurrentHashMap<>();
    public static boolean renderLines = false; // 是否开启连线渲染

    public static class TargetData {
        public final BlockPos pos;
        public long lastUpdateTime;

        public TargetData(BlockPos pos) {
            this.pos = pos;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }

    public static void updateTarget(int entityId, BlockPos target) {
        if (target == null) {
            targets.remove(entityId);
        } else {
            targets.put(entityId, new TargetData(target));
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!renderLines || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || targets.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f); // 设置线宽

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f mat = poseStack.last().pose();

        Iterator<Map.Entry<Integer, TargetData>> iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, TargetData> entry = iterator.next();
            // 如果数据超过 2 秒没更新（比如怪物死了或者目标丢失），自动清理
            if (currentTime - entry.getValue().lastUpdateTime > 2000) {
                iterator.remove();
                continue;
            }

            Entity a = mc.level.getEntity(entry.getKey());
            if (a == null || !a.isAlive()) {
                iterator.remove();
                continue;
            }

            BlockPos target = entry.getValue().pos;
            Vec3 pa = a.position().add(0.0, a.getBbHeight() * 0.5, 0.0);
            Vec3 pb = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

            bb.vertex(mat, (float) pa.x, (float) pa.y, (float) pa.z).color(255, 0, 0, 255).endVertex();
            bb.vertex(mat, (float) pb.x, (float) pb.y, (float) pb.z).color(255, 0, 0, 255).endVertex();
        }

        BufferUploader.drawWithShader(bb.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }
}
