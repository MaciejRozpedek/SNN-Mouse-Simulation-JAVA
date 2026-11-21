package com.macroz.snnmousesimulation.loader;

import com.macroz.snnmousesimulation.exception.InvalidConfigNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnnConfigProviderTest {

    private static String network(int neurons) {
        return """
                plasticity:
                  stdp: {tau_plus_ms: 20, tau_minus_ms: 20, a_plus: 0.1, a_minus: 0.15, tau_eligibility_ms: 400, max_weight: 30}
                  dopamine: {tau_ms: 50, tau_baseline_ms: 7000, initial_baseline: 0}
                neuron_types:
                  RS: {a: 0.02, b: 0.2, c: -65.0, d: 8.0, v0: -70.0, u0: -14.0}
                groups:
                  - {name: G, neurons: [{type: RS, count: %d}]}
                connections: []
                """.formatted(neurons);
    }

    private static SnnConfigProvider provider(Path configsDir) {
        SnnConfigProvider provider = new SnnConfigProvider();
        ReflectionTestUtils.setField(provider, "configsDir", configsDir.toString());
        return provider;
    }

    @Test
    void savesAndSelectsConfig(@TempDir Path dir) throws Exception {
        SnnConfigProvider provider = provider(dir);

        provider.save("Mouse", network(1), false);
        assertEquals(1, provider.loadConfig().neuronToTypeId().length);

        assertThrows(FileAlreadyExistsException.class, () -> provider.save("Mouse", network(2), false));
        assertEquals(1, provider.loadConfig().neuronToTypeId().length);

        provider.save("Mouse", network(2), true);
        assertEquals(2, provider.loadConfig().neuronToTypeId().length);
    }

    @Test
    void listsAndReadsSavedConfigs(@TempDir Path dir) throws Exception {
        SnnConfigProvider provider = provider(dir);
        assertEquals(List.of(), provider.list());

        provider.save("examples/Rat", network(2), false);
        provider.save("Mouse", network(1), false);

        assertEquals(List.of("Mouse", "examples/Rat"), provider.list());
        assertEquals(network(2), provider.read("examples/Rat"));
        assertThrows(NoSuchFileException.class, () -> provider.read("Cat"));
    }

    @Test
    void rejectsNamesOutsideTheFolder(@TempDir Path dir) {
        SnnConfigProvider provider = provider(dir.resolve("configs"));

        var error = assertThrows(InvalidConfigNameException.class,() -> provider.save("../../Mouse", network(1), false));
        assertTrue(error.getMessage().contains("'../../Mouse'"), error.getMessage());
        assertFalse(Files.exists(dir.resolve("Mouse.yaml")));
    }
}
