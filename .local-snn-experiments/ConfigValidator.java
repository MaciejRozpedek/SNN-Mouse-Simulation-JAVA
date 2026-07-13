import com.macroz.snnmousesimulation.loader.NetworkTopologyLoader;
import com.macroz.snnmousesimulation.world.Agent;

import java.io.FileInputStream;
import java.nio.file.Path;

public class ConfigValidator {
    public static void main(String[] args) throws Exception {
        for (String argument : args) {
            Path path = Path.of(argument);
            try (var input = new FileInputStream(path.toFile())) {
                var network = new NetworkTopologyLoader(1L).load(input);
                new Agent(0.0, 0.0, network);
                int synapses = 0;
                for (int[] targets : network.synapticTargets()) {
                    synapses += targets.length;
                }
                System.out.printf("OK %s neurons=%d synapses=%d%n", path, network.neuronToTypeId().length, synapses);
            }
        }
    }
}
