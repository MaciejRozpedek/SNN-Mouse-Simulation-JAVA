package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.world.Agent;
import com.macroz.snnmousesimulation.world.World;

import java.util.Map;
import java.util.Random;

/**
 * Injects a constant background current with independent Gaussian noise.
 * This can keep a population spontaneously active when sensory inputs are silent.
 */
public final class TonicNoiseStrategy implements InputStrategy {

    private final double baseCurrent;
    private final double noiseStd;
    private final Random random;

    public TonicNoiseStrategy(double baseCurrent, double noiseStd) {
        this(baseCurrent, noiseStd, new Random());
    }

    private TonicNoiseStrategy(double baseCurrent, double noiseStd, Random random) {
        if (!Double.isFinite(baseCurrent) || baseCurrent < 0) {
            throw new IllegalArgumentException("Base current must be finite and non-negative.");
        }
        if (!Double.isFinite(noiseStd) || noiseStd < 0) {
            throw new IllegalArgumentException("Noise standard deviation must be finite and non-negative.");
        }

        this.baseCurrent = baseCurrent;
        this.noiseStd = noiseStd;
        this.random = random;
    }

    public static TonicNoiseStrategy create(Map<String, Object> params) {
        double baseCurrent = numberParam(params, "base_current", 0.0);
        double noiseStd = numberParam(params, "noise_std", 0.0);
        Object seed = params.get("seed");

        if (seed == null) {
            return new TonicNoiseStrategy(baseCurrent, noiseStd);
        }
        if (!(seed instanceof Number number)) {
            throw new IllegalArgumentException("Parameter 'seed' must be numeric.");
        }
        return new TonicNoiseStrategy(baseCurrent, noiseStd, new Random(number.longValue()));
    }

    @Override
    public double[] calculateCurrents(Agent agent, World worldSnapshot, double deltaTime, int targetNeuronCount) {
        if (targetNeuronCount <= 0) {
            return new double[0];
        }

        double[] currents = new double[targetNeuronCount];
        for (int i = 0; i < targetNeuronCount; i++) {
            currents[i] = baseCurrent + noiseStd * random.nextGaussian();
        }
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
