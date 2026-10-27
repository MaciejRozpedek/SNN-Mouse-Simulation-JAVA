package com.macroz.snnmousesimulation.world.rule.concrete;

import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentBody;
import com.macroz.snnmousesimulation.world.Food;
import com.macroz.snnmousesimulation.world.FoodEaten;
import com.macroz.snnmousesimulation.world.WorldState;
import com.macroz.snnmousesimulation.world.rule.WorldRule;

import java.util.List;

/**
 * Keeps a fixed amount of food in the world. Food within reach of an agent is eaten,
 * which posts a {@link FoodEaten} event to that agent, and new food appears elsewhere.
 */
public final class FoodRule implements WorldRule {

    private final int count;
    private final double eatRadius;

    public FoodRule(int count, double eatRadius) {
        ParamChecks.check("count", count, count >= 0, "must be non-negative");
        this.count = count;
        this.eatRadius = ParamChecks.positive("eat_radius", eatRadius);
    }

    public static FoodRule create(ConfigParams params) {
        return new FoodRule(
                params.getInt("count", 100),
                params.getDouble("eat_radius", 30.0));
    }

    @Override
    public void initialize(WorldState world) {
        for (int i = 0; i < count; i++) {
            spawnFood(world);
        }
    }

    @Override
    public void apply(WorldState world, double deltaTimeMs) {
        for (AgentBody agent : world.agents()) {
            List<Food> eaten = world.food().stream()
                    .filter(food -> isWithinReach(food.x(), food.y(), agent))
                    .toList();
            for (Food food : eaten) {
                world.food().remove(food);
                agent.postEvent(new FoodEaten(world.simulationTimeMs()));
                spawnFood(world);
            }
        }
    }

    // New food never appears within reach of an agent
    private void spawnFood(WorldState world) {
        double x;
        double y;
        do {
            x = world.random().nextDouble(0, world.width());
            y = world.random().nextDouble(0, world.height());
        } while (isWithinReachOfAnyAgent(x, y, world));
        world.food().add(new Food(x, y));
    }

    private boolean isWithinReachOfAnyAgent(double x, double y, WorldState world) {
        for (AgentBody agent : world.agents()) {
            if (isWithinReach(x, y, agent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinReach(double x, double y, AgentBody agent) {
        double dx = x - agent.getX();
        double dy = y - agent.getY();
        return dx * dx + dy * dy < eatRadius * eatRadius;
    }
}
