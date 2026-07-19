package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.core.input.InputFrame;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {
    private final double width;
    private final double height;

    @Getter
    private double simulationTimeMs = 0;
    @Getter
    private final Agent agent;
    @Getter
    private final List<Food> food;
    private final WorldView worldView;
    private List<AgentEvent> pendingAgentEvents = List.of();
    private final List<AgentEvent> currentTickEvents = new ArrayList<>();

    private final Random random = new Random();

    private static final double EAT_RADIUS = 30.0;

    public World(double width, double height, int numberOfFood, SnnNetworkData snnNetworkData) {
        this.width = width;
        this.height = height;
        this.food = new ArrayList<>();
        this.worldView = WorldView.from(this);
        this.agent = new Agent(width / 2, height / 2, snnNetworkData);
        initializeFood(numberOfFood);
    }

    public void update(double deltaTime) {
        InputFrame inputFrame = new InputFrame(
                AgentView.from(agent),
                worldView,
                pendingAgentEvents,
                simulationTimeMs,
                deltaTime
        );

        agent.update(inputFrame);
        pendingAgentEvents = List.of();
        currentTickEvents.clear();
        handleBoundaries(simulationTimeMs);

        List<Food> collidedFoods = new ArrayList<>();
        for (Food f : food) {
            if (distanceSquared(f.x(), f.y(), agent.getX(), agent.getY()) < EAT_RADIUS * EAT_RADIUS) {
                collidedFoods.add(f);
            }
        }
        for (Food f : collidedFoods) {
            handleFoodCollision(f, simulationTimeMs);
        }

        pendingAgentEvents = currentTickEvents.isEmpty() ? List.of() : List.copyOf(currentTickEvents);
        simulationTimeMs += deltaTime;
    }

    private void handleFoodCollision(Food eatenFood, double timestampMs) {
        food.remove(eatenFood);
        agent.applyReward();
        currentTickEvents.add(new FoodEaten(timestampMs));
        spawnSingleFood();
    }

    private void initializeFood(int numberOfFood) {
        for (int i = 0; i < numberOfFood; i++) {
            spawnSingleFood();
        }
    }

    private void spawnSingleFood() {
        double x;
        double y;
        do {
            x = random.nextDouble(0, width);
            y = random.nextDouble(0, height);
        } while (distanceSquared(x, y, agent.getX(), agent.getY()) < EAT_RADIUS * EAT_RADIUS);
        food.add(new Food(x, y));
    }

    private double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private void handleBoundaries(double timestampMs) {
        double x = agent.getX();
        double y = agent.getY();
        double angle = agent.getAngle();
        boolean bounced = false;

        // Check horizontal walls (Left/Right)
        if (x < 0) {
            x = -x;
            angle = Math.PI - angle;
            bounced = true;
            currentTickEvents.add(new BoundaryHit(timestampMs, BoundaryHit.Side.LEFT));
        } else if (x > width) {
            x = 2 * width - x;
            angle = Math.PI - angle;
            bounced = true;
            currentTickEvents.add(new BoundaryHit(timestampMs, BoundaryHit.Side.RIGHT));
        }

        // Check vertical walls (Top/Bottom)
        if (y < 0) {
            y = -y;
            angle = -angle;
            bounced = true;
            currentTickEvents.add(new BoundaryHit(timestampMs, BoundaryHit.Side.TOP));
        } else if (y > height) {
            y = 2 * height - y;
            angle = -angle;
            bounced = true;
            currentTickEvents.add(new BoundaryHit(timestampMs, BoundaryHit.Side.BOTTOM));
        }

        if (bounced) {
            agent.setX(x);
            agent.setY(y);
            agent.setAngle(normalizeAngle(angle));
        }
    }
}
