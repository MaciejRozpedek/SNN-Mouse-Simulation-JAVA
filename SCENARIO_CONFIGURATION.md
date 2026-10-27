# Scenario File Schema

This document describes the scenario file, which defines the world, its rules and
the agents living in it. It is organized into three main sections: `world`,
`rules` and `agents`. The network controlling an agent is defined in a separate
brain file, see [`SNN_CONFIGURATION.md`](SNN_CONFIGURATION.md).

The scenario is read from the path in the `snn.scenario.path` application property
(default `src/main/resources/config/Scenario.yaml`).

## `world`

```yaml
world:
  width: <double>
  height: <double>
```

*   `width` (`<double>`): Width of the world (must be positive).
*   `height` (`<double>`): Height of the world (must be positive).

## `rules`

A list of mechanics of the world, e.g. eating or boundaries. After all agents have
acted in a simulation step, the rules are applied in the order of this list. Rules
may post events to agents (e.g. `FOOD_EATEN`).

```yaml
rules:
  - name: <string>
    rule_type: <string>
    params:
      # ... (rule-specific parameters)
```

*   `name` (`<string>`): A unique name for the rule.
*   `rule_type` (`<string>`): The rule type (e.g. "FOOD").
*   `params`: A map of rule-specific parameters.

### `BOUNCE_BOUNDARY`

Reflects agents that crossed the world boundary and posts a `BOUNDARY_HIT` event
to them. Has no parameters.

### `FOOD`

Keeps a fixed amount of food in the world. Food within reach of an agent is eaten,
which posts a `FOOD_EATEN` event to that agent, and new food appears at a random
position out of reach of all agents.

```yaml
- name: Food
  rule_type: FOOD
  params:
    count: 100
    eat_radius: 30.0
```

- `count` - amount of food in the world; default `100`, must be non-negative.
- `eat_radius` - distance at which an agent eats food; default `30`, must be positive.

## `agents`

The agents living in the world. Only one agent is supported for now.

```yaml
agents:
  - name: <string>
    brain: <string>
    start:
      x: <double>
      y: <double>
      angle: <double>
```

*   `name` (`<string>`): A unique name for the agent.
*   `brain` (`<string>`): Path to the brain file of the agent, relative to the scenario file.
*   `start`: Initial position and heading of the agent.
    *   `x`, `y` (`<double>`): Initial position (required).
    *   `angle` (`<double>`): Initial heading in radians (default `0`).
