# SNN benchmark report

Stable firing range: 0.1-50.0 Hz. Reward delta is relative to the baseline. The second table column is learningEnabled.

| Experiment | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight Δ | Path | Sim/wall | Δ rewards | Verdict |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| smoke-baseline | true | 1 | 0.000 | 0.000 | 0.000 | 7.510 | 1.00 | 0.000000 | 55.200 | 7.098 | 0.000 | reference |
| smoke-modified-current | true | 1 | 0.000 | 0.000 | 0.000 | 9.280 | 1.00 | 0.000000 | 52.800 | 7.471 | 0.000 | insufficient_repeats |
| smoke-without-learning | false | 1 | 0.000 | 0.000 | 0.000 | 7.510 | 1.00 | 0.000000 | 55.200 | 4.226 | 0.000 | insufficient_repeats |
