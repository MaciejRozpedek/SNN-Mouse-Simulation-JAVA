package com.macroz.snnmousesimulation.registry;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import com.macroz.snnmousesimulation.utility.ConfigValue;
import com.macroz.snnmousesimulation.world.AgentEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps event names used in the config (e.g. FOOD_EATEN) to event classes.
 */
public final class EventRegistry {

    private final Map<String, Class<? extends AgentEvent>> types = new HashMap<>();

    public void register(String name, Class<? extends AgentEvent> type) {
        types.put(name.toUpperCase(), type);
    }

    public Class<? extends AgentEvent> resolve(ConfigValue name) {
        var type = types.get(name.raw().toUpperCase());
        if (type == null) {
            String message = "Unknown event '" + name.raw() + "'. Available: " + types.keySet();
            throw name.hasPosition()
                    ? new SnnParseException(message, name.line(), name.column())
                    : new SnnParseException(message);
        }
        return type;
    }
}
