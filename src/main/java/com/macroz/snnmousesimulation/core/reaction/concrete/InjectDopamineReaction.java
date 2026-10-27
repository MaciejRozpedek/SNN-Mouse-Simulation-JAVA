package com.macroz.snnmousesimulation.core.reaction.concrete;

import com.macroz.snnmousesimulation.core.reaction.ReactionStrategy;
import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.utility.ParamChecks;
import com.macroz.snnmousesimulation.world.AgentEffects;
import com.macroz.snnmousesimulation.world.AgentEvent;

/**
 * Injects a fixed amount of dopamine for every event it is bound to.
 */
public final class InjectDopamineReaction implements ReactionStrategy<AgentEvent> {

    private final double amount;

    public InjectDopamineReaction(double amount) {
        this.amount = ParamChecks.finite("amount", amount);
    }

    public static InjectDopamineReaction create(ConfigParams params) {
        return new InjectDopamineReaction(params.getRequiredDouble("amount"));
    }

    @Override
    public Class<AgentEvent> eventClass() {
        return AgentEvent.class;
    }

    @Override
    public void react(AgentEvent event, AgentEffects effects) {
        effects.injectDopamine(amount);
    }
}
