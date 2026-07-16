package com.macroz.snnmousesimulation.api;

import java.util.List;

public record SnnDiagnosticState(
        double dopamineLevel,
        double dopamineBaseLevel,
        double meanFiringRateHz,
        int totalSpikesSinceLastSnapshot,
        List<SpikeSample> spikeSamples,
        double averageWeight,
        double minWeight,
        double maxWeight,
        float[] neuronPotentials
) {
    public record SpikeSample(double simulationTimeMs, List<Integer> neuronIndices) {
    }
}
