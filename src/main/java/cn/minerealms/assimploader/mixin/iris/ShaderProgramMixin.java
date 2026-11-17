package cn.minerealms.assimploader.mixin.iris;

import cn.minerealms.assimploader.render.ShaderProgramHooks;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional pinned Oculus hook. Uniform providers never own the program lifetime. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.gl.program.Program",remap=false)
public abstract class ShaderProgramMixin {
    @Unique private ShaderProgramHooks.Binding assimploader$binding;
    @Inject(method="use()V",at=@At("TAIL"),require=1,remap=false)
    private void assimploader$bound(CallbackInfo callback){
        if(assimploader$binding==null)assimploader$binding=new ShaderProgramHooks.Binding(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM));
        ShaderProgramHooks.bound(assimploader$binding);
    }
}
