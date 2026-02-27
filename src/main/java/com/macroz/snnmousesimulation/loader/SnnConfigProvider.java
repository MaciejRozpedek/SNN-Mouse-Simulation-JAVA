package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Component
public class SnnConfigProvider {

    @Value("${snn.config.path:src/main/resources/config/SNNConfig.yaml}")
    private String configPath;

    public SnnNetworkData loadConfig() {
        File externalFile = new File(configPath);

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
            throw new RuntimeException("Failed to load SNN configuration from " + configPath, e);
        }
    }
}
