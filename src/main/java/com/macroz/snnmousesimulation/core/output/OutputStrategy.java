package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.world.AgentEffects;

public interface OutputStrategy {
    void apply(AgentEffects effects, boolean[] firedLocalIndices, double deltaTime);
}
