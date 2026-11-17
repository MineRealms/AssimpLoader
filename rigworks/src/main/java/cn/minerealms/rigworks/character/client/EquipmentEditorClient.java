package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.RigWorks;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Normal client input entry points; no renderer or resource reload is needed. */
@Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT)
public final class EquipmentEditorClient {
    private static final KeyMapping EDIT = new KeyMapping("key.rigworks.equipment", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.rigworks");
    private static int pendingOpen;

    private EquipmentEditorClient() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (pendingOpen > 0) {
            pendingOpen--;
            if (minecraft.screen == null && minecraft.level != null) {
                pendingOpen = 0;
                EquipmentTransformScreen.open();
            }
        }
        while (EDIT.consumeClick()) if (minecraft.screen == null) EquipmentTransformScreen.open();
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimploader").then(Commands.literal("character")
                .then(Commands.literal("equipment").executes(context -> {
                    // Chat closes after command execution; open on a later normal client tick.
                    pendingOpen = 20;
                    return 1;
                }))));
    }

    @Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(EDIT); }
    }
}
