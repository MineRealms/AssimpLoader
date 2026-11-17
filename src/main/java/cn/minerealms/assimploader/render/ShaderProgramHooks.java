package cn.minerealms.assimploader.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/** Host-owned native uniform providers; no content semantics or script callbacks in GL draws. */
public final class ShaderProgramHooks {
    private static final Map<String,Consumer<Binding>> providers=new LinkedHashMap<>();
    public static final class Binding {
        private final int program;
        private final Map<String,Object> state=new LinkedHashMap<>();
        private record UniformSlot(int location,int type) {}
        private final Map<String,UniformSlot> locations=new LinkedHashMap<>();
        private final java.util.Set<String> typeWarnings=new java.util.HashSet<>();
        public Binding(int program){this.program=program;}
        public <T> T state(String id,Class<T> type,IntFunction<T> factory) {
            if(!state.containsKey(id)&&state.size()>=32)throw new IllegalStateException("Too many program states");
            return type.cast(state.computeIfAbsent(id,key->factory.apply(program)));
        }
        int location(String name,ShaderParameterDefinition.Type type) {
            if(locations.size()>=2048 && !locations.containsKey(name))locations.clear();
            var slot=locations.computeIfAbsent(name,key->{
                int location=org.lwjgl.opengl.GL20C.glGetUniformLocation(program,key);
                if(location<0)return new UniformSlot(-1,0);
                int index=org.lwjgl.opengl.GL31C.glGetUniformIndices(program,key);
                int actual=index==-1?0:org.lwjgl.opengl.GL31C.glGetActiveUniformsi(program,index,org.lwjgl.opengl.GL31C.GL_UNIFORM_TYPE);
                return new UniformSlot(location,actual);
            });
            int expected=switch(type){
                case INT->org.lwjgl.opengl.GL11C.GL_INT;
                case FLOAT->org.lwjgl.opengl.GL11C.GL_FLOAT;
                case VEC2->org.lwjgl.opengl.GL20C.GL_FLOAT_VEC2;
                case VEC3->org.lwjgl.opengl.GL20C.GL_FLOAT_VEC3;
                case VEC4->org.lwjgl.opengl.GL20C.GL_FLOAT_VEC4;
            };
            if(slot.location>=0 && slot.type!=expected) {
                if(typeWarnings.size()<2048 && typeWarnings.add(name+":"+type))
                    cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Shader parameter {} declared {}, but GL program {} has type {}; skipping",name,type,program,slot.type);
                return -1;
            }
            return slot.location;
        }
    }
    private ShaderProgramHooks() {}
    public static void register(String id,Consumer<Binding> provider) {
        if(!providers.containsKey(id) && providers.size()>=32)throw new IllegalStateException("Too many shader providers");
        providers.put(java.util.Objects.requireNonNull(id),java.util.Objects.requireNonNull(provider));
    }
    public static void bound(Binding binding) {
        for(var provider:providers.values())provider.accept(binding);
        ScriptShaderParameters.upload(binding);
    }
}
