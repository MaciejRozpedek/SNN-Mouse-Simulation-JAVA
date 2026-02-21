package com.macroz.snnmousesimulation.exception;

import java.util.Map;

public class AgentConfigurationException extends RuntimeException {

    public enum StrategyType {
        INPUT("InputStrategy"),
        OUTPUT("OutputStrategy");

        private final String displayName;

        StrategyType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public AgentConfigurationException(StrategyType sType, String type, Map<String, Object> params, Throwable cause) {
        super("Failed to create %sStrategy for type '%s' with params %s: %s"
            .formatted(
                sType,
                type,
                params,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()
            ), cause);
    }
}
