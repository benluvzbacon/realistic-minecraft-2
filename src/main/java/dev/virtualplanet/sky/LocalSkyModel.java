package dev.virtualplanet.sky;

import dev.virtualplanet.config.PlanetConfig;
import dev.virtualplanet.math.Angles;
import dev.virtualplanet.math.LatitudeAxis;
import dev.virtualplanet.math.PlanetGeometry;
import dev.virtualplanet.math.PlanetTime;
import dev.virtualplanet.math.SolarCalculator;
import dev.virtualplanet.math.SolarPosition;

/**
 * Turns "shared world time + player coordinates + world border" into the local sky of one player.
 *
 * <p>Pure Java by design: the client rendering hook, the commands and the tests all go through this class,
 * so there is exactly one implementation of the mod's astronomy.
 */
public final class LocalSkyModel {
    private final PlanetConfig config;

    public LocalSkyModel(PlanetConfig config) {
        PlanetConfig copy = config == null ? PlanetConfig.defaults() : config.copy();
        copy.sanitize();
        this.config = copy;
    }

    public PlanetConfig config() {
        return config;
    }

    /**
     * Computes the local sky.
     *
     * @param sharedTime    the server's shared world time in ticks (the mod never changes it)
     * @param x             player X coordinate
     * @param z             player Z coordinate
     * @param partialTicks  tick delta in {@code [0, 1]}, only used when smoothing is enabled
     * @param borderCenterX world border centre X (authoritative value from the server)
     * @param borderCenterZ world border centre Z
     * @param borderSize    world border side length
     */
    public LocalSkyView view(long sharedTime, double x, double z, double partialTicks,
                             double borderCenterX, double borderCenterZ, double borderSize) {
        PlanetGeometry geometry = config.createGeometry(borderCenterX, borderCenterZ, borderSize);

        double longitudeOffset = geometry.longitudeOffsetTurns(x);
        double vanillaSkyAngle = PlanetTime.skyAngleTurns(sharedTime, config.rotationPeriodTicks);
        double localSkyAngle = config.enabled
                ? PlanetTime.localSkyAngleTurns(sharedTime, config.rotationPeriodTicks, longitudeOffset,
                        partialTicks, config.smoothCelestialMotion)
                : vanillaSkyAngle;

        LatitudeAxis axis = config.latitudeAxis == null ? LatitudeAxis.NONE : config.latitudeAxis;
        double latitude = geometry.latitudeRadians(axis == LatitudeAxis.X ? x : z, axis,
                config.latitudeHalfExtent(borderSize), config.latitudeRangeRadians());

        double dayOfYear = (double) Math.floorDiv(sharedTime, PlanetTime.VANILLA_DAY_TICKS);
        double declination = SolarCalculator.declinationRadians(config.axialTiltRadians(), dayOfYear,
                config.yearLengthDays, config.seasonOffsetDays, config.seasonsEnabled);

        double solarTime = PlanetTime.solarTimeTurns(localSkyAngle);
        SolarPosition computed = SolarCalculator.position(latitude, declination, solarTime);
        SolarPosition rendered = SolarCalculator.vanillaRenderedPosition(localSkyAngle);

        int vanillaMoonPhase = vanillaMoonPhase(sharedTime);
        int moonPhase = config.localMoonPhase ? localMoonPhase(sharedTime, longitudeOffset) : vanillaMoonPhase;

        double sunrise = SolarCalculator.sunriseSolarTurns(latitude, declination);
        double sunset = SolarCalculator.sunsetSolarTurns(latitude, declination);
        double civilDawn = SolarCalculator.morningTwilightTurns(latitude, declination, SolarCalculator.CIVIL_TWILIGHT_DEGREES);
        double civilDusk = SolarCalculator.eveningTwilightTurns(latitude, declination, SolarCalculator.CIVIL_TWILIGHT_DEGREES);

        return new LocalSkyView(sharedTime, x, z, geometry.longitudeRadians(x), latitude,
                vanillaSkyAngle, localSkyAngle, solarTime, declination, computed, rendered,
                moonPhase, vanillaMoonPhase, config.celestialTiltDegrees(latitude, declination),
                sunrise, sunset, civilDawn, civilDusk,
                SolarCalculator.daylightFraction(latitude, declination),
                SolarCalculator.isPolarDay(latitude, declination),
                SolarCalculator.isPolarNight(latitude, declination));
    }

    /** Convenience overload without a known world border (uses the configured circumference). */
    public LocalSkyView view(long sharedTime, double x, double z) {
        return view(sharedTime, x, z, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Vanilla's own moon phase index, verified against 1.21.1:
     * {@code (int)(time / 24000 % 8 + 8) % 8}.
     */
    public static int vanillaMoonPhase(long sharedTime) {
        return (int) (sharedTime / PlanetTime.VANILLA_DAY_TICKS % 8L + 8L) % 8;
    }

    /**
     * Local moon phase index: the lunar cycle follows the player's <i>local</i> day boundary, so players on
     * opposite sides of the virtual planet can be on different days of the lunar cycle.
     */
    public static int localMoonPhase(long sharedTime, double longitudeOffsetTurns) {
        long localDay = (long) Math.floor((double) sharedTime / (double) PlanetTime.VANILLA_DAY_TICKS + longitudeOffsetTurns);
        return (int) (localDay % 8L + 8L) % 8;
    }

    /** Vanilla moon size for a phase index ({@code 0} = full moon). */
    public static float moonSize(int moonPhase) {
        float[] sizes = {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};
        int index = moonPhase % 8;
        if (index < 0) {
            index += 8;
        }
        return sizes[index];
    }

    /**
     * Sky brightness (the value that drives the vanilla light map), replicated from 1.21.1
     * {@code ClientWorld#getSkyBrightness} so the same local sky angle can drive both the sky and the
     * brightness.
     */
    public static float skyBrightness(double skyAngleTurns, float rainGradient, float thunderGradient) {
        float f = (float) Angles.wrapTurns(skyAngleTurns);
        float g = 1.0F - (float) (Math.cos(f * (float) (Math.PI * 2)) * 2.0F + 0.2F);
        g = (float) Angles.clamp(g, 0.0F, 1.0F);
        g = 1.0F - g;
        g *= 1.0F - rainGradient * 5.0F / 16.0F;
        g *= 1.0F - thunderGradient * 5.0F / 16.0F;
        return g * 0.8F + 0.2F;
    }
}
