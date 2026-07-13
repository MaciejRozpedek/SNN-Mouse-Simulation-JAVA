package com.macroz.snnmousesimulation.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnnEngineLearningTest {

    @Test
    void disabledLearningKeepsWeightsFixedWhileNeuronsAndDopamineStillRun() {
        var engine = new SnnEngine(new SnnNetworkData(
                List.of(new IzhikevichParams(0.02, 0.2, -65.0, 8.0, -70.0, -14.0)),
                new int[]{0}, new double[]{31.0}, new double[]{-14.0},
                new int[][]{new int[]{0}}, new double[][]{new double[]{2.0}},
                List.of(), List.of()
        ));
        assertTrue(engine.isLearningEnabled());
        engine.setLearningEnabled(false);
        engine.injectDopamine(5.0);

        double initialWeight = engine.getSynapticWeights()[0][0];
        var fired = engine.step(1.0);

        assertFalse(fired.isEmpty());
        assertFalse(engine.isLearningEnabled());
        assertEquals(initialWeight, engine.getSynapticWeights()[0][0]);
        assertEquals(5.0 * Math.exp(-1.0 / 20.0), engine.getDopamineLevel(), 1e-12);
    }
}
