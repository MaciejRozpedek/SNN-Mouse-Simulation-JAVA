package com.macroz.snnmousesimulation.world;

/**
 * Size and shape of the world. On a {@code TORUS} opposite edges are glued together, so
 * distances and directions go across them; a {@code RECTANGLE} has walls at its edges.
 */
public record Geometry(double width, double height, Type type) {

    public enum Type { TORUS, RECTANGLE }

    // Shortest signed offset between two coordinates
    public double dx(double fromX, double toX) {
        return offset(toX - fromX, width);
    }

    public double dy(double fromY, double toY) {
        return offset(toY - fromY, height);
    }

    public double distanceSquared(double x1, double y1, double x2, double y2) {
        double dx = dx(x1, x2);
        double dy = dy(y1, y2);
        return dx * dx + dy * dy;
    }

    // Brings a coordinate that left a torus back in on the opposite side
    double wrapX(double x) {
        return wrap(x, width);
    }

    double wrapY(double y) {
        return wrap(y, height);
    }

    private double offset(double delta, double size) {
        if (type == Type.RECTANGLE) {
            return delta;
        }
        return wrap(delta + size / 2, size) - size / 2;
    }

    private static double wrap(double value, double size) {
        double wrapped = value % size;
        return wrapped < 0 ? wrapped + size : wrapped;
    }
}
