package com.macroz.snnmousesimulation.api;

import java.util.List;

public record SnnDiagnosticState(
        double dopamineLevel,
        double dopamineBaseLevel,
        double meanFiringRateHz,
        int totalSpikesInLastStep,
        List<Integer> firedNeuronIndices,
        double averageWeight,
        double minWeight,
        double maxWeight,
        float[] neuronPotentials
) {
}
