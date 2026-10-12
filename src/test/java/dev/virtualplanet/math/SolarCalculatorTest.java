package dev.virtualplanet.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Declination, elevation, azimuth, sunrise/sunset, twilight and the vanilla rendered geometry. */
class SolarCalculatorTest {
    private static final double EPSILON = 1.0e-6;
    private static final double TILT = Math.toRadians(SolarCalculator.EARTH_AXIAL_TILT_DEGREES);

    @Test
    void declinationCycleStartsAtTheVernalEquinox() {
        assertEquals(0.0, SolarCalculator.declinationRadians(TILT, 0.0, 365.0, 0.0, true), EPSILON);
        assertEquals(TILT, SolarCalculator.declinationRadians(TILT, 365.0 / 4.0, 365.0, 0.0, true), EPSILON);
        assertEquals(0.0, SolarCalculator.declinationRadians(TILT, 365.0 / 2.0, 365.0, 0.0, true), EPSILON);
        assertEquals(-TILT, SolarCalculator.declinationRadians(TILT, 365.0 * 0.75, 365.0, 0.0, true), EPSILON);
    }

    @Test
    void seasonsAndTiltCanBeDisabled() {
        assertEquals(0.0, SolarCalculator.declinationRadians(TILT, 100.0, 365.0, 0.0, false), EPSILON);
        assertEquals(0.0, SolarCalculator.declinationRadians(0.0, 100.0, 365.0, 0.0, true), EPSILON);
        assertEquals(0.0, SolarCalculator.declinationRadians(TILT, 100.0, 0.0, 0.0, true), EPSILON);
    }

    @Test
    void seasonOffsetShiftsTheCycle() {
        double withoutOffset = SolarCalculator.declinationRadians(TILT, 10.0, 365.0, 0.0, true);
        // Shifting the year by 81.25 days moves day 10 onto the summer solstice (day 91.25).
        double withOffset = SolarCalculator.declinationRadians(TILT, 10.0, 365.0, 81.25, true);
        assertEquals(TILT, withOffset, 1.0e-9, "the offset day must land exactly on the solstice");
        // Declinations are radians here, so compare in degrees: 23.44 vs 4.02 degrees.
        assertTrue(Math.abs(Angles.radiansToDegrees(withoutOffset - withOffset)) > 1.0,
                "the offset must actually change the declination");
        assertEquals(23.44, Angles.radiansToDegrees(withOffset), 1.0e-9);
        assertEquals(4.022, Angles.radiansToDegrees(withoutOffset), 1.0e-3);
    }

    @Test
    void noonElevationFollowsLatitudeAndDeclination() {
        // Equator, equinox: sun overhead at solar noon.
        assertEquals(Math.PI / 2, SolarCalculator.position(0.0, 0.0, 0.5).elevationRadians(), EPSILON);
        // 45 degrees north, equinox: 45 degrees at noon.
        assertEquals(Math.toRadians(45.0), SolarCalculator.position(Math.toRadians(45.0), 0.0, 0.5).elevationRadians(), 1.0e-6);
        // 45 degrees north, northern summer: 90 - (45 - 23.44) degrees at noon.
        SolarPosition summerNoon = SolarCalculator.position(Math.toRadians(45.0), TILT, 0.5);
        assertEquals(90.0 - 45.0 + SolarCalculator.EARTH_AXIAL_TILT_DEGREES, summerNoon.elevationDegrees(), 1.0e-6);
    }

    @Test
    void azimuthIsCorrectForTheFourCardinalCases() {
        // Northern hemisphere noon: sun due south.
        assertEquals(180.0, SolarCalculator.position(Math.toRadians(45.0), 0.0, 0.5).azimuthDegrees(), 1.0e-4);
        // Equator sunset: due west; equator sunrise: due east.
        assertEquals(270.0, SolarCalculator.position(0.0, 0.0, 0.75).azimuthDegrees(), 1.0e-4);
        assertEquals(90.0, SolarCalculator.position(0.0, 0.0, 0.25).azimuthDegrees(), 1.0e-4);
        // Southern hemisphere noon: sun due north.
        assertEquals(0.0, SolarCalculator.position(Math.toRadians(-45.0), 0.0, 0.5).azimuthDegrees(), 1.0e-4);
    }

    @Test
    void directionVectorMatchesElevationAndAzimuth() {
        SolarPosition position = SolarCalculator.position(Math.toRadians(30.0), TILT, 0.4);
        SolarPosition.Direction direction = position.direction();
        assertEquals(Math.sin(position.elevationRadians()), direction.up(), 1.0e-9);
        double length = Math.sqrt(direction.east() * direction.east() + direction.up() * direction.up()
                + direction.south() * direction.south());
        assertEquals(1.0, length, 1.0e-9);
    }

    @Test
    void sunriseAndSunsetAreSymmetricAroundSolarNoon() {
        for (double latitude = -60.0; latitude <= 60.0; latitude += 15.0) {
            double sunrise = SolarCalculator.sunriseSolarTurns(Math.toRadians(latitude), 0.0);
            double sunset = SolarCalculator.sunsetSolarTurns(Math.toRadians(latitude), 0.0);
            assertEquals(0.25, sunrise, 1.0e-9, "equinox sunrise at latitude " + latitude);
            assertEquals(0.75, sunset, 1.0e-9, "equinox sunset at latitude " + latitude);
            assertEquals(0.5, (sunrise + sunset) / 2.0, 1.0e-9);
        }
    }

    @Test
    void summerDaysAreLongerInTheNorthernHemisphere() {
        double northernSummer = SolarCalculator.daylightFraction(Math.toRadians(50.0), TILT);
        double northernWinter = SolarCalculator.daylightFraction(Math.toRadians(50.0), -TILT);
        assertEquals(0.5, SolarCalculator.daylightFraction(0.0, 0.0), 1.0e-9);
        assertTrue(northernSummer > 0.5, "summer day should be longer than 12h");
        assertTrue(northernWinter < 0.5, "winter day should be shorter than 12h");
    }

    @Test
    void twilightOrderingAndPolarConditions() {
        double latitude = Math.toRadians(50.0);
        double dawn = SolarCalculator.morningTwilightTurns(latitude, 0.0, SolarCalculator.CIVIL_TWILIGHT_DEGREES);
        double sunrise = SolarCalculator.sunriseSolarTurns(latitude, 0.0);
        double sunset = SolarCalculator.sunsetSolarTurns(latitude, 0.0);
        double dusk = SolarCalculator.eveningTwilightTurns(latitude, 0.0, SolarCalculator.CIVIL_TWILIGHT_DEGREES);
        assertTrue(dawn < sunrise, "civil dawn must precede sunrise");
        assertTrue(sunrise < sunset, "sunrise must precede sunset");
        assertTrue(sunset < dusk, "civil dusk must follow sunset");
        assertTrue(Double.isNaN(SolarCalculator.morningTwilightTurns(Math.toRadians(20.0), 0.0, -80.0)),
                "a twilight altitude the sun never reaches must yield NaN");
    }

    @Test
    void polarDayAndNightAtHighLatitude() {
        double nearPole = Math.toRadians(80.0);
        assertTrue(SolarCalculator.isPolarDay(nearPole, TILT));
        assertFalse(SolarCalculator.isPolarNight(nearPole, TILT));
        assertTrue(SolarCalculator.isPolarNight(nearPole, -TILT));
        assertFalse(SolarCalculator.isPolarDay(nearPole, -TILT));
        assertEquals(1.0, SolarCalculator.daylightFraction(nearPole, TILT), 1.0e-9);
        assertEquals(0.0, SolarCalculator.daylightFraction(nearPole, -TILT), 1.0e-9);
        assertTrue(Double.isNaN(SolarCalculator.sunriseSolarTurns(nearPole, TILT)));
    }

    @Test
    void vanillaRenderedGeometryIsAnEastWestCircleThroughTheZenith() {
        // Verified against the 1.21.1 renderer: rotateY(-90) then rotateX(skyAngle * 360), sun drawn at +Y.
        assertEquals(90.0, SolarCalculator.vanillaRenderedPosition(0.0).elevationDegrees(), 1.0e-6);
        assertEquals(0.0, SolarCalculator.vanillaRenderedPosition(0.25).elevationDegrees(), 1.0e-6);
        assertEquals(270.0, SolarCalculator.vanillaRenderedPosition(0.25).azimuthDegrees(), 1.0e-6);
        assertEquals(-90.0, SolarCalculator.vanillaRenderedPosition(0.5).elevationDegrees(), 1.0e-6);
        assertEquals(90.0, SolarCalculator.vanillaRenderedPosition(0.75).azimuthDegrees(), 1.0e-6);
        SolarPosition.Direction direction = SolarCalculator.vanillaRenderedPosition(0.125).direction();
        assertTrue(direction.up() > 0.0 && direction.up() < 1.0);
    }
}
