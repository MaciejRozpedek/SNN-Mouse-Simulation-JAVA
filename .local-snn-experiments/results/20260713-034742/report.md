# SNN benchmark report

Stable firing range: 0.1-50.0 Hz. Reward delta is relative to the baseline.

| Experiment | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight Δ | Path | Sim/wall | Δ rewards | Verdict |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| baseline | 12 | 2.417 | 2.234 | -1.917 | 0.220 | 0.75 | 0.062161 | 173.508 | 35.082 | 0.000 | reference |
| meitner-007-inhibition-minus3 | 12 | 2.000 | 2.412 | -1.500 | 0.539 | 0.83 | 0.057146 | 168.817 | 26.404 | -0.417 | no_reward_gain |
| meitner-009-inhibition-minus6 | 12 | 1.500 | 1.977 | -1.500 | 0.206 | 0.67 | 0.028762 | 165.329 | 20.753 | -0.917 | unstable_firing |
| meitner-008-inhibition-minus4 | 12 | 1.417 | 1.084 | -1.083 | 0.206 | 0.75 | 0.016948 | 176.412 | 23.211 | -1.000 | unstable_firing |
