package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.world.Agent;
import com.macroz.snnmousesimulation.world.World;

import java.util.Arrays;
import java.util.Map;

/**
 * Gradually increases population drive after the agent has gone without food.
 * The drive starts at zero, ramps linearly after a delay and is reset by a meal.
 */
public final class HungerDriveStrategy implements InputStrategy {

    private final double activationDelayMs;
    private final double rampDurationMs;
    private final double maxCurrent;

    public HungerDriveStrategy(double activationDelayMs, double rampDurationMs, double maxCurrent) {
        if (!Double.isFinite(activationDelayMs) || activationDelayMs < 0) {
            throw new IllegalArgumentException("Activation delay must be finite and non-negative.");
        }
        if (!Double.isFinite(rampDurationMs) || rampDurationMs <= 0) {
            throw new IllegalArgumentException("Ramp duration must be finite and positive.");
        }
        if (!Double.isFinite(maxCurrent) || maxCurrent < 0) {
            throw new IllegalArgumentException("Maximum current must be finite and non-negative.");
        }

        this.activationDelayMs = activationDelayMs;
        this.rampDurationMs = rampDurationMs;
        this.maxCurrent = maxCurrent;
    }

    public static HungerDriveStrategy create(Map<String, Object> params) {
        return new HungerDriveStrategy(
                numberParam(params, "activation_delay_ms", 5_000.0),
                numberParam(params, "ramp_duration_ms", 20_000.0),
                numberParam(params, "max_current", 10.0)
        );
    }

    @Override
    public double[] calculateCurrents(Agent agent, World worldSnapshot, double deltaTime, int targetNeuronCount) {
        if (targetNeuronCount <= 0) {
            return new double[0];
        }

        double hungryForMs = agent.getTimeSinceLastMealMs() - activationDelayMs;
        double current = hungryForMs <= 0
                ? 0.0
                : maxCurrent * Math.min(1.0, hungryForMs / rampDurationMs);

        double[] currents = new double[targetNeuronCount];
        Arrays.fill(currents, current);
        return currents;
    }

    private static double numberParam(Map<String, Object> params, String name, double defaultValue) {
        Object value = params.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be numeric.");
        }
        return number.doubleValue();
    }
}
