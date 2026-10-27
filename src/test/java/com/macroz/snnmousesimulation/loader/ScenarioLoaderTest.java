package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.world.ScenarioData;
import com.macroz.snnmousesimulation.world.WorldParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioLoaderTest {

    // Smallest brain the network loader accepts
    private static final String BRAIN = """
            plasticity:
              stdp: {tau_plus_ms: 20, tau_minus_ms: 20, a_plus: 0.1, a_minus: 0.15, tau_eligibility_ms: 476, max_weight: 50}
              dopamine: {tau_ms: 50, tau_baseline_ms: 7000, initial_baseline: 0}
            neuron_types:
              RS: {a: 0.02, b: 0.2, c: -65.0, d: 8.0, v0: -70.0, u0: -14.0}
            groups:
              - {name: G, neurons: [{type: RS, count: 1}]}
            connections: []
            """;

    private static final String SCENARIO = """
            world: {width: 300, height: 200}
            rules:
              - {name: Food, rule_type: FOOD, params: {count: 5}}
            agents:
              - {name: Mouse, brain: brain.yaml, start: {x: 10, y: 20}}
            """;

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static ScenarioData load(Map<String, String> files) {
        return new ScenarioLoader().load(stream(SCENARIO), name -> files.containsKey(name) ? stream(files.get(name)) : null);
    }

    @Test
    void loadsWorldRulesAndAgentWithItsBrain() {
        ScenarioData scenario = load(Map.of("brain.yaml", BRAIN));

        assertEquals(new WorldParams(300, 200), scenario.world());
        assertEquals("FOOD", scenario.rules().getFirst().ruleType());
        ScenarioData.AgentData agent = scenario.agents().getFirst();
        assertEquals(List.of(10.0, 20.0, 0.0), List.of(agent.startX(), agent.startY(), agent.startAngle()));
        assertEquals(1, agent.brain().neuronToTypeId().length);
    }

    @Test
    void reportsMissingBrainFile() {
        var e = assertThrows(SnnParseException.class, () -> load(Map.of()));
        assertTrue(e.getMessage().contains("'brain.yaml'"), e.getMessage());
    }
}
