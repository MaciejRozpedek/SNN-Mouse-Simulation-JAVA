package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.core.output.concrete.ForwardDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.PopulationDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnLeftStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnRightStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class OutputStrategyFactory {

    private static final Map<String, Function<ConfigParams, OutputStrategy>> REGISTRY = new HashMap<>();

    static {
        register("POPULATION_DRIVE", PopulationDriveStrategy::create);
        register("FORWARD_DRIVE", ForwardDriveStrategy::create);
        register("TURN_LEFT", TurnLeftStrategy::create);
        register("TURN_RIGHT", TurnRightStrategy::create);
    }

    public static void register(String type, Function<ConfigParams, OutputStrategy> factory) {
        REGISTRY.put(type.toUpperCase(), factory);
    }

    public static OutputStrategy create(String outputType, Map<String, ConfigValue> params) {
        var factory = REGISTRY.get(outputType.toUpperCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown output type: " + outputType + ". Available: " + REGISTRY.keySet());
        }
        try {
            return new ConfigParams(params).build(factory);
        } catch (Exception e) {
            throw new AgentConfigurationException(StrategyType.OUTPUT, outputType, params, e);
        }
    }
}