package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.world.Agent;
import com.macroz.snnmousesimulation.world.Food;
import com.macroz.snnmousesimulation.world.World;

public class GaussianVisionStrategy implements InputStrategy {

    private final double OVERLAP_FACTOR;
    private final double MAX_CURRENT;

    private final double fovRadians;
    private final double maxRange;
    private final double maxRangeSq;
    private final double halfFov;

    public GaussianVisionStrategy(double fovDegrees, double range, double overlap_factor, double maxCurrent) {
        if (fovDegrees <= 0 || range <= 0) {
            throw new IllegalArgumentException("FOV and range must be positive.");
        }
        if (overlap_factor <= 0) {
            throw new IllegalArgumentException("Overlap factor must be positive.");
        }
        this.fovRadians = Math.toRadians(fovDegrees);
        this.halfFov = this.fovRadians / 2.0;
        this.maxRange = range;
        this.maxRangeSq = range * range;
        this.OVERLAP_FACTOR = overlap_factor;
        this.MAX_CURRENT = maxCurrent;
    }

    @Override
    public double[] calculateCurrents(Agent agent, World worldSnapshot, double deltaTime, int targetNeuronCount) {
        if (targetNeuronCount <= 0) return new double[0];

        double[] currents = new double[targetNeuronCount];

        // Sigma calculation ensures smooth transition between neurons
        double sigma = (fovRadians / targetNeuronCount) * OVERLAP_FACTOR;
        double sigmaSq2 = 2 * sigma * sigma;

        for (Food food : worldSnapshot.getFood()) {
            double dx = food.x() - agent.getX();
            double dy = food.y() - agent.getY();
            double distSq = dx * dx + dy * dy;

            if (distSq > maxRangeSq) continue;

            double globalAngleToFood = Math.atan2(dy, dx);
            double relativeAngle = normalizeAngle(globalAngleToFood - agent.getAngle());

            if (Math.abs(relativeAngle) > halfFov) continue;

            double distance = Math.sqrt(distSq);

            // Quadratic falloff: Signal increases sharply as distance decreases.
            // Formula: (1 - distance/maxRange)^2
            double proximity = 1.0 - (distance / maxRange);
            double signalStrength = MAX_CURRENT * (proximity * proximity);

            for (int i = 0; i < targetNeuronCount; i++) {
                double preferredAngle = calculatePreferredAngle(i, targetNeuronCount);
                double angleDifference = relativeAngle - preferredAngle;

                // Gaussian spatial distribution
                double activation = Math.exp(-(angleDifference * angleDifference) / sigmaSq2);

                currents[i] += signalStrength * activation;
            }
        }

        return currents;
    }

    private double calculatePreferredAngle(int neuronIndex, int totalNeurons) {
        if (totalNeurons == 1) return 0.0;
        double t = (double) neuronIndex / (totalNeurons - 1);
        return -halfFov + (t * fovRadians);
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
}