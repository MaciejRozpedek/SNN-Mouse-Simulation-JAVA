package com.macroz.snnmousesimulation.api;

import java.util.List;

public record SimulationState(
    AgentState agent,
    List<FoodState> food
) {
    public record AgentState(double x, double y, double angle) {}
    public record FoodState(double x, double y) {}
}
