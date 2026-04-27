package cn.minerealms.assimploader.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public class AutoTestClient {

    private enum State {
        WAIT_TITLE,
        WAIT_OPEN_WORLD,
        WAIT_IN_WORLD,
        WAIT_CMD,
        DONE,
        FAILED
    }

    private static State state = State.WAIT_TITLE;
    private static long titleEnterTime = -1L;
    private static long worldEnterTime = -1L;
    private static long lastStateChangeTime = -1L;
    private static boolean commandSent = false;
    private static boolean worldOpenTriggered = false;

    private static final long TITLE_DELAY_MS = 5000L;
    private static final long CMD_DELAY_MS = 5000L;
    private static final long WATCHDOG_MS = 30000L;

    // 从系统属性读取世界名称，默认为 "test"
    private static final String WORLD_NAME = System.getProperty("autotest.world", "test");

    @SubscribeEvent
    public static void onScreenOpened(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();

        if (newScreen instanceof TitleScreen) {
            state = State.WAIT_TITLE;
            titleEnterTime = System.currentTimeMillis();
            lastStateChangeTime = titleEnterTime;
            worldEnterTime = -1L;
            commandSent = false;
            worldOpenTriggered = false;
            System.out.println("[AutoTest] Title screen detected, will open world: " + WORLD_NAME);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (lastStateChangeTime > 0 && now - lastStateChangeTime > WATCHDOG_MS && state != State.DONE) {
            failAndExit(mc, "watchdog timeout in state: " + state);
            return;
        }

        switch (state) {
            case WAIT_TITLE -> {
                if (mc.screen instanceof TitleScreen) {
                    if (now - titleEnterTime >= TITLE_DELAY_MS && !worldOpenTriggered) {
                        worldOpenTriggered = true;
                        lastStateChangeTime = now;
                        System.out.println("[AutoTest] Opening world: " + WORLD_NAME);
                        openWorld(mc);
                        state = State.WAIT_OPEN_WORLD;
                    }
                }
            }

            case WAIT_OPEN_WORLD -> {
                if (mc.level != null && mc.player != null) {
                    worldEnterTime = now;
                    lastStateChangeTime = now;
                    state = State.WAIT_IN_WORLD;
                    System.out.println("[AutoTest] World loaded, waiting before command...");
                }
            }

            case WAIT_IN_WORLD -> {
                if (mc.level != null && mc.player != null) {
                    if (now - worldEnterTime >= CMD_DELAY_MS && !commandSent) {
                        System.out.println("[AutoTest] Sending /lookdev command...");
                        sendLookdevCommand(mc);
                        commandSent = true;
                        lastStateChangeTime = now;
                        state = State.WAIT_CMD;
                    }
                }
            }

            case WAIT_CMD -> {
                if (now - lastStateChangeTime >= 5000L) {
                    System.out.println("[AutoTest] Test sequence completed successfully");
                    state = State.DONE;
                }
            }

            case DONE, FAILED -> {
                // 测试完成
            }
        }
    }

    private static void openWorld(Minecraft mc) {
        try {
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD_NAME);
        } catch (Exception e) {
            failAndExit(mc, "openWorld error: " + e.getMessage());
        }
    }

    private static void sendLookdevCommand(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) return;

        try {
            mc.player.connection.sendCommand("lookdev");
        } catch (Exception e) {
            failAndExit(mc, "send command error: " + e.getMessage());
        }
    }

    private static void failAndExit(Minecraft mc, String reason) {
        state = State.FAILED;
        System.err.println("[AutoTest] FAILED: " + reason);
        mc.execute(mc::stop);
    }
}
