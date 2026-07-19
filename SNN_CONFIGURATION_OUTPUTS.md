# SNN output types

This document describes the concrete output types accepted by the `output_type`
property in the `outputs` section. The general configuration structure is
documented in [`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

## Table of contents

- [`POPULATION_DRIVE`](#population_drive)

## `POPULATION_DRIVE`

Moves the agent based on spikes from the selected neurons. All neurons control
forward speed. The first half turns the agent left and the second half turns it
right.

```yaml
- name: MotorControl
  output_type: POPULATION_DRIVE
  source_group: Cortex.Layer3
  source_type: RS
  params:
    speed: 2.0
    turn_rate: 90.0
```

Parameters:

- `speed` - maximum speed in meters per second; default `0.5`.
- `turn_rate` - maximum turn rate in degrees per second; default `90`.
