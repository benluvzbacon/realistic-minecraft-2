package dev.virtualplanet.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Time offsets, the vanilla time-to-sky-angle mapping and the local phase shift.
 *
 * <p>The vanilla expectation is taken from the verified 1.21.1 implementation of
 * {@code DimensionType#getSkyAngle(long)}:
 * {@code d = fractionalPart(time/24000 - 0.25); skyAngle = (d*2 + (0.5 - cos(d*PI)/2)) / 3}.
 */
class PlanetTimeTest {
    private static final double EPSILON = 1.0e-9;

    /** Reference implementation of the vanilla formula, kept here so the test is independent. */
    private static double vanillaReference(long time) {
        double d = time / 24000.0 - 0.25;
        d = d - Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return ((d * 2.0 + e) / 3.0) % 1.0;
    }

    @Test
    void matchesVanillaAtTheReferenceMeridian() {
        for (long time = 0; time <= 24000; time += 250) {
            assertEquals(vanillaReference(time), PlanetTime.skyAngleTurns(time, 24000L), 1.0e-6,
                    "vanilla parity at time " + time);
        }
    }

    @Test
    void noonIsTurnZeroAndMidnightIsHalf() {
        assertEquals(0.0, PlanetTime.skyAngleTurns(6000L, 24000L), EPSILON);
        assertEquals(0.5, PlanetTime.skyAngleTurns(18000L, 24000L), EPSILON);
    }

    @Test
    void solarTimeTurnsMapToAClock() {
        assertEquals(0.5, PlanetTime.solarTimeTurns(0.0), EPSILON);
        assertEquals(0.0, PlanetTime.solarTimeTurns(0.5), EPSILON);
        assertEquals("12:00", PlanetTime.formatSolarTime(0.5));
        assertEquals("00:00", PlanetTime.formatSolarTime(0.0));
        assertEquals("18:00", PlanetTime.formatSolarTime(0.75));
    }

    @Test
    void longitudeOffsetIsAPhaseShift() {
        double reference = PlanetTime.skyAngleTurns(3000L, 24000L);
        assertEquals(0.25, PlanetTime.localSkyAngleTurns(3000L, 24000L, 0.25) - reference, 1.0e-12);
        assertEquals(0.0, PlanetTime.localSkyAngleTurns(3000L, 24000L, 0.0), 1.0e-12);
        assertEquals(reference, PlanetTime.localSkyAngleTurns(3000L, 24000L, 1.0), 1.0e-12);
    }

    @Test
    void oppositeLongitudesAreHalfARotationApartAtTheSameGlobalTime() {
        long sharedTime = 12345L;
        double west = PlanetTime.localSkyAngleTurns(sharedTime, 24000L, -0.25);
        double east = PlanetTime.localSkyAngleTurns(sharedTime, 24000L, 0.25);
        double difference = Math.abs(Angles.wrapTurnsSigned(west - east));
        assertEquals(0.5, difference, 1.0e-12);
    }

    @Test
    void localPhaseIsAlwaysANormalisedTurn() {
        for (long time = -100_000L; time <= 100_000L; time += 7919L) {
            double angle = PlanetTime.localSkyAngleTurns(time, 24000L, 0.37);
            assertTrue(angle >= 0.0 && angle < 1.0, "angle out of range at " + time + ": " + angle);
        }
    }

    @Test
    void customRotationPeriodScalesThePhase() {
        // With the vanilla period, 6000 ticks is exactly solar noon (turn 0.0).
        double fast = PlanetTime.uniformTurns(6000L, 24000L);
        assertEquals(0.0, fast, EPSILON);
        // With a period twice as long the same 6000 ticks are only an eighth of a rotation, i.e. three
        // hours before the planet's noon: 0.875 turns (== -0.125).
        double slow = PlanetTime.uniformTurns(6000L, 48000L);
        assertEquals(0.875, slow, EPSILON);
        assertEquals(-0.125, Angles.wrapTurnsSigned(slow), EPSILON);
        // The mapping still produces a valid, normalised sky angle.
        double slowSkyAngle = PlanetTime.skyAngleTurns(6000L, 48000L);
        assertEquals(PlanetTime.easedTurns(slow), slowSkyAngle, EPSILON);
    }
}
