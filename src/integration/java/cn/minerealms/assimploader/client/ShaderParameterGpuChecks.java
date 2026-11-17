package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.render.ScriptShaderParameters;
import cn.minerealms.assimploader.render.ShaderProgramHooks;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.util.Map;

/** Explicit acceptance only: no user shader sources or pack files are modified. */
public final class ShaderParameterGpuChecks {
    private ShaderParameterGpuChecks() {}

    public static Map<String,Object> verify() {
        int previous=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int vertex=0,fragment=0,program=0;
        try {
            vertex=shader(GL20C.GL_VERTEX_SHADER,"#version 150\nvoid main(){gl_Position=vec4(0.0,0.0,0.0,1.0);}");
            fragment=shader(GL20C.GL_FRAGMENT_SHADER,"""
                    #version 150
                    uniform int assimpAcceptanceInt;
                    uniform float assimpAcceptanceFloat;
                    uniform vec2 assimpAcceptanceVec2;
                    uniform vec3 assimpAcceptanceVec3;
                    uniform vec4 assimpAcceptanceVec4;
                    uniform float assimpAcceptanceWrongType;
                    out vec4 color;
                    void main(){color=vec4(float(assimpAcceptanceInt)+assimpAcceptanceFloat+
                        assimpAcceptanceVec2.x+assimpAcceptanceVec2.y+
                        dot(assimpAcceptanceVec3,vec3(1.0))+dot(assimpAcceptanceVec4,vec4(1.0))+
                        assimpAcceptanceWrongType);}
                    """);
            program=GL20C.glCreateProgram();
            GL20C.glAttachShader(program,vertex);GL20C.glAttachShader(program,fragment);GL20C.glLinkProgram(program);
            require(GL20C.glGetProgrami(program,GL20C.GL_LINK_STATUS)!=0,GL20C.glGetProgramInfoLog(program));
            GL20C.glUseProgram(program);
            ScriptShaderParameters.requestReset();
            ScriptShaderParameters.define("acceptance:gpu","assimploader:shader_parameters/acceptance.json");
            ScriptShaderParameters.enabled("acceptance:gpu",true);
            ScriptShaderParameters.set("acceptance:gpu","assimpAcceptanceInt",new double[]{7});
            ScriptShaderParameters.set("acceptance:gpu","assimpAcceptanceVec3",new double[]{.2,.4,.6});
            var binding=new ShaderProgramHooks.Binding(program);
            ShaderProgramHooks.bound(binding);
            require(GL20C.glGetUniformi(program,location(program,"assimpAcceptanceInt"))==7,"Integer uniform upload");
            check(program,"assimpAcceptanceFloat",new float[]{.25f});
            check(program,"assimpAcceptanceVec2",new float[]{.1f,.2f});
            check(program,"assimpAcceptanceVec3",new float[]{.2f,.4f,.6f});
            check(program,"assimpAcceptanceVec4",new float[]{.1f,.2f,.3f,.4f});
            check(program,"assimpAcceptanceWrongType",new float[]{0});
            ScriptShaderParameters.enabled("acceptance:gpu",false);
            ShaderProgramHooks.bound(binding);
            require(GL20C.glGetUniformi(program,location(program,"assimpAcceptanceInt"))==0,"Disabled integer uniform");
            check(program,"assimpAcceptanceVec3",new float[]{0,0,0});
            ScriptShaderParameters.enabled("acceptance:gpu",true);
            ShaderProgramHooks.bound(binding);
            require(GL20C.glGetUniformi(program,location(program,"assimpAcceptanceInt"))==7,"Reenabled uniform");
            // Binding reload may be requested off-thread; all GL writes happen on the next bind.
            ScriptShaderParameters.requestReset();
            ShaderProgramHooks.bound(binding);
            require(GL20C.glGetUniformi(program,location(program,"assimpAcceptanceInt"))==0,"Reload did not retire old uniform");
            check(program,"assimpAcceptanceVec3",new float[]{0,0,0});
            require(GL11C.glGetError()==GL11C.GL_NO_ERROR,"Uniform bridge produced an OpenGL error");
            return Map.of("passed",true,"types",5,"disabledValues",true,"typeMismatchSkipped",true,"reloadRetirement",true);
        } finally {
            GL20C.glUseProgram(previous);
            if(program!=0)GL20C.glDeleteProgram(program);
            if(vertex!=0)GL20C.glDeleteShader(vertex);
            if(fragment!=0)GL20C.glDeleteShader(fragment);
        }
    }

    private static int shader(int type,String source) {
        int shader=GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader,source);GL20C.glCompileShader(shader);
        if(GL20C.glGetShaderi(shader,GL20C.GL_COMPILE_STATUS)==0) {
            String error=GL20C.glGetShaderInfoLog(shader);GL20C.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }
    private static int location(int program,String name) {
        int result=GL20C.glGetUniformLocation(program,name);require(result>=0,"Missing acceptance uniform: "+name);return result;
    }
    private static void check(int program,String name,float[] expected) {
        float[] actual=new float[expected.length];GL20C.glGetUniformfv(program,location(program,name),actual);
        for(int index=0;index<actual.length;index++)require(Math.abs(actual[index]-expected[index])<1e-5,"Uniform value mismatch: "+name);
    }
    private static void require(boolean value,String message) {if(!value)throw new IllegalStateException(message);}
}
