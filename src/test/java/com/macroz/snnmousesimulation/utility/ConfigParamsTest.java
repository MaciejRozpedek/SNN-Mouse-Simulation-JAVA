package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParamsTest {

    private static final int LINE = 3;
    private static final int COLUMN = 7;

    private static ConfigParams withX(String raw) {
        return new ConfigParams(Map.of("x", new ConfigValue(raw, false, LINE, COLUMN)));
    }

    private static ConfigParams withQuotedX(String raw) {
        return new ConfigParams(Map.of("x", new ConfigValue(raw, true, LINE, COLUMN)));
    }

    @Test
    void readsValueWithRequestedType() {
        assertEquals(7.0, withX("7").getDouble("x", 0.0));
        assertEquals(0.001, withX("1e-3").getDouble("x", 0.0));
        assertEquals(42L, withX("42").getOptionalLong("x"));
        assertTrue(withX("true").getBoolean("x", false));
    }

    @Test
    void usesDefaultForMissingOrEmptyValue() {
        assertEquals(1.5, new ConfigParams(Map.of()).getDouble("x", 1.5));
        assertEquals(1.5, withX("~").getDouble("x", 1.5));
        assertNull(withX("").getOptionalLong("x"));
    }

    // Values that a lenient parser would silently accept or convert
    @Test
    void rejectsInvalidValues() {
        assertThrows(SnnParseException.class, () -> withX("7.9").getOptionalLong("x"));
        assertThrows(SnnParseException.class, () -> withX("99999999999999999999").getOptionalLong("x"));
        assertThrows(SnnParseException.class, () -> withQuotedX("7").getDouble("x", 0.0));
        assertThrows(SnnParseException.class, () -> withX("7f").getDouble("x", 0.0));
        assertThrows(SnnParseException.class, () -> withX("yes").getBoolean("x", false));
    }

    @Test
    void reportsPositionOfInvalidValue() {
        SnnParseException e = assertThrows(SnnParseException.class, () -> withX("7.9").getOptionalLong("x"));

        assertTrue(e.getMessage().contains("line: " + LINE + ", column: " + COLUMN), e.getMessage());
    }

    @Test
    void reportsOnlyParamsThatWereNeverRead() {
        ConfigParams params = ConfigParams.of(Map.of("used", 1, "typo", 2));
        params.getDouble("used", 0.0);

        SnnParseException e = assertThrows(SnnParseException.class, params::requireNoUnknownKeys);
        assertTrue(e.getMessage().contains("typo"), e.getMessage());

        params.getDouble("typo", 0.0);
        assertDoesNotThrow(params::requireNoUnknownKeys);
    }
}
