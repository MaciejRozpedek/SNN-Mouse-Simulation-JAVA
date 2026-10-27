package com.macroz.snnmousesimulation.exception;

import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.Map;

public class AgentConfigurationException extends RuntimeException {

    public enum StrategyType {
        INPUT("InputStrategy"),
        OUTPUT("OutputStrategy"),
        REACTION("ReactionStrategy"),
        WORLD_RULE("WorldRule");

        private final String displayName;

        StrategyType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public AgentConfigurationException(StrategyType sType, String type, Map<String, ConfigValue> params, Throwable cause) {
        super("Failed to create %s for type '%s' with params %s: %s"
            .formatted(
                sType,
                type,
                params,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()
            ), cause);
    }
}
