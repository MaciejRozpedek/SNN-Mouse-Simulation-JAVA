package com.macroz.snnmousesimulation.core.input.concrete;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TonicNoiseStrategyTest {

    @Test
    void returnsConstantCurrentWhenNoiseIsDisabled() {
        var strategy = new TonicNoiseStrategy(3.5, 0.0);

        double[] currents = strategy.calculateCurrents(null, null, 1.0, 3);

        assertArrayEquals(new double[]{3.5, 3.5, 3.5}, currents);
    }

    @Test
    void optionalSeedMakesNoiseReproducible() {
        var params = Map.<String, Object>of("base_current", 2.0, "noise_std", 0.5, "seed", 42);
        var first = TonicNoiseStrategy.create(params);
        var second = TonicNoiseStrategy.create(params);

        assertArrayEquals(
                first.calculateCurrents(null, null, 1.0, 8),
                second.calculateCurrents(null, null, 1.0, 8)
        );
    }

    @Test
    void returnsEmptyArrayForEmptyPopulation() {
        var strategy = new TonicNoiseStrategy(1.0, 1.0);

        assertEquals(0, strategy.calculateCurrents(null, null, 1.0, 0).length);
    }

    @Test
    void rejectsNegativeParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TonicNoiseStrategy(-1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new TonicNoiseStrategy(1.0, -0.1));
    }
}
