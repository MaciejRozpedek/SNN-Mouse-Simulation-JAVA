package com.macroz.snnmousesimulation.service;

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
        this.world = new World(800, 600, 200);
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

        scheduler.scheduleAtFixedRate(() -> tick(16), 0, 16, TimeUnit.MILLISECONDS);
    }

    private void tick(double deltaTime) {
        world.update(deltaTime);
    }

    @PreDestroy
    public void stopSimulation() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public World getWorldState() {
        return world;
    }
}
