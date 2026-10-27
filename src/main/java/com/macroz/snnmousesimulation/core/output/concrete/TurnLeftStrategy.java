package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentEffects;


/** Maps every spike in the assigned population to a positive rotation. */
public final class TurnLeftStrategy implements OutputStrategy {

    private final double rotationDegreesPerSecond;

    private TurnLeftStrategy(double rotationDegreesPerSecond) {
        this.rotationDegreesPerSecond = ParamChecks.nonNegative("turn_rate", rotationDegreesPerSecond);
    }

    public static TurnLeftStrategy create(ConfigParams params) {
        return new TurnLeftStrategy(params.getDouble("turn_rate", 0.03));
    }

    @Override
    public void apply(AgentEffects effects, boolean[] firedLocalIndices, double deltaTimeMs) {
        double spikeCount = 0;
        for (boolean fired : firedLocalIndices) {
            if (fired) {
                spikeCount++;
            }
        }
        if (spikeCount > 0) {
            double deltaTimeSeconds = deltaTimeMs / 1000.0;
            double rotationRateDegrees = spikeCount * rotationDegreesPerSecond / firedLocalIndices.length;
            effects.move(0.0, Math.toRadians(rotationRateDegrees * deltaTimeSeconds));
        }
    }
}
