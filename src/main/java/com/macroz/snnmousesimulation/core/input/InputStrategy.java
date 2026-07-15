package com.macroz.snnmousesimulation.core.input;

public interface InputStrategy {
    /**
     * Returns an array of currents with a length equal to the target neuron count.
     */
    double[] calculateCurrents(InputFrame frame, int targetNeuronCount);
}