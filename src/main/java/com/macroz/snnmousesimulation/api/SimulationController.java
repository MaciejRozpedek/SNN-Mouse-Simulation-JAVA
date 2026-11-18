package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;

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

    @PostMapping("/reload")
    public void reloadSimulation() {
        simulationEngine.reloadSimulation();
    }

    @PostMapping("/speed")
    public void setSpeed(@RequestParam double multiplier) {
        simulationEngine.setSpeedMultiplier(multiplier);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSimulation() {
        return simulationEngine.subscribe();
    }

    @PostMapping("/config/brains/{name}")
    public void createConfig(@PathVariable String name, @RequestBody String yaml) throws IOException {
        simulationEngine.saveConfig(name, yaml, false);
    }

    @PutMapping("/config/brains/{name}")
    public void updateConfig(@PathVariable String name, @RequestBody String yaml) throws IOException {
        simulationEngine.saveConfig(name, yaml, true);
    }

    @ExceptionHandler(FileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void configAlreadyExists() {
    }
}
