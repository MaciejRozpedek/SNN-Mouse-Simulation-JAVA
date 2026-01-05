# Configuration File Schema

This document describes the configuration format for defining a neural network model. It is organized into three main sections: `neuron_types`, `groups`, and `connections`.

## `neuron_types`

This section defines the parameter sets for different types of neurons based on the Izhikevich model.

### Structure

```yaml
neuron_types:
  <type_name (string)>:
    a: <double>
    b: <double>
    c: <double>
    d: <double>
    v0: <double>
    u0: <double>
```

### Parameters

*   `<type_name>`: A unique name for the neuron type (e.g., "RS", "FS").
    *   `a` (`<double>`): The timescale of the recovery variable `u`.
    *   `b` (`<double>`): The sensitivity of `u` to subthreshold fluctuations of the membrane potential `v`.
    *   `c` (`<double>`): The after-spike reset value of the membrane potential `v`.
    *   `d` (`<double>`): The after-spike reset increment of the recovery variable `u`.
    *   `v0` (`<double>`): The initial value for the membrane potential `v`.
    *   `u0` (`<double>`): The initial value for the recovery variable `u`.

## `groups`

This section defines the hierarchical structure of neuron populations. Groups can be nested to create complex arrangements like layers or columns. A group can contain either `subgroups` or `neurons`, but not both.

### Structure

```yaml
groups:
  - name: <string>
    # --- Option 1: A group containing other groups ---
    subgroups:
      - name: <string>
        # ... (recursive structure)

    # --- Option 2: A leaf group containing neurons ---
    neurons:
      - type: <string>
        count: <integer>
```

### Properties

*   `name` (`<string>`): A unique identifier for the group (e.g., "Layer1", "Sensory").
*   `subgroups` (Optional `list`): A list of nested group objects, following the same recursive structure.
*   `neurons` (Optional `list`): Defines the composition of a "leaf" group.
    *   `type` (`<string>`): The name of the neuron type, which must correspond to a key in the `neuron_types` map.
    *   `count` (`<integer>`): The number of neurons of this specified type to create in the group (must be >= 0).

## `connections`

This section is a list of rules that define how different groups of neurons are connected.

### Structure

```yaml
connections:
  - from: <string>
    to: <string>
    from_type: <string>
    to_type: <string>
    exclude_self: <boolean>
    weight:
      # ... (exactly one weight rule)
    rule:
      type: <string>
      allow_autapses: <boolean>
      # ... (topology-specific parameters)
```

### General Properties

*   `from` (`<string>`): An identifier or pattern for the source group (doesn't have to be a leaf). A group's path indicates its nested location, with names separated by dots (e.g., `Sensory.Vision.Excitatory`). Patterns can use wildcards like `[i]` (where `i` is an integer) to match a single name in this path. A given wildcard (e.g., `[0]`) must represent the same name across all its occurrences in both the `from` and `to` patterns.
*   `to` (`<string>`): An identifier or pattern for the target group (doesn't have to be a leaf), following the same syntax and rules as the `from` field.
*   `from_type` (`<string>`): Specifies which neuron types within the source group will project connections. This can be a specific type (e.g., "RS") or `"all"`.
*   `to_type` (`<string>`): Specifies which neuron types within the target group will receive connections. This can be a specific type (e.g., "FS") or `"all"`.
*   `exclude_self` (Optional `<boolean>`, default `false`): If `true`, prevents a group from connecting to itself when patterns or wildcards are used.

---

### Weight Definition (`weight`)

Defines the synaptic weight for the connections. **Exactly one** of the following options must be used.

*   `fixed: <double>`: All connections have this single, constant weight.
*   `uniform`: Draws weights from a uniform distribution.
    *   `min` (`<double>`): The minimum weight.
    *   `max` (`<double>`): The maximum weight.
*   `normal`: Draws weights from a normal (Gaussian) distribution.
    *   `mean` (`<double>`): The mean of the distribution.
    *   `std` (`<double>`): The standard deviation of the distribution.

---

### Connection Topology (`rule`)

Defines the connection pattern. **Exactly one** of the following topology rules must be chosen.


*   **Common Property:** `allow_autapses` (Optional `<boolean>`, default `false`): If `true`, allows a neuron to form a connection with itself (autapse). If `false`, source neuron `i` will never connect to target neuron `i`.

**Topology rules:**

*   `type: "all_to_all"`: Every neuron in the source group connects to every neuron in the target group.
*   `type: "one_to_one"`: Neuron `i` in the source connects to neuron `i` in the target. Requires source and target groups to have the same size.
*   `type: "probabilistic"`: Each potential connection is created with a given probability.
    *   `probability` (`<double>`): The connection probability, between 0.0 and 1.0.
*   `type: "fixed_out_degree"`: Each source neuron connects to a fixed number of randomly chosen target neurons.
    *   `count` (`<integer>`): The exact number of targets for each source neuron.
*   `type: "fixed_in_degree"`: Each target neuron receives connections from a fixed number of randomly chosen source neurons.
    *   `count` (`<integer>`): The exact number of sources for each target neuron.
