package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.world.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HungerDriveStrategyTest {

    private final HungerDriveStrategy strategy = new HungerDriveStrategy(1_000.0, 4_000.0, 10.0);

    @Test
    void remainsInactiveUntilDelayPasses() {
        Agent agent = agentAfter(1_000.0);

        assertArrayEquals(
                new double[]{0.0, 0.0},
                strategy.calculateCurrents(agent, null, 1.0, 2)
        );
    }

    @Test
    void rampsLinearlyAfterDelay() {
        Agent agent = agentAfter(3_000.0);

        assertArrayEquals(
                new double[]{5.0, 5.0, 5.0},
                strategy.calculateCurrents(agent, null, 1.0, 3)
        );
    }

    @Test
    void capsCurrentAtConfiguredMaximum() {
        Agent agent = agentAfter(10_000.0);

        assertArrayEquals(
                new double[]{10.0, 10.0},
                strategy.calculateCurrents(agent, null, 1.0, 2)
        );
    }

    private Agent agentAfter(double elapsedMs) {
        Agent agent = new Agent(0.0, 0.0, new SnnNetworkData(
                List.of(),
                new int[0],
                new double[0],
                new double[0],
                new int[0][],
                new double[0][],
                List.of(),
                List.of()
        ));
        agent.update(null, elapsedMs);
        return agent;
    }
}
