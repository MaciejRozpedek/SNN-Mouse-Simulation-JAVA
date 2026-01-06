package com.macroz.snnmousesimulation.world;


import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {
    // Boundaries
    private final double width;
    private final double height;

    // Entities
    @Getter
    private final Agent agent;
    @Getter
    private final List<Food> food;

    private final Random random = new Random();

    // PARAMETERS
    private static final double EAT_RADIUS = 10.0;

    public World(double width, double height, int numberOfFood) {
        this.width = width;
        this.height = height;
        this.agent = new Agent(width / 2, height / 2);
        this.food = new ArrayList<>();
        initializeFood(numberOfFood);
    }

    public void update(){
        Food closestFood = null;
        double minDistanceSquared = Double.MAX_VALUE;
        double dxToClosest = 0;
        double dyToClosest = 0;

        for (Food f : food) {
            double dx = f.x() - agent.getX();
            double dy = f.y() - agent.getY();
            double distSq = dx * dx + dy * dy;

            if (distSq < minDistanceSquared) {
                minDistanceSquared = distSq;
                closestFood = f;
                dxToClosest = dx;
                dyToClosest = dy;
            }
        }
        double distance = Math.sqrt(minDistanceSquared);
        double globalAngleToFood = Math.atan2(dyToClosest, dxToClosest);
        double relativeAngle = normalizeAngle(globalAngleToFood - agent.getAngle());

        agent.update(distance, relativeAngle);
        handleBoundaries();

        if (closestFood != null) {
            double newDx = closestFood.x() - agent.getX();
            double newDy = closestFood.y() - agent.getY();
            double newDistSq = newDx * newDx + newDy * newDy;

            if (newDistSq < (EAT_RADIUS * EAT_RADIUS)) {
                handleFoodCollision(closestFood);
            }
        }
    }

    private void handleFoodCollision(Food eatenFood) {
        food.remove(eatenFood);
        agent.applyReward();
        spawnSingleFood();
    }

    private void initializeFood(int numberOfFood) {
        for (int i = 0; i < numberOfFood; i++) {
            spawnSingleFood();
        }
    }

    private void spawnSingleFood() {
        double x = random.nextDouble(0,width);
        double y = random.nextDouble(0,height);
        food.add(new Food(x,y));
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private void handleBoundaries() {
        double x = agent.getX();
        double y = agent.getY();
        double angle = agent.getAngle();
        boolean bounced = false;

        // Check horizontal walls (Left/Right)
        if (x < 0) {
            x = 0;
            angle = Math.PI - angle;
            bounced = true;
        } else if (x > width) {
            x = width;
            angle = Math.PI - angle;
            bounced = true;
        }

        // Check vertical walls (Top/Bottom)
        if (y < 0) {
            y = 0;
            angle = -angle;
            bounced = true;
        } else if (y > height) {
            y = height;
            angle = -angle;
            bounced = true;
        }

        if (bounced) {
            agent.setX(x);
            agent.setY(y);
            agent.setAngle(normalizeAngle(angle));
            
            // Penalize or Reset SNN?
            // agent.applyPunishment(); 
        }
    }
}
