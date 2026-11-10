package com.macroz.snnmousesimulation.world;

import java.util.Collections;
import java.util.List;

public final class WorldView {

    private final Geometry geometry;
    private final List<Food> food;

    private WorldView(Geometry geometry, List<Food> food) {
        this.geometry = geometry;
        this.food = Collections.unmodifiableList(food);
    }

    public static WorldView from(World world) {
        return new WorldView(world.getGeometry(), world.getFood());
    }

    public Geometry geometry() {
        return geometry;
    }

    public List<Food> food() {
        return food;
    }
}
