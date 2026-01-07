package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationEngine simulationEngine;

    public SimulationController(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    @GetMapping("/start")
    public void startSimulation() {
        simulationEngine.startSimulation();
    }

    @GetMapping("/stop")
    public void stopSimulation() {
        simulationEngine.stopSimulation();
    }

    @GetMapping("/state")
    public SimulationState getSimulationState() {
        return simulationEngine.getSimulationState();
    }
}
