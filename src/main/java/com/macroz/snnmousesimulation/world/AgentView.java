package com.macroz.snnmousesimulation.world;

public final class AgentView {

    private final double x;
    private final double y;
    private final double angle;

    private AgentView(double x, double y, double angle) {
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    public static AgentView from(Agent agent) {
        return new AgentView(agent.getX(), agent.getY(), agent.getAngle());
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double angle() {
        return angle;
    }
}
