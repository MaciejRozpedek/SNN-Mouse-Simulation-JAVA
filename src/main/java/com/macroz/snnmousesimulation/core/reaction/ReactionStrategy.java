package com.macroz.snnmousesimulation.core.reaction;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.world.AgentEffects;

public interface ReactionStrategy {
    void apply(InputFrame frame, AgentEffects effects);
}
