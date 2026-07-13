package com.macroz.snnmousesimulation.api;

import java.util.List;
import java.util.Objects;

public record BenchmarkResult(
        Parameters parameters,
        Summary summary,
        List<Run> runs
) {
    public BenchmarkResult {
        parameters = Objects.requireNonNull(parameters, "parameters");
        summary = Objects.requireNonNull(summary, "summary");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
    }

    public record Parameters(
            double durationMs,
            double stepMs,
            double burnInMs,
            int repeats,
            long baseSeed,
            boolean learningEnabled
    ) {
        public Parameters(double durationMs, double stepMs, double burnInMs, int repeats, long baseSeed) {
            this(durationMs, stepMs, burnInMs, repeats, baseSeed, true);
        }
    }

    public record Summary(
            long wallTimeMs,
            double simulatedToWallRatio,
            double meanEvaluationRewards,
            double rewardStandardDeviation,
            double meanRewardTrend,
            double meanFinalFiringRateHz,
            double meanWeightDelta,
            double meanPathLength
    ) {
    }

    public record Run(
            int index,
            long seed,
            long wallTimeMs,
            double simulatedToWallRatio,
            int totalRewards,
            int evaluationRewards,
            int firstHalfRewards,
            int secondHalfRewards,
            int rewardTrend,
            double pathLength,
            double finalFiringRateHz,
            double initialAverageWeight,
            double finalAverageWeight,
            double averageWeightDelta
    ) {
    }
}
