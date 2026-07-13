# SNN learning progress

## 2026-07-13 - Background activity and hunger drive

The project documentation and `SNN_CONFIGURATION.md` were reviewed before changing
the input layer. The existing factory-based architecture supports both additions
without modifying the SNN engine or world loop.

`TONIC_NOISE` now supplies configurable constant current plus independent Gaussian
noise. It also accepts an optional seed, which will be useful when comparing
network configurations under repeatable input noise.

`HUNGER_DRIVE` now reads a meal timer from the agent. It remains inactive for a
configurable delay, then linearly increases current until reaching a configured
maximum. The timer starts at simulation creation, advances with simulated time and
resets whenever food triggers the existing reward path. This prevents an idle
network from remaining silent indefinitely while keeping hunger separate from the
SNN engine.

Both strategies are registered in `InputStrategyFactory`, documented with YAML
parameters and covered by focused unit tests. An integration-style unit test also
checks that the agent's hunger clock advances and resets on reward.

## 2026-07-13 - Benchmark feasibility and reproducibility foundations

A local probe compared the normal 1 ms simulation cadence with a tight headless
loop. The current 300-neuron configuration advanced 10 seconds of simulated time
in about 0.44 seconds, approximately 22.8 times faster than real time. Constructing
the diagnostic state was not the dominant cost in this model; real-time sleeping
and sending up to 1000 large SSE states per second are the main reasons the UI path
is unsuitable for configuration sweeps. A small headless benchmark endpoint is
therefore justified, provided it reuses `World` and `SnnEngine`.

Seed-aware overloads were added to topology loading, weight generation and world
creation. They preserve random defaults for interactive simulation while allowing
benchmark runs to compare configurations on matching topology, weight and food
seeds. `World` also records the exact reward count and simulation timestamps, so
benchmarks no longer need to infer food events from dopamine samples.
