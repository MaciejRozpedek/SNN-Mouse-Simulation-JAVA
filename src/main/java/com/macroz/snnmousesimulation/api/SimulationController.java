package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationEngine simulationEngine;

    public SimulationController(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    @PostMapping("/start")
    public void startSimulation() {
        simulationEngine.startSimulation();
    }

    @PostMapping("/stop")
    public void stopSimulation() {
        simulationEngine.stopSimulation();
    }

    @PostMapping("/speed")
    public void setSpeed(@org.springframework.web.bind.annotation.RequestParam double multiplier) {
        simulationEngine.setSpeedMultiplier(multiplier);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSimulation() {
        return simulationEngine.subscribe();
    }
}
