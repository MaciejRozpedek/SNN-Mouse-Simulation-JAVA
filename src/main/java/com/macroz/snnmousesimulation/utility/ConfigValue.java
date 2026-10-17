package com.macroz.snnmousesimulation.utility;

import org.springframework.lang.NonNull;

import java.util.Objects;

/**
 * Raw configuration value as written in YAML, with its 1-based position.
 * Line and column are 0 for values created in code.
 */
public record ConfigValue(String raw, boolean quoted, int line, int column) {

    public ConfigValue {
        Objects.requireNonNull(raw, "raw");
    }

    public static ConfigValue of(Object value) {
        return new ConfigValue(String.valueOf(value), false, 0, 0);
    }

    public boolean hasPosition() {
        return line > 0;
    }

    @NonNull
    @Override
    public String toString() {
        return quoted ? "\"" + raw + "\"" : raw;
    }
}
