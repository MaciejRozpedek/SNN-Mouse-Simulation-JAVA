package com.macroz.snnmousesimulation.world;

import java.util.List;
import java.util.Random;

/**
 * The world as seen by world rules.
 */
public interface WorldState {

    double width();

    double height();

    double simulationTimeMs();

    Random random();

    List<? extends AgentBody> agents();

    /** Mutable list of food in the world. */
    List<Food> food();
}
