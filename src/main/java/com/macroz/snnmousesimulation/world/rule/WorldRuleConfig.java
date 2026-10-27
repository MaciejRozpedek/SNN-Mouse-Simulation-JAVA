package com.macroz.snnmousesimulation.world.rule;

import com.macroz.snnmousesimulation.utility.ConfigValue;

import java.util.Map;

public record WorldRuleConfig(
        String name,
        String ruleType,
        Map<String, ConfigValue> params
) {}
