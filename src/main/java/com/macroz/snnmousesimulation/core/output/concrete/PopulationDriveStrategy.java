package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.world.Agent;


public class PopulationDriveStrategy implements OutputStrategy {

    private final double speedPerSecond;
    private final double rotationDegreesPerSecond;

    private PopulationDriveStrategy(double speedPerSecond, double rotationDegreesPerSecond) {
        this.speedPerSecond = speedPerSecond;
        this.rotationDegreesPerSecond = rotationDegreesPerSecond;
    }

    public static PopulationDriveStrategy create(ConfigParams params) {
        double speed = params.getDouble("speed", 0.5);
        double rotation = params.getDouble("turn_rate", 90.0);
        return new PopulationDriveStrategy(speed, rotation);
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTimeMs) {
        int count = firedLocalIndices.length;
        if (count < 2) return;

        int midPoint = count / 2;
        int leftSpikes = 0;
        int rightSpikes = 0;

        for (int i = 0; i < midPoint; i++) {
            if (firedLocalIndices[i]) {
                leftSpikes++;
            }
        }

        for (int i = midPoint; i < count; i++) {
            if (firedLocalIndices[i]) {
                rightSpikes++;
            }
        }

        int totalSpikes = leftSpikes + rightSpikes;

        double forwardSpeed = totalSpikes * (speedPerSecond / count);

        double rotationRateDegrees = 2.0 * (leftSpikes - rightSpikes) * rotationDegreesPerSecond / count;

        if (forwardSpeed > 0 || Math.abs(rotationRateDegrees) > 0) {
            double deltaTimeSeconds = deltaTimeMs / 1000.0;
            double rotationRadians = Math.toRadians(rotationRateDegrees * deltaTimeSeconds);
            agent.move(forwardSpeed * deltaTimeSeconds, rotationRadians);
        }
    }
}
