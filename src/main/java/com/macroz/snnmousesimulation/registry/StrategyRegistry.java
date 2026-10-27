package com.macroz.snnmousesimulation.registry;

import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates strategies of one kind (e.g. inputs) by their configured type name.
 */
public final class StrategyRegistry<T> {

    private final StrategyType kind;
    private final Map<String, Function<ConfigParams, T>> factories = new HashMap<>();

    public StrategyRegistry(StrategyType kind) {
        this.kind = kind;
    }

    public void register(String type, Function<ConfigParams, T> factory) {
        factories.put(type.toUpperCase(), factory);
    }

    public T create(String type, Map<String, ConfigValue> params) {
        var factory = factories.get(type.toUpperCase());
        if (factory == null) {
            throw new IllegalArgumentException("Unknown " + kind + " type: " + type + ". Available: " + factories.keySet());
        }
        try {
            return new ConfigParams(params).build(factory);
        } catch (Exception e) {
            throw new AgentConfigurationException(kind, type, params, e);
        }
    }
}
