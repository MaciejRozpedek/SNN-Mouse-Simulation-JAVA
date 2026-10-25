package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.core.input.concrete.GaussianVisionStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.OrnsteinUhlenbeckNoiseStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.TonicNoiseStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class InputStrategyFactory {

    private static final Map<String, Function<ConfigParams, InputStrategy>> REGISTRY = new HashMap<>();

    static {
        register("GAUSSIAN_VISION", GaussianVisionStrategy::create);
        register("TONIC_NOISE", TonicNoiseStrategy::create);
        register("OU_NOISE", OrnsteinUhlenbeckNoiseStrategy::create);
    }

    public static void register(String type, Function<ConfigParams, InputStrategy> factory) {
        REGISTRY.put(type.toUpperCase(), factory);
    }

    public static InputStrategy create(String inputType, Map<String, ConfigValue> params) {
        var factory = REGISTRY.get(inputType.toUpperCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown input type: " + inputType + ". Available: " + REGISTRY.keySet());
        }
        try {
            return new ConfigParams(params).build(factory);
        } catch (Exception e) {
            throw new AgentConfigurationException(StrategyType.INPUT, inputType, params, e);
        }
    }
}
