package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.world.Agent;

import java.util.Map;

/** Maps every spike in the assigned population to a positive rotation. */
public final class TurnLeftStrategy extends PopulationActionStrategy {

    private TurnLeftStrategy(double radiansPerSpike) {
        super(radiansPerSpike);
    }

    public static TurnLeftStrategy create(Map<String, Object> params) {
        return new TurnLeftStrategy(readNonNegativeFinite(params, "radians_per_spike", 0.03));
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTime) {
        applyIfNonZero(agent, 0.0, amountFor(firedLocalIndices));
    }
}
