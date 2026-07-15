package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.world.AgentView;
import com.macroz.snnmousesimulation.world.WorldView;

import java.util.Objects;

public record InputFrame(
        AgentView agent,
        WorldView world,
        double simulationTimeMs,
        double deltaTimeMs) {

    public InputFrame {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(world, "world");
    }
}
