package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.lookdev.ui.LoadedModelsScreen;
import cn.minerealms.assimploader.showcase.ShowcaseModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.Arrays;

/** Explicit development smoke run; normal clients never open worlds or capture frames here. */
@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public final class PreviewSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("autotest.enabled")
            && (Boolean.getBoolean("autotest.capture") || Boolean.getBoolean("autotest.startupOnly"));
    private static final String[] MODELS = Arrays.stream(ShowcaseModel.values())
            .map(ShowcaseModel::modelName).toArray(String[]::new);
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final long TEXTURE_SETTLE_MS = 10_000;
    private enum Phase { TITLE, WORLD, OPEN, RENDER, CAPTURE, ITEMS_RENDER, ITEMS_CAPTURE, RELOAD, FINISH, DONE }
    private static Phase phase = Phase.TITLE;
    private static long changedAt = System.currentTimeMillis();
    private static int model;
    private static boolean afterReload;
    private static boolean captureDone;

    private PreviewSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        long elapsed = System.currentTimeMillis() - changedAt;
        if (elapsed > 120_000) { fail(mc, "timeout in " + phase, null); return; }
        try {
            switch (phase) {
                case TITLE -> {
                    if (mc.screen instanceof TitleScreen && elapsed > 3_000) {
                        if (Boolean.getBoolean("autotest.startupOnly")) {
                            AssimpLoader.LOGGER.info("[PreviewSmoke] SUCCESS: client startup and initial resource bake");
                            phase = Phase.DONE;
                            mc.stop();
                            return;
                        }
                        String world = System.getProperty("autotest.world");
                        if (world == null || world.isBlank()) throw new IllegalStateException("Existing world must be specified");
                        AssimpLoader.LOGGER.info("[PreviewSmoke] Opening existing world {}", world);
                        mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                        move(Phase.WORLD);
                    }
                }
                case WORLD -> {
                    if (mc.level != null && mc.player != null) move(Phase.OPEN);
                }
                case OPEN -> {
                    if (elapsed < 2_000) return;
                    if (!ClientCommandHandler.runCommand("lookdev")) throw new IllegalStateException("LookDev command unavailable");
                    if (!(mc.screen instanceof LoadedModelsScreen screen)) throw new IllegalStateException("LookDev did not open");
                    select(screen);
                }
                case RENDER -> {
                    if (!(mc.screen instanceof LoadedModelsScreen screen)) throw new IllegalStateException("Preview screen closed");
                    if (screen.isPreviewFailed()) throw new IllegalStateException("Preview renderer failed");
                    if (elapsed < TEXTURE_SETTLE_MS || !screen.isPreviewRendered()) return;
                    int error = GL11.glGetError();
                    if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL error " + error);
                    String name = "assimp-smoke-" + RUN + "-" + (afterReload ? "reload-" : "") + MODELS[model] + ".png";
                    captureDone = false;
                    move(Phase.CAPTURE);
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) {
                            fail(mc, "screenshot failed: " + message.getString(), null);
                        } else {
                            AssimpLoader.LOGGER.info("[PreviewSmoke] Captured {}", name);
                            captureDone = true;
                        }
                    }));
                }
                case CAPTURE -> {
                    if (!captureDone) return;
                    if (afterReload) { move(Phase.FINISH); return; }
                    if (++model < MODELS.length) {
                        select((LoadedModelsScreen) mc.screen);
                    } else {
                        mc.setScreen(new ItemViews());
                        move(Phase.ITEMS_RENDER);
                    }
                }
                case ITEMS_RENDER -> {
                    if (elapsed < TEXTURE_SETTLE_MS) return;
                    int error = GL11.glGetError();
                    if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("Item OpenGL error " + error);
                    String name = "assimp-smoke-" + RUN + "-items.png";
                    captureDone = false;
                    move(Phase.ITEMS_CAPTURE);
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                        if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) {
                            fail(mc, "item screenshot failed: " + message.getString(), null);
                        } else {
                            AssimpLoader.LOGGER.info("[PreviewSmoke] Captured {}", name);
                            captureDone = true;
                        }
                    }));
                }
                case ITEMS_CAPTURE -> {
                    if (!captureDone) return;
                    mc.setScreen(null);
                    move(Phase.RELOAD);
                    mc.reloadResourcePacks().whenComplete((ignored, failure) -> mc.execute(() -> {
                        if (failure != null) { fail(mc, "resource reload failed", failure); return; }
                        afterReload = true;
                        model = 1;
                        AssimpLoader.LOGGER.info("[PreviewSmoke] Resource reload completed");
                        move(Phase.OPEN);
                    }));
                }
                case FINISH -> {
                    mc.setScreen(null);
                    AssimpLoader.LOGGER.info("[PreviewSmoke] SUCCESS: {} scenes, {} GUI items, view input and resource reload captured; run={}",
                            MODELS.length, MODELS.length, RUN);
                    phase = Phase.DONE;
                    mc.stop();
                }
                case RELOAD, DONE -> { }
            }
        } catch (Exception | LinkageError failure) {
            fail(mc, "failure in " + phase, failure);
        }
    }

    private static void select(LoadedModelsScreen screen) {
        if (!screen.selectModel(MODELS[model])) throw new IllegalStateException("Missing loaded model " + MODELS[model]);
        if (model == 1 && !afterReload) {
            double x = screen.width * 0.75, y = screen.height * 0.5;
            screen.mouseClicked(x, y, 0);
            screen.mouseDragged(x + 24, y + 12, 0, 24, 12);
            screen.mouseReleased(x + 24, y + 12, 0);
            screen.mouseClicked(x, y, 2);
            screen.mouseDragged(x + 8, y, 2, 8, 0);
            screen.mouseReleased(x + 8, y, 2);
            screen.mouseScrolled(x, y, 1);
        }
        move(Phase.RENDER);
    }

    private static void move(Phase next) {
        phase = next;
        changedAt = System.currentTimeMillis();
    }

    private static void fail(Minecraft mc, String message, Throwable failure) {
        AssimpLoader.LOGGER.error("[PreviewSmoke] FAILED: " + message, failure);
        phase = Phase.DONE;
        mc.stop();
    }

    private static final class ItemViews extends Screen {
        private ItemViews() { super(Component.literal("AssimpLoader Item Smoke")); }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff30363a);
            int rows = (MODELS.length + 2) / 3;
            for (int index = 0; index < MODELS.length; index++) {
                int x = width * (index % 3 + 1) / 4;
                int y = height * (index / 3 + 1) / (rows + 1);
                var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("assimploader", "showcase_" + MODELS[index]));
                if (item == null) throw new IllegalStateException("Missing item " + MODELS[index]);
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x, y, 0);
                    graphics.pose().scale(3, 3, 3);
                    graphics.renderItem(new ItemStack(item), -8, -8);
                } finally {
                    graphics.pose().popPose();
                }
                graphics.drawCenteredString(font, MODELS[index], x, y + 30, 0xffe9edef);
            }
        }
    }
}
