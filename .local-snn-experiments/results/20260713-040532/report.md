# SNN benchmark report

Stable firing range: 0.1-50.0 Hz. Reward delta is relative to the baseline. The second table column is learningEnabled.

| Experiment | learningEnabled | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight delta | Path | Sim/wall | Reward delta | Verdict |
|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| poincare-002-frozen | false | 16 | 21.188 | 3.146 | -0.812 | 3.785 | 1.00 | 0.000000 | 1963.401 | 27.814 | 2.562 | no_learning_trend |
| poincare-001-frozen | false | 16 | 18.625 | 5.071 | 1.875 | 3.879 | 1.00 | 0.000000 | 1689.859 | 32.017 | 0.000 | reference |
