package cn.minerealms.assimploader.mixin.iris;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.rocket.client.ExhaustUniforms;
import cn.minerealms.assimploader.rocket.client.RocketExhausts;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Oculus bridge. It writes only explicitly opted-in pack uniforms after Program.use binds them. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gl.program.Program", remap = false)
public abstract class RocketProgramMixin {
    @Unique private ExhaustUniforms assimploader$rocketUniforms;

    @Inject(method = "use()V", at = @At("TAIL"), require = 1, remap = false)
    private void assimploader$uploadRocketData(CallbackInfo callback) {
        if (assimploader$rocketUniforms == null)
            assimploader$rocketUniforms = new ExhaustUniforms(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM));
        if (assimploader$rocketUniforms.active()) {
            if (!AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.rocketShaderBridgeEnabled::get, true)) {
                assimploader$rocketUniforms.disable();
                return;
            }
            assimploader$rocketUniforms.upload(RocketExhausts.packet());
            RocketExhausts.recordBridgeDraw();
        }
    }
}
