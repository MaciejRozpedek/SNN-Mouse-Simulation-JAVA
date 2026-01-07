package com.macroz.snnmousesimulation.core;

import com.macroz.snnmousesimulation.core.input.InputConfig;
import com.macroz.snnmousesimulation.core.output.OutputConfig;

import java.util.List;

public record SnnNetworkData(
    List<IzhikevichParams> neuronParamTypes,
    int[] neuronToTypeId,      // Map: neuronIndex -> paramTypeIndex
    double[] initialV,         // Initial membrane potential
    double[] initialU,         // Initial recovery variable
    int[][] synapticTargets,   // Adjacency list: source -> [targets]
    double[][] synapticWeights,// Weights matching the targets

	// Agent data
	List<InputConfig> inputConfigs,
	List<OutputConfig> outputConfigs
) {}