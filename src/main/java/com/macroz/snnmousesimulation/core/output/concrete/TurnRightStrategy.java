package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.world.Agent;


/** Maps every spike in the assigned population to a negative rotation. */
public final class TurnRightStrategy implements OutputStrategy {

    private final double rotationDegreesPerSecond;

    private TurnRightStrategy(double rotationDegreesPerSecond) {
        this.rotationDegreesPerSecond = rotationDegreesPerSecond;
    }

    public static TurnRightStrategy create(ConfigParams params) {
        return new TurnRightStrategy(params.getNonNegativeDouble("turn_rate", 0.03));
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTimeMs) {
        double spikeCount = 0;
        for (boolean fired : firedLocalIndices) {
            if (fired) {
                spikeCount++;
            }
        }
        if (spikeCount > 0) {
            double deltaTimeSeconds = deltaTimeMs / 1000.0;
            double rotationRateDegrees = -spikeCount * rotationDegreesPerSecond / firedLocalIndices.length;
            agent.move(0.0, Math.toRadians(rotationRateDegrees * deltaTimeSeconds));
        }
    }
}
