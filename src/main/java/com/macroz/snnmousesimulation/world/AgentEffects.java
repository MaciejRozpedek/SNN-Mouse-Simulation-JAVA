package com.macroz.snnmousesimulation.world;

/**
 * Changes that agent strategies (outputs and reactions) may apply to their agent.
 */
public interface AgentEffects {

    void move(double distance, double rotationRadians);

    void injectDopamine(double amount);
}
