package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.core.output.concrete.PopulationDriveStrategy;

import java.util.Map;

public class OutputStrategyFactory {

    public static OutputStrategy create(String outputType, Map<String, Object> params) {
        return switch (outputType.toUpperCase()) {
            case "POPULATION_DRIVE" -> createPopulationDrive(params);
            default -> throw new IllegalArgumentException("Unknown output type: " + outputType);
        };
    }

    private static OutputStrategy createPopulationDrive(Map<String, Object> params) {
        double speed = ((Number) params.getOrDefault("speed_per_spike", 0.5)).doubleValue();
        double turn = ((Number) params.getOrDefault("turn_factor", 0.03)).doubleValue();
        return new PopulationDriveStrategy(speed, turn);
    }
}