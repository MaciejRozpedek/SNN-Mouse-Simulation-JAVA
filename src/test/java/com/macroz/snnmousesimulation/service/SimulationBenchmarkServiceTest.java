package com.macroz.snnmousesimulation.service;

import com.macroz.snnmousesimulation.core.IzhikevichParams;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.core.input.InputConfig;
import com.macroz.snnmousesimulation.core.output.OutputConfig;
import com.macroz.snnmousesimulation.loader.SnnConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationBenchmarkServiceTest {

    private final TinyConfigProvider tinyConfigProvider = new TinyConfigProvider();
    private final SimulationBenchmarkService service = new SimulationBenchmarkService(tinyConfigProvider);

    @Test
    void runsIndependentSeededWorldsWithoutRealtimePacing() {
        var result = service.run(10.0, 1.0, 2.0, 2, 100L);

        assertEquals(2, result.runs().size());
        assertEquals(100L, result.runs().get(0).seed());
        assertEquals(101L, result.runs().get(1).seed());
        assertEquals(List.of(100L, 101L), tinyConfigProvider.requestedSeeds);
        assertEquals(20.0, result.parameters().durationMs() * result.parameters().repeats());
        assertEquals(0.0, result.summary().meanEvaluationRewards());
        assertTrue(Double.isFinite(result.summary().simulatedToWallRatio()));
        assertTrue(result.summary().simulatedToWallRatio() > 0.0);
    }

    @Test
    void aggregatesRewardCountsTrendsAndSampleStandardDeviation() {
        var result = service.run(1.0, 1.0, 0.0, 2, 2L);

        var noRewardRun = result.runs().get(0);
        var rewardedRun = result.runs().get(1);

        assertAll(
                () -> assertEquals(0, noRewardRun.totalRewards()),
                () -> assertEquals(0, noRewardRun.evaluationRewards()),
                () -> assertEquals(0, noRewardRun.rewardTrend()),
                () -> assertEquals(1, rewardedRun.totalRewards()),
                () -> assertEquals(1, rewardedRun.evaluationRewards()),
                () -> assertEquals(0, rewardedRun.firstHalfRewards()),
                () -> assertEquals(1, rewardedRun.secondHalfRewards()),
                () -> assertEquals(1, rewardedRun.rewardTrend()),
                () -> assertEquals(0.5, result.summary().meanEvaluationRewards()),
                () -> assertEquals(Math.sqrt(0.5), result.summary().rewardStandardDeviation(), 1e-12),
                () -> assertEquals(0.5, result.summary().meanRewardTrend())
        );
    }

    @Test
    void excludesBurnInRewardsFromEvaluationMetrics() {
        var run = service.run(2.0, 1.0, 1.5, 1, 3L).runs().getFirst();

        assertAll(
                () -> assertEquals(1, run.totalRewards()),
                () -> assertEquals(0, run.evaluationRewards()),
                () -> assertEquals(0, run.firstHalfRewards()),
                () -> assertEquals(0, run.secondHalfRewards()),
                () -> assertEquals(0, run.rewardTrend())
        );
    }

    @Test
    void countsOnlyThePostBurnInFractionOfAPathStep() {
        var run = new SimulationBenchmarkService(new DrivenMotorConfigProvider())
                .run(3.0, 1.0, 1.5, 1, 3L).runs().getFirst();

        assertEquals(0.1, run.pathLength(), 1e-12);
    }

    @Test
    void reportsActivityMovementAndWeightMetrics() {
        var drivenService = new SimulationBenchmarkService(new DrivenMotorConfigProvider());

        var result = drivenService.run(10.0, 1.0, 2.0, 2, 100L);

        for (var run : result.runs()) {
            assertAll(
                    () -> assertEquals(0.4, run.pathLength(), 1e-9),
                    () -> assertEquals(5.0, run.finalFiringRateHz(), 1e-12),
                    () -> assertEquals(2.0, run.initialAverageWeight(), 1e-12),
                    () -> assertTrue(Double.isFinite(run.finalAverageWeight())),
                    () -> assertEquals(run.finalAverageWeight() - run.initialAverageWeight(), run.averageWeightDelta(), 1e-12),
                    () -> assertTrue(Double.isFinite(run.simulatedToWallRatio())),
                    () -> assertTrue(run.simulatedToWallRatio() > 0.0)
            );
        }

        assertAll(
                () -> assertEquals(0.4, result.summary().meanPathLength(), 1e-9),
                () -> assertEquals(5.0, result.summary().meanFinalFiringRateHz(), 1e-12),
                () -> assertTrue(Double.isFinite(result.summary().meanWeightDelta()))
        );
    }

    @Test
    void recordsLearningSettingAndFreezesWeightsWhenDisabled() {
        var result = new SimulationBenchmarkService(new DrivenMotorConfigProvider())
                .run(10.0, 1.0, 2.0, 1, 100L, false);

        assertEquals(false, result.parameters().learningEnabled());
        assertEquals(0.0, result.runs().getFirst().averageWeightDelta(), 1e-12);
    }

    @Test
    void rejectsUnsafeOrNumericallyInvalidParameters() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(0, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(Double.NaN, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(Double.POSITIVE_INFINITY, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(3_600_001, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 0, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, Double.NaN, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, Double.POSITIVE_INFINITY, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 2, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, -1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, Double.NaN, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, Double.POSITIVE_INFINITY, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, 10, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, 0, 0, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(10, 1, 0, 101, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.run(1, 1e-9, 0, 1, 1))
                , () -> assertThrows(IllegalArgumentException.class,
                        () -> service.run(1, 1, 0, 2, Long.MAX_VALUE))
        );
    }

    @Test
    void resultOwnsAnImmutableCopyOfItsRuns() {
        var runs = new ArrayList<com.macroz.snnmousesimulation.api.BenchmarkResult.Run>();
        var result = new com.macroz.snnmousesimulation.api.BenchmarkResult(
                new com.macroz.snnmousesimulation.api.BenchmarkResult.Parameters(1, 1, 0, 1, 1),
                new com.macroz.snnmousesimulation.api.BenchmarkResult.Summary(0, 0, 0, 0, 0, 0, 0, 0),
                runs
        );
        runs.add(null);

        assertEquals(0, result.runs().size());
        assertThrows(UnsupportedOperationException.class, () -> result.runs().add(null));
    }

    private static class TinyConfigProvider extends SnnConfigProvider {
        private final List<Long> requestedSeeds = new ArrayList<>();

        @Override
        public SnnNetworkData loadConfig(long seed) {
            requestedSeeds.add(seed);
            return new SnnNetworkData(
                    List.of(new IzhikevichParams(0.02, 0.2, -65.0, 8.0, -70.0, -14.0)),
                    new int[]{0},
                    new double[]{-70.0},
                    new double[]{-14.0},
                    new int[][]{new int[0]},
                    new double[][]{new double[0]},
                    List.of(),
                    List.of()
            );
        }
    }

    private static class DrivenMotorConfigProvider extends SnnConfigProvider {
        @Override
        public SnnNetworkData loadConfig(long seed) {
            return new SnnNetworkData(
                    List.of(new IzhikevichParams(0.02, 0.2, -65.0, 8.0, -70.0, -14.0)),
                    new int[]{0, 0},
                    new double[]{-70.0, -70.0},
                    new double[]{-14.0, -14.0},
                    new int[][]{new int[]{1}, new int[0]},
                    new double[][]{new double[]{2.0}, new double[0]},
                    List.of(new InputConfig(
                            "TonicDrive",
                            "TONIC_NOISE",
                            new int[]{0, 1},
                            Map.of("base_current", 100.0, "noise_std", 0.0, "seed", 1L)
                    )),
                    List.of(new OutputConfig(
                            "MotorControl",
                            "POPULATION_DRIVE",
                            new int[]{0, 1},
                            Map.of("speed_per_spike", 0.1, "turn_factor", 0.1)
                    ))
            );
        }
    }
}
