package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.world.ScenarioData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ScenarioProvider {

    @Value("${snn.scenario.path:src/main/resources/config/Scenario.yaml}")
    private String scenarioPath;

    public ScenarioData loadScenario() {
        Path externalFile = Path.of(scenarioPath);

        try {
            if (Files.exists(externalFile)) {
                Path directory = externalFile.toAbsolutePath().getParent();
                try (InputStream is = Files.newInputStream(externalFile)) {
                    return new ScenarioLoader().load(is, name -> openFile(directory.resolve(name)));
                }
            } else {
                try (InputStream is = getClass().getResourceAsStream("/config/Scenario.yaml")) {
                    if (is == null) throw new RuntimeException("Default scenario not found in resources!");
                    return new ScenarioLoader().load(is, name -> getClass().getResourceAsStream("/config/" + name));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load scenario from " + scenarioPath, e);
        }
    }

    private static InputStream openFile(Path path) {
        try {
            return Files.exists(path) ? Files.newInputStream(path) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
