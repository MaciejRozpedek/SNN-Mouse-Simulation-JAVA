package com.macroz.snnmousesimulation.world;

/**
 * Something that happened to an agent. Events are delivered to the agent at the start of the next step.
 */
public interface AgentEvent {

    double timestampMs();
}
