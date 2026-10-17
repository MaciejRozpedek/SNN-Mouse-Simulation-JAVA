package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.utility.ConfigValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkTopologyLoaderParamsTest {

    // Smallest network the loader accepts. The loader does not resolve input types,
    // so the input does not need a real strategy.
    private static final String NETWORK = """
            neuron_types:
              RS: {a: 0.02, b: 0.2, c: -65.0, d: 8.0, v0: -70.0, u0: -14.0}
            groups:
              - {name: G, neurons: [{type: RS, count: 1}]}
            connections: []
            inputs:
              - name: In
                input_type: ANY
                target_group: G
                target_type: RS
                params:
            """;

    private static String withParams(String params) {
        return NETWORK + params.indent(6);
    }

    private static Map<String, ConfigValue> loadParams(String yaml) {
        var data = new NetworkTopologyLoader().load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        return data.inputConfigs().getFirst().params();
    }

    // The value is kept as written and its position points to it in the YAML text
    private static void assertParam(String yaml, ConfigValue value, String raw, boolean quoted) {
        assertEquals(raw, value.raw());
        assertEquals(quoted, value.quoted());
        String written = quoted ? "\"" + raw + "\"" : raw;
        String textAtPosition = yaml.lines().toList().get(value.line() - 1).substring(value.column() - 1);
        assertTrue(textAtPosition.startsWith(written), textAtPosition);
    }

    @Test
    void keepsRawValueQuotingAndPosition() {
        String yaml = withParams("""
                rate: 7
                seed: "123"
                """);
        Map<String, ConfigValue> params = loadParams(yaml);

        assertParam(yaml, params.get("rate"), "7", false);
        assertParam(yaml, params.get("seed"), "123", true);
    }

    @Test
    void rejectsListAndDuplicateParams() {
        var list = assertThrows(SnnParseException.class, () -> loadParams(withParams("values: [1, 2]")));
        assertTrue(list.getMessage().contains("'values'"), list.getMessage());

        var duplicate = assertThrows(SnnParseException.class, () -> loadParams(withParams("""
                seed: 1
                seed: 2
                """)));
        assertTrue(duplicate.getMessage().contains("'seed'"), duplicate.getMessage());
    }
}
