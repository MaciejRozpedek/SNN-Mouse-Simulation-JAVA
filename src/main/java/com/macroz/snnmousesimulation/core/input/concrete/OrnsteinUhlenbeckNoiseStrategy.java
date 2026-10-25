package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;

import java.util.Random;

/**
 * Injects a base current with temporally correlated Ornstein-Uhlenbeck noise,
 * optionally correlated between neighboring neurons.
 */
public final class OrnsteinUhlenbeckNoiseStrategy implements InputStrategy {

    private final double baseCurrent;
    private final double noiseStd;
    private final double tauMs;
    private final double neighborCorrelation;
    private final double independentFactor;
    private final boolean allowNegative;
    private final Random random;

    private double[] deviations;
    private double[] noise;
    private double decay;
    private double noiseScale;

    public OrnsteinUhlenbeckNoiseStrategy(
            double baseCurrent,
            double noiseStd,
            double tauMs,
            double neighborCorrelation,
            boolean allowNegative,
            Long seed) {
        this.baseCurrent = ParamChecks.finite("base_current", baseCurrent);
        this.noiseStd = ParamChecks.nonNegative("noise_std", noiseStd);
        this.tauMs = ParamChecks.positive("tau_ms", tauMs);
        this.neighborCorrelation = ParamChecks.check("neighbor_correlation", neighborCorrelation,
                neighborCorrelation >= 0.0 && neighborCorrelation < 1.0, "must be in range [0, 1)");
        this.independentFactor = Math.sqrt(1.0 - neighborCorrelation * neighborCorrelation);
        this.allowNegative = allowNegative;
        this.random = seed == null ? new Random() : new Random(seed);
    }

    public static OrnsteinUhlenbeckNoiseStrategy create(ConfigParams params) {
        return new OrnsteinUhlenbeckNoiseStrategy(
                params.getDouble("base_current", 0.0),
                params.getDouble("noise_std", 0.0),
                params.getDouble("tau_ms", 20.0),
                params.getDouble("neighbor_correlation", 0.0),
                params.getBoolean("allow_negative", false),
                params.getOptionalLong("seed").orElse(null));
    }

    @Override
    public double[] calculateCurrents(InputFrame frame, int targetNeuronCount) {
        if (targetNeuronCount <= 0) {
            return new double[0];
        }
        if (deviations == null) {
            init(frame.deltaTimeMs(), targetNeuronCount);
        } else if (targetNeuronCount != deviations.length) {
            throw new IllegalArgumentException("Target neuron count must not change.");
        }

        fillCorrelatedNoise();

        double[] currents = new double[targetNeuronCount];
        for (int i = 0; i < targetNeuronCount; i++) {
            deviations[i] = deviations[i] * decay + noiseScale * noise[i];
            double current = baseCurrent + deviations[i];
            currents[i] = allowNegative ? current : Math.max(0.0, current);
        }
        return currents;
    }

    private void init(double dt, int targetNeuronCount) {
        decay = Math.exp(-dt / tauMs);
        noiseScale = noiseStd * Math.sqrt(1.0 - decay * decay);
        noise = new double[targetNeuronCount];
        deviations = new double[targetNeuronCount];

        // Start from the stationary distribution to skip the warm-up phase.
        fillCorrelatedNoise();
        for (int i = 0; i < targetNeuronCount; i++) {
            deviations[i] = noiseStd * noise[i];
        }
    }

    private void fillCorrelatedNoise() {
        double value = random.nextGaussian();
        noise[0] = value;
        for (int i = 1; i < noise.length; i++) {
            value = neighborCorrelation * value + independentFactor * random.nextGaussian();
            noise[i] = value;
        }
    }
}
