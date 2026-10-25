package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(Optional.of(42L), withX("42").getOptionalLong("x"));
        assertEquals(Optional.of(42), withX("42").getOptionalInt("x"));
        assertTrue(withX("true").getBoolean("x", false));
    }

    @Test
    void usesDefaultForMissingOrEmptyValue() {
        assertEquals(1.5, new ConfigParams(Map.of()).getDouble("x", 1.5));
        assertEquals(1.5, withX("~").getDouble("x", 1.5));
        assertTrue(withX("").getOptionalLong("x").isEmpty());
    }

    // Values that a lenient parser would silently accept or convert
    @Test
    void rejectsInvalidValues() {
        assertThrows(SnnParseException.class, () -> withX("7.9").getOptionalLong("x"));
        assertThrows(SnnParseException.class, () -> withX("99999999999999999999").getOptionalLong("x"));
        assertThrows(SnnParseException.class, () -> withX("3000000000").getOptionalInt("x"));
        assertThrows(SnnParseException.class, () -> withQuotedX("7").getDouble("x", 0.0));
        assertThrows(SnnParseException.class, () -> withX("7f").getDouble("x", 0.0));
        assertThrows(SnnParseException.class, () -> withX("yes").getBoolean("x", false));
    }

    @Test
    void reportsPositionOfInvalidValue() {
        String position = "line: " + LINE + ", column: " + COLUMN;

        SnnParseException wrongType = assertThrows(SnnParseException.class, () -> withX("7.9").getOptionalLong("x"));
        assertTrue(wrongType.getMessage().contains(position), wrongType.getMessage());

        SnnParseException failedCheck = assertThrows(SnnParseException.class,
                () -> withX("-1").build(params -> ParamChecks.positive("x", params.getDouble("x", 1.0))));
        assertTrue(failedCheck.getMessage().contains(position), failedCheck.getMessage());
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
