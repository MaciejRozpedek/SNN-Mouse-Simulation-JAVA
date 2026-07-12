package com.macroz.snnmousesimulation.core;

import com.macroz.snnmousesimulation.api.SnnDiagnosticState;

import java.util.ArrayDeque;
import java.util.List;

public class SnnDiagnosticTracker {
    private static final double WINDOW_DURATION_MS = 1000.0;
    private static final double DETAILED_EXPORT_INTERVAL_MS = 1.0;

    private final SnnEngine engine;
    private final ArrayDeque<SpikeRecord> spikeHistory = new ArrayDeque<>();
    private double simulatedTimeMs = 0.0;
    private double lastDetailedExportTimeMs = -DETAILED_EXPORT_INTERVAL_MS;
    private List<Integer> lastFiredNeuronIndices = List.of();

    private record SpikeRecord(double timestampMs, int spikeCount) {
    }

    private record WeightStats(double average, double min, double max) {
    }

    public SnnDiagnosticTracker(SnnEngine engine) {
        this.engine = engine;
    }

    public void registerStep(double deltaTimeMs, List<Integer> firedNeuronIndices) {
        simulatedTimeMs += deltaTimeMs;
        lastFiredNeuronIndices = List.copyOf(firedNeuronIndices);

        if (!firedNeuronIndices.isEmpty()) {
            spikeHistory.addLast(new SpikeRecord(simulatedTimeMs, firedNeuronIndices.size()));
        }

        double cutoff = simulatedTimeMs - WINDOW_DURATION_MS;
        while (!spikeHistory.isEmpty() && spikeHistory.peekFirst().timestampMs() < cutoff) {
            spikeHistory.removeFirst();
        }
    }

    public SnnDiagnosticState snapshot() {
        boolean includePotentials = simulatedTimeMs - lastDetailedExportTimeMs >= DETAILED_EXPORT_INTERVAL_MS;
        if (includePotentials) {
            lastDetailedExportTimeMs = simulatedTimeMs;
        }

        int totalSpikesInWindow = spikeHistory.stream()
                .mapToInt(SpikeRecord::spikeCount)
                .sum();
        int neuronCount = engine.getTotalNeuronCount();
        double meanFiringRateHz = neuronCount > 0
                ? totalSpikesInWindow / (WINDOW_DURATION_MS / 1_000.0) / neuronCount
                : 0.0;

        WeightStats weightStats = calculateWeightStats();
        return new SnnDiagnosticState(
                engine.getDopamineLevel(),
                engine.getDopamineBaseLevel(),
                meanFiringRateHz,
                lastFiredNeuronIndices.size(),
                lastFiredNeuronIndices,
                weightStats.average(),
                weightStats.min(),
                weightStats.max(),
                includePotentials ? readPotentials(neuronCount) : null
        );
    }

    private WeightStats calculateWeightStats() {
        double[][] weights = engine.getSynapticWeights();
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int count = 0;

        for (double[] neuronWeights : weights) {
            for (double weight : neuronWeights) {
                sum += weight;
                min = Math.min(min, weight);
                max = Math.max(max, weight);
                count++;
            }
        }

        if (count == 0) {
            return new WeightStats(0.0, 0.0, 0.0);
        }
        return new WeightStats(sum / count, min, max);
    }

    private float[] readPotentials(int neuronCount) {
        float[] potentials = new float[neuronCount];
        for (int i = 0; i < neuronCount; i++) {
            potentials[i] = (float) engine.getV(i);
        }
        return potentials;
    }
}
