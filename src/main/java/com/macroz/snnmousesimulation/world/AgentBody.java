package com.macroz.snnmousesimulation.world;

/**
 * An agent as seen by world rules: its position and its event queue.
 */
public interface AgentBody {

    double getX();

    double getY();

    double getAngle();

    void setX(double x);

    void setY(double y);

    void setAngle(double angle);

    void postEvent(AgentEvent event);
}
