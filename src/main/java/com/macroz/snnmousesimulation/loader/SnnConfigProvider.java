package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.exception.InvalidConfigNameException;
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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class SnnConfigProvider {

    private static final Pattern CONFIG_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}(/[A-Za-z0-9_-]{1,64})*");

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

    public List<String> list() throws IOException {
        Path dir = Path.of(configsDir, "brains");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> dir.relativize(file).toString().replace(File.separatorChar, '/'))
                    .filter(path -> path.endsWith(".yaml"))
                    .map(path -> path.substring(0, path.length() - ".yaml".length()))
                    .filter(name -> CONFIG_NAME.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    public String read(String name) throws IOException {
        return Files.readString(configFile(name));
    }

    public void save(String name, String yaml, boolean overwrite) throws IOException {
        Path file = configFile(name);
        Files.createDirectories(file.getParent());
        if (overwrite) {
            Files.writeString(file, yaml);
        } else {
            Files.writeString(file, yaml, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        selectedConfig = file;
    }

    private Path configFile(String name) {
        if (!CONFIG_NAME.matcher(name).matches()) {
            throw new InvalidConfigNameException(name);
        }
        return Path.of(configsDir, "brains", name + ".yaml");
    }
}
