package com.macroz.snnmousesimulation.core;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SnnEngine {

    // STDP Constants
    private static final double TAU_PLUS = 5.0;
    private static final double TAU_MINUS = 5.0;
    private static final double A_PLUS = 0.01;
    private static final double A_MINUS = 0.012;
    private static final double MAX_WEIGHT = 10000.0;

    // dopamine
    private static final double TAU_C = 1000.0;
    private static final double TAU_D = 20.0;
    private static final double TAU_BASE_D = 200.0;
    private double dopamineLevel = 0.0;
    private double dopamineBaseLevel = 0.0;
    private final double[][] eligibilityTraces;

    @Getter
    private final int totalNeuronCount;
    private final List<IzhikevichParams> neuronParamTypes;
    private final int[] neuronToTypeId;

    private final double[] v;
    private final double[] u;
    private final double[] I;
    private final double[] spikeI;

    private final int[][] synapticTargets;
    private final double[][] synapticWeights;

    private final int[][] synapticSources;
    private final int[][] synapticSourceIndices;
    private final double[] presynapticTrace;
    private final double[] postsynapticTrace;

    private int stepCount = 0;

    public SnnEngine(SnnNetworkData data) {
        this.totalNeuronCount = data.neuronToTypeId().length;
        this.neuronParamTypes = data.neuronParamTypes();
        this.neuronToTypeId = data.neuronToTypeId();

        this.v = Arrays.copyOf(data.initialV(), totalNeuronCount);
        this.u = Arrays.copyOf(data.initialU(), totalNeuronCount);
        this.I = new double[totalNeuronCount];
        this.spikeI = new double[totalNeuronCount];

        this.synapticTargets = data.synapticTargets();
        this.synapticWeights = data.synapticWeights();

        this.presynapticTrace = new double[totalNeuronCount];
        this.postsynapticTrace = new double[totalNeuronCount];

        // Initialize STDP structures
        int[] inputCounts = new int[totalNeuronCount];
        for (int[] targets : synapticTargets) {
            for (int target : targets) {
                inputCounts[target]++;
            }
        }

        this.synapticSources = new int[totalNeuronCount][];
        this.synapticSourceIndices = new int[totalNeuronCount][];
        for (int i = 0; i < totalNeuronCount; i++) {
            this.synapticSources[i] = new int[inputCounts[i]];
            this.synapticSourceIndices[i] = new int[inputCounts[i]];
        }

        int[] currentFillIndex = new int[totalNeuronCount];
        for (int pre = 0; pre < totalNeuronCount; pre++) {
            int[] targets = synapticTargets[pre];
            for (int k = 0; k < targets.length; k++) {
                int post = targets[k];
                int fillIdx = currentFillIndex[post];
                this.synapticSources[post][fillIdx] = pre;
                this.synapticSourceIndices[post][fillIdx] = k;
                currentFillIndex[post]++;
            }
        }

        this.eligibilityTraces = new double[totalNeuronCount][];
        for (int i = 0; i < totalNeuronCount; i++) {
            this.eligibilityTraces[i] = new double[synapticTargets[i].length];
        }
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
            v[i] += dt * (0.04 * v[i] * v[i] + 5 * v[i] + 140 - u[i] + I[i]) + spikeI[i];
        }

        // 2. Handle Spikes and Reset
        List<Integer> firedIndices = new ArrayList<>();
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

                // Propagate spikes
                int[] targets = synapticTargets[i];
                double[] weights = synapticWeights[i];
                for (int k = 0; k < targets.length; k++) {
                    spikeI[targets[k]] += weights[k];
                }
            }
        }

        // 3. Apply STDP Rule if any neuron fired
        if (!firedIndices.isEmpty()) {
            applySTDP(firedIndices);
        }

        // 4. Update Synaptic Traces
        updateSynapticTraces(dt);

        // 5. Update Weights and Dopamine
        updateWeightsAndTraces(dt);

        stepCount++;
        return firedIndices;
    }

    private void updateSynapticTraces(double dt) {
        double decayPre = Math.exp(-dt / TAU_PLUS);
        double decayPost = Math.exp(-dt / TAU_MINUS);

        for (int i = 0; i < totalNeuronCount; i++) {
            presynapticTrace[i] *= decayPre;
            postsynapticTrace[i] *= decayPost;
        }
    }

    private void updateWeightsAndTraces(double dt) {
        double decayTrace = Math.exp(-dt / TAU_C);

        double dopamineSignal = dopamineLevel - dopamineBaseLevel;

        boolean isDopamineActive = Math.abs(dopamineSignal) > 1e-6;

        for (int i = 0; i < totalNeuronCount; i++) {
            double[] traces = eligibilityTraces[i];
            if (isDopamineActive) {
                double[] weights = synapticWeights[i];
                for (int k = 0; k < weights.length; k++) {
                    double weightChange = traces[k] * dopamineSignal * dt;

                    if (weightChange != 0) {
                        weights[k] += weightChange;
                        if (weights[k] > MAX_WEIGHT) weights[k] = MAX_WEIGHT;
                        if (weights[k] < 0) weights[k] = 0;
                    }
                    traces[k] *= decayTrace;
                }
            } else {
                for (int k = 0; k < traces.length; k++) {
                    traces[k] *= decayTrace;
                }
            }
        }

        double alphaBase = Math.exp(-dt / TAU_BASE_D);
        dopamineBaseLevel += (dopamineLevel - dopamineBaseLevel) * alphaBase;

        double decayDopamine = Math.exp(-dt / TAU_D);
        dopamineLevel *= decayDopamine;
    }

    private void applySTDP(List<Integer> firedIndices) {
        for (int firedNeuron : firedIndices) {
            // LTP
            int[] sources = synapticSources[firedNeuron];
            int[] sourceIndices = synapticSourceIndices[firedNeuron];
            for (int i = 0; i < sources.length; i++) {
                int preNeuron = sources[i];
                int k = sourceIndices[i];
                double deltaW = A_PLUS * presynapticTrace[preNeuron];
                eligibilityTraces[preNeuron][k] += deltaW;
            }

            // LTD
            int[] targets = synapticTargets[firedNeuron];
            for (int k = 0; k < targets.length; k++) {
                int postNeuron = targets[k];
                double deltaW = -A_MINUS * postsynapticTrace[postNeuron];
                eligibilityTraces[firedNeuron][k] += deltaW;
            }
        }

        // Update traces for neurons that just fired
        for (int firedNeuron : firedIndices) {
            presynapticTrace[firedNeuron] += 1.0;
            postsynapticTrace[firedNeuron] += 1.0;
        }
    }

    public void injectDopamine(double amount) {
        this.dopamineLevel += amount;
    }

    public void addInputCurrent(int[] neuronIndices, double[] currents) {
        if (neuronIndices.length != currents.length) {
            throw new IllegalArgumentException("Neuron indices and currents arrays must have the same length.");
        }
        for (int i = 0; i < neuronIndices.length; i++) {
            int idx = neuronIndices[i];
            if (idx < 0 || idx >= totalNeuronCount) {
                throw new IllegalArgumentException("Invalid neuron index: " + idx);
            }
            this.I[idx] += currents[i];
        }
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