package dev.virtualplanet.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Angle normalisation, wrapping and edge cases. */
class AnglesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void wrapTurnsNormalisesIntoUnitInterval() {
        assertEquals(0.25, Angles.wrapTurns(1.25), EPSILON);
        assertEquals(0.75, Angles.wrapTurns(-0.25), EPSILON);
        assertEquals(0.0, Angles.wrapTurns(4.0), EPSILON);
        assertEquals(0.5, Angles.wrapTurns(-3.5), EPSILON);
    }

    @Test
    void wrapTurnsHandlesExtremeAndInvalidInput() {
        assertEquals(0.0, Angles.wrapTurns(Double.NaN), EPSILON);
        assertEquals(0.0, Angles.wrapTurns(Double.POSITIVE_INFINITY), EPSILON);
        assertEquals(0.0, Angles.wrapTurns(Double.NEGATIVE_INFINITY), EPSILON);
        double huge = Angles.wrapTurns(1.0e9 + 0.25);
        assertEquals(0.25, huge, 1.0e-6);
    }

    @Test
    void signedWrappingUsesHalfOpenInterval() {
        assertEquals(-0.25, Angles.wrapTurnsSigned(-0.25), EPSILON);
        assertEquals(-0.5, Angles.wrapTurnsSigned(0.5), EPSILON);
        assertEquals(0.25, Angles.wrapTurnsSigned(0.25), EPSILON);
        assertEquals(-0.1, Angles.wrapTurnsSigned(0.9), 1.0e-12);
    }

    @Test
    void radiansWrappingIsConsistentWithTurns() {
        assertEquals(Math.PI, Angles.wrapRadians(-Math.PI), 1.0e-12);
        assertEquals(-Math.PI / 2, Angles.wrapRadiansSigned(3 * Math.PI / 2), 1.0e-12);
        assertEquals(0.5, Angles.radiansToTurns(Math.PI), EPSILON);
        assertEquals(Math.PI, Angles.turnsToRadians(0.5), EPSILON);
    }

    @Test
    void degreesAndClamping() {
        assertEquals(Math.PI, Angles.degreesToRadians(180.0), 1.0e-12);
        assertEquals(180.0, Angles.radiansToDegrees(Math.PI), 1.0e-9);
        assertEquals(5.0, Angles.clamp(9.0, -5.0, 5.0), EPSILON);
        assertEquals(-5.0, Angles.clamp(-9.0, -5.0, 5.0), EPSILON);
        assertTrue(Angles.clamp(Double.NaN, 1.0, 2.0) == 1.0);
    }
}
