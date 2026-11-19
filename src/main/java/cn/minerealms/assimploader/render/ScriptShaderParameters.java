package cn.minerealms.assimploader.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20C;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import cn.minerealms.assimploader.util.ResourceLocations;
/** Client-tick commands publish bounded values; the bound program consumes them without entering JS. */
public final class ScriptShaderParameters {
    static final class Parameter {
        final ShaderParameterDefinition.Uniform definition;
        final float[] value,disabled;
        Parameter(ShaderParameterDefinition.Uniform definition) { this.definition=definition;value=definition.defaults();disabled=definition.disabled(); }
    }
    static final class Pass {
        final Map<String,Parameter> parameters=new LinkedHashMap<>();
        final boolean defaultEnabled;
        boolean enabled;
        Pass(ShaderParameterDefinition definition) {
            defaultEnabled=enabled=definition.enabled();
            definition.uniforms().forEach((name,uniform)->parameters.put(name,new Parameter(uniform)));
        }
    }
    /** Pure command state. Retired uniforms keep publishing disabled values to every bound program. */
    static final class Registry {
        final Map<String,Pass> passes=new LinkedHashMap<>();
        final Map<String,Parameter> retired=new LinkedHashMap<>();
        void define(String id,ShaderParameterDefinition definition) {
            if(id==null || !id.matches("[A-Za-z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Invalid shader pass ID");
            if(!passes.containsKey(id)&&passes.size()>=32)throw new IllegalArgumentException("Shader pass limit");
            Pass replacement=new Pass(java.util.Objects.requireNonNull(definition));
            for(var other:passes.entrySet())if(!other.getKey().equals(id)) {
                for(String name:replacement.parameters.keySet())if(other.getValue().parameters.containsKey(name))
                    throw new IllegalArgumentException("Uniform already owned by shader pass "+other.getKey()+": "+name);
            }
            var nextRetired=new LinkedHashMap<>(retired);
            Pass old=passes.get(id);
            if(old!=null)old.parameters.forEach((name,value)->{if(!replacement.parameters.containsKey(name))nextRetired.put(name,value);});
            replacement.parameters.keySet().forEach(nextRetired::remove);
            int names=nextRetired.size()+replacement.parameters.size();
            for(var other:passes.entrySet())if(!other.getKey().equals(id))names+=other.getValue().parameters.size();
            if(names>2048)throw new IllegalArgumentException("Uniform lifetime limit; too many distinct parameter names");
            // Publish only after schema, ownership and retirement budget all validate.
            retired.clear();retired.putAll(nextRetired);passes.put(id,replacement);
        }
        void enabled(String id,boolean enabled){pass(id).enabled=enabled;}
        void set(String id,String name,double[] values) {
            Parameter parameter=pass(id).parameters.get(name);
            if(parameter==null)throw new IllegalArgumentException("Unknown shader uniform: "+name);
            float[] validated=parameter.definition.validate(values);
            System.arraycopy(validated,0,parameter.value,0,validated.length);
        }
        void reset() {
            var nextRetired=new LinkedHashMap<>(retired);
            for(var pass:passes.values())nextRetired.putAll(pass.parameters);
            if(nextRetired.size()>2048)throw new IllegalArgumentException("Retired uniform limit; cannot discard disabled values");
            retired.clear();retired.putAll(nextRetired);passes.clear();
        }
        void defaults() {
            for(var pass:passes.values()) {
                pass.enabled=pass.defaultEnabled;
                for(var parameter:pass.parameters.values()) {
                    float[] defaults=parameter.definition.defaults();
                    System.arraycopy(defaults,0,parameter.value,0,defaults.length);
                }
            }
        }
        private Pass pass(String id) {
            Pass result=passes.get(id);
            if(result==null)throw new IllegalArgumentException("Unknown shader parameters: "+id);
            return result;
        }
    }
    private static final Registry registry=new Registry();
    private static final java.util.concurrent.atomic.AtomicBoolean resetRequested=new java.util.concurrent.atomic.AtomicBoolean();
    private ScriptShaderParameters() {}
    /** KubeJS rebuilds bindings on its loading thread; actual registry mutation stays on the client thread. */
    public static void requestReset(){resetRequested.set(true);}
    private static void applyReset(){if(resetRequested.getAndSet(false))registry.reset();}
    public static void define(String id,String resource) {
        checkThread();applyReset();
        try(var input=Minecraft.getInstance().getResourceManager().open(ResourceLocations.parse(resource));
            var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            registry.define(id,ShaderParameterDefinition.read(reader));
        }catch(java.io.IOException error){throw new IllegalArgumentException("Cannot read shader parameters: "+resource,error);}
    }
    public static void enabled(String id,boolean enabled){checkThread();applyReset();registry.enabled(id,enabled);}
    public static void set(String id,String name,double[] values){checkThread();applyReset();registry.set(id,name,values);}
    public static void reset(){checkThread();applyReset();registry.reset();}
    public static void defaults(){registry.defaults();}
    static void uploadRetired(ShaderProgramHooks.Binding binding) {
        applyReset();
        for(var entry:registry.retired.entrySet())upload(binding,entry.getKey(),entry.getValue(),false);
    }
    static void upload(ShaderProgramHooks.Binding binding) {
        // Active script declarations override native providers; retired declarations cannot suppress native defaults.
        for(var pass:registry.passes.values())for(var entry:pass.parameters.entrySet())
            upload(binding,entry.getKey(),entry.getValue(),pass.enabled);
    }
    private static void upload(ShaderProgramHooks.Binding binding,String name,Parameter parameter,boolean enabled) {
        int location=binding.location(name,parameter.definition.type());if(location<0)return;
        var value=enabled?parameter.value:parameter.disabled;
        if(parameter.definition.type()==ShaderParameterDefinition.Type.INT)GL20C.glUniform1i(location,(int)value[0]);
        else switch(value.length){case 1->GL20C.glUniform1f(location,value[0]);case 2->GL20C.glUniform2f(location,value[0],value[1]);case 3->GL20C.glUniform3f(location,value[0],value[1],value[2]);case 4->GL20C.glUniform4f(location,value[0],value[1],value[2],value[3]);default->throw new IllegalStateException();}
    }
    private static void checkThread(){if(!Minecraft.getInstance().isSameThread())throw new IllegalStateException("Shader commands require client thread");}
}
