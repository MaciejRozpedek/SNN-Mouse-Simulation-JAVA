package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.world.Agent;

import java.util.Map;

/** Maps every spike in the assigned population to a negative rotation. */
public final class TurnRightStrategy extends PopulationActionStrategy {

    private TurnRightStrategy(double radiansPerSpike) {
        super(radiansPerSpike);
    }

    public static TurnRightStrategy create(Map<String, Object> params) {
        return new TurnRightStrategy(readNonNegativeFinite(params, "radians_per_spike", 0.03));
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTime) {
        applyIfNonZero(agent, 0.0, -amountFor(firedLocalIndices));
    }
}
