package com.macroz.snnmousesimulation.core.input;

import com.macroz.snnmousesimulation.world.Agent;
import com.macroz.snnmousesimulation.world.World;

public interface InputStrategy {
    /**
	 * Returns an array of currents with a length equal to targetNeuronIndices.length.
	 */
    double[] calculateCurrents(Agent agent, World worldSnapshot, double deltaTime, int targetNeuronCount);
}