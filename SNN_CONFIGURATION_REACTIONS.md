# SNN reaction types

This document describes the event types accepted by the `event` property and the
concrete reaction types accepted by the `reaction_type` property in the
`reactions` section. The general configuration structure is documented in
[`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

## Table of contents

- [Events](#events)
- [`INJECT_DOPAMINE`](#inject_dopamine)

## Events

Events are posted to an agent by world rules (see
[`SCENARIO_CONFIGURATION.md`](SCENARIO_CONFIGURATION.md)).

- `FOOD_EATEN` - the agent ate food.
- `BOUNDARY_HIT` - the agent hit the world boundary; carries the side of the boundary.

## `INJECT_DOPAMINE`

Injects a fixed amount of dopamine for every event. Accepts every event type.

```yaml
- name: FoodReward
  event: FOOD_EATEN
  reaction_type: INJECT_DOPAMINE
  params:
    amount: 5.0
```

Parameters:

- `amount` - injected dopamine; required, must be finite (negative values punish).
