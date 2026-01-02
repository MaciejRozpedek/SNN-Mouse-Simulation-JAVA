package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationEngine;
import com.macroz.snnmousesimulation.world.World;
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

    @GetMapping("/state")
    public World getSimulationState() {
        return simulationEngine.getWorldState();
    }
}
