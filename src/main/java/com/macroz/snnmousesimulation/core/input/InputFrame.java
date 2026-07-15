package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.world.AgentEvent;
import com.macroz.snnmousesimulation.world.AgentView;
import com.macroz.snnmousesimulation.world.WorldView;

import java.util.List;
import java.util.Objects;

public record InputFrame(
        AgentView agent,
        WorldView world,
        List<AgentEvent> events,
        double simulationTimeMs,
        double deltaTimeMs) {

    public InputFrame {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(world, "world");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
