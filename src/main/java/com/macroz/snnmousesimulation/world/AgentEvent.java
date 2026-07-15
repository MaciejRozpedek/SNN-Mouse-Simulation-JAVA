package com.macroz.snnmousesimulation.world;

public sealed interface AgentEvent permits BoundaryHit, FoodEaten {

    double timestampMs();
}
