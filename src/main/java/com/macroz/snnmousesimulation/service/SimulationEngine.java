package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.world.World;
import jakarta.annotation.PostConstruct;
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
        this.world = new World(800, 600, 3);
    }

    @PostConstruct
    public void startSimulation() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Simulation-Loop");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::tick, 0, 16, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        world.update();
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
