package cn.minerealms.assimpshowcase.rocket.client;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.render.ShaderProgramHooks;
import cn.minerealms.assimpshowcase.ShowcaseConfig;

/** The engine knows only a program callback; exhaust uniforms and packets belong to this mod. */
public final class RocketShaderBridge {
    private RocketShaderBridge() {}
    public static void register(){ShaderProgramHooks.register("assimpshowcase:exhaust",RocketShaderBridge::upload);}
    private static void upload(ShaderProgramHooks.Binding binding) {
        var uniforms=binding.state("assimpshowcase:exhaust",ExhaustUniforms.class,ExhaustUniforms::new);
        if(!uniforms.active())return;
        if(!AssimpLoaderConfig.getOrDefault(ShowcaseConfig.shaderBridge::get,true)){uniforms.disable();return;}
        uniforms.upload(RocketExhausts.packet());RocketExhausts.recordBridgeDraw();
    }
}
