# SNN learning progress

## Working protocol

Important code changes use a faster worker with a bounded write set. The main agent
waits for that implementation, then performs review, integration and tests before
committing. During configuration testing, three long-running mathematical agents
(Meitner, Hooke and Poincare) analyse dynamics, DA-STDP and experimental topology
on ultra reasoning. They communicate directly and through their separate
`raports/network-analysis-{name}.md` files. Their work does not block implementation
or routine tests; it is consumed when choosing the next experiments. Subagents do
not commit or push.

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

## 2026-07-13 - Headless benchmark endpoint

`POST /api/benchmark` runs independent worlds in a tight loop with no sleep, SSE,
per-tick DTO construction or JSON serialization. It accepts `durationMs`, `stepMs`,
`burnInMs`, `repeats` and `baseSeed`. Every repeat uses a deterministic but distinct
topology/weight seed and world seed, allowing the same seed range to be reused when
comparing two configurations.

The response contains per-run reward counts, reward trend between evaluation
halves, post-warmup path length, final firing rate, weight change and simulated to
wall-clock ratio. It also returns aggregate mean rewards, sample deviation and
other means. Input validation and a total-step limit prevent accidental runaway
requests. The interactive simulation remains unchanged.

The implementation-worker patch was reviewed by the main agent. The complete test
suite passed 22/22 tests. A packaged HTTP smoke test ran two deterministic 1000 ms
worlds in 373 ms overall (5.36 times real time including initialization), and an
invalid request returned HTTP 400. No SSE connection or interactive simulation
thread was involved.
