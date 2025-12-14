package com.macroz.snnmousesimulation.exception;

import org.yaml.snakeyaml.error.Mark;

public class SnnParseException extends RuntimeException {

    public SnnParseException(String message) {
        super(message);
    }

    // Constructor with Mark for line and column information in YAML
    public SnnParseException(String message, Mark mark) {
        super(buildMessage(message, mark));
    }

    private static String buildMessage(String message, Mark mark) {
        if (mark != null) {
            return String.format("Parse error (line: %d, column: %d): %s",
                    mark.getLine() + 1,
                    mark.getColumn() + 1,
                    message);
        }
        return "Parse error: " + message;
    }
}
