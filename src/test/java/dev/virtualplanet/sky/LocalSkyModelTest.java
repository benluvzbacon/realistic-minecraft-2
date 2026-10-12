package dev.virtualplanet.sky;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.LatitudeAxis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the local sky model: the same object the client renders, the commands report and
 * the payload carries. Everything here is pure computation, so it runs without a Minecraft client.
 */
class LocalSkyModelTest {
    private static final double EPSILON = 1.0e-9;
    private static final double BORDER_SIZE = 40_000.0;

    private static LocalSkyModel model(PlanetConfig config) {
        config.sanitize();
        return new LocalSkyModel(config);
    }

    private static LocalSkyView view(PlanetConfig config, long sharedTime, double x, double z) {
        return model(config).view(sharedTime, x, z, 0.0, 0.0, 0.0, BORDER_SIZE);
    }

    @Test
    void theReferenceMeridianStaysOnVanillaTime() {
        LocalSkyView noon = view(PlanetConfig.defaults(), 6000L, 0.0, 0.0);

        assertEquals(0.0, noon.vanillaSkyAngleTurns(), EPSILON);
        assertEquals(0.0, noon.localSkyAngleTurns(), EPSILON);
        assertEquals("12:00", noon.solarClock());
        assertEquals(0.0, noon.longitudeDegrees(), EPSILON);
        assertEquals(0.0, noon.latitudeDegrees(), EPSILON);
        assertEquals(90.0, noon.computedPosition().elevationDegrees(), 1.0e-6);
    }

    @Test
    void oppositeLongitudesAreHalfARotationApartAtOneSharedTime() {
        PlanetConfig config = PlanetConfig.defaults();
        long sharedTime = 6000L;

        LocalSkyView reference = view(config, sharedTime, 0.0, 0.0);
        LocalSkyView antimeridian = view(config, sharedTime, 20_000.0, 0.0);

        assertEquals(0.5, Angles.wrapTurns(antimeridian.localSkyAngleTurns() - reference.localSkyAngleTurns()),
                EPSILON);
        assertEquals("00:00", antimeridian.solarClock());
        assertTrue(antimeridian.computedPosition().elevationDegrees() < -89.0, "local midnight");
        assertEquals(sharedTime, reference.sharedTime());
        assertEquals(sharedTime, antimeridian.sharedTime());
    }

    @Test
    void fourQuarterLongitudesSeeMiddaySunsetMidnightAndSunrise() {
        PlanetConfig config = PlanetConfig.defaults();
        long sharedTime = 6000L;

        LocalSkyView midday = view(config, sharedTime, 0.0, 0.0);
        LocalSkyView sunset = view(config, sharedTime, 10_000.0, 0.0);
        LocalSkyView midnight = view(config, sharedTime, 20_000.0, 0.0);
        LocalSkyView sunrise = view(config, sharedTime, -10_000.0, 0.0);

        assertEquals("12:00", midday.solarClock());
        assertEquals("18:00", sunset.solarClock());
        assertEquals("00:00", midnight.solarClock());
        assertEquals("06:00", sunrise.solarClock());

        assertTrue(midday.computedPosition().elevationDegrees() > 89.0);
        assertEquals(0.0, sunset.computedPosition().elevationDegrees(), 1.0e-6);
        assertEquals(270.0, sunset.computedPosition().azimuthDegrees(), 1.0e-4);
        assertTrue(midnight.computedPosition().elevationDegrees() < -89.0);
        assertEquals(0.0, sunrise.computedPosition().elevationDegrees(), 1.0e-6);
        assertEquals(90.0, sunrise.computedPosition().azimuthDegrees(), 1.0e-4);

        // The estimates the command prints agree with the computed position: on the equator the sun
        // crosses the horizon at exactly 06:00 and 18:00, and civil twilight brackets it.
        assertEquals(0.75, sunset.sunsetTurns(), 1.0e-9);
        assertEquals(0.25, sunrise.sunriseTurns(), 1.0e-9);
        assertTrue(sunset.civilDuskTurns() > sunset.sunsetTurns(), "civil dusk follows sunset");
        assertTrue(sunrise.civilDawnTurns() < sunrise.sunriseTurns(), "civil dawn precedes sunrise");
        assertEquals(0.5, sunset.daylightFraction(), 1.0e-9);
    }

    @Test
    void thePhaseShiftDoesNotDependOnTheRotationPeriod() {
        PlanetConfig fast = PlanetConfig.defaults();
        PlanetConfig slow = PlanetConfig.defaults();
        slow.rotationPeriodTicks = 48_000L;

        for (long time : new long[] {0L, 3000L, 6000L, 13_000L, 18_000L, 240_000L}) {
            LocalSkyView fastReference = view(fast, time, 0.0, 0.0);
            LocalSkyView slowReference = view(slow, time, 0.0, 0.0);

            // A longer rotation period really does slow the sun down at any fixed meridian ...
            assertEquals(fastReference.vanillaSkyAngleTurns(), fastReference.localSkyAngleTurns(), EPSILON);
            assertEquals(slowReference.vanillaSkyAngleTurns(), slowReference.localSkyAngleTurns(), EPSILON);

            // ... but the shift between two meridians is still exactly the longitude offset.
            assertEquals(0.5, Angles.wrapTurns(view(slow, time, 20_000.0, 0.0).localSkyAngleTurns()
                    - slowReference.localSkyAngleTurns()), EPSILON);
            assertEquals(0.25, Angles.wrapTurns(view(slow, time, 10_000.0, 0.0).localSkyAngleTurns()
                    - slowReference.localSkyAngleTurns()), EPSILON);
        }
    }

    @Test
    void borderCentreAndBorderSizeAreHonoured() {
        PlanetConfig config = PlanetConfig.defaults();
        LocalSkyModel model = model(config);
        double centerX = 1_000.0;
        double centerZ = -2_000.0;

        // The reference meridian follows the border centre, which is not the origin.
        LocalSkyView atCentre = model.view(6000L, centerX, centerZ, 0.0, centerX, centerZ, BORDER_SIZE);
        assertEquals(0.0, atCentre.localSkyAngleTurns(), EPSILON);

        LocalSkyView quarter = model.view(6000L, centerX + BORDER_SIZE / 4.0, centerZ, 0.0,
                centerX, centerZ, BORDER_SIZE);
        assertEquals(0.25, quarter.localSkyAngleTurns(), EPSILON);

        // The same coordinate on a planet of half the size is half a rotation away.
        LocalSkyView halved = model.view(6000L, centerX + BORDER_SIZE / 4.0, centerZ, 0.0,
                centerX, centerZ, BORDER_SIZE / 2.0);
        assertEquals(0.5, halved.localSkyAngleTurns(), EPSILON);

        // ... and the world origin is 1000 blocks, a fortieth of the planet, west of noon.
        LocalSkyView origin = model.view(6000L, 0.0, 0.0, 0.0, centerX, centerZ, BORDER_SIZE);
        assertEquals(-0.025, Angles.wrapTurnsSigned(origin.localSkyAngleTurns()), EPSILON);
        assertEquals(-9.0, origin.longitudeDegrees(), 1.0e-9);
    }

    @Test
    void latitudeMappingIsConfigurableAndClamped() {
        PlanetConfig config = PlanetConfig.defaults().copy();
        assertEquals(LatitudeAxis.Z, config.latitudeAxis);

        assertEquals(33.25, view(config, 6000L, 0.0, 10_000.0).latitudeDegrees(), 1.0e-9);
        assertEquals(-33.25, view(config, 6000L, 0.0, -10_000.0).latitudeDegrees(), 1.0e-9);
        assertEquals(66.5, view(config, 6000L, 0.0, 30_000.0).latitudeDegrees(), 1.0e-9);
        assertEquals(-66.5, view(config, 6000L, 0.0, -300_000.0).latitudeDegrees(), 1.0e-9);

        PlanetConfig equatorial = PlanetConfig.defaults().copy();
        equatorial.latitudeAxis = LatitudeAxis.NONE;
        assertEquals(0.0, view(equatorial, 6000L, 0.0, 30_000.0).latitudeDegrees(), EPSILON);

        PlanetConfig xAxis = PlanetConfig.defaults().copy();
        xAxis.latitudeAxis = LatitudeAxis.X;
        assertEquals(33.25, view(xAxis, 6000L, 10_000.0, 0.0).latitudeDegrees(), 1.0e-9);
        assertEquals(0.0, view(xAxis, 6000L, 0.0, 30_000.0).latitudeDegrees(), EPSILON);
    }

    @Test
    void polarDayAndNightAreReportedForHighLatitudes() {
        PlanetConfig config = PlanetConfig.defaults().copy();
        config.latitudeAxis = LatitudeAxis.Z;
        config.latitudeRangeDegrees = 89.0;
        config.seasonsEnabled = true;

        long midsummer = (long) (91.25 * 24_000.0);
        long midwinter = (long) (273.75 * 24_000.0);

        LocalSkyView poleInSummer = view(config, midsummer, 0.0, BORDER_SIZE / 2.0);
        assertTrue(poleInSummer.isPolarDay(), "the pole has midnight sun in summer");
        assertFalse(poleInSummer.isPolarNight());

        LocalSkyView poleInWinter = view(config, midwinter, 0.0, BORDER_SIZE / 2.0);
        assertTrue(poleInWinter.isPolarNight(), "and polar night in winter");
        assertFalse(poleInWinter.isPolarDay());

        LocalSkyView equatorInWinter = view(config, midwinter, 0.0, 0.0);
        assertFalse(equatorInWinter.isPolarNight(), "the equator never has polar night");
    }

    @Test
    void seasonsChangeTheDeclinationOnlyWhenEnabled() {
        PlanetConfig off = PlanetConfig.defaults().copy();
        assertEquals(0.0, view(off, 2_190_000L, 0.0, 0.0).declinationDegrees(), EPSILON);

        PlanetConfig on = PlanetConfig.defaults().copy();
        on.seasonsEnabled = true;
        assertEquals(23.44, view(on, 2_190_000L, 0.0, 0.0).declinationDegrees(), 0.01);
        assertEquals(-23.44, view(on, 6_570_000L, 0.0, 0.0).declinationDegrees(), 0.01);
    }

    @Test
    void skyBrightnessMatchesTheVerifiedVanillaFormula() {
        assertEquals(1.0F, LocalSkyModel.skyBrightness(0.0, 0.0F, 0.0F), 1.0e-6F);
        assertEquals(0.2F, LocalSkyModel.skyBrightness(0.5, 0.0F, 0.0F), 1.0e-6F);
        assertEquals(0.36F, LocalSkyModel.skyBrightness(0.25, 0.0F, 0.0F), 1.0e-6F);
        assertEquals(0.31F, LocalSkyModel.skyBrightness(0.25, 1.0F, 0.0F), 1.0e-6F);
        assertEquals(0.31F, LocalSkyModel.skyBrightness(0.25, 0.0F, 1.0F), 1.0e-6F);
    }

    @Test
    void moonPhaseAndMoonSizeFollowTheLocalDay() {
        assertEquals(0, LocalSkyModel.vanillaMoonPhase(0L));
        assertEquals(1, LocalSkyModel.vanillaMoonPhase(24_000L));
        assertEquals(7, LocalSkyModel.vanillaMoonPhase(7L * 24_000L));

        // A player half a rotation east crosses the day boundary twelve hours early.
        assertEquals(1, LocalSkyModel.localMoonPhase(24_000L - 1L, 0.5));
        assertEquals(0, LocalSkyModel.vanillaMoonPhase(24_000L - 1L));

        assertEquals(1.0F, LocalSkyModel.moonSize(0), 1.0e-6F);
        assertEquals(0.75F, LocalSkyModel.moonSize(1), 1.0e-6F);
        assertEquals(0.0F, LocalSkyModel.moonSize(4), 1.0e-6F);

        PlanetConfig config = PlanetConfig.defaults().copy();
        config.localMoonPhase = false;
        assertEquals(LocalSkyModel.vanillaMoonPhase(30_000L), view(config, 30_000L, 20_000.0, 0.0).moonPhase());
    }

    @Test
    void disablingTheModRestoresVanillaPhasesEverywhere() {
        PlanetConfig config = PlanetConfig.defaults().copy();
        config.enabled = false;

        for (long time : new long[] {0L, 3000L, 6000L, 13_000L, 18_000L, 20_000L}) {
            LocalSkyView reference = view(config, time, 0.0, 0.0);
            LocalSkyView farEast = view(config, time, 15_000.0, 0.0);

            assertEquals(reference.vanillaSkyAngleTurns(), reference.localSkyAngleTurns(), EPSILON);
            assertEquals(reference.vanillaSkyAngleTurns(), farEast.localSkyAngleTurns(), EPSILON,
                    "with the mod disabled every player is back on vanilla time");
        }
    }

    @Test
    void theDebugStringReportsTheLocalView() {
        String debug = view(PlanetConfig.defaults(), 6000L, 10_000.0, 0.0).toDebugString();

        assertTrue(debug.contains("longitude=90.000 deg"), debug);
        assertTrue(debug.contains("latitude=0.000 deg"), debug);
        assertTrue(debug.contains("local solar time=18:00"), debug);
        assertTrue(debug.contains("sunrise="), debug);
        assertTrue(debug.contains("moon phase="), debug);
        assertTrue(debug.contains("celestial phase="), debug);
    }
}
