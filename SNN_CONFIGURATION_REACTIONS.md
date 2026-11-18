# SNN reaction types

This document describes the concrete reaction types accepted by the
`reaction_type` property in the `reactions` section. The general configuration
structure is documented in [`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

## Table of contents

- [`FOOD_REWARD`](#food_reward)

## `FOOD_REWARD`

Releases a fixed amount of dopamine every time the agent eats food.

```yaml
- name: FoodReward
  reaction_type: FOOD_REWARD
  params:
    amount: 5.0
```

Parameters:

- `amount` - released dopamine; required.
