package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;

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
        this.baseCurrent = ParamChecks.finite("base_current", baseCurrent);
        this.noiseStd = ParamChecks.nonNegative("noise_std", noiseStd);
        this.allowNegative = allowNegative;
        this.random = seed == null ? new Random() : new Random(seed);
    }

    public static TonicNoiseStrategy create(ConfigParams params) {
        return new TonicNoiseStrategy(
                params.getDouble("base_current", 0.0),
                params.getDouble("noise_std", 0.0),
                params.getBoolean("allow_negative", false),
                params.getOptionalLong("seed").orElse(null));
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
