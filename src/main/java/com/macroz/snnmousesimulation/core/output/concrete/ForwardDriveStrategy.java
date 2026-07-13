package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.world.Agent;

import java.util.Map;

/** Maps every spike in the assigned population to forward movement. */
public final class ForwardDriveStrategy extends PopulationActionStrategy {

    private ForwardDriveStrategy(double speedPerSpike) {
        super(speedPerSpike);
    }

    public static ForwardDriveStrategy create(Map<String, Object> params) {
        return new ForwardDriveStrategy(readNonNegativeFinite(params, "speed_per_spike", 0.5));
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTime) {
        applyIfNonZero(agent, amountFor(firedLocalIndices), 0.0);
    }
}
