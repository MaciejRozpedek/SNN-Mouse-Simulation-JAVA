package com.macroz.snnmousesimulation.world;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.macroz.snnmousesimulation.core.SnnEngine;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.core.input.InputConfig;
import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.core.input.InputStrategyFactory;
import com.macroz.snnmousesimulation.core.input.InputSystem;
import com.macroz.snnmousesimulation.core.output.OutputConfig;
import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.core.output.OutputStrategyFactory;
import com.macroz.snnmousesimulation.core.output.OutputSystem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class Agent {
    @Setter
    private double x;
    @Setter
    private double y;
    @Setter
    private double angle;
    // SNN instance
    @JsonIgnore
    private final SnnEngine engine;
    @JsonIgnore
    private final InputSystem inputSystem;
    @JsonIgnore
    private final OutputSystem outputSystem;

    public Agent(double startX, double startY, SnnNetworkData data) {
        this.x = startX;
        this.y = startY;
        this.angle = 0;
        // SNN initialization
        this.engine = new SnnEngine(data);
        this.inputSystem = new InputSystem();
        this.outputSystem = new OutputSystem();

        if (data.inputConfigs() != null) {
            for (InputConfig config : data.inputConfigs()) {
                InputStrategy strategy = InputStrategyFactory.create(config.sensorType(), config.params());
                inputSystem.addInput(config.targetNeuronIndices(), strategy);
            }
        }

        if (data.outputConfigs() != null) {
            for (OutputConfig config : data.outputConfigs()) {
                OutputStrategy strategy = OutputStrategyFactory.create(config.outputType(), config.params());
                outputSystem.addOutput(config.sourceNeuronIndices(), strategy);
            }
        }
    }

    public void update(World worldSnapshot, double deltaTime) {
        // 1. SENSORS - Get sensory inputs and convert to currents
        var registeredInputs = inputSystem.calculateFrameInputs(this, worldSnapshot, deltaTime);
        for (var input : registeredInputs) {
            engine.addInputCurrent(input.indices(), input.currents());
        }

        // 2. BRAIN PROCESS - Run one time step
        List<Integer> firedIndices = engine.step(deltaTime);

        // 3. RESPONSE - Interpret outputs and move
        outputSystem.processOutputs(this, firedIndices, deltaTime);
    }

    // Executes one tick of the agent's life.
    public void update_old(double distanceToFood, double angleToFood) {
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

    public void move(double speed, double rotation) {
        this.angle += rotation;
        this.x += Math.cos(this.angle) * speed;
        this.y += Math.sin(this.angle) * speed;
    }
}
