package com.macroz.snnmousesimulation.core.reaction.concrete;

import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.reaction.ReactionStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentEffects;
import com.macroz.snnmousesimulation.world.AgentEvent;
import com.macroz.snnmousesimulation.world.FoodEaten;

public class FoodRewardStrategy implements ReactionStrategy {

    private final double amount;

    private FoodRewardStrategy(double amount) {
        this.amount = ParamChecks.finite("amount", amount);
    }

    public static FoodRewardStrategy create(ConfigParams params) {
        return new FoodRewardStrategy(params.getRequiredDouble("amount"));
    }

    @Override
    public void apply(InputFrame frame, AgentEffects effects) {
        for (AgentEvent event : frame.events()) {
            if (event instanceof FoodEaten) {
                effects.injectDopamine(amount);
            }
        }
    }
}
