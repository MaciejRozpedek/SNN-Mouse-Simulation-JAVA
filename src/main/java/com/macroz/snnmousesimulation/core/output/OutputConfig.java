package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.Map;

public record OutputConfig(
    String name,
    String outputType,
    int[] sourceNeuronIndices,
    Map<String, ConfigValue> params
) {}