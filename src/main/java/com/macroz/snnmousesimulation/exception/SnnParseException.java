package com.macroz.snnmousesimulation.exception;

import org.yaml.snakeyaml.error.Mark;

public class SnnParseException extends RuntimeException {

    public SnnParseException(String message) {
        super(message);
    }

    // Constructor with Mark for line and column information in YAML
    public SnnParseException(String message, Mark mark) {
        super(mark != null
                ? buildMessage(message, mark.getLine() + 1, mark.getColumn() + 1)
                : "Parse error: " + message);
    }

    // Line and column are 1-based
    public SnnParseException(String message, int line, int column) {
        super(buildMessage(message, line, column));
    }

    private static String buildMessage(String message, int line, int column) {
        return String.format("Parse error (line: %d, column: %d): %s", line, column, message);
    }
}
