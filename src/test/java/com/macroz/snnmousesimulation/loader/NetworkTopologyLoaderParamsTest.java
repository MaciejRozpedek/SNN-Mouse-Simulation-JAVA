package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.PlasticityParams;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
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

    // Smallest network the loader accepts. Plasticity values are distinct so that swapped
    // parameters are detected. The loader does not resolve input types, so the input does
    // not need a real strategy.
    private static final String NETWORK = """
            plasticity:
              stdp: {tau_plus_ms: 21, tau_minus_ms: 22, a_plus: 0.1, a_minus: 0.2, tau_eligibility_ms: 400, max_weight: 30}
              dopamine: {tau_ms: 50, tau_baseline_ms: 7000, initial_baseline: -0.5}
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

    private static SnnNetworkData load(String yaml) {
        return new NetworkTopologyLoader().load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, ConfigValue> loadParams(String yaml) {
        return load(yaml).inputConfigs().getFirst().params();
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

    @Test
    void readsRequiredPlasticityParams() {
        PlasticityParams expected = new PlasticityParams(
                new PlasticityParams.Stdp(21.0, 22.0, 0.1, 0.2, 400.0, 30.0),
                new PlasticityParams.Dopamine(50.0, 7000.0, -0.5));
        assertEquals(expected, load(NETWORK).plasticity());

        var missing = assertThrows(SnnParseException.class, () -> load(NETWORK.replace("tau_ms: 50, ", "")));
        assertTrue(missing.getMessage().contains("'tau_ms'"), missing.getMessage());
    }
}
