package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.InvalidParamException;

/**
 * Validates config parameters, reporting the parameter name on failure.
 */
public final class ParamChecks {

    private ParamChecks() {
    }

    public static double finite(String param, double value) {
        return check(param, value, Double.isFinite(value), "must be finite");
    }

    public static double positive(String param, double value) {
        return check(param, value, Double.isFinite(value) && value > 0.0, "must be positive");
    }

    public static double nonNegative(String param, double value) {
        return check(param, value, Double.isFinite(value) && value >= 0.0, "must be non-negative");
    }

    public static double check(String param, double value, boolean valid, String requirement) {
        if (!valid) {
            throw new InvalidParamException(param, requirement);
        }
        return value;
    }
}
