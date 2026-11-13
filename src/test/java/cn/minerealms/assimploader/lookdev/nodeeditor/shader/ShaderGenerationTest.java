package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeConnection;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeGraphExecutor;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.OutputNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.TimeNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.TextureSampleNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.ValueNode;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.MathFunctionNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShaderGenerationTest {
    @Test
    void dependenciesPrecedeConsumersRegardlessOfNodeListOrder() {
        ValueNode value = new ValueNode();
        MathFunctionNode function = new MathFunctionNode();
        OutputNode output = new OutputNode();
        NodeConnection first = new NodeConnection(value.getOutputs().get(0), function.getInputs().get(0));
        NodeConnection second = new NodeConnection(function.getOutputs().get(0), output.getInputByName("Roughness"));

        List<Node> order = new NodeGraphExecutor(List.of(output, function, value), List.of(first, second))
                .getExecutionOrder();

        assertEquals(List.of(value, function, output), order);
    }

    @Test
    void fullShaderDeclaresSourceBeforeUsingItInMath() {
        ValueNode value = new ValueNode();
        MathFunctionNode function = new MathFunctionNode();
        OutputNode output = new OutputNode();
        NodeConnection first = new NodeConnection(value.getOutputs().get(0), function.getInputs().get(0));
        NodeConnection second = new NodeConnection(function.getOutputs().get(0), output.getInputByName("Roughness"));
        ShaderCodeGenerator generator = new ShaderCodeGenerator(List.of(output, function, value), List.of(first, second));

        ShaderCodeGenerator.ShaderCode shader = generator.generate();

        assertNotNull(shader);
        String variable = generator.getNodeVariable(value);
        int declaration = shader.fragmentShader.indexOf("float " + variable + " =");
        int use = shader.fragmentShader.indexOf("sin(" + variable + ")");
        assertTrue(declaration >= 0 && use > declaration, shader.fragmentShader);
    }

    @Test
    void fullShaderDeclaresTimeUniformBeforeMain() {
        TimeNode time = new TimeNode();
        OutputNode output = new OutputNode();
        NodeConnection connection = new NodeConnection(time.getOutputs().get(0), output.getInputByName("Roughness"));

        ShaderCodeGenerator.ShaderCode shader = new ShaderCodeGenerator(List.of(time, output), List.of(connection)).generate();

        assertNotNull(shader);
        assertTrue(shader.fragmentShader.contains("uniform float uTime;"), shader.fragmentShader);
        assertTrue(shader.fragmentShader.indexOf("uniform float uTime;") < shader.fragmentShader.indexOf("void main()"));
    }

    @Test
    void fullShaderDeclaresTextureSampler() {
        TextureSampleNode texture = new TextureSampleNode();
        OutputNode output = new OutputNode();
        NodeConnection connection = new NodeConnection(texture.getOutputs().get(2), output.getInputByName("Roughness"));

        ShaderCodeGenerator.ShaderCode shader = new ShaderCodeGenerator(List.of(texture, output), List.of(connection)).generate();

        assertNotNull(shader);
        assertTrue(shader.fragmentShader.contains("uniform sampler2D texture_" + texture.getId().replace('-', '_') + ";"), shader.fragmentShader);
    }

    @Test
    void injectableFragmentDeclaresTextureSampler() {
        TextureSampleNode texture = new TextureSampleNode();
        OutputNode output = new OutputNode();
        NodeConnection connection = new NodeConnection(texture.getOutputs().get(2), output.getInputByName("Roughness"));

        GLSLFragment fragment = new GLSLFragmentGenerator(List.of(texture, output), List.of(connection)).generate(null, null);

        assertNotNull(fragment);
        assertTrue(fragment.getCode().contains("uniform sampler2D texture_" + texture.getId().replace('-', '_') + ";"), fragment.getCode());
    }
}
