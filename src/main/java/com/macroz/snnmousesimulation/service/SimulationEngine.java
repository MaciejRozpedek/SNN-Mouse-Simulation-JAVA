package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.api.SimulationState;
import com.macroz.snnmousesimulation.world.World;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationEngine {
    private final World world;
    private ScheduledExecutorService scheduler;

    public SimulationEngine() {
        this.world = new World(800, 600, 50);
    }

    public void startSimulation() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Simulation-Loop");
            t.setDaemon(true);
            return t;
        });

        // Run at 1ms -> 1000 TPS for high resolution and fast network updates
        scheduler.scheduleAtFixedRate(() -> tick(16), 0, 16, TimeUnit.MILLISECONDS);
    }

    private synchronized void tick(double deltaTime) {
        world.update(deltaTime);
    }

    @PreDestroy
    public void stopSimulation() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public synchronized SimulationState getSimulationState() {
        var agent = world.getAgent();
        var foodList = world.getFood();

        var agentState = new SimulationState.AgentState(
                agent.getX(), agent.getY(), agent.getAngle()
        );

        var foodStates = foodList.stream()
                .map(f -> new SimulationState.FoodState(f.x(), f.y()))
                .toList();

        return new SimulationState(agentState, foodStates);
    }
}
