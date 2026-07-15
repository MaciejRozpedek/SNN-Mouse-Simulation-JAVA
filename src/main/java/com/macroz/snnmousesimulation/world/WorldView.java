package com.macroz.snnmousesimulation.world;

import java.util.Collections;
import java.util.List;

public final class WorldView {

    private final List<Food> food;

    private WorldView(List<Food> food) {
        this.food = Collections.unmodifiableList(food);
    }

    public static WorldView from(World world) {
        return new WorldView(world.getFood());
    }

    public List<Food> food() {
        return food;
    }
}
