package com.macroz.snnmousesimulation.exception;

public class InvalidConfigNameException extends IllegalArgumentException {

    public InvalidConfigNameException(String name) {
        super("Invalid config name '" + name + "': only letters, digits, '_' and '-' are allowed, with '/' between folders (max 64 characters per part).");
    }
}
