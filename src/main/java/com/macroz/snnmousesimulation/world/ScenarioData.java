package com.macroz.snnmousesimulation.world;

import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.world.rule.WorldRuleConfig;

import java.util.List;

public record ScenarioData(
        WorldParams world,
        List<WorldRuleConfig> rules,
        List<AgentData> agents
) {
    public record AgentData(String name, SnnNetworkData brain, double startX, double startY, double startAngle) {}
}
