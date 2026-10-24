package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.ui.LookDevScreen;
import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind that opens the LookDev viewer.
 *
 * The key mapping itself is registered on the MOD bus (see
 * {@link #registerKeyMappings(RegisterKeyMappingsEvent)}, wired up from
 * {@code AssimpLoader#clientSetup}); the click is consumed on the FORGE bus.
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LookDevKeybind {

    public static final String KEY_NAME = "key.assimploader.lookdev";

    private static KeyMapping openLookDev;

    private LookDevKeybind() {
    }

    /**
     * MOD bus - called from {@code AssimpLoader}.
     */
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openLookDev = new KeyMapping(
                KEY_NAME,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "key.categories.assimploader"
        );
        event.register(openLookDev);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || openLookDev == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            // Drain the queue so the key does not fire when the GUI is closed again.
            while (openLookDev.consumeClick()) {
                // ignore
            }
            return;
        }

        while (openLookDev.consumeClick()) {
            if (minecraft.player == null) {
                continue;
            }
            try {
                MuiModApi.openScreen(new LookDevScreen());
            } catch (Throwable t) {
                DebugManager.error(DebugCategory.LOOKDEV, "Failed to open LookDev viewer", t);
            }
        }
    }
}
