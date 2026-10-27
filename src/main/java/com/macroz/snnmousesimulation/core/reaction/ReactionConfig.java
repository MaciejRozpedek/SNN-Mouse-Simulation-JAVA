package com.macroz.snnmousesimulation.core.reaction;

import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.Map;

public record ReactionConfig(
        String name,
        ConfigValue event,
        String reactionType,
        Map<String, ConfigValue> params
) {}
