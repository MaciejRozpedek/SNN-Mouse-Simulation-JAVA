package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParameterReader;

import java.util.Map;
import java.util.Random;

/**
 * Injects a constant background current with independent Gaussian noise.
 */
public final class TonicNoiseStrategy implements InputStrategy {

    private final double baseCurrent;
    private final double noiseStd;
    private final boolean allowNegative;
    private final Random random;

    public TonicNoiseStrategy(double baseCurrent, double noiseStd, boolean allowNegative, Long seed) {
        if (!Double.isFinite(baseCurrent)) {
            throw new IllegalArgumentException("Base current must be finite.");
        }
        if (!Double.isFinite(noiseStd) || noiseStd < 0) {
            throw new IllegalArgumentException("Noise standard deviation must be finite and non-negative.");
        }

        this.baseCurrent = baseCurrent;
        this.noiseStd = noiseStd;
        this.allowNegative = allowNegative;
        this.random = seed == null ? new Random() : new Random(seed);
    }

    public static TonicNoiseStrategy create(Map<String, Object> params) {
        return new TonicNoiseStrategy(
                ConfigParameterReader.getDouble(params, "base_current", 0.0),
                ConfigParameterReader.getDouble(params, "noise_std", 0.0),
                ConfigParameterReader.getBoolean(params, "allow_negative", false),
                ConfigParameterReader.getOptionalLong(params, "seed"));
    }

    @Override
    public double[] calculateCurrents(InputFrame frame, int targetNeuronCount) {
        if (targetNeuronCount <= 0) {
            return new double[0];
        }

        double[] currents = new double[targetNeuronCount];
        for (int i = 0; i < targetNeuronCount; i++) {
            double current = baseCurrent + noiseStd * random.nextGaussian();
            currents[i] = allowNegative ? current : Math.max(0.0, current);
        }
        return currents;
    }
}
