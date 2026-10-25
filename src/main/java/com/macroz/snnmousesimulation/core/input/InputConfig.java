package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.Map;

public record InputConfig(
        String name,
        String sensorType,
        int[] targetNeuronIndices,
        Map<String, ConfigValue> params
) {}