package dev.virtualplanet.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Longitude mapping, normalisation, border centres, border resizing, huge coordinates and latitude. */
class PlanetGeometryTest {
    private static final double EPSILON = 1.0e-9;
    private static final double CIRCUMFERENCE = 40_000.0; // one revolution = 40000 blocks

    private static PlanetGeometry flat() {
        return new PlanetGeometry(CIRCUMFERENCE, 0.0, 0.0, 0.0);
    }

    @Test
    void longitudeFollowsTheSpecifiedFormula() {
        double x = 10_000.0;
        double expected = 2 * Math.PI * x / CIRCUMFERENCE;
        assertEquals(expected, flat().longitudeRadians(x), EPSILON);
        assertEquals(Math.PI / 2, flat().longitudeRadians(x), EPSILON);
    }

    @Test
    void longitudeIsNormalisedIntoMinusPiToPi() {
        PlanetGeometry geometry = flat();
        assertEquals(-Math.PI / 2, geometry.longitudeRadians(-10_000.0), EPSILON);
        assertEquals(0.0, geometry.longitudeRadians(0.0), EPSILON);
        // One and a half revolutions are equivalent to half a revolution.
        double threeQuarters = geometry.longitudeRadians(-30_000.0);
        assertTrue(threeQuarters >= -Math.PI && threeQuarters < Math.PI, "must stay in [-PI, PI)");
        assertEquals(Math.PI / 2, threeQuarters, EPSILON);
    }

    @Test
    void offsetTurnsAreWrappedIntoMinusHalfPlusHalf() {
        PlanetGeometry geometry = flat();
        assertEquals(0.25, geometry.longitudeOffsetTurns(10_000.0), EPSILON);
        assertEquals(-0.5, geometry.longitudeOffsetTurns(20_000.0), EPSILON);
        assertEquals(-0.25, geometry.longitudeOffsetTurns(-10_000.0), EPSILON);
    }

    @Test
    void oppositeCoordinatesAreHalfARotationApart() {
        PlanetGeometry geometry = flat();
        assertEquals(0.5, geometry.angularSeparationTurns(0.0, 20_000.0), EPSILON);
        assertEquals(0.5, geometry.angularSeparationTurns(-20_000.0, 20_000.0), EPSILON);
        assertEquals(0.25, geometry.angularSeparationTurns(0.0, 10_000.0), EPSILON);
    }

    @Test
    void nonzeroBorderCentreIsSupported() {
        PlanetGeometry geometry = PlanetGeometry.fromWorldBorder(1_000.0, -2_000.0, CIRCUMFERENCE, 1.0, 0.0);
        assertEquals(1_000.0, geometry.referenceX(), EPSILON);
        assertEquals(-2_000.0, geometry.referenceZ(), EPSILON);
        assertEquals(0.0, geometry.longitudeRadians(1_000.0), EPSILON);
        assertEquals(Math.PI / 2, geometry.longitudeRadians(11_000.0), EPSILON);
        assertEquals(0.5, geometry.angularSeparationTurns(1_000.0, 21_000.0), EPSILON);
    }

    @Test
    void borderResizingAndCircumferenceFactors() {
        PlanetGeometry small = PlanetGeometry.fromWorldBorder(0.0, 0.0, 10_000.0, 1.0, 0.0);
        PlanetGeometry large = PlanetGeometry.fromWorldBorder(0.0, 0.0, 20_000.0, 1.0, 0.0);
        assertEquals(10_000.0, small.circumference(), EPSILON);
        assertEquals(20_000.0, large.circumference(), EPSILON);
        // Half the border is always half a revolution, whatever the size.
        assertEquals(0.5, small.angularSeparationTurns(0.0, 5_000.0), EPSILON);
        assertEquals(0.5, large.angularSeparationTurns(0.0, 10_000.0), EPSILON);
        // The circumference can deliberately be independent from the border.
        PlanetGeometry halved = PlanetGeometry.fromWorldBorder(0.0, 0.0, CIRCUMFERENCE, 0.5, 0.0);
        assertEquals(0.5, halved.angularSeparationTurns(0.0, 10_000.0), EPSILON);
    }

    @Test
    void veryLargeCoordinatesStayWellDefined() {
        PlanetGeometry geometry = new PlanetGeometry(4_000.0, 0.0, 0.0, 0.0);
        // 1e12 blocks with a 4000 block circumference is exactly 250,000,000 revolutions.
        assertEquals(0.0, geometry.longitudeOffsetTurns(1.0e12), 1.0e-6);
        double longitude = geometry.longitudeRadians(1.0e12 + 1_000.0);
        assertEquals(Math.PI / 2, longitude, 1.0e-6);
        assertTrue(Double.isFinite(geometry.longitudeRadians(-1.0e12)));
    }

    @Test
    void referenceLongitudeShiftsTheMeridian() {
        PlanetGeometry geometry = new PlanetGeometry(CIRCUMFERENCE, 0.0, 0.0, Math.toRadians(90.0));
        assertEquals(Math.PI / 2, geometry.longitudeRadians(0.0), EPSILON);
    }

    @Test
    void latitudeMappingIsLinearAndClamped() {
        PlanetGeometry geometry = flat();
        double range = Math.toRadians(66.5);
        assertEquals(0.0, geometry.latitudeRadiansFromZ(0.0, 2_000.0, range), EPSILON);
        assertEquals(range, geometry.latitudeRadiansFromZ(2_000.0, 2_000.0, range), EPSILON);
        assertEquals(-range, geometry.latitudeRadiansFromZ(-2_000.0, 2_000.0, range), EPSILON);
        // Beyond the mapped extent the value clamps instead of running away.
        assertEquals(range, geometry.latitudeRadiansFromZ(500_000.0, 2_000.0, range), EPSILON);
        // Halfway to the edge is half the latitude range.
        assertEquals(range / 2, geometry.latitudeRadiansFromZ(1_000.0, 2_000.0, range), EPSILON);
    }

    @Test
    void latitudeCanBeDisabledOrUseTheXAxis() {
        PlanetGeometry geometry = flat();
        double range = Math.toRadians(66.5);
        assertEquals(0.0, geometry.latitudeRadians(1_000.0, LatitudeAxis.NONE, 2_000.0, range), EPSILON);
        assertEquals(range, geometry.latitudeRadiansFromX(2_000.0, 2_000.0, range), EPSILON);
    }

    @Test
    void invalidConstructionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PlanetGeometry(0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PlanetGeometry(Double.NaN, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PlanetGeometry(1.0e12, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PlanetGeometry(CIRCUMFERENCE, Double.NaN, 0.0, 0.0));
    }
}
