package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.SnnParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads typed values from the params of an input or output
 * and reports parameters that were never read.
 */
public final class ConfigParams {

    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private final Map<String, ConfigValue> values;
    private final Set<String> readNames = new LinkedHashSet<>();

    public ConfigParams(Map<String, ConfigValue> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ConfigParams of(Map<String, ?> values) {
        Map<String, ConfigValue> converted = new LinkedHashMap<>();
        values.forEach((name, value) -> converted.put(name, ConfigValue.of(value)));
        return new ConfigParams(converted);
    }

    public double getDouble(String name, double defaultValue) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return defaultValue;
        }
        if (value.quoted() || !DECIMAL.matcher(value.raw()).matches()) {
            throw invalid(name, value, "must be a number");
        }
        double parsed = Double.parseDouble(value.raw());
        if (!Double.isFinite(parsed)) {
            throw invalid(name, value, "is out of range");
        }
        return parsed;
    }

    public double getNonNegativeDouble(String name, double defaultValue) {
        double parsed = getDouble(name, defaultValue);
        if (parsed < 0.0) {
            throw invalid(name, values.get(name), "must be non-negative");
        }
        return parsed;
    }

    public Long getOptionalLong(String name) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return null;
        }
        if (value.quoted() || !INTEGER.matcher(value.raw()).matches()) {
            throw invalid(name, value, "must be an integer");
        }
        try {
            return Long.parseLong(value.raw());
        } catch (NumberFormatException e) {
            throw invalid(name, value, "is out of range");
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.quoted()) {
            if (value.raw().equalsIgnoreCase("true")) {
                return true;
            }
            if (value.raw().equalsIgnoreCase("false")) {
                return false;
            }
        }
        throw invalid(name, value, "must be true or false");
    }

    public void requireNoUnknownKeys() {
        List<String> unknown = new ArrayList<>();
        for (String name : values.keySet()) {
            if (!readNames.contains(name)) {
                unknown.add(name);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }
        String known = readNames.isEmpty() ? "none" : String.join(", ", readNames);
        throw error(values.get(unknown.getFirst()),
                "Unknown parameter(s) " + String.join(", ", unknown) + ". Known parameters: " + known + ".");
    }

    @Override
    public String toString() {
        return values.toString();
    }

    private ConfigValue lookup(String name) {
        readNames.add(name);
        ConfigValue value = values.get(name);
        return value == null || isNull(value) ? null : value;
    }

    private static boolean isNull(ConfigValue value) {
        String raw = value.raw();
        return !value.quoted() && (raw.isEmpty() || raw.equals("~") || raw.equalsIgnoreCase("null"));
    }

    private static SnnParseException invalid(String name, ConfigValue value, String requirement) {
        String got = value == null ? "" : ", got " + value;
        return error(value, "Parameter '" + name + "' " + requirement + got + ".");
    }

    private static SnnParseException error(ConfigValue value, String message) {
        return value != null && value.hasPosition()
                ? new SnnParseException(message, value.line(), value.column())
                : new SnnParseException(message);
    }
}
