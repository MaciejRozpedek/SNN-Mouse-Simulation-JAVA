package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldExperimentMetricsTest {

    @Test
    void seededWorldProducesRepeatableFoodPlacement() {
        World first = new World(1_000, 800, 3, emptyNetwork(), 123L);
        World second = new World(1_000, 800, 3, emptyNetwork(), 123L);

        assertEquals(first.getFood(), second.getFood());
    }

    @Test
    void recordsFoodRewardsAndTheirSimulationTimes() {
        World world = new World(1_000, 800, 0, emptyNetwork(), 123L);
        world.getFood().add(new Food(500.0, 400.0));

        world.update(1.0);

        assertEquals(1, world.getFoodEatenCount());
        assertEquals(List.of(1.0), world.getFoodEatenTimesMs());
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
