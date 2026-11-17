package cn.minerealms.assimploader.render;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class ScriptShaderParametersTest {
    private static ShaderParameterDefinition definition(String name, String type, String defaults) {
        return ShaderParameterDefinition.read(new StringReader("{\"version\":1,\"enabled\":true,\"uniforms\":{\""
                + name + "\":{\"type\":\"" + type + "\",\"default\":[" + defaults + "],\"disabled\":[-1]}}}"));
    }

    @Test void conflictingPassAndFailedReplacementPreservePreviouslyPublishedDefinitions() {
        var registry = new ScriptShaderParameters.Registry();
        registry.define("pack:a", definition("u_first", "float", "1"));
        registry.define("pack:b", definition("u_second", "float", "2"));
        var original = registry.passes.get("pack:b");
        registry.set("pack:b", "u_second", new double[]{3});
        assertThrows(IllegalArgumentException.class, () -> registry.define("pack:c", definition("u_first", "float", "5")));
        assertThrows(IllegalArgumentException.class, () -> registry.define("pack:b", definition("u_first", "float", "5")));
        assertSame(original, registry.passes.get("pack:b"));
        assertEquals(3, original.parameters.get("u_second").value[0]);
        assertEquals(2, registry.passes.size()); assertTrue(registry.retired.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.define("bad id", definition("u_third", "float", "5")));
    }

    @Test void integerCommandsAndRangesValidateBeforeMutatingRuntimeValues() {
        var registry = new ScriptShaderParameters.Registry();
        registry.define("pack:a", definition("u_first", "int", "1"));
        var parameter = registry.passes.get("pack:a").parameters.get("u_first");
        assertThrows(IllegalArgumentException.class, () -> registry.set("pack:a", "u_first", new double[]{1.00000001}));
        assertThrows(IllegalArgumentException.class, () -> registry.set("pack:a", "u_first", new double[]{Double.NaN}));
        assertEquals(1, parameter.value[0]);
        registry.set("pack:a", "u_first", new double[]{2});
        assertEquals(2, parameter.value[0]);
        registry.defaults(); assertEquals(1, parameter.value[0]);
    }

    @Test void replacementAndClearRetainDisabledWritesUntilAUniformIsOwnedAgain() {
        var registry = new ScriptShaderParameters.Registry();
        registry.define("pack:a", definition("u_old", "float", "2"));
        registry.define("pack:a", definition("u_new", "float", "3"));
        assertArrayEquals(new float[]{-1}, registry.retired.get("u_old").disabled);
        registry.reset();
        assertTrue(registry.passes.isEmpty());
        assertEquals(2, registry.retired.size());
        assertArrayEquals(new float[]{-1}, registry.retired.get("u_new").disabled);
        registry.define("pack:other", definition("u_old", "float", "4"));
        assertFalse(registry.retired.containsKey("u_old"));
        assertEquals(4, registry.passes.get("pack:other").parameters.get("u_old").value[0]);
    }

    @Test void lifetimeBudgetKeepsReloadCleanupPossibleWithoutDiscardingDisabledWrites() {
        var registry = new ScriptShaderParameters.Registry();
        for (int index=0;index<2048;index++)
            registry.define("pack:a", definition("u_"+index, "float", "1"));
        var original=registry.passes.get("pack:a");
        assertThrows(IllegalArgumentException.class,
                ()->registry.define("pack:b", definition("u_overflow", "float", "1")));
        assertSame(original,registry.passes.get("pack:a"));
        registry.reset();
        assertEquals(2048,registry.retired.size());
        assertTrue(registry.passes.isEmpty());
        assertDoesNotThrow(()->registry.define("pack:b",definition("u_0","float","2")));
        assertDoesNotThrow(registry::reset);
    }
}
