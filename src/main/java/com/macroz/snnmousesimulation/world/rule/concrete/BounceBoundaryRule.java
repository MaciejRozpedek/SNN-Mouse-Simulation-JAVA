package com.macroz.snnmousesimulation.world.rule.concrete;

import com.macroz.snnmousesimulation.utility.ConfigParams;
import com.macroz.snnmousesimulation.world.AgentBody;
import com.macroz.snnmousesimulation.world.BoundaryHit;
import com.macroz.snnmousesimulation.world.WorldState;
import com.macroz.snnmousesimulation.world.rule.WorldRule;

/**
 * Reflects agents that crossed the world boundary and posts a {@link BoundaryHit} event.
 */
public final class BounceBoundaryRule implements WorldRule {

    public static BounceBoundaryRule create(ConfigParams params) {
        return new BounceBoundaryRule();
    }

    @Override
    public void apply(WorldState world, double deltaTimeMs) {
        for (AgentBody agent : world.agents()) {
            bounce(agent, world);
        }
    }

    private void bounce(AgentBody agent, WorldState world) {
        double x = agent.getX();
        double y = agent.getY();
        double angle = agent.getAngle();
        double timestampMs = world.simulationTimeMs();
        boolean bounced = false;

        if (x < 0) {
            x = -x;
            angle = Math.PI - angle;
            bounced = true;
            agent.postEvent(new BoundaryHit(timestampMs, BoundaryHit.Side.LEFT));
        } else if (x > world.width()) {
            x = 2 * world.width() - x;
            angle = Math.PI - angle;
            bounced = true;
            agent.postEvent(new BoundaryHit(timestampMs, BoundaryHit.Side.RIGHT));
        }

        if (y < 0) {
            y = -y;
            angle = -angle;
            bounced = true;
            agent.postEvent(new BoundaryHit(timestampMs, BoundaryHit.Side.TOP));
        } else if (y > world.height()) {
            y = 2 * world.height() - y;
            angle = -angle;
            bounced = true;
            agent.postEvent(new BoundaryHit(timestampMs, BoundaryHit.Side.BOTTOM));
        }

        if (bounced) {
            agent.setX(x);
            agent.setY(y);
            agent.setAngle(normalizeAngle(angle));
        }
    }

    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
}
