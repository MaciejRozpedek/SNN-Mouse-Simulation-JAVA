package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.world.Agent;

import java.util.Map;

/** Common implementation for strategies mapped to one neuron population. */
abstract class PopulationActionStrategy implements OutputStrategy {

    private final double amountPerSpike;

    protected PopulationActionStrategy(double amountPerSpike) {
        this.amountPerSpike = amountPerSpike;
    }

    protected final double amountFor(boolean[] firedLocalIndices) {
        int spikeCount = 0;
        for (boolean fired : firedLocalIndices) {
            if (fired) {
                spikeCount++;
            }
        }
        return spikeCount * amountPerSpike;
    }

    protected static double readNonNegativeFinite(Map<String, Object> params, String name, double defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed) || parsed < 0.0) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
        return parsed;
    }

    protected static void applyIfNonZero(Agent agent, double speed, double rotation) {
        if (speed != 0.0 || rotation != 0.0) {
            agent.move(speed, rotation);
        }
    }
}
