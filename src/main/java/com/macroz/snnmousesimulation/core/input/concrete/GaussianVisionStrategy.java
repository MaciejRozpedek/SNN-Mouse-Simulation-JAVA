package com.macroz.snnmousesimulation.core.input.concrete;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentView;
import com.macroz.snnmousesimulation.world.Food;


public class GaussianVisionStrategy implements InputStrategy {

    private final double OVERLAP_FACTOR;
    private final double MAX_CURRENT;

    private final double fovRadians;
    private final double maxRange;
    private final double maxRangeSq;
    private final double halfFov;

    private GaussianVisionStrategy(double fovDegrees, double range, double overlap_factor, double maxCurrent) {
        ParamChecks.positive("fov", fovDegrees);
        ParamChecks.positive("range", range);
        ParamChecks.positive("overlap_factor", overlap_factor);
        this.fovRadians = Math.toRadians(fovDegrees);
        this.halfFov = this.fovRadians / 2.0;
        this.maxRange = range;
        this.maxRangeSq = range * range;
        this.OVERLAP_FACTOR = overlap_factor;
        this.MAX_CURRENT = maxCurrent;
    }

    public static GaussianVisionStrategy create(ConfigParams params) {
        return new GaussianVisionStrategy(
                params.getDouble("fov", 120.0),
                params.getDouble("range", 200.0),
                params.getDouble("overlap_factor", 1.5),
                params.getDouble("max_current", 10.0));
    }

    @Override
    public double[] calculateCurrents(InputFrame frame, int targetNeuronCount) {
        if (targetNeuronCount <= 0) return new double[0];

        double[] currents = new double[targetNeuronCount];
        AgentView agent = frame.agent();

        double sigma = (fovRadians / targetNeuronCount) * OVERLAP_FACTOR;
        double sigmaSq2 = 2 * sigma * sigma;

        for (Food food : frame.world().food()) {
            double dx = food.x() - agent.x();
            double dy = food.y() - agent.y();
            double distSq = dx * dx + dy * dy;

            if (distSq > maxRangeSq) continue;

            double globalAngleToFood = Math.atan2(dy, dx);
            double relativeAngle = normalizeAngle(globalAngleToFood - agent.angle());

            if (Math.abs(relativeAngle) > halfFov) continue;

            double distance = Math.sqrt(distSq);

            double proximity = 1.0 - (distance / maxRange);
            double signalStrength = MAX_CURRENT * (proximity * proximity);

            for (int i = 0; i < targetNeuronCount; i++) {
                double preferredAngle = calculatePreferredAngle(i, targetNeuronCount);
                double angleDifference = relativeAngle - preferredAngle;

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
