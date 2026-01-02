package com.macroz.snnmousesimulation.core;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SnnEngine {

    @Getter
    private final int totalNeuronCount;
    private final List<IzhikevichParams> neuronParamTypes;
    private final int[] neuronToTypeId;

    private final double[] v; // Membrane potential
    private final double[] u; // Recovery variable
    private final double[] I; // Input current

    private final int[][] synapticTargets;
    private final double[][] synapticWeights;

    private int stepCount = 0;

    public SnnEngine(SnnNetworkData data) {
        this.totalNeuronCount = data.neuronToTypeId().length;
        this.neuronParamTypes = data.neuronParamTypes();
        this.neuronToTypeId = data.neuronToTypeId();

        this.v = Arrays.copyOf(data.initialV(), totalNeuronCount);
        this.u = Arrays.copyOf(data.initialU(), totalNeuronCount);
        this.I = new double[totalNeuronCount]; // default 0.0

        this.synapticTargets = data.synapticTargets();
        this.synapticWeights = data.synapticWeights();
    }

    /**
     * Advances the simulation by dt milliseconds. Returns the list of neuron indices that fired.
     */
    public List<Integer> step(double dt) {
        // 1. Update membrane potentials (v) and recovery variables (u)
        IzhikevichParams p;
        for (int i = 0; i < totalNeuronCount; i++) {
            // v' = 0.04v^2 + 5v + 140 - u + I
            // u' = a(bv - u)
            int typeId = neuronToTypeId[i];
			p = neuronParamTypes.get(typeId);
            u[i] += dt * (p.a() * (p.b() * v[i] - u[i]));
            v[i] += dt * (0.04 * v[i] * v[i] + 5 * v[i] + 140 - u[i] + I[i]);
        }

        // 2. Handle Spikes and Reset
        List<Integer> firedIndices = new ArrayList<>();

        // Reset Input Current I for the NEXT step (current injection is instantaneous)
        Arrays.fill(I, 0.0);

        for (int i = 0; i < totalNeuronCount; i++) {
            // if v >= 30 mV
            //  v = c, u = u + d
            if (v[i] >= 30.0) {
                firedIndices.add(i);
                int typeId = neuronToTypeId[i];
                p = neuronParamTypes.get(typeId);
                v[i] = p.c();
                u[i] += p.d();

                // Propagate spikes to targets
                int[] targets = synapticTargets[i];
                double[] weights = synapticWeights[i];

                if (targets != null) {
                    for (int k = 0; k < targets.length; k++) {
                        int targetIdx = targets[k];
                        double weight = weights[k];
                        I[targetIdx] += weight;
                    }
                }
            }
        }

        stepCount++;
        return firedIndices;
    }

    public void addInputCurrent(int startNeuronIndex, int endNeuronIndex, double current) {
        if (startNeuronIndex < 0 || endNeuronIndex >= totalNeuronCount ||
                startNeuronIndex > endNeuronIndex) {
            throw new IllegalArgumentException("Invalid neuron index range.");
        }
        for (int idx = startNeuronIndex; idx <= endNeuronIndex; idx++) {
            this.I[idx] += current;
        }
    }

    public void addInputCurrent(int neuronIndex, double current) {
        if (neuronIndex < 0 || neuronIndex >= totalNeuronCount) {
            throw new IllegalArgumentException("Invalid neuron index.");
        }
        this.I[neuronIndex] += current;
    }

    public double getV(int index) {
        return v[index];
    }

}