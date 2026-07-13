package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.core.output.concrete.ForwardDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.PopulationDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnLeftStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnRightStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class OutputStrategyFactory {

    private static final Map<String, Function<Map<String, Object>, OutputStrategy>> REGISTRY = new HashMap<>();

    static {
        register("POPULATION_DRIVE", PopulationDriveStrategy::create);
        register("FORWARD_DRIVE", ForwardDriveStrategy::create);
        register("TURN_LEFT", TurnLeftStrategy::create);
        register("TURN_RIGHT", TurnRightStrategy::create);
    }

    public static void register(String type, Function<Map<String, Object>, OutputStrategy> factory) {
        REGISTRY.put(type.toUpperCase(Locale.ROOT), factory);
    }

    public static OutputStrategy create(String outputType, Map<String, Object> params) {
        var factory = REGISTRY.get(outputType.toUpperCase(Locale.ROOT));
        if (factory == null) {
            throw new IllegalArgumentException("Unknown output type: " + outputType + ". Available: " + REGISTRY.keySet());
        }
        try {
            return factory.apply(params);
        } catch (Exception e) {
            throw new AgentConfigurationException(StrategyType.OUTPUT, outputType, params, e);
        }
    }
}
