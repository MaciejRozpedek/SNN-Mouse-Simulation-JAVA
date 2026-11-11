package com.macroz.snnmousesimulation.registry;

import com.macroz.snnmousesimulation.exception.AgentConfigurationException;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;
import com.macroz.snnmousesimulation.exception.SnnParseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyRegistryTest {

    @Test
    void reportsUnknownTypeAndMissingParamAtTheEntryPosition() {
        StrategyRegistry<Double> registry = new StrategyRegistry<>(StrategyType.INPUT);
        registry.register("NEEDS_VALUE", params -> params.getRequiredDouble("value"));

        var unknown = assertThrows(SnnParseException.class, () -> registry.create("OTHER", Map.of(), 7, 3));
        assertTrue(unknown.getMessage().contains("OTHER"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("line: 7, column: 3"), unknown.getMessage());

        var missing = assertThrows(AgentConfigurationException.class, () -> registry.create("NEEDS_VALUE", Map.of(), 7, 3));
        assertTrue(missing.getMessage().contains("'value'"), missing.getMessage());
        assertTrue(missing.getMessage().contains("line: 7, column: 3"), missing.getMessage());
    }
}
