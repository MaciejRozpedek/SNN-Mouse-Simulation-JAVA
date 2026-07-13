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

Each mathematical agent also owns `raports/templates-{name}/`. Files use ordered
names such as `001-short-description.yaml`. Every file must be a complete benchmark-
ready configuration and begin with YAML comments documenting the hypothesis,
mathematical mechanism, difference from baseline, expected result, measured
metrics and rejection criterion. Agents inspect one another's reports and template
directories to cross-review ideas and avoid duplicate experiments. They never edit
the production `SNNConfig.yaml` directly.

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

## 2026-07-13 - Separate directional motor outputs

The motor layer can now be mapped explicitly with `FORWARD_DRIVE`, `TURN_LEFT` and
`TURN_RIGHT`. Each strategy converts every spike in its assigned population into a
configurable displacement or signed rotation. This removes the hidden first-half /
second-half convention from `POPULATION_DRIVE` and lets experiment configurations
connect sensory channels to three named motor populations independently.

A faster implementation worker created the strategies and focused tests. Main-
agent review tightened the public classes, verified exact factory types, corrected
the output schema documentation and documented binding-order semantics.

## 2026-07-13 - Headless local experiment runner

The local, Git-excluded runner now starts one JVM per candidate and calls
`POST /api/benchmark` instead of consuming 1000 SSE states per second. A candidate
can be built from the baseline plus declarative overrides or loaded as a complete
YAML from a mathematician's template directory. Complete templates are validated
and copied byte-for-byte so their hypothesis comments remain attached to results.

Each candidate is evaluated on a shared seed range (10 repeats by default), and
the report compares reward mean/deviation/trend, firing rate, stable-run ratio,
weight change, path length and simulated-to-wall ratio against the baseline. Main-
agent review added strict manifest paths, configurable endpoint timeout and an 80%
per-run firing-stability gate. Python compilation, candidate generation, an HTTP
smoke comparison and comment-preservation check all passed.

The first mathematical batch contains 12 complete templates: Hooke 3, Meitner 6
and Poincare 3. A local Java validator loaded every YAML through
`NetworkTopologyLoader`, instantiated its `Agent` and all configured input/output
strategies, and counted the resulting topology. All 12 templates passed.

## 2026-07-13 - Topographic policy screening

Poincare 001/002/003 and the original baseline were first screened for 30 seconds
on 10 paired seeds. All topographic variants improved mean evaluation rewards;
003 was weaker than 001, while the hunger addition in 002 gave only a small gain
over 001. Baseline, 001 and 002 then ran for 180 seconds on 16 paired seeds with a
30-second burn-in.

| configuration | rewards mean | reward trend | stable runs | path | paired median vs baseline |
|---|---:|---:|---:|---:|---:|
| baseline | 0.750 | -0.375 | 0.44 | 69.09 | 0 |
| Poincare 001 | 22.688 | +2.938 | 1.00 | 1739.54 | +21 |
| Poincare 002 | 22.875 | +0.750 | 1.00 | 1959.38 | +22 |

Both topographic policies beat baseline on all 16 seeds. The difference between
002 and 001 was small (mean +0.188, median +1), so hunger mainly increased travel
rather than food efficiency. This establishes a much better policy but does not
yet establish learning, because hard-wired routing alone may explain the result.

## 2026-07-13 - Frozen-learning control

`SnnEngine` now exposes a benchmark control that freezes STDP, eligibility traces
and weights while preserving neuron dynamics and dopamine decay. The benchmark
accepts `learningEnabled`, records it in results and the local runner can override
it per candidate. Changing mode clears transient learning traces to prevent stale
credit from leaking across a toggle. Main-agent review and the complete 31-test
suite passed; an independent true/false HTTP smoke test confirmed exactly zero
weight change in the disabled run. The next experiment compares Poincare 001/002
with identical seeds under learning enabled and frozen.
