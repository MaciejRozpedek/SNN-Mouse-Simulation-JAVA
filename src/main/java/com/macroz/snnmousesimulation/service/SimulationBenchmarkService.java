package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.api.BenchmarkResult;
import com.macroz.snnmousesimulation.loader.SnnConfigProvider;
import com.macroz.snnmousesimulation.world.World;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

@Service
public class SimulationBenchmarkService {

    private static final double WORLD_WIDTH = 1_000.0;
    private static final double WORLD_HEIGHT = 800.0;
    private static final int FOOD_COUNT = 100;
    private static final long MAX_TOTAL_STEPS = 10_000_000L;

    private final SnnConfigProvider configProvider;

    public SimulationBenchmarkService(SnnConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public synchronized BenchmarkResult run(
            double durationMs,
            double stepMs,
            double burnInMs,
            int repeats,
            long baseSeed
    ) {
        return run(durationMs, stepMs, burnInMs, repeats, baseSeed, true);
    }

    public synchronized BenchmarkResult run(
            double durationMs,
            double stepMs,
            double burnInMs,
            int repeats,
            long baseSeed,
            boolean learningEnabled
    ) {
        validate(durationMs, stepMs, burnInMs, repeats, baseSeed);

        long estimatedStepsPerRun = estimateSteps(durationMs, stepMs);
        if (estimatedStepsPerRun > MAX_TOTAL_STEPS / repeats) {
            throw new IllegalArgumentException("Benchmark exceeds the limit of " + MAX_TOTAL_STEPS + " total steps.");
        }

        long benchmarkStarted = System.nanoTime();
        List<BenchmarkResult.Run> runs = new ArrayList<>(repeats);
        for (int index = 0; index < repeats; index++) {
            long seed = Math.addExact(baseSeed, index);
            runs.add(runOnce(index + 1, seed, durationMs, stepMs, burnInMs, estimatedStepsPerRun, learningEnabled));
        }
        long benchmarkElapsedNs = System.nanoTime() - benchmarkStarted;

        double totalSimulatedMs = durationMs * repeats;
        BenchmarkResult.Summary summary = new BenchmarkResult.Summary(
                nanosToMillisRounded(benchmarkElapsedNs),
                ratio(totalSimulatedMs, benchmarkElapsedNs),
                mean(runs, run -> run.evaluationRewards()),
                sampleStandardDeviation(runs, run -> run.evaluationRewards()),
                mean(runs, run -> run.rewardTrend()),
                mean(runs, BenchmarkResult.Run::finalFiringRateHz),
                mean(runs, BenchmarkResult.Run::averageWeightDelta),
                mean(runs, BenchmarkResult.Run::pathLength)
        );

        return new BenchmarkResult(
                new BenchmarkResult.Parameters(durationMs, stepMs, burnInMs, repeats, baseSeed, learningEnabled),
                summary,
                List.copyOf(runs)
        );
    }

    private BenchmarkResult.Run runOnce(
            int index,
            long seed,
            double durationMs,
            double stepMs,
            double burnInMs,
            long maxSteps,
            boolean learningEnabled
    ) {
        var network = configProvider.loadConfig(seed);
        var world = new World(WORLD_WIDTH, WORLD_HEIGHT, FOOD_COUNT, network, mixSeed(seed));
        world.getAgent().getEngine().setLearningEnabled(learningEnabled);
        double initialWeight = world.getAgent().getSnnDiagnostics().averageWeight();
        double pathLength = 0.0;

        long started = System.nanoTime();
        long steps = 0;
        while (world.getSimulationTimeMs() < durationMs) {
            if (steps++ >= maxSteps) {
                throw new IllegalStateException("Benchmark exceeded its calculated step limit.");
            }
            double previousTime = world.getSimulationTimeMs();
            double x = world.getAgent().getX();
            double y = world.getAgent().getY();
            double remaining = durationMs - previousTime;
            world.update(Math.min(stepMs, remaining));
            double currentTime = world.getSimulationTimeMs();
            double postBurnInDuration = currentTime - Math.max(previousTime, burnInMs);
            if (postBurnInDuration > 0.0) {
                double stepDuration = currentTime - previousTime;
                double fraction = postBurnInDuration / stepDuration;
                pathLength += fraction * Math.hypot(world.getAgent().getX() - x, world.getAgent().getY() - y);
            }
        }
        long elapsedNs = System.nanoTime() - started;

        var diagnostics = world.getAgent().getSnnDiagnostics();
        List<Double> evaluationRewardTimes = world.getFoodEatenTimesMs().stream()
                .filter(time -> time >= burnInMs)
                .toList();
        double splitTime = burnInMs + (durationMs - burnInMs) / 2.0;
        int firstHalfRewards = (int) evaluationRewardTimes.stream().filter(time -> time <= splitTime).count();
        int secondHalfRewards = evaluationRewardTimes.size() - firstHalfRewards;

        return new BenchmarkResult.Run(
                index,
                seed,
                nanosToMillisRounded(elapsedNs),
                ratio(durationMs, elapsedNs),
                world.getFoodEatenCount(),
                evaluationRewardTimes.size(),
                firstHalfRewards,
                secondHalfRewards,
                secondHalfRewards - firstHalfRewards,
                pathLength,
                diagnostics.meanFiringRateHz(),
                initialWeight,
                diagnostics.averageWeight(),
                diagnostics.averageWeight() - initialWeight
        );
    }

    private void validate(double durationMs, double stepMs, double burnInMs, int repeats, long baseSeed) {
        if (!Double.isFinite(durationMs) || durationMs <= 0 || durationMs > 3_600_000) {
            throw new IllegalArgumentException("durationMs must be in (0, 3600000].");
        }
        if (!Double.isFinite(stepMs) || stepMs <= 0 || stepMs > 1.0) {
            throw new IllegalArgumentException("stepMs must be in (0, 1].");
        }
        if (!Double.isFinite(burnInMs) || burnInMs < 0 || burnInMs >= durationMs) {
            throw new IllegalArgumentException("burnInMs must be in [0, durationMs).");
        }
        if (repeats < 1 || repeats > 100) {
            throw new IllegalArgumentException("repeats must be in [1, 100].");
        }
        try {
            Math.addExact(baseSeed, repeats - 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("baseSeed is too close to Long.MAX_VALUE for the requested repeats.", exception);
        }
    }

    private long estimateSteps(double durationMs, double stepMs) {
        double estimate = Math.ceil(durationMs / stepMs);
        if (!Double.isFinite(estimate) || estimate > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Benchmark step count is too large.");
        }
        return (long) estimate;
    }

    private double mean(List<BenchmarkResult.Run> runs, ToDoubleFunction<BenchmarkResult.Run> value) {
        return runs.stream().mapToDouble(value).average().orElse(0.0);
    }

    private double sampleStandardDeviation(
            List<BenchmarkResult.Run> runs,
            ToDoubleFunction<BenchmarkResult.Run> value
    ) {
        if (runs.size() < 2) {
            return 0.0;
        }
        double mean = mean(runs, value);
        double squaredError = runs.stream()
                .mapToDouble(run -> {
                    double error = value.applyAsDouble(run) - mean;
                    return error * error;
                })
                .sum();
        return Math.sqrt(squaredError / (runs.size() - 1));
    }

    private long nanosToMillisRounded(long nanoseconds) {
        return Math.round(nanoseconds / 1_000_000.0);
    }

    private double ratio(double simulatedMs, long elapsedNs) {
        if (elapsedNs <= 0) {
            return 0.0;
        }
        return simulatedMs / (elapsedNs / 1_000_000.0);
    }

    private long mixSeed(long seed) {
        long value = seed + 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
