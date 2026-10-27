package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.registry.Registries;
import com.macroz.snnmousesimulation.world.rule.WorldRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World implements WorldState {
    private final double width;
    private final double height;

    private double simulationTimeMs = 0;
    private final List<Agent> agents;
    private final List<Food> food = new ArrayList<>();
    private final List<WorldRule> rules;
    private final WorldView worldView;
    private final Random random = new Random();

    public World(ScenarioData scenario) {
        this.width = scenario.world().width();
        this.height = scenario.world().height();
        this.worldView = WorldView.from(this);
        this.agents = scenario.agents().stream()
                .map(agent -> new Agent(agent.startX(), agent.startY(), agent.startAngle(), agent.brain()))
                .toList();
        this.rules = scenario.rules().stream()
                .map(rule -> Registries.WORLD_RULES.create(rule.ruleType(), rule.params()))
                .toList();

        for (WorldRule rule : rules) {
            rule.initialize(this);
        }
    }

    public void update(double deltaTimeMs) {
        // 1. AGENTS - Each agent reacts to its events, senses, thinks and acts
        for (Agent agent : agents) {
            agent.update(new InputFrame(
                    AgentView.from(agent),
                    worldView,
                    agent.drainEvents(),
                    simulationTimeMs,
                    deltaTimeMs
            ));
        }

        // 2. WORLD - Rules apply mechanics such as boundaries and eating, and post events to agents
        for (WorldRule rule : rules) {
            rule.apply(this, deltaTimeMs);
        }

        simulationTimeMs += deltaTimeMs;
    }

    @Override
    public double width() {
        return width;
    }

    @Override
    public double height() {
        return height;
    }

    @Override
    public double simulationTimeMs() {
        return simulationTimeMs;
    }

    @Override
    public Random random() {
        return random;
    }

    @Override
    public List<Agent> agents() {
        return agents;
    }

    @Override
    public List<Food> food() {
        return food;
    }
}
