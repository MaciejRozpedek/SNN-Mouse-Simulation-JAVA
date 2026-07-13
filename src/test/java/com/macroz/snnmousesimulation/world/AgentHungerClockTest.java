package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentHungerClockTest {

    @Test
    void tracksTimeWithoutFoodAndResetsWhenRewarded() {
        Agent agent = new Agent(0.0, 0.0, emptyNetwork());

        agent.update(null, 250.0);
        agent.update(null, 750.0);
        assertEquals(1_000.0, agent.getTimeSinceLastMealMs());

        agent.applyReward();
        assertEquals(0.0, agent.getTimeSinceLastMealMs());
    }

    private SnnNetworkData emptyNetwork() {
        return new SnnNetworkData(
                List.of(),
                new int[0],
                new double[0],
                new double[0],
                new int[0][],
                new double[0][],
                List.of(),
                List.of()
        );
    }
}
