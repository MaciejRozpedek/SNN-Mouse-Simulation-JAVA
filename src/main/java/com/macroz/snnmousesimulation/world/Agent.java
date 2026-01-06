package com.macroz.snnmousesimulation.world;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Agent {
    @Setter
    private double x;
    @Setter
    private double y;
    @Setter
    private double angle;
    // SNN instance

    // PARAMETERS
    private static final double SPEED_FACTOR = 2.0;
    private static final double TURN_FACTOR = 0.1;

    public Agent(double startX, double startY) {
        this.x = startX;
        this.y = startY;
        this.angle = 0;
        // SNN initialization
    }

    // Executes one tick of the agent's life.
    public void update(double distanceToFood, double angleToFood) {
        // 1. SENSORS - Convert physical distance/angle into input current for the network
        // 2. BRAIN PROCESS - Feed inputs to SNN and run one time step
        // 3. MOTORS (Movement Logic) - Interpret specific output neurons as "Left Motor" and "Right Motor"

        // Differential steering logic

        // double speed = 0;
        // double rotation = 0;
        //
        // if (leftMotorActive) {
        //    speed += SPEED_FACTOR;
        //    rotation += TURN_FACTOR; // Turn right
        // }
        // if (rightMotorActive) {
        //    speed += SPEED_FACTOR;
        //    rotation -= TURN_FACTOR; // Turn left
        // }

        // Apply movement physics
        // move(speed, rotation);
    }

    public void applyReward(){
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    private double[] calculateSensoryInput(double distToFood, double angleToFood){
        // Implementation of sensor transformation
        return new double[]{ /* currents */ };
    }

    private void move(double speed, double rotation) {
        this.angle += rotation;
        this.x += Math.cos(this.angle) * speed;
        this.y += Math.sin(this.angle) * speed;
    }

}
