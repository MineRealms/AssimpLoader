package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.animation.InstanceDefinitionResources;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.rendering.CachedMeshRenderer;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;

/** Owns render caches as one resource generation, including GPU allocation lifetime. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class ClientRenderResources {
    private static long generation = Long.MIN_VALUE;
    private static SkinnedMeshRenderer skinnedRenderer;
    private static boolean skinningFailed;
    private static final FrameReleases FRAME_RELEASES = new FrameReleases();
    static {
        AssimpModelLoader.addCacheInvalidationListener(ClientRenderResources::clear);
    }
    private ClientRenderResources() {}

    public static boolean checkGeneration() {
        long current = AssimpModelLoader.getResourceGeneration();
        if (current == generation) return false;
        clear();
        return true;
    }

    public static SkinnedMeshRenderer getSkinnedRenderer() {
        if (skinningFailed) return null;
        if (skinnedRenderer == null) {
            SkinnedMeshRenderer renderer = new SkinnedMeshRenderer();
            try {
                renderer.initialize();
                skinnedRenderer = renderer;
            } catch (Exception e) {
                renderer.cleanup();
                skinningFailed = true;
                AssimpLoader.LOGGER.warn("GPU skinning unavailable; using CPU skinning", e);
            }
        }
        return skinnedRenderer;
    }

    public static SkinnedMeshRenderer.ComputeStatistics computeStatistics() {
        return skinnedRenderer==null?new SkinnedMeshRenderer.ComputeStatistics(0,0,0,0,false):skinnedRenderer.computeStatistics();
    }

    public static void disableGpuSkinning(RuntimeException failure) {
        if (!skinningFailed) AssimpLoader.LOGGER.warn("GPU skinning failed; using CPU skinning", failure);
        skinningFailed = true;
        if (skinnedRenderer != null) skinnedRenderer.cleanup();
        skinnedRenderer = null;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ClientRenderResources::clear);
            return;
        }
        generation = AssimpModelLoader.getResourceGeneration();
        CachedMeshRenderer.clear();
        cn.minerealms.assimploader.client.WorldModelBatches.clear();
        OculusEntityBufferCache.clear();
        if (skinnedRenderer != null) skinnedRenderer.cleanup();
        skinnedRenderer = null;
        skinningFailed = false;
        ModelTextureCache.clear();
        AssimpRenderTypes.clear();
        SceneTransforms.clear();
        CpuSkinning.clear();
        ClientAnimationManager.clearCache();
        InstanceDefinitionResources.clear();
        ScriptShaderParameters.defaults();
    }

    /** Buffered entity passes retain texture locations until the entire render frame has finished. */
    static void releaseAfterFrame(Runnable release) {
        RenderSystem.assertOnRenderThread();
        FRAME_RELEASES.retire(release);
    }

    //? if forge {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.START) FRAME_RELEASES.begin();
        else FRAME_RELEASES.end();
    }
    //?}
    //? if neoforge {
    /*
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frameStart(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        FRAME_RELEASES.begin();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frameEnd(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        FRAME_RELEASES.end();
    }
    *///?}

    static final class FrameReleases {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private boolean active;

        void begin() { active = true; }

        void retire(Runnable release) {
            if (active) pending.addLast(release);
            else release.run();
        }

        void end() {
            active = false;
            RuntimeException failure = null;
            while (!pending.isEmpty()) {
                try {
                    pending.removeFirst().run();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else if (failure != exception) failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    /** Final teardown runs before Minecraft closes its texture manager and GL context. */
    public static void shutdown() {
        Runnable shutdown = () -> { clear(); FRAME_RELEASES.end(); };
        if (RenderSystem.isOnRenderThread()) shutdown.run();
        else RenderSystem.recordRenderCall(shutdown::run);
    }
    @SubscribeEvent public static void entityLeft(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            Minecraft.getInstance().execute(() -> ClientAnimationManager.getInstance().release(event.getEntity()));
        }
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.END) {
            OculusEntityBufferCache.prunePosed();
            if(skinnedRenderer!=null) skinnedRenderer.pruneCompute();
        }
    }

    @Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadRegistration {
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
        }
    }
}
