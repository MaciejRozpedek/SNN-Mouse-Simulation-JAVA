package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.exception.InvalidConfigNameException;
import com.macroz.snnmousesimulation.service.SimulationEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.util.List;

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

    @GetMapping("/config/brains")
    public List<String> listConfigs() throws IOException {
        return simulationEngine.listConfigs();
    }

    @GetMapping(value = "/config/brains/{*name}", produces = "application/yaml;charset=UTF-8")
    public String readConfig(@PathVariable String name) throws IOException {
        return simulationEngine.readConfig(configName(name));
    }

    @PostMapping("/config/brains/{*name}")
    public void createConfig(@PathVariable String name, @RequestBody String yaml) throws IOException {
        simulationEngine.saveConfig(configName(name), yaml, false);
    }

    @PutMapping("/config/brains/{*name}")
    public void updateConfig(@PathVariable String name, @RequestBody String yaml) throws IOException {
        simulationEngine.saveConfig(configName(name), yaml, true);
    }

    @ExceptionHandler(FileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void configAlreadyExists() {
    }

    @ExceptionHandler(InvalidConfigNameException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidConfigName(InvalidConfigNameException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchFileException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void configNotFound() {
    }

    private static String configName(String urlPath) {
        return StringUtils.trimLeadingCharacter(urlPath, '/');
    }
}
