package com.macroz.snnmousesimulation.exception;

import lombok.Getter;

/**
 * Invalid value of a named config parameter. The name lets the caller report
 * where the value is written in the config.
 */
@Getter
public class InvalidParamException extends IllegalArgumentException {

    private final String param;
    private final String requirement;

    public InvalidParamException(String param, String requirement) {
        super("Parameter '" + param + "' " + requirement + ".");
        this.param = param;
        this.requirement = requirement;
    }
}
