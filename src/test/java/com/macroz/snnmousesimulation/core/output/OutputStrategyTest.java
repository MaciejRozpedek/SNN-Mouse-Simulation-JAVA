package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.world.Agent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutputStrategyTest {

    @Test
    void forwardDriveAddsSpeedForEverySpike() {
        Agent agent = mock(Agent.class);

        OutputStrategyFactory.create("forward_drive", Map.of("speed_per_spike", 0.4))
            .apply(agent, new boolean[]{true, false, true}, 100.0);

        verify(agent).move(0.8, 0.0);
    }

    @Test
    void leftTurnAddsPositiveRotationForEverySpike() {
        Agent agent = mock(Agent.class);

        OutputStrategyFactory.create("TURN_LEFT", Map.of("radians_per_spike", 0.25))
            .apply(agent, new boolean[]{false, true, true}, 100.0);

        verify(agent).move(0.0, 0.5);
    }

    @Test
    void rightTurnAddsNegativeRotationForEverySpike() {
        Agent agent = mock(Agent.class);

        OutputStrategyFactory.create("turn_right", Map.of("radians_per_spike", 0.25))
            .apply(agent, new boolean[]{true, false, true}, 100.0);

        verify(agent).move(0.0, -0.5);
    }

    @Test
    void noSpikesDoNotChangeAgent() {
        Agent agent = mock(Agent.class);

        OutputStrategyFactory.create("FORWARD_DRIVE", Map.of("speed_per_spike", 1.0))
            .apply(agent, new boolean[]{false, false}, 100.0);

        verifyNoInteractions(agent);
    }
}
