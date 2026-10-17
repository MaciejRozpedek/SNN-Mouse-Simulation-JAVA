# SNN input types

This document describes the concrete input types accepted by the `input_type`
property in the `inputs` section. The general configuration structure is
documented in [`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

## Table of contents

- [`GAUSSIAN_VISION`](#gaussian_vision)
- [`TONIC_NOISE`](#tonic_noise)
- [`OU_NOISE`](#ou_noise)

## `GAUSSIAN_VISION`

Converts visible food into angle-selective currents. Neurons are ordered by
their preferred angle across the configured field of view. Current decreases
with distance and angular distance from a neuron's preferred direction. Signals
from multiple visible food items are added together.

```yaml
- name: VisionFront
  input_type: GAUSSIAN_VISION
  target_group: Cortex.Layer1
  target_type: RS
  params:
    fov: 120
    range: 200
    overlap_factor: 1.5
    max_current: 10.0
```

Parameters:

- `fov` - field of view in degrees; default `120`, must be positive.
- `range` - maximum food detection distance; default `200`, must be positive.
- `overlap_factor` - width of neighboring angular tuning curves; default `1.5`,
  must be positive.
- `max_current` - current produced by food at zero distance; default `10`.

## `TONIC_NOISE`

Provides a constant background current with independent Gaussian noise for each
target neuron and simulation step:

`current = base_current + noise_std * N(0, 1)`.

```yaml
- name: BackgroundDrive
  input_type: TONIC_NOISE
  target_group: Cortex.Layer2
  target_type: RS
  params:
    base_current: 2.0
    noise_std: 0.5
    seed: 123
```

Parameters:

- `base_current` - mean current; default `0`, must be finite.
- `noise_std` - Gaussian standard deviation; default `0`, must be finite and
  non-negative.
- `allow_negative` - if `false`, negative currents are clamped to `0`; default
  `false`.
- `seed` - optional numeric random seed. Omit it for a random stream or provide
  it for reproducible experiments.

## `OU_NOISE`

Provides a base current with temporally correlated Ornstein-Uhlenbeck noise.
Noise of neighboring target neurons (by index) can also be correlated:

`current = base_current + x`, where `x` is an OU process with time constant
`tau_ms` and stationary standard deviation `noise_std`.

```yaml
- name: BackgroundDrive
  input_type: OU_NOISE
  target_group: Cortex.Layer2
  target_type: RS
  params:
    base_current: 2.0
    noise_std: 0.5
    tau_ms: 20.0
    neighbor_correlation: 0.3
    allow_negative: false
    seed: 123
```

Parameters:

- `base_current` - mean current; default `0`, must be finite.
- `noise_std` - stationary standard deviation; default `0`, must be finite and
  non-negative.
- `tau_ms` - noise time constant in milliseconds; default `20`, must be
  positive.
- `neighbor_correlation` - noise correlation of neighboring neurons; default
  `0`, must be in range `[0, 1)`.
- `allow_negative` - if `false`, negative currents passed to neurons are clamped
  to `0` (the noise itself is not clamped); default `false`.
- `seed` - optional numeric random seed. Omit it for a random stream or provide
  it for reproducible experiments.
