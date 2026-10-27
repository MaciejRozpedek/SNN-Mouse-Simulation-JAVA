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
import com.macroz.snnmousesimulation.core.reaction.ReactionStrategy;
import com.macroz.snnmousesimulation.core.reaction.concrete.InjectDopamineReaction;
import com.macroz.snnmousesimulation.exception.AgentConfigurationException.StrategyType;
import com.macroz.snnmousesimulation.world.BoundaryHit;
import com.macroz.snnmousesimulation.world.FoodEaten;
import com.macroz.snnmousesimulation.world.rule.WorldRule;
import com.macroz.snnmousesimulation.world.rule.concrete.BounceBoundaryRule;
import com.macroz.snnmousesimulation.world.rule.concrete.FoodRule;

/**
 * All extension points of the simulation. Built-in types are registered here; custom types
 * can be registered the same way before the simulation is created.
 */
public final class Registries {

    public static final EventRegistry EVENTS = new EventRegistry();
    public static final StrategyRegistry<InputStrategy> INPUTS = new StrategyRegistry<>(StrategyType.INPUT);
    public static final StrategyRegistry<OutputStrategy> OUTPUTS = new StrategyRegistry<>(StrategyType.OUTPUT);
    public static final StrategyRegistry<ReactionStrategy<?>> REACTIONS = new StrategyRegistry<>(StrategyType.REACTION);
    public static final StrategyRegistry<WorldRule> WORLD_RULES = new StrategyRegistry<>(StrategyType.WORLD_RULE);

    static {
        EVENTS.register("FOOD_EATEN", FoodEaten.class);
        EVENTS.register("BOUNDARY_HIT", BoundaryHit.class);

        INPUTS.register("GAUSSIAN_VISION", GaussianVisionStrategy::create);
        INPUTS.register("TONIC_NOISE", TonicNoiseStrategy::create);
        INPUTS.register("OU_NOISE", OrnsteinUhlenbeckNoiseStrategy::create);

        OUTPUTS.register("POPULATION_DRIVE", PopulationDriveStrategy::create);
        OUTPUTS.register("FORWARD_DRIVE", ForwardDriveStrategy::create);
        OUTPUTS.register("TURN_LEFT", TurnLeftStrategy::create);
        OUTPUTS.register("TURN_RIGHT", TurnRightStrategy::create);

        REACTIONS.register("INJECT_DOPAMINE", InjectDopamineReaction::create);

        WORLD_RULES.register("BOUNCE_BOUNDARY", BounceBoundaryRule::create);
        WORLD_RULES.register("FOOD", FoodRule::create);
    }

    private Registries() {
    }
}
