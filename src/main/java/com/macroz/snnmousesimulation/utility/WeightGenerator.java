package com.macroz.snnmousesimulation.utility;

import com.macroz.snnmousesimulation.exception.SnnParseException;
import java.util.Random;

public class WeightGenerator {

    private enum GenerationType {
        FIXED,
        UNIFORM,
        NORMAL
    }

    private final GenerationType type;
    private final double p1;
    private final double p2;
    private final Random random;

    private WeightGenerator(GenerationType type, double p1, double p2) {
        this.type = type;
        this.p1 = p1;
        this.p2 = p2;
        this.random = new Random();
    }

    public static WeightGenerator createFixed(double fixedValue) {
        return new WeightGenerator(GenerationType.FIXED, fixedValue, 0.0);
    }

    public static WeightGenerator createUniform(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("Minimal value cannot be greater than maximal value in Uniform distribution.");
        }
        return new WeightGenerator(GenerationType.UNIFORM, min, max);
    }

    public static WeightGenerator createNormal(double mean, double std) {
        if (std < 0) {
            throw new IllegalArgumentException("Standard deviation cannot be negative.");
        }
        return new WeightGenerator(GenerationType.NORMAL, mean, std);
    }

    public double generate() {
		return switch (type) {
			case FIXED -> p1;
			case UNIFORM -> p1 + (p2 - p1) * random.nextDouble();
			case NORMAL -> p1 + random.nextGaussian() * p2; // mean + N(0,1) * std
			default -> throw new IllegalStateException("Unknown generation type.");
		};
    }
}
