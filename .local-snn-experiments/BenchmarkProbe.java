import com.macroz.snnmousesimulation.api.SimulationState;
import com.macroz.snnmousesimulation.loader.NetworkTopologyLoader;
import com.macroz.snnmousesimulation.world.World;

import java.io.FileInputStream;
import java.nio.file.Path;

public class BenchmarkProbe {
    private static final Path CONFIG = Path.of("src/main/resources/config/SNNConfig.yaml");
    private static final int WARMUP_STEPS = 2_000;
    private static final int MEASURED_STEPS = 10_000;

    public static void main(String[] args) throws Exception {
        run(false, WARMUP_STEPS);
        run(true, WARMUP_STEPS);
        long headlessNs = run(false, MEASURED_STEPS);
        long fullStateNs = run(true, MEASURED_STEPS);
        System.out.printf("headless_ms=%.3f%n", headlessNs / 1_000_000.0);
        System.out.printf("full_state_ms=%.3f%n", fullStateNs / 1_000_000.0);
        System.out.printf("full_state_overhead=%.2fx%n", (double) fullStateNs / headlessNs);
        System.out.printf("headless_simulated_to_wall=%.2fx%n", MEASURED_STEPS / (headlessNs / 1_000_000.0));
    }

    private static long run(boolean buildState, int steps) throws Exception {
        var loader = new NetworkTopologyLoader();
        var input = new FileInputStream(CONFIG.toFile());
        var world = new World(1_000, 800, 100, loader.load(input));
        input.close();
        long started = System.nanoTime();
        for (int i = 0; i < steps; i++) {
            world.update(1.0);
            if (buildState) {
                var agent = world.getAgent();
                var agentState = new SimulationState.AgentState(agent.getX(), agent.getY(), agent.getAngle());
                var food = world.getFood().stream()
                        .map(item -> new SimulationState.FoodState(item.x(), item.y()))
                        .toList();
                new SimulationState(world.getSimulationTimeMs(), agentState, food, agent.getSnnDiagnostics());
            }
        }
        return System.nanoTime() - started;
    }
}
