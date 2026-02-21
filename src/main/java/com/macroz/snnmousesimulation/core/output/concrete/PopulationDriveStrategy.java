package com.macroz.snnmousesimulation.core.output.concrete;

import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.world.Agent;

import java.util.Map;

public class PopulationDriveStrategy implements OutputStrategy {

    private final double speedPerSpike;
    private final double turnFactor;

    private PopulationDriveStrategy(double speedPerSpike, double turnFactor) {
        this.speedPerSpike = speedPerSpike;
        this.turnFactor = turnFactor;
    }

    public static PopulationDriveStrategy create(Map<String, Object> params) {
        double speed = ((Number) params.getOrDefault("speed_per_spike", 0.5)).doubleValue();
        double turn = ((Number) params.getOrDefault("turn_factor", 0.03)).doubleValue();
        return new PopulationDriveStrategy(speed, turn);
    }

    @Override
    public void apply(Agent agent, boolean[] firedLocalIndices, double deltaTime) {
        int count = firedLocalIndices.length;
        if (count < 2) return;

        int midPoint = count / 2;
        int leftSpikes = 0;
        int rightSpikes = 0;

        // Sum spikes for Left Motor (First half of the group)
        for (int i = 0; i < midPoint; i++) {
            if (firedLocalIndices[i]) leftSpikes++;
        }

        // Sum spikes for Right Motor (Second half of the group)
        for (int i = midPoint; i < count; i++) {
            if (firedLocalIndices[i]) rightSpikes++;
        }
        double leftMotorPower = leftSpikes * speedPerSpike;
        double rightMotorPower = rightSpikes * speedPerSpike;

        double forwardSpeed = (leftMotorPower + rightMotorPower) / 2.0;
        double rotation = (leftMotorPower - rightMotorPower) * turnFactor;

        if (forwardSpeed > 0 || Math.abs(rotation) > 0) {
            agent.move(forwardSpeed, rotation);
        }
    }
}