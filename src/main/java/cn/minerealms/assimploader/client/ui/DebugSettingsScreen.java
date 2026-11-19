package cn.minerealms.assimploader.client.ui;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.debug.ClientDebugTools;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Client visual controls share config with the local debug commands. */
public final class DebugSettingsScreen extends Screen {
    private final Screen parent;
    private int tab, left, span, contentY;
    public DebugSettingsScreen(Screen parent){super(Component.literal("AssimpLoader / Debug"));this.parent=parent;}
    @Override protected void init() {
        span=Math.min(720,width-32);left=(width-span)/2;contentY=height<320?80:104;
        LookDevNavigation.install(this,widget->addRenderableWidget(widget),LookDevNavigation.Page.DEBUG);
        String[] tabs={"Geometry","Materials","Rendering","HUD"};
        int track=(span-6)/4;
        for(int i=0;i<tabs.length;i++) {
            int selected=i;
            addRenderableWidget(FlatUi.choice(left+i*(track+2),height<320?50:62,track,height<320?22:26,
                Component.literal(tabs[i]),()->tab==selected,button->{tab=selected;rebuildWidgets();}));
        }
        var config=AssimpLoaderConfig.CLIENT;var debug=config.debugView;
        switch(tab) {
            case 0 -> {
                toggle(0,"Debug geometry",debug.enabled);toggle(1,"Model bounds",debug.bounds);
                toggle(2,"Skeleton / joints",debug.skeleton);toggle(3,"Collision / rigid bodies",debug.collision);
                toggle(4,"World collision proxies",debug.worldCollision);toggle(5,"Local axes",debug.axes);
                toggle(6,"Attachments",debug.attachments);
            }
            case 1 -> {
                toggle(0,"Material diagnostics",debug.enabled);toggle(1,"PBR",config.pbrEnabled);
                int i=0;
                for(var view:AssimpLoaderConfig.MaterialView.values()) {
                    int column=i%2,row=i/2;
                    addRenderableWidget(FlatUi.choice(left+column*(span+8)/2,contentY+30+row*22,(span-8)/2,20,
                            Component.literal(view.name()),()->AssimpLoaderConfig.getOrDefault(debug.material::get,AssimpLoaderConfig.MaterialView.DEFAULT)==view,
                            button->{debug.material.set(view);debug.material.save();debug.enabled.set(true);debug.enabled.save();}));i++;
                }
            }
            case 2 -> {
                toggle(0,"PBR",config.pbrEnabled);toggle(1,"GPU skinning",config.gpuSkinningEnabled);
                toggle(2,"Oculus pose cache",config.oculusPoseCacheEnabled);toggle(3,"Frustum culling",config.frustumCullingEnabled);
                toggle(4,"Shader integration",config.oculusEnabled);toggle(5,"Shader PBR maps",config.oculusPbrSupportEnabled);
                enumRow(3,"Shader",config.shaderMode,AssimpLoaderConfig.ShaderMode.values());
                enumRow(4,"Runtime",config.runtime.mode,cn.minerealms.assimploader.config.RuntimePerformancePolicy.Mode.values());
            }
            case 3 -> {
                toggle(0,"Performance HUD",debug.hud);
                int cell=(span-4)/3;
                for(int i=1;i<=3;i++) {
                    int scale=i;
                    addRenderableWidget(FlatUi.choice(left+(i-1)*(cell+2),contentY+36,cell,26,
                            Component.literal(i+"x"),()->AssimpLoaderConfig.getIntOrDefault(debug.hudScale,1)==scale,
                            button->{debug.hudScale.set(scale);debug.hudScale.save();}));
                }
            }
        }
        addRenderableWidget(FlatUi.button(left,height-36,110,26,Component.literal("Reset debug"),button->{ClientDebugTools.reset();rebuildWidgets();}));
    }
    private void toggle(int index,String label,ForgeConfigSpec.BooleanValue value) {
        int column=index%2,row=index/2;
        addRenderableWidget(FlatUi.toggle(left+column*(span+8)/2,contentY+row*24,(span-8)/2,22,Component.literal(label),
                ()->AssimpLoaderConfig.getOrDefault(value::get,false),button->{value.set(!value.get());value.save();}));
    }
    private <E extends Enum<E>> void enumRow(int row,String name,ForgeConfigSpec.EnumValue<E> value,E[] choices) {
        int cell=(span-2*(choices.length-1))/choices.length;
        for(int i=0;i<choices.length;i++) {
            E choice=choices[i];
            addRenderableWidget(FlatUi.choice(left+i*(cell+2),contentY+row*24,cell,22,Component.literal(choice.name()),
                    ()->value.get()==choice,button->{value.set(choice);value.save();}));
        }
    }
    // Same reason as LoadedModelsScreen: 1.21's default background blur would smear this
    // screen's opaque background and leave only the widgets sharp.
    //? if neoforge {
    /*
    @Override
    public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
    }
    *///?}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,FlatUi.BACKGROUND);
        LookDevNavigation.render(graphics,width);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    @Override public void onClose(){LookDevNavigation.close(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
