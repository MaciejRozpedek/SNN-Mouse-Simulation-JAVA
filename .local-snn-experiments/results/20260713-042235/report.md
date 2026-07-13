# SNN benchmark report

Stable firing range: 0.0-1000.0 Hz. Reward delta is relative to the baseline. The second table column is learningEnabled.

| Experiment | learningEnabled | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight delta | Path | Sim/wall | Reward delta | Verdict |
|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| poincare-001 | true | 1 | 0.000 | 0.000 | 0.000 | 3.603 | 1.00 | 0.000000 | 10.000 | 3.637 | 0.000 | reference |
| meitner-010-association-3p7 | true | 1 | 0.000 | 0.000 | 0.000 | 0.937 | 1.00 | 0.000000 | 1.750 | 3.202 | 0.000 | no_reward_gain |
| meitner-011-association-3p6 | true | 1 | 0.000 | 0.000 | 0.000 | 0.920 | 1.00 | 0.000000 | 1.750 | 4.010 | 0.000 | no_reward_gain |
| meitner-012-association-3p5 | true | 1 | 0.000 | 0.000 | 0.000 | 0.943 | 1.00 | 0.000000 | 1.850 | 3.570 | 0.000 | no_reward_gain |
