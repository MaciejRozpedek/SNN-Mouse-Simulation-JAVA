package com.macroz.snnmousesimulation.core.output;

import com.macroz.snnmousesimulation.world.Agent;

public interface OutputStrategy {
    void apply(Agent agent, boolean[] firedLocalIndices, double deltaTime);
}
