package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.core.input.concrete.GaussianVisionStrategy;

import java.util.Map;

public class InputStrategyFactory {

	public static InputStrategy create(String inputType, Map<String, Object> params) {
		return switch (inputType.toUpperCase()) {
			case "EXAMPLE" -> createExampleStrategy(params);
			case "GAUSSIAN_VISION" -> createSectorVisionStrategy(params);
			default -> throw new IllegalArgumentException("Unknown input type: " + inputType);
		};
	}

	private static InputStrategy createExampleStrategy(Map<String, Object> params) {
		// Extract parameters from the map and create the strategy
		throw new UnsupportedOperationException("Not implemented yet");
	}

	private static InputStrategy createSectorVisionStrategy(Map<String, Object> params) {
		return new GaussianVisionStrategy(
			((Number) params.getOrDefault("fov", 120)).doubleValue(),
			((Number) params.getOrDefault("range", 200)).doubleValue(),
			((Number) params.getOrDefault("overlap_factor", 1.5)).doubleValue(),
			((Number) params.getOrDefault("max_current", 10)).doubleValue()
		);
	}
}
