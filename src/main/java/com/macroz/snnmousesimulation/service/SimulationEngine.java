package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.api.SimulationState;
import com.macroz.snnmousesimulation.world.World;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SimulationEngine {
    private static final long TICK_RATE_MS = 16; 

    private volatile double speedMultiplier = 1.0;
    private volatile boolean running = false;
    private Thread simulationThread;

    private final World world;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SimulationEngine() {
        this.world = new World(1000, 800, 100);
    }

    public void setSpeedMultiplier(double multiplier) {
        this.speedMultiplier = Math.max(0.1, Math.min(1.0, multiplier));
    }

    public void startSimulation() {
        if (running) return;

        running = true;
        simulationThread = new Thread(this::runLoop, "Simulation-Loop");
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    private void runLoop() {
        while (running) {
            long startTime = System.currentTimeMillis();

            tick(TICK_RATE_MS);

            long targetInterval = (long) (TICK_RATE_MS / speedMultiplier);
            long processTime = System.currentTimeMillis() - startTime;
            long sleepTime = targetInterval - processTime;

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
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
        running = false;
        if (simulationThread != null) {
            try {
                simulationThread.join(1000);
            } catch (InterruptedException e) {
                System.err.println("Failed to join simulation thread");
            }
            simulationThread = null;
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
