package com.macroz.snnmousesimulation.core.output;

import java.util.Map;

public record OutputConfig(
    String name,
    String outputType,
    int[] sourceNeuronIndices,
    Map<String, Object> params
) {}