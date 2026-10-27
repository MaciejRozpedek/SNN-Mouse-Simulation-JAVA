package com.macroz.snnmousesimulation.api;

import java.util.List;

public record SimulationState(
    double simulationTimeMs,
    double worldWidth,
    double worldHeight,
    AgentState agent,
    List<FoodState> food,
    SnnDiagnosticState snnDiagnostics
) {
    public record AgentState(double x, double y, double angle) {}
    public record FoodState(double x, double y) {}
}
