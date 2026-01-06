package com.macroz.snnmousesimulation.core.input;

import java.util.Map;

public record InputConfig(
    String name,
    String sensorType,
	int[] targetNeuronIndices,
    Map<String, Object> params
) {}