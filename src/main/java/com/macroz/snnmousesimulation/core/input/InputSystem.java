package com.macroz.snnmousesimulation.core.input;

import java.util.ArrayList;
import java.util.List;

public class InputSystem {

    private record InputBinding(int[] targetIndices, InputStrategy strategy) {}

    private final List<InputBinding> inputs = new ArrayList<>();

    public void addInput(int[] targetIndices, InputStrategy strategy) {
        inputs.add(new InputBinding(targetIndices, strategy));
    }

    public List<RegisteredInput> calculateFrameInputs(InputFrame frame) {
        List<RegisteredInput> inputs = new ArrayList<>();
        for (InputBinding sb : this.inputs) {
            double[] currents = sb.strategy.calculateCurrents(frame, sb.targetIndices.length);
            inputs.add(new RegisteredInput(sb.targetIndices, currents));
        }
        return inputs;
    }

    public record RegisteredInput(int[] indices, double[] currents) {}
}
