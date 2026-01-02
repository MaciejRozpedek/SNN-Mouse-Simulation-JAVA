package com.macroz.snnmousesimulation.world;

import lombok.Getter;

import java.util.List;

@Getter
public class World {
    // Boundaries
    private final double width;
    private final double height;

    // Entities
    private Agent agent;
    List<Food> food;

    public World(double width, double height) {
        this.width = width;
        this.height = height;
        this.agent = new Agent(width / 2, height / 2);
    }

    private void update(){
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void respawnFood() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void handleBoundaries() {
        // keep agent inside [0, width] and [0, height]
    }
}
