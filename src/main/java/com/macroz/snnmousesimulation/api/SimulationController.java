package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationEngine;
import com.macroz.snnmousesimulation.service.SimulationBenchmarkService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class SimulationController {

    private final SimulationEngine simulationEngine;
    private final SimulationBenchmarkService benchmarkService;

    public SimulationController(SimulationEngine simulationEngine, SimulationBenchmarkService benchmarkService) {
        this.simulationEngine = simulationEngine;
        this.benchmarkService = benchmarkService;
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
    public void setSpeed(@org.springframework.web.bind.annotation.RequestParam double multiplier) {
        simulationEngine.setSpeedMultiplier(multiplier);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSimulation() {
        return simulationEngine.subscribe();
    }

    @PostMapping("/benchmark")
    public BenchmarkResult benchmark(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "60000") double durationMs,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") double stepMs,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1000") double burnInMs,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int repeats,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") long baseSeed
    ) {
        return benchmarkService.run(durationMs, stepMs, burnInMs, repeats, baseSeed);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> handleInvalidBenchmarkParameters(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().build();
    }
}
