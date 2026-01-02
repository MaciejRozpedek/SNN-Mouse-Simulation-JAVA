package com.macroz.snnmousesimulation.core;

import java.util.List;

public record SnnNetworkData(
    List<IzhikevichParams> neuronParamTypes,
    int[] neuronToTypeId,      // Map: neuronIndex -> paramTypeIndex
    double[] initialV,         // Initial membrane potential
    double[] initialU,         // Initial recovery variable
    int[][] synapticTargets,   // Adjacency list: source -> [targets]
    double[][] synapticWeights // Weights matching the targets
) {}