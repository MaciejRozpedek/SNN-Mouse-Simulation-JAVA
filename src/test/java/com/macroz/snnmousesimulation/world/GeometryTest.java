package com.macroz.snnmousesimulation.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryTest {

    private static final double EPSILON = 1e-9;

    @Test
    void measuresAcrossTheEdgesOnlyOnATorus() {
        Geometry torus = new Geometry(100, 50, Geometry.Type.TORUS);
        assertEquals(10, torus.dx(95, 5), EPSILON);
        assertEquals(-10, torus.dx(5, 95), EPSILON);
        assertEquals(30, torus.dx(20, 50), EPSILON);
        assertEquals(-20, torus.dy(10, 40), EPSILON);
        assertEquals(10 * 10 + 20 * 20, torus.distanceSquared(95, 10, 5, 40), EPSILON);

        Geometry rectangle = new Geometry(100, 50, Geometry.Type.RECTANGLE);
        assertEquals(-90, rectangle.dx(95, 5), EPSILON);
        assertEquals(30, rectangle.dy(10, 40), EPSILON);
        assertEquals(90 * 90 + 30 * 30, rectangle.distanceSquared(95, 10, 5, 40), EPSILON);
    }

    @Test
    void wrapsCoordinatesThatLeftTheWorld() {
        Geometry geometry = new Geometry(100, 50, Geometry.Type.TORUS);
        assertEquals(3, geometry.wrapX(103), EPSILON);
        assertEquals(98, geometry.wrapX(-2), EPSILON);
        assertEquals(40, geometry.wrapX(40), EPSILON);
        assertEquals(49, geometry.wrapY(-51), EPSILON);
    }
}
