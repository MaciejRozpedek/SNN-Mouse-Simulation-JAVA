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
