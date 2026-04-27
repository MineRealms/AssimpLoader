package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;

import java.util.*;

/**
 * Emits GLSL code fragments (not complete shaders).
 *
 * Extends GLSLEmitter to:
 * - Track used uniforms for injection
 * - Generate fragment code without shader wrapper
 * - Support injection-specific features
 */
public class GLSLFragmentEmitter extends GLSLEmitter {

    private final Set<String> usedUniforms = new HashSet<>();

    @Override
    public void reset() {
        super.reset();
        usedUniforms.clear();
    }

    @Override
    public String emitNode(Node node, ShaderCodeGenerator generator) {
        // Track uniforms used by this node
        if (node instanceof TextureSampleNode) {
            String textureName = "texture_" + node.getId().replace("-", "_");
            usedUniforms.add("sampler2D " + textureName);
        }

        return super.emitNode(node, generator);
    }

    /**
     * Emit only the uniforms that are actually used
     */
    @Override
    public String emitUniforms() {
        if (usedUniforms.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Node-based material uniforms\n");
        for (String uniform : usedUniforms) {
            sb.append("uniform ").append(uniform).append(";\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Get the set of uniforms used in the generated code
     */
    public Set<String> getUsedUniforms() {
        return Collections.unmodifiableSet(usedUniforms);
    }

    /**
     * Declare a uniform and track it
     */
    public void declareUniform(String type, String name) {
        usedUniforms.add(type + " " + name);
    }
}
