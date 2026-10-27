package com.macroz.snnmousesimulation.registry;

import com.macroz.snnmousesimulation.core.input.InputStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.GaussianVisionStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.OrnsteinUhlenbeckNoiseStrategy;
import com.macroz.snnmousesimulation.core.input.concrete.TonicNoiseStrategy;
import com.macroz.snnmousesimulation.core.output.OutputStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.ForwardDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.PopulationDriveStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnLeftStrategy;
import com.macroz.snnmousesimulation.core.output.concrete.TurnRightStrategy;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;

/**
 * All extension points of the simulation. Built-in types are registered here; custom types
 * can be registered the same way before the simulation is created.
 */
public final class Registries {

    public static final StrategyRegistry<InputStrategy> INPUTS = new StrategyRegistry<>(StrategyType.INPUT);
    public static final StrategyRegistry<OutputStrategy> OUTPUTS = new StrategyRegistry<>(StrategyType.OUTPUT);

    static {
        INPUTS.register("GAUSSIAN_VISION", GaussianVisionStrategy::create);
        INPUTS.register("TONIC_NOISE", TonicNoiseStrategy::create);
        INPUTS.register("OU_NOISE", OrnsteinUhlenbeckNoiseStrategy::create);

        OUTPUTS.register("POPULATION_DRIVE", PopulationDriveStrategy::create);
        OUTPUTS.register("FORWARD_DRIVE", ForwardDriveStrategy::create);
        OUTPUTS.register("TURN_LEFT", TurnLeftStrategy::create);
        OUTPUTS.register("TURN_RIGHT", TurnRightStrategy::create);
    }

    private Registries() {
    }
}
