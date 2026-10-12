package dev.virtualplanet.config;

import dev.virtualplanet.math.LatitudeAxis;
import dev.virtualplanet.math.PlanetGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration validation: malformed values are repaired, never fatal. */
class PlanetConfigTest {

    @Test
    void defaultsAreValidOnTheirOwn() {
        PlanetConfig config = PlanetConfig.defaults();
        List<String> warnings = config.sanitize();
        assertTrue(warnings.isEmpty(), "defaults must not need repairs: " + warnings);
        assertEquals(24000L, config.rotationPeriodTicks);
        assertEquals(LatitudeAxis.Z, config.latitudeAxis);
    }

    @Test
    void malformedNumbersAreRepaired() {
        PlanetConfig config = PlanetConfig.defaults();
        config.circumferenceBlocks = Double.NaN;
        config.borderCircumferenceFactor = -3.0;
        config.referenceX = Double.POSITIVE_INFINITY;
        config.referenceZ = Double.NaN;
        config.referenceLongitudeDegrees = 1000.0;
        config.latitudeRangeDegrees = 900.0;
        config.axialTiltDegrees = Double.NaN;
        config.yearLengthDays = -1.0;
        config.seasonOffsetDays = Double.NaN;

        List<String> warnings = config.sanitize();

        assertFalse(warnings.isEmpty(), "repairs must be reported");
        assertEquals(0.0, config.circumferenceBlocks);
        assertEquals(1.0, config.borderCircumferenceFactor);
        assertEquals(0.0, config.referenceX);
        assertEquals(0.0, config.referenceZ);
        assertTrue(config.referenceLongitudeDegrees >= -180.0 && config.referenceLongitudeDegrees < 180.0);
        assertEquals(90.0, config.latitudeRangeDegrees);
        assertEquals(PlanetConfig.DEFAULT_AXIAL_TILT_DEGREES, config.axialTiltDegrees);
        assertEquals(365.0, config.yearLengthDays);
        assertEquals(0.0, config.seasonOffsetDays);
        // A configuration that needed repairs is still perfectly usable.
        PlanetGeometry geometry = config.createGeometry(0.0, 0.0, 40000.0);
        assertEquals(40000.0, geometry.circumference(), 1.0e-9);
    }

    @Test
    void rotationPeriodAndCircumferenceAreClamped() {
        PlanetConfig config = PlanetConfig.defaults();
        config.rotationPeriodTicks = 1L;
        config.circumferenceBlocks = 1.0;
        config.sanitize();
        assertEquals(PlanetConfig.MIN_ROTATION_PERIOD_TICKS, config.rotationPeriodTicks);
        assertEquals(PlanetGeometry.MIN_CIRCUMFERENCE, config.circumferenceBlocks, 1.0e-9);

        config.rotationPeriodTicks = Long.MAX_VALUE;
        config.circumferenceBlocks = 1.0e12;
        config.sanitize();
        assertEquals(PlanetConfig.MAX_ROTATION_PERIOD_TICKS, config.rotationPeriodTicks);
        assertEquals(PlanetGeometry.MAX_CIRCUMFERENCE, config.circumferenceBlocks, 1.0e-3);
    }

    @Test
    void nullEnumFallsBackToTheDefault() {
        PlanetConfig config = PlanetConfig.defaults();
        config.latitudeAxis = null;
        List<String> warnings = config.sanitize();
        assertEquals(LatitudeAxis.Z, config.latitudeAxis);
        assertFalse(warnings.isEmpty());
    }

    @Test
    void circumferenceCanBeIndependentFromTheBorder() {
        PlanetConfig config = PlanetConfig.defaults();
        config.deriveCircumferenceFromWorldBorder = false;
        config.circumferenceBlocks = 12345.0;
        config.sanitize();
        assertEquals(12345.0, config.effectiveCircumference(99999.0), 1.0e-9);
        config.deriveCircumferenceFromWorldBorder = true;
        assertEquals(99999.0, config.effectiveCircumference(99999.0), 1.0e-9);
    }

    @Test
    void referenceCoordinateFollowsTheBorderCentreByDefault() {
        PlanetConfig config = PlanetConfig.defaults();
        assertEquals(1500.0, config.effectiveReferenceX(1500.0), 1.0e-9);
        assertEquals(-2500.0, config.effectiveReferenceZ(-2500.0), 1.0e-9);
        config.referenceFollowsWorldBorderCenter = false;
        config.referenceX = 7.0;
        config.referenceZ = -7.0;
        assertEquals(7.0, config.effectiveReferenceX(1500.0), 1.0e-9);
        assertEquals(-7.0, config.effectiveReferenceZ(-2500.0), 1.0e-9);
    }

    @Test
    void celestialTiltIsDeclinationMinusLatitude() {
        PlanetConfig config = PlanetConfig.defaults();
        // On the equator at the equinox the sun passes through the zenith: no tilt.
        assertEquals(0.0, config.celestialTiltDegrees(0.0, 0.0), 1.0e-9);
        // 45 degrees north at the equinox: the noon sun is 45 degrees from the zenith, towards the south.
        assertEquals(-45.0, config.celestialTiltDegrees(Math.toRadians(45.0), 0.0), 1.0e-9);
        // 45 degrees north at the summer solstice: much closer to the zenith.
        assertEquals(-21.56, config.celestialTiltDegrees(Math.toRadians(45.0), Math.toRadians(23.44)), 1.0e-6);
    }

    @Test
    void copyAndEqualityBehave() {
        PlanetConfig config = PlanetConfig.defaults();
        PlanetConfig copy = config.copy();
        assertEquals(config, copy);
        assertEquals(config.hashCode(), copy.hashCode());
        copy.debug = !copy.debug;
        assertNotEquals(config, copy);
        assertFalse(config.skyRelevantDifference(config.copy()));
        assertTrue(config.skyRelevantDifference(copy));
    }
}
