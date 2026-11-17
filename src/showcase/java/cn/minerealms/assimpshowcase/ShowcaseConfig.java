package cn.minerealms.assimpshowcase;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class ShowcaseConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue exhaustEnabled, shaderBridge;
    static {
        var builder=new ForgeConfigSpec.Builder();builder.push("rocketExhaust");
        exhaustEnabled=builder.define("enabled",true);
        shaderBridge=builder.define("shaderPackBridge",true);
        builder.pop();SPEC=builder.build();
    }
    private ShowcaseConfig() {}
    public static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,SPEC);}
}
