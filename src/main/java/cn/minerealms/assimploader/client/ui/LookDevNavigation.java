package cn.minerealms.assimploader.client.ui;

import cn.minerealms.assimploader.character.client.CharacterSelectionScreen;
import cn.minerealms.assimploader.lookdev.ui.LoadedModelsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** One workspace navigation shared by every active AssimpLoader GUI. */
public final class LookDevNavigation {
    public enum Page { MODELS, NODES, PLAYER, DEBUG }
    private static Workspace workspace;
    private static final class Workspace {
        final LoadedModelsScreen models = new LoadedModelsScreen();
        final CharacterSelectionScreen player = new CharacterSelectionScreen(null);
        final DebugSettingsScreen debug = new DebugSettingsScreen(null);
        boolean contains(Screen screen) { return screen == models || screen == player || screen == debug; }
    }
    private LookDevNavigation(){}
    public static void open(Page page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (workspace == null || !workspace.contains(minecraft.screen)) workspace = new Workspace();
        Screen target = switch (page) {
            case MODELS, NODES -> workspace.models;
            case PLAYER -> workspace.player;
            case DEBUG -> workspace.debug;
        };
        if (target == workspace.models) workspace.models.setTab(page == Page.NODES);
        if (target != minecraft.screen) minecraft.setScreen(target);
    }
    public static void close(Screen fallback) {
        workspace = null;
        Minecraft.getInstance().setScreen(fallback);
    }
    public static void install(Screen host,Consumer<AbstractWidget> add,Page selected) {
        int start=78,cell=(host.width-start-44-6)/4;
        for(var page:Page.values()) {
            String label=switch(page){case MODELS->"Models";case NODES->"Nodes";case PLAYER->"Player";case DEBUG->"Debug";};
            add.accept(FlatUi.choice(start+page.ordinal()*(cell+2),10,cell,26,Component.literal(label),()->selected==page,
                    button->{if(selected!=page)open(page);}));
        }
        add.accept(FlatUi.icon(host.width-36,10,FlatUi.Icon.CLOSE,Component.literal("Close LookDev"),button->host.onClose()));
    }
    public static void render(GuiGraphics graphics,int width) {
        graphics.fill(0,0,width,46,FlatUi.SURFACE);graphics.fill(0,45,width,46,FlatUi.LINE);
        graphics.drawString(Minecraft.getInstance().font,"LookDev",12,19,FlatUi.ACCENT,false);
    }
}
