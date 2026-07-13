package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.core.input.concrete.HungerDriveStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.TonicNoiseStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class InputStrategyFactoryTest {

    @Test
    void createsTonicNoiseStrategy() {
        InputStrategy strategy = InputStrategyFactory.create(
                "TONIC_NOISE",
                Map.of("base_current", 2.0, "noise_std", 0.5)
        );

        assertInstanceOf(TonicNoiseStrategy.class, strategy);
    }

    @Test
    void createsHungerDriveStrategy() {
        InputStrategy strategy = InputStrategyFactory.create(
                "HUNGER_DRIVE",
                Map.of("activation_delay_ms", 5_000, "ramp_duration_ms", 20_000, "max_current", 10.0)
        );

        assertInstanceOf(HungerDriveStrategy.class, strategy);
    }
}
