package com.macroz.snnmousesimulation.core.reaction;

import com.macroz.snnmousesimulation.world.AgentEffects;
import com.macroz.snnmousesimulation.world.AgentEvent;

import java.util.ArrayList;
import java.util.List;

public class ReactionSystem {

    private record ReactionBinding(Class<? extends AgentEvent> eventClass, ReactionStrategy<?> strategy) {}

    private final List<ReactionBinding> reactions = new ArrayList<>();

    public void addReaction(String name, Class<? extends AgentEvent> eventClass, ReactionStrategy<?> strategy) {
        if (!strategy.eventClass().isAssignableFrom(eventClass)) {
            throw new IllegalArgumentException("Reaction '" + name + "' handles " + strategy.eventClass().getSimpleName()
                    + " events and cannot react to " + eventClass.getSimpleName() + " events.");
        }
        reactions.add(new ReactionBinding(eventClass, strategy));
    }

    public void processEvents(List<AgentEvent> events, AgentEffects effects) {
        for (AgentEvent event : events) {
            for (ReactionBinding binding : reactions) {
                if (binding.eventClass.isInstance(event)) {
                    react(binding.strategy, event, effects);
                }
            }
        }
    }

    private static <E extends AgentEvent> void react(ReactionStrategy<E> strategy, AgentEvent event, AgentEffects effects) {
        strategy.react(strategy.eventClass().cast(event), effects);
    }
}
