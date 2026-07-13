# Local SNN benchmark experiments

This directory contains local-only tooling. It is excluded through the local
`.git/info/exclude`; do not commit or push its generated results.

## Flow

1. Build the application: `mvnw.cmd -DskipTests package`.
2. Edit `experiments.yaml` and choose the baseline plus candidates.
3. Validate candidate YAML files:
   `py .local-snn-experiments/runner.py --generate-only`.
4. Run the experiments:
   `py .local-snn-experiments/runner.py`.
5. Inspect `results/<timestamp>/report.md`, `report.csv`, `report.json`, and
   each run's `benchmark.json`.

For every experiment the runner generates `candidate.yaml`, starts a separate
JVM with `--snn.config.path=<candidate>`, waits for the application, calls
`POST /api/benchmark`, saves the complete JSON response, and then terminates
that JVM. Benchmark settings are `duration_ms`, `step_ms`, `burn_in_ms`,
`repeats`, `base_seed`, and `learning_enabled` (default `true`); the runner
does not consume a live state stream. An experiment can override these through
`benchmark_settings`, allowing the same candidate configuration to be run with
learning enabled and disabled in one report.

## Candidate configuration

The normal form uses the manifest's `base_config` and a list of overrides:

```yaml
- name: stronger-sensory-current
  overrides:
    - path: inputs[0].params.max_current
      value: 12.0
```

An experiment may instead point to a mathematician-provided complete YAML:

```yaml
- name: mathematician-config
  config_file: ../path/to/complete.yaml
```

`config_file` and a non-empty `overrides` list are mutually exclusive. A
complete `config_file` is validated and copied byte-for-byte so its mathematical
header comments remain in the archived candidate. It does not inherit
`base_config`. Do not put an `overrides` key next to `config_file`.

## Report and verdict

The report exposes the benchmark summary fields
`meanEvaluationRewards`, `rewardStandardDeviation`, `meanRewardTrend`,
`meanFinalFiringRateHz`, `meanWeightDelta`, `meanPathLength`, and
`simulatedToWallRatio`, plus `learningEnabled` and reward delta versus baseline. Firing is considered
stable when it is within the explicit `firing_rate_min_hz` and
`firing_rate_max_hz` range (default 0.1–50 Hz) in at least
`minimum_stable_run_ratio` of runs (default 80%).

For candidates, the verdict order is: fewer than `minimum_repeats` repeats
(`insufficient_repeats`), unstable firing, no reward advantage over baseline,
no trend advantage, or `promising`. The baseline is marked `reference`.
The smoke manifest intentionally uses one repeat, so its candidate verdict is
expected to be `insufficient_repeats`.

`report.json` and `report.csv` are derived summaries; `benchmark.json` is the
authoritative full response from the endpoint. The manifest snapshot and
candidate are kept beside each run for reproducibility.
