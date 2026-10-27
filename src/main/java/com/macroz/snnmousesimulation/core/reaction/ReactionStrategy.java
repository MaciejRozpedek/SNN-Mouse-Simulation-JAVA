package com.macroz.snnmousesimulation.core.reaction;

import com.macroz.snnmousesimulation.world.AgentEffects;
import com.macroz.snnmousesimulation.world.AgentEvent;

/**
 * Reacts to events of one type from the agent's event queue.
 */
public interface ReactionStrategy<E extends AgentEvent> {

    /**
     * Type of events this reaction accepts; {@code AgentEvent.class} accepts every event.
     */
    Class<E> eventClass();

    void react(E event, AgentEffects effects);
}
