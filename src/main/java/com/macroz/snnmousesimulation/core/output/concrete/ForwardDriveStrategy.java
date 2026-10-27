package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentEffects;


/**
 * Maps every spike in the assigned population to forward movement.
 */
public final class ForwardDriveStrategy implements OutputStrategy {

    private final double speedPerSecond;

    private ForwardDriveStrategy(double speedPerSecond) {
        this.speedPerSecond = ParamChecks.nonNegative("speedPerSecond", speedPerSecond);
    }

    public static ForwardDriveStrategy create(ConfigParams params) {
        return new ForwardDriveStrategy(params.getDouble("speedPerSecond", 0.5));
    }

    @Override
    public void apply(AgentEffects effects, boolean[] firedLocalIndices, double deltaTimeMs) {
        int spikeCount = 0;
        for (boolean fired : firedLocalIndices) {
            if (fired) {
                spikeCount++;
            }
        }
        if (spikeCount > 0) {
            double forwardSpeed = speedPerSecond * spikeCount / firedLocalIndices.length;
            double deltaTimeSeconds = deltaTimeMs / 1000.0;
            effects.move(forwardSpeed * deltaTimeSeconds, 0.0);
        }
    }
}
