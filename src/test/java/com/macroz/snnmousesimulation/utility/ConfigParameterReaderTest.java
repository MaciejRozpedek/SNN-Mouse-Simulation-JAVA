package com.macroz.snnmousesimulation.utility;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigParameterReaderTest {

    @Test
    void readsNumbersDefaultsAndOptionalLong() {
        Map<String, Object> params = Map.of("current", 2, "seed", 42L);

        assertEquals(2.0, ConfigParameterReader.getDouble(params, "current", 0.0));
        assertEquals(7.0, ConfigParameterReader.getDouble(params, "missing", 7.0));
        assertEquals(42L, ConfigParameterReader.getOptionalLong(params, "seed"));
        assertNull(ConfigParameterReader.getOptionalLong(params, "missing"));
    }

    @Test
    void rejectsNonNumericValues() {
        Map<String, Object> params = Map.of("current", "2.0", "seed", true);

        assertThrows(IllegalArgumentException.class,
                () -> ConfigParameterReader.getDouble(params, "current", 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigParameterReader.getOptionalLong(params, "seed"));
    }
}
