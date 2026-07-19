# SNN output types

This document describes the concrete output types accepted by the `output_type`
property in the `outputs` section. The general configuration structure is
documented in [`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

## Table of contents

- [`POPULATION_DRIVE`](#population_drive)

## `POPULATION_DRIVE`

Converts spikes from the selected source neurons into movement. The source
neurons are divided by their order into two motor populations: the first half
controls the left motor and the second half controls the right motor. An even
number of source neurons gives both populations the same size.

For every simulation step, motor power and movement are calculated as follows:

```text
left_motor_power  = left_spikes  * speed_per_spike
right_motor_power = right_spikes * speed_per_spike
forward_speed     = (left_motor_power + right_motor_power) / 2
rotation          = (left_motor_power - right_motor_power) * turn_factor
```

```yaml
- name: MotorControl
  output_type: POPULATION_DRIVE
  source_group: Cortex.Layer3
  source_type: RS
  params:
    speed_per_spike: 0.1
    turn_factor: 0.1
```

Parameters:

- `speed_per_spike` - motor power contributed by each spike; default `0.5`.
- `turn_factor` - scales the rotation caused by the difference between left and
  right motor power; default `0.03`.
