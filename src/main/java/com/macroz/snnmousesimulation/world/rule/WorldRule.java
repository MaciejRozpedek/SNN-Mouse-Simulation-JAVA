package com.macroz.snnmousesimulation.world.rule;

import com.macroz.snnmousesimulation.world.WorldState;

/**
 * A mechanic of the world, e.g. eating or boundaries. Rules are applied in the order of the
 * scenario config after all agents have acted, and may post events to agents.
 */
public interface WorldRule {

    default void initialize(WorldState world) {
    }

    void apply(WorldState world, double deltaTimeMs);
}
