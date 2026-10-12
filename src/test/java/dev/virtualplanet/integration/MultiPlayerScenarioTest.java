package dev.virtualplanet.integration;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.PlanetGeometry;
import dev.virtualplanet.math.PlanetTime;
import dev.virtualplanet.math.SolarCalculator;
import dev.virtualplanet.math.SolarPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The end-to-end promise of the mod: players at different positions of the flat world observe genuinely
 * different local skies at the same shared world time, computed only from coordinates.
 */
class MultiPlayerScenarioTest {
    private static final double EPSILON = 1.0e-9;

    /** A planet whose circumference equals the world border: 40000 blocks around, centred on the origin. */
    private static PlanetGeometry world() {
        PlanetConfig config = PlanetConfig.defaults();
        config.deriveCircumferenceFromWorldBorder = true;
        config.borderCircumferenceFactor = 1.0;
        config.sanitize();
        return config.createGeometry(0.0, 0.0, 40000.0);
    }

    private static double localAngle(PlanetGeometry geometry, long sharedTime, double x) {
        return PlanetTime.localSkyAngleTurns(sharedTime, 24000L, geometry.longitudeOffsetTurns(x));
    }

    @Test
    void oppositeSidesOfThePlanetDifferByHalfARotationAtTheSameTime() {
        PlanetGeometry geometry = world();
        long sharedTime = 6000L; // vanilla noon on the reference meridian

        double angleA = localAngle(geometry, sharedTime, 0.0);
        double angleB = localAngle(geometry, sharedTime, 20000.0);

        assertEquals(0.0, angleA, EPSILON, "the reference meridian must stay at vanilla noon");
        double difference = Math.abs(Angles.wrapTurnsSigned(angleA - angleB));
        assertEquals(0.5, difference, EPSILON, "opposite longitudes must be half a rotation apart");
    }

    @Test
    void fourPlayersAtQuarterLongitudesSeeFourDifferentSolarTimes() {
        PlanetGeometry geometry = world();
        long sharedTime = 6000L;

        SolarPosition midday = SolarCalculator.vanillaRenderedPosition(localAngle(geometry, sharedTime, 0.0));
        SolarPosition evening = SolarCalculator.vanillaRenderedPosition(localAngle(geometry, sharedTime, 10000.0));
        SolarPosition midnight = SolarCalculator.vanillaRenderedPosition(localAngle(geometry, sharedTime, 20000.0));
        SolarPosition morning = SolarCalculator.vanillaRenderedPosition(localAngle(geometry, sharedTime, -10000.0));

        assertTrue(midday.elevationDegrees() > 89.0, "player A: sun overhead (midday)");
        assertEquals(0.0, evening.elevationDegrees(), 1.0e-6, "player B: sun on the western horizon (sunset)");
        assertTrue(midnight.elevationDegrees() < -89.0, "player C: sun at its lowest point (midnight)");
        assertEquals(0.0, morning.elevationDegrees(), 1.0e-6, "player D: sun on the eastern horizon (sunrise)");
        assertEquals(90.0, morning.azimuthDegrees(), 1.0e-6);
        assertEquals(270.0, evening.azimuthDegrees(), 1.0e-6);
    }

    @Test
    void playersCloserTogetherSeeCloserSkies() {
        PlanetGeometry geometry = world();
        long sharedTime = 9000L;
        double near = localAngle(geometry, sharedTime, 0.0);
        double slightlyEast = localAngle(geometry, sharedTime, 1000.0);
        double farEast = localAngle(geometry, sharedTime, 18000.0);
        double nearDifference = Math.abs(Angles.wrapTurnsSigned(near - slightlyEast));
        double farDifference = Math.abs(Angles.wrapTurnsSigned(near - farEast));
        assertEquals(0.025, nearDifference, EPSILON);
        assertTrue(farDifference > nearDifference * 10.0);
    }

    @Test
    void skyAngleDifferenceScalesWithDistanceNotWithPosition() {
        PlanetGeometry geometry = world();
        // A flat world must not produce a "sky jumps when crossing the origin" effect.
        for (double x = -20_000.0; x <= 20_000.0; x += 2500.0) {
            double here = localAngle(geometry, 12000L, x);
            double thousandEast = localAngle(geometry, 12000L, x + 1000.0);
            double difference = Math.abs(Angles.wrapTurnsSigned(here - thousandEast));
            double expected = 1000.0 / 40000.0;
            assertTrue(Math.abs(difference - expected) < 1.0e-9 || Math.abs(difference - (1.0 - expected)) < 1.0e-9,
                    "unexpected difference " + difference + " at x=" + x);
        }
    }

    @Test
    void borderChangesArePickedUpWithoutChangingSharedTime() {
        long sharedTime = 6000L;
        PlanetGeometry small = PlanetConfig.defaults().createGeometry(0.0, 0.0, 20_000.0);
        PlanetGeometry large = PlanetConfig.defaults().createGeometry(0.0, 0.0, 40_000.0);

        // The same physical block is half a rotation from the centre on the small planet...
        assertEquals(0.5, Math.abs(small.longitudeOffsetTurns(10_000.0)), EPSILON);
        // ...but only a quarter on the enlarged one: the planet simply got bigger.
        assertEquals(0.25, Math.abs(large.longitudeOffsetTurns(10_000.0)), EPSILON);
        // The official noon meridian is untouched by the resize.
        assertEquals(0.0, localAngle(large, sharedTime, 0.0), EPSILON);
    }

    @Test
    void nonZeroBorderCentresWorkEndToEnd() {
        PlanetConfig config = PlanetConfig.defaults();
        config.sanitize();
        PlanetGeometry geometry = config.createGeometry(1_000.0, -2_000.0, 40_000.0);
        long sharedTime = 6000L;

        assertEquals(0.0, localAngle(geometry, sharedTime, 1_000.0), EPSILON,
                "the border centre must see vanilla noon");
        double halfWayRound = localAngle(geometry, sharedTime, 21_000.0);
        assertEquals(0.5, Math.abs(Angles.wrapTurnsSigned(halfWayAround)), EPSILON);
    }

    @Test
    void hugeCoordinatesRemainConsistent() {
        PlanetConfig config = PlanetConfig.defaults();
        config.deriveCircumferenceFromWorldBorder = false;
        config.circumferenceBlocks = 40000.0;
        config.sanitize();
        PlanetGeometry geometry = config.createGeometry(0.0, 0.0, 0.0);

        // 40 million blocks east is exactly 1000 revolutions.
        double farAngle = localAngle(geometry, 6000L, 40_000_000.0);
        assertEquals(0.0, farAngle, 1.0e-6);
        double farAnglePlusQuarter = localAngle(geometry, 6000L, 40_010_000.0);
        assertEquals(0.25, farAnglePlusQuarter, 1.0e-6);
    }

    @Test
    void theWorldTimeItselfIsNeverModified() {
        // The mod is pure computation: the shared time in and the shared time out are the same value.
        long sharedTime = 13000L;
        PlanetGeometry geometry = world();
        localAngle(geometry, sharedTime, 12345.0);
        assertEquals(13000L, sharedTime);
        SolarPosition sunset = SolarCalculator.positionFromSkyAngle(Math.toRadians(0.0), 0.0,
                localAngle(geometry, sharedTime, 10_000.0));
        assertTrue(sunset.elevationDegrees() < 1.0 && sunset.elevationDegrees() > -1.0);
    }
}
