package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Auto-test manager for automated testing.
 *
 * When enabled via config, automatically executes test commands after joining world.
 */
public class AutoTestManager {

    // State
    private static boolean hasExecuted = false;
    private static int tickCounter = 0;
    private static boolean wasInWorld = false;

    /**
     * Check if auto-test is enabled
     */
    private static boolean isEnabled() {
        return AssimpLoaderConfig.CLIENT.devAutoTestEnabled.get();
    }

    /**
     * Get test commands from config
     */
    private static List<? extends String> getTestCommands() {
        return AssimpLoaderConfig.CLIENT.devAutoTestCommands.get();
    }

    /**
     * Get delay ticks from config
     */
    private static int getDelayTicks() {
        return AssimpLoaderConfig.CLIENT.devAutoTestDelay.get();
    }

    /**
     * Reset state when leaving world
     */
    public static void reset() {
        hasExecuted = false;
        tickCounter = 0;
        wasInWorld = false;
        DebugManager.debug(DebugManager.DebugCategory.CORE, "AutoTestManager reset");
    }

    /**
     * Tick handler for auto-test execution
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!isEnabled()) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        // Detect world join
        if (level != null && player != null) {
            if (!wasInWorld) {
                // Just joined world
                wasInWorld = true;
                tickCounter = 0;
                hasExecuted = false;
                DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: Player joined world, starting countdown");
            }

            // Execute commands after delay
            if (!hasExecuted && tickCounter >= getDelayTicks()) {
                executeTestCommands(player);
                hasExecuted = true;
            }

            tickCounter++;
        } else {
            // Not in world
            if (wasInWorld) {
                // Just left world
                reset();
            }
            wasInWorld = false;
        }
    }

    /**
     * Execute test commands
     */
    private static void executeTestCommands(LocalPlayer player) {
        try {
            List<? extends String> commands = getTestCommands();
            DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: Executing %d test commands", commands.size());

            for (String command : commands) {
                DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: Executing command: %s", command);
                player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
            }

            DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: All commands executed successfully");
        } catch (Exception e) {
            DebugManager.error(DebugManager.DebugCategory.CORE, "AutoTest: Failed to execute commands", e);
        }
    }
}
