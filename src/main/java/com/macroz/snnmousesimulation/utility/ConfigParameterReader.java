package com.macroz.snnmousesimulation.utility;

import java.util.Map;

/**
 * Converts configuration values used by input strategies.
 */
public final class ConfigParameterReader {

    private ConfigParameterReader() {
    }

    public static double getDouble(Map<String, Object> params, String name, double defaultValue) {
        Object value = params.getOrDefault(name, defaultValue);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be numeric.");
        }
        return number.doubleValue();
    }

    public static Long getOptionalLong(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be numeric.");
        }
        return number.longValue();
    }
}
