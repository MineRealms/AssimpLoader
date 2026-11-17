package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.render.ScriptShaderParameters;

/** JSON declares uniform types; scripts submit only activation and numeric values. */
public final class ShaderScriptApi {
    public ShaderScriptApi define(String id,String resource){ScriptShaderParameters.define(id,resource);return this;}
    public ShaderScriptApi enabled(String id,boolean enabled){ScriptShaderParameters.enabled(id,enabled);return this;}
    public ShaderScriptApi uniform(String id,String name,double[] values){ScriptShaderParameters.set(id,name,values);return this;}
    public void clear(){ScriptShaderParameters.reset();}
}
