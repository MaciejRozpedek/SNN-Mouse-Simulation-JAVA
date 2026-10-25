package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.InvalidParamException;
import com.macroz.snnmousesimulation.exception.SnnParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Reads typed values from config params (e.g. of an input or output)
 * and reports parameters that were never read.
 * For each type T: {@code getT(name, default)}, {@code getOptionalT(name)}, {@code getRequiredT(name)}.
 */
public final class ConfigParams {

    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private final Map<String, ConfigValue> values;
    private final int line;
    private final int column;
    private final Set<String> readNames = new LinkedHashSet<>();

    public ConfigParams(Map<String, ConfigValue> values) {
        this(values, 0, 0);
    }

    // Line and column (1-based) of the params map, used to report missing parameters
    public ConfigParams(Map<String, ConfigValue> values, int line, int column) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.line = line;
        this.column = column;
    }

    public static ConfigParams of(Map<String, ?> values) {
        Map<String, ConfigValue> converted = new LinkedHashMap<>();
        values.forEach((name, value) -> converted.put(name, ConfigValue.of(value)));
        return new ConfigParams(converted);
    }

    /**
     * Creates an object from these params. Invalid parameter values are reported at their
     * position and unread parameters are reported as unknown.
     */
    public <T> T build(Function<ConfigParams, T> factory) {
        T result;
        try {
            result = factory.apply(this);
        } catch (InvalidParamException e) {
            throw invalid(e.getParam(), values.get(e.getParam()), e.getRequirement());
        }
        requireNoUnknownKeys();
        return result;
    }

    public double getDouble(String name, double defaultValue) {
        return getOptionalDouble(name).orElse(defaultValue);
    }

    public Optional<Double> getOptionalDouble(String name) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value.quoted() || !DECIMAL.matcher(value.raw()).matches()) {
            throw invalid(name, value, "must be a number");
        }
        double parsed = Double.parseDouble(value.raw());
        if (!Double.isFinite(parsed)) {
            throw invalid(name, value, "is out of range");
        }
        return Optional.of(parsed);
    }

    public double getRequiredDouble(String name) {
        return getOptionalDouble(name).orElseThrow(() -> missing(name));
    }

    public long getLong(String name, long defaultValue) {
        return getOptionalLong(name).orElse(defaultValue);
    }

    public Optional<Long> getOptionalLong(String name) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value.quoted() || !INTEGER.matcher(value.raw()).matches()) {
            throw invalid(name, value, "must be an integer");
        }
        try {
            return Optional.of(Long.parseLong(value.raw()));
        } catch (NumberFormatException e) {
            throw invalid(name, value, "is out of range");
        }
    }

    public long getRequiredLong(String name) {
        return getOptionalLong(name).orElseThrow(() -> missing(name));
    }

    public int getInt(String name, int defaultValue) {
        return getOptionalInt(name).orElse(defaultValue);
    }

    public Optional<Integer> getOptionalInt(String name) {
        ConfigValue value = lookup(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value.quoted() || !INTEGER.matcher(value.raw()).matches()) {
            throw invalid(name, value, "must be an integer");
        }
        try {
            return Optional.of(Integer.parseInt(value.raw()));
        } catch (NumberFormatException e) {
            throw invalid(name, value, "is out of range");
        }
    }

    public int getRequiredInt(String name) {
        return getOptionalInt(name).orElseThrow(() -> missing(name));
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

    // Marks the parameter as read; returns null when it is missing or empty
    private ConfigValue lookup(String name) {
        readNames.add(name);
        ConfigValue value = values.get(name);
        return value == null || isNull(value) ? null : value;
    }

    private static boolean isNull(ConfigValue value) {
        String raw = value.raw();
        return !value.quoted() && (raw.isEmpty() || raw.equals("~") || raw.equalsIgnoreCase("null"));
    }

    private SnnParseException missing(String name) {
        String message = "Missing required parameter '" + name + "'.";
        return line > 0 ? new SnnParseException(message, line, column) : new SnnParseException(message);
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
