package cn.minerealms.assimploader.render;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShaderParameterDefinitionTest {
    private static ShaderParameterDefinition read(String json) {
        return ShaderParameterDefinition.read(new StringReader(json));
    }
    private static ShaderParameterDefinition uniform(String spec) {
        return read("{\"version\":1,\"enabled\":true,\"uniforms\":{\"u_gain\":" + spec + "}}");
    }

    @Test void supportsEachDeclaredTypeAndDefensiveCopiesWithoutMinecraftOrGl() {
        var definition = read("""
                {"version":1,"enabled":true,"uniforms":{
                  "u_i":{"type":"int","default":[2],"disabled":[-1]},
                  "u_f":{"type":"float","default":0.25,"disabled":0.1},
                  "u_v2":{"type":"vec2","default":[1,2]},
                  "u_v3":{"type":"vec3","default":[1,2,3]},
                  "u_v4":{"type":"vec4","default":[1,2,3,4]}}}
                """);
        assertTrue(definition.enabled());
        assertEquals(5, definition.uniforms().size());
        assertEquals(ShaderParameterDefinition.Type.INT, definition.uniforms().get("u_i").type());
        assertArrayEquals(new float[]{-1}, definition.uniforms().get("u_i").disabled());
        assertArrayEquals(new float[]{0,0}, definition.uniforms().get("u_v2").disabled());
        var values = definition.uniforms().get("u_v3").defaults(); values[0] = 999;
        assertArrayEquals(new float[]{1,2,3}, definition.uniforms().get("u_v3").defaults());
        assertThrows(UnsupportedOperationException.class, () -> definition.uniforms().clear());
        assertFalse(read("{\"version\":1,\"uniforms\":{}}").enabled());
    }

    @Test void enforcesExactVersionBooleanAndSchemaFieldTypes() {
        for (String json : List.of("null", "[]", "{}",
                "{\"version\":1.1,\"uniforms\":{}}",
                "{\"version\":1.0,\"uniforms\":{}}",
                "{\"version\":\"1\",\"uniforms\":{}}",
                "{\"version\":true,\"uniforms\":{}}",
                "{\"version\":1,\"enabled\":1,\"uniforms\":{}}",
                "{\"version\":1,\"enabled\":\"false\",\"uniforms\":{}}",
                "{\"version\":1,\"enabled\":null,\"uniforms\":{}}",
                "{\"version\":1,\"uniforms\":[],\"unknown\":true}"))
            assertThrows(IllegalArgumentException.class, () -> read(json), json);
        for (String spec : List.of("null", "[]", "{}",
                "{\"type\":\"float\",\"default\":[1],\"weight\":0.5}",
                "{\"type\":1,\"default\":[1]}",
                "{\"type\":\"mat4\",\"default\":[1]}",
                "{\"type\":\"float\",\"default\":[\"1\"]}",
                "{\"type\":\"float\",\"default\":[true]}",
                "{\"type\":\"float\",\"default\":[null]}",
                "{\"type\":\"vec2\",\"default\":1}",
                "{\"type\":\"vec2\",\"default\":[1,2,3]}",
                "{\"type\":\"vec3\",\"default\":[1,2,3],\"disabled\":[0,0]}"))
            assertThrows(IllegalArgumentException.class, () -> uniform(spec), spec);
    }

    @Test void declaredRangesApplyToDefaultsAndCommandsWhileDisabledValuesMayBeSentinels() {
        var parameter = uniform("""
                {"type":"vec3","default":[1,2,3],"disabled":[-1,0,0],
                 "min":[1,1,1],"max":4}
                """).uniforms().get("u_gain");
        assertArrayEquals(new float[]{4,1,2}, parameter.validate(new double[]{4,1,2}));
        assertArrayEquals(new float[]{-1,0,0}, parameter.disabled());
        assertThrows(IllegalArgumentException.class, () -> parameter.validate(new double[]{0,2,3}));
        assertThrows(IllegalArgumentException.class, () -> parameter.validate(new double[]{1,2,4.00001}));
        assertThrows(IllegalArgumentException.class, () -> parameter.validate(new double[]{1,2}));
        assertThrows(IllegalArgumentException.class, () -> parameter.validate(null));
        var min = parameter.minimum(); min[0] = -999;
        assertEquals(1, parameter.minimum()[0]);
        for (String spec : List.of(
                "{\"type\":\"float\",\"default\":[0],\"min\":1}",
                "{\"type\":\"float\",\"default\":[1],\"min\":2,\"max\":1}",
                "{\"type\":\"vec2\",\"default\":[1,2],\"max\":[3]}",
                "{\"type\":\"float\",\"default\":[1],\"max\":\"2\"}"))
            assertThrows(IllegalArgumentException.class, () -> uniform(spec));
    }

    @Test void rejectsFractionalIntegersBeforeAnyDoubleOrFloatRounding() {
        for (String value : List.of("1.00000001", "999999.999", "1.000000000000000000000000001")) {
            assertThrows(IllegalArgumentException.class, () -> uniform("{\"type\":\"int\",\"default\":[" + value + "]}"));
            assertThrows(IllegalArgumentException.class, () -> uniform("{\"type\":\"int\",\"default\":[0],\"disabled\":[" + value + "]}"));
        }
        var integer = uniform("{\"type\":\"int\",\"default\":[1]}").uniforms().get("u_gain");
        assertArrayEquals(new float[]{1_000_000}, integer.validate(new double[]{1_000_000}));
        assertThrows(IllegalArgumentException.class, () -> integer.validate(new double[]{1.00000001}));
        assertThrows(IllegalArgumentException.class, () -> integer.validate(new double[]{-1.00000001}));
    }

    @Test void globalBoundsAndNonfiniteCommandsAreAlwaysRejected() {
        var scalar = uniform("{\"type\":\"float\",\"default\":[0]}").uniforms().get("u_gain");
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1_000_001, -1_000_001})
            assertThrows(IllegalArgumentException.class, () -> scalar.validate(new double[]{value}));
        for (String spec : List.of(
                "{\"type\":\"float\",\"default\":[1e1000]}",
                "{\"type\":\"float\",\"default\":[0],\"disabled\":[1000001]}",
                "{\"type\":\"float\",\"default\":[0],\"min\":-1000001}"))
            assertThrows(IllegalArgumentException.class, () -> uniform(spec));
    }

    @Test void checksUniformNamesAndDeclarationCount() {
        assertThrows(IllegalArgumentException.class, () -> read("{\"version\":1,\"uniforms\":{\"bad name\":{}}}"));
        var entries = new java.util.ArrayList<String>();
        for (int index = 0; index < 65; index++) entries.add("\"u_" + index + "\":{\"type\":\"int\",\"default\":[0]}");
        assertThrows(IllegalArgumentException.class, () -> read("{\"version\":1,\"uniforms\":{" + String.join(",", entries) + "}}"));
    }
}
