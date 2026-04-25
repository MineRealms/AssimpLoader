package cn.minerealms.assimploader.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Auto-test manager for automated testing.
 *
 * When enabled, automatically executes test commands after joining world.
 */
public class AutoTestManager {

    // Configuration
    public static boolean AUTO_TEST_ENABLED = Boolean.getBoolean("autotest.enabled"); // Enable via -Dautotest.enabled=true
    private static final String[] TEST_COMMANDS = {
        "/lookdev"
    };
    private static final int DELAY_TICKS = 100; // 5 seconds after world join

    // State
    private static boolean hasExecuted = false;
    private static int tickCounter = 0;
    private static boolean wasInWorld = false;

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
        if (!AUTO_TEST_ENABLED) {
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
            if (!hasExecuted && tickCounter >= DELAY_TICKS) {
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
            DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: Executing %d test commands", TEST_COMMANDS.length);

            for (String command : TEST_COMMANDS) {
                DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: Executing command: %s", command);
                player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
            }

            DebugManager.info(DebugManager.DebugCategory.CORE, "AutoTest: All commands executed successfully");
        } catch (Exception e) {
            DebugManager.error(DebugManager.DebugCategory.CORE, "AutoTest: Failed to execute commands", e);
        }
    }
}
