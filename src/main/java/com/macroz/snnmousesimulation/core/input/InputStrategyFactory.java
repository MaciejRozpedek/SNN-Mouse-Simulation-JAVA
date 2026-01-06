package com.macroz.snnmousesimulation.core.input;

import java.util.Map;

public class InputStrategyFactory {

	public static InputStrategy create(String inputType, Map<String, Object> params) {
		return switch (inputType.toUpperCase()) {
			case "EXAMPLE" -> createExampleStrategy(params);
			default -> throw new IllegalArgumentException("Unknown input type: " + inputType);
		};
	}

	private static InputStrategy createExampleStrategy(Map<String, Object> params) {
		// Extract parameters from the map and create the strategy
		throw new UnsupportedOperationException("Not implemented yet");
	}
}
