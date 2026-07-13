package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.core.output.concrete.ForwardDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnLeftStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnRightStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputStrategyFactoryTest {

    @Test
    void createsAllPopulationStrategiesCaseInsensitively() {
        assertInstanceOf(ForwardDriveStrategy.class, OutputStrategyFactory.create("FoRwArD_DrIvE", Map.of()));
        assertInstanceOf(TurnLeftStrategy.class, OutputStrategyFactory.create("turn_left", Map.of()));
        assertInstanceOf(TurnRightStrategy.class, OutputStrategyFactory.create("TURN_RIGHT", Map.of()));
    }

    @Test
    void rejectsNegativeAndNonFiniteParameters() {
        assertThrows(AgentConfigurationException.class,
            () -> OutputStrategyFactory.create("FORWARD_DRIVE", Map.of("speed_per_spike", -0.1)));
        assertThrows(AgentConfigurationException.class,
            () -> OutputStrategyFactory.create("TURN_LEFT", Map.of("radians_per_spike", Double.NaN)));
        assertThrows(AgentConfigurationException.class,
            () -> OutputStrategyFactory.create("TURN_RIGHT", Map.of("radians_per_spike", Double.POSITIVE_INFINITY)));
    }
}
