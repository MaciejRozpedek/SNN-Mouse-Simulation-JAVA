package com.macroz.snnmousesimulation.core.output;
import com.macroz.snnmousesimulation.world.Agent;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class OutputSystem {
    private record OutputBinding(
        int[] sourceIndices,
        OutputStrategy strategy
    ){}

    private final List<OutputBinding> outputs = new ArrayList<>();

    public void addOutput(int[] targetIndices, OutputStrategy strategy) {
        outputs.add(new OutputSystem.OutputBinding(targetIndices, strategy));
    }

    public void processOutputs(Agent agent, List<Integer> allGlobalFiredIndices, double deltaTime) {
        Set<Integer> firedSet = new HashSet<>(allGlobalFiredIndices);

        for (OutputBinding binding : outputs) {

            boolean[] localFiredState = new boolean[binding.sourceIndices.length];

            for (int i = 0; i < binding.sourceIndices.length; i++) {
                if (firedSet.contains(binding.sourceIndices[i])) {
                    localFiredState[i] = true;
                }
            }

            binding.strategy.apply(agent, localFiredState, deltaTime);
        }
    }
}
