package com.macroz.snnmousesimulation.world;

import java.util.Objects;

public record BoundaryHit(double timestampMs, Side side) implements AgentEvent {

    public BoundaryHit {
        Objects.requireNonNull(side, "side");
    }

    public enum Side {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }
}
