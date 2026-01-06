package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.world.Agent;
import com.macroz.snnmousesimulation.world.World;

import java.util.ArrayList;
import java.util.List;

public class InputSystem {

    private record InputBinding(int[] targetIndices, InputStrategy strategy) {}

    private final List<InputBinding> inputs = new ArrayList<>();

    public void addInput(int[] targetIndices, InputStrategy strategy) {
        inputs.add(new InputBinding(targetIndices, strategy));
    }

    public List<RegisteredInput> calculateFrameInputs(Agent agent, World worldSnapshot, double deltaTime) {
        List<RegisteredInput> inputs = new ArrayList<>();
        for (InputBinding sb : this.inputs) {
            double[] currents = sb.strategy.calculateCurrents(agent, worldSnapshot, deltaTime, sb.targetIndices.length);
            inputs.add(new RegisteredInput(sb.targetIndices, currents));
        }
        return inputs;
    }

    public record RegisteredInput(int[] indices, double[] currents) {}
}