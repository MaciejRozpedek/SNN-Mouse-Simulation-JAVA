package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

@Component
public class SnnConfigProvider {

    private static final Pattern CONFIG_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Value("${snn.config.path:src/main/resources/config/SNNConfig.yaml}")
    private String configPath;

    @Value("${snn.configs.dir:configs}")
    private String configsDir;

    private Path selectedConfig;

    public SnnNetworkData loadConfig() {
        File externalFile = selectedConfig != null ? selectedConfig.toFile() : new File(configPath);

        try {
            if (externalFile.exists()) {
                try (InputStream is = new FileInputStream(externalFile)) {
                    return new NetworkTopologyLoader().load(is);
                }
            } else {
                try (InputStream is = getClass().getResourceAsStream("/config/SNNConfig.yaml")) {
                    if (is == null) throw new RuntimeException("Default config not found in resources!");
                    return new NetworkTopologyLoader().load(is);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SNN configuration from " + externalFile, e);
        }
    }

    public SnnNetworkData parse(String yaml) {
        return new NetworkTopologyLoader().load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    public void save(String name, String yaml, boolean overwrite) throws IOException {
        if (!CONFIG_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid config name '" + name + "'. Use 1-64 letters, digits, '_' or '-'.");
        }
        Path file = Path.of(configsDir, "brains", name + ".yaml");
        Files.createDirectories(file.getParent());
        if (overwrite) {
            Files.writeString(file, yaml);
        } else {
            Files.writeString(file, yaml, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        selectedConfig = file;
    }
}
