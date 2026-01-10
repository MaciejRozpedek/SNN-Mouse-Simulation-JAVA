package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.api.SimulationState;
import com.macroz.snnmousesimulation.world.World;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SimulationEngine {
    private static final long TICK_RATE_MS = 16; // ~60 FPS
    
    private final World world;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    public SimulationEngine() {
        this.world = new World(800, 600, 100);
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

        scheduler.scheduleAtFixedRate(() -> tick(TICK_RATE_MS), 0, TICK_RATE_MS, TimeUnit.MILLISECONDS);
    }

    private void tick(double deltaTime) {
        try {
            SimulationState state;
            synchronized (this) {
                world.update(deltaTime);
                state = getSimulationState();
            }
            broadcast(state);
        } catch (Exception e) {
            System.err.println("Simulation tick failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcast(SimulationState state) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("state")
                        .data(state));
            } catch (IOException e) {
                // Client disconnected, remove emitter
                emitters.remove(emitter);
            }
        }
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        
        emitters.add(emitter);
        return emitter;
    }

    @PreDestroy
    public void stopSimulation() {
        if (scheduler != null) {
            scheduler.shutdown();
        }

        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
    }

    private SimulationState getSimulationState() {
        var agent = world.getAgent();
        var foodList = world.getFood();

        var agentState = new SimulationState.AgentState(
                agent.getX(), agent.getY(), agent.getAngle()
        );

        var foodStates = foodList.stream()
                .map(f -> new SimulationState.FoodState(f.x(), f.y()))
                .toList();

        return new SimulationState(agentState, foodStates, System.currentTimeMillis());
    }
}
