# SNN benchmark report

Stable firing range: 0.1-50.0 Hz. Reward delta is relative to the baseline. The second table column is learningEnabled.

| Experiment | learningEnabled | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight delta | Path | Sim/wall | Reward delta | Verdict |
|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| poincare-001-learning | true | 30 | 21.467 | 4.408 | 2.400 | 3.890 | 1.00 | 0.190293 | 1744.473 | 26.638 | 2.067 | promising |
| poincare-001-frozen | false | 30 | 19.400 | 4.753 | 0.733 | 3.743 | 1.00 | 0.000000 | 1690.788 | 28.353 | 0.000 | reference |
