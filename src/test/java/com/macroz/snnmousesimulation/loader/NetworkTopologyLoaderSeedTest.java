package com.macroz.snnmousesimulation.loader;

import org.junit.jupiter.api.Test;

import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class NetworkTopologyLoaderSeedTest {

    private static final String CONFIG_PATH = "src/main/resources/config/SNNConfig.yaml";

    @Test
    void sameSeedProducesSameTopologyAndWeights() throws Exception {
        var first = loadWithSeed(1234L);
        var second = loadWithSeed(1234L);

        assertArrayEquals(first.synapticTargets(), second.synapticTargets());
        assertArrayEquals(first.synapticWeights(), second.synapticWeights());
    }

    private com.macroz.snnmousesimulation.core.SnnNetworkData loadWithSeed(long seed) throws Exception {
        try (var input = new FileInputStream(CONFIG_PATH)) {
            return new NetworkTopologyLoader(seed).load(input);
        }
    }
}
