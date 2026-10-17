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

    public static boolean getBoolean(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a boolean.");
        }
        return bool;
    }

    public static double getNonNegativeDouble(Map<String, Object> params, String name, double defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed) || parsed < 0.0) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
        return parsed;
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
