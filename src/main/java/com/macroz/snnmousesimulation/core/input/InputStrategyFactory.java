package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.core.input.concrete.GaussianVisionStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class InputStrategyFactory {

    private static final Map<String, Function<Map<String, Object>, InputStrategy>> REGISTRY = new HashMap<>();

    static {
        register("GAUSSIAN_VISION", GaussianVisionStrategy::create);
    }

    public static void register(String type, Function<Map<String, Object>, InputStrategy> factory) {
        REGISTRY.put(type.toUpperCase(), factory);
    }

    public static InputStrategy create(String inputType, Map<String, Object> params) {
        var factory = REGISTRY.get(inputType.toUpperCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown input type: " + inputType + ". Available: " + REGISTRY.keySet());
        }
        try {
            return factory.apply(params);
        } catch (Exception e) {
            throw new AgentConfigurationException(StrategyType.INPUT, inputType, params, e);
        }
    }
}
